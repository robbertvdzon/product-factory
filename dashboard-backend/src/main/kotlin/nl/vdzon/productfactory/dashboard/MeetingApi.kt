package nl.vdzon.productfactory.dashboard

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

@RestController
@RequestMapping("/api/products/{slug}/meetings")
class MeetingApi(private val runtime: ProductFactoryRuntimeClient) {
    @GetMapping
    fun list(@PathVariable slug: String): Any = runtime.meetings(slug)

    @GetMapping("/{id}")
    fun get(@PathVariable slug: String, @PathVariable id: String): Any = runtime.meeting(slug, id)

    @GetMapping("/{id}/messages")
    fun messages(@PathVariable slug: String, @PathVariable id: String): Any = runtime.meetingMessages(slug, id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun start(@PathVariable slug: String): Any = runtime.startMeeting(slug)

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun sendMessage(@PathVariable slug: String, @PathVariable id: String, @RequestBody body: Map<String, Any?>): Any =
        runtime.sendMeetingMessage(
            slug,
            id,
            body["content"]?.toString().orEmpty(),
            (body["imageAssetIds"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty(),
        )

    @PostMapping("/{id}/close")
    fun close(@PathVariable slug: String, @PathVariable id: String): Any = runtime.closeMeeting(slug, id)

    @PostMapping("/media-library", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadMedia(
        @PathVariable slug: String,
        @RequestParam file: MultipartFile,
        @RequestParam(required = false) altText: String?,
    ): Any = runtime.uploadProductMedia(
        slug,
        file.originalFilename.orEmpty(),
        file.contentType.orEmpty(),
        file.bytes,
        altText,
    )

    @GetMapping("/media-library/{mediaId}/content")
    fun mediaContent(@PathVariable slug: String, @PathVariable mediaId: String): ResponseEntity<ByteArray> =
        runtime.productMediaContent(slug, mediaId)
}
