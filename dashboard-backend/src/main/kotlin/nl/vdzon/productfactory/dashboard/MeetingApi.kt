package nl.vdzon.productfactory.dashboard

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

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
    fun sendMessage(@PathVariable slug: String, @PathVariable id: String, @RequestBody body: Map<String, String>): Any =
        runtime.sendMeetingMessage(slug, id, body["content"].orEmpty())

    @PostMapping("/{id}/close")
    fun close(@PathVariable slug: String, @PathVariable id: String): Any = runtime.closeMeeting(slug, id)
}
