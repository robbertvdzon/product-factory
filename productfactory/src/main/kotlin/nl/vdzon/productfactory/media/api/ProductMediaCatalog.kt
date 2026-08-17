package nl.vdzon.productfactory.media.api

import nl.vdzon.productfactory.contracts.ProductMediaView
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.sql.ResultSet
import java.util.UUID
import javax.imageio.ImageIO
import java.io.ByteArrayInputStream

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
        val (width, height) = imageDimensions(mediaType, bytes)
        require(width in 1..MAX_IMAGE_DIMENSION && height in 1..MAX_IMAGE_DIMENSION && width.toLong() * height <= MAX_IMAGE_PIXELS) {
            "Afbeeldingsafmetingen zijn ongeldig of te groot"
        }
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

    private fun imageDimensions(mediaType: String, bytes: ByteArray): Pair<Int, Int> {
        if (mediaType != "image/webp") {
            val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: throw IllegalArgumentException("Afbeeldingsafmetingen zijn onleesbaar")
            return image.width to image.height
        }
        require(bytes.size >= 30) { "WebP-afbeelding is onvolledig" }
        fun u(index: Int) = bytes[index].toInt() and 0xff
        return when (bytes.decodeToString(12, 16)) {
            "VP8X" -> (1 + u(24) + (u(25) shl 8) + (u(26) shl 16)) to
                (1 + u(27) + (u(28) shl 8) + (u(29) shl 16))
            "VP8L" -> {
                require(u(20) == 0x2f) { "Ongeldige WebP-lossless-header" }
                (1 + u(21) + ((u(22) and 0x3f) shl 8)) to
                    (1 + ((u(22) and 0xc0) shr 6) + (u(23) shl 2) + ((u(24) and 0x0f) shl 10))
            }
            "VP8 " -> {
                require(u(23) == 0x9d && u(24) == 0x01 && u(25) == 0x2a) { "Ongeldige WebP-header" }
                ((u(26) + (u(27) shl 8)) and 0x3fff) to ((u(28) + (u(29) shl 8)) and 0x3fff)
            }
            else -> throw IllegalArgumentException("Onbekende WebP-header")
        }
    }

    companion object {
        const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
        const val MAX_IMAGES_PER_MESSAGE = 5
        const val MAX_IMAGE_DIMENSION = 4096
        const val MAX_IMAGE_PIXELS = 16_000_000L
        val ALLOWED_MEDIA_TYPES = setOf("image/png", "image/jpeg", "image/webp", "image/gif")
        private const val SELECT = "select id, product_slug, filename, media_type, size_bytes, alt_text, source, source_reference, created_at from product_media"
    }
}
