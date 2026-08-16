package nl.vdzon.productfactory.media.api

import nl.vdzon.productfactory.contracts.ProductMediaView
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.sql.ResultSet
import java.util.UUID

data class ProductMediaContent(val metadata: ProductMediaView, val bytes: ByteArray)

@Service
class ProductMediaCatalog(private val jdbc: JdbcTemplate, private val products: ProductCatalog) {
    fun store(
        productSlug: String,
        filename: String,
        mediaType: String,
        bytes: ByteArray,
        altText: String?,
        source: String,
        sourceReference: String?,
    ): ProductMediaView {
        val product = products.requireContext(productSlug)
        require(mediaType in ALLOWED_MEDIA_TYPES) { "Alleen PNG-, JPEG-, WebP- en GIF-afbeeldingen zijn toegestaan" }
        require(bytes.isNotEmpty() && bytes.size <= MAX_IMAGE_BYTES) { "Een afbeelding moet tussen 1 byte en 5 MB groot zijn" }
        require(hasExpectedSignature(mediaType, bytes)) { "De bestandsinhoud komt niet overeen met het afbeeldingstype" }
        require(source in setOf("owner", "ai")) { "Ongeldige mediabron" }
        val safeFilename = filename.substringAfterLast('/').substringAfterLast('\\').trim().take(255).ifBlank { "afbeelding" }
        val cleanAltText = altText?.trim()?.take(1000)?.ifBlank { null }
        val id = "media-${UUID.randomUUID()}"
        jdbc.update(
            """insert into product_media(id, product_slug, filename, media_type, size_bytes, alt_text, source, source_reference, content)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
            id, product.slug, safeFilename, mediaType, bytes.size.toLong(), cleanAltText, source, sourceReference?.take(255), bytes,
        )
        return require(product.slug, id)
    }

    fun list(productSlug: String, limit: Int = 100): List<ProductMediaView> {
        val product = products.requireContext(productSlug)
        return jdbc.query(
            SELECT + " where product_slug = ? order by created_at desc limit ?",
            ::map,
            product.slug,
            limit.coerceIn(1, 200),
        )
    }

    fun require(productSlug: String, id: String): ProductMediaView {
        val product = products.requireContext(productSlug)
        return jdbc.query(SELECT + " where product_slug = ? and id = ?", ::map, product.slug, id).singleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Onbekende afbeelding voor dit product")
    }

    fun requireAll(productSlug: String, ids: List<String>): List<ProductMediaView> {
        val distinctIds = ids.map(String::trim).filter(String::isNotBlank).distinct()
        require(distinctIds.size <= MAX_IMAGES_PER_MESSAGE) { "Een bericht mag maximaal 5 afbeeldingen bevatten" }
        return distinctIds.map { require(productSlug, it) }
    }

    fun content(productSlug: String, id: String): ProductMediaContent {
        val metadata = require(productSlug, id)
        val bytes = jdbc.queryForObject("select content from product_media where id = ?", ByteArray::class.java, id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Afbeeldingsinhoud ontbreekt")
        return ProductMediaContent(metadata, bytes)
    }

    fun context(productSlug: String, baseUrl: String, limit: Int = 30): String = list(productSlug, limit)
        .joinToString("\n") {
            "${it.id} | ${it.filename} | ${it.altText ?: "geen beschrijving"} | bron ${it.source}" +
                " | $baseUrl/api/products/${it.productSlug}/media/${it.id}/content"
        }
        .ifBlank { "Geen opgeslagen afbeeldingen." }

    private fun map(row: ResultSet, ignored: Int) = ProductMediaView(
        id = row.getString("id"),
        productSlug = row.getString("product_slug"),
        filename = row.getString("filename"),
        mediaType = row.getString("media_type"),
        sizeBytes = row.getLong("size_bytes"),
        altText = row.getString("alt_text"),
        source = row.getString("source"),
        sourceReference = row.getString("source_reference"),
        createdAt = row.getTimestamp("created_at").toInstant(),
    )

    private fun hasExpectedSignature(mediaType: String, bytes: ByteArray): Boolean = when (mediaType) {
        "image/png" -> bytes.startsWith(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        "image/jpeg" -> bytes.startsWith(0xff, 0xd8, 0xff)
        "image/gif" -> bytes.decodeToString(0, minOf(bytes.size, 6)) in setOf("GIF87a", "GIF89a")
        "image/webp" -> bytes.size >= 12 && bytes.decodeToString(0, 4) == "RIFF" && bytes.decodeToString(8, 12) == "WEBP"
        else -> false
    }

    private fun ByteArray.startsWith(vararg signature: Int): Boolean = size >= signature.size &&
        signature.indices.all { index -> this[index].toInt() and 0xff == signature[index] }

    companion object {
        const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
        const val MAX_IMAGES_PER_MESSAGE = 5
        val ALLOWED_MEDIA_TYPES = setOf("image/png", "image/jpeg", "image/webp", "image/gif")
        private const val SELECT = "select id, product_slug, filename, media_type, size_bytes, alt_text, source, source_reference, created_at from product_media"
    }
}
