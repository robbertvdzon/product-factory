package nl.vdzon.productfactory.media

import nl.vdzon.productfactory.contracts.ProductMediaView
import nl.vdzon.productfactory.media.api.ProductMediaCatalog
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/products/{slug}/media")
class ProductMediaController(private val media: ProductMediaCatalog) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @PathVariable slug: String,
        @RequestParam file: MultipartFile,
        @RequestParam(required = false) altText: String?,
    ): ProductMediaView = media.store(
        productSlug = slug,
        filename = file.originalFilename.orEmpty(),
        mediaType = file.contentType.orEmpty().lowercase(),
        bytes = file.bytes,
        altText = altText,
        source = "owner",
        sourceReference = null,
    )

    @GetMapping("/{id}/content")
    fun content(@PathVariable slug: String, @PathVariable id: String): ResponseEntity<ByteArray> {
        val image = media.content(slug, id)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(image.metadata.mediaType))
            .contentLength(image.metadata.sizeBytes)
            .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().immutable())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(image.metadata.filename).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(image.bytes)
    }
}
