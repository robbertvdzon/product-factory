package nl.vdzon.productfactory.meeting

import nl.vdzon.productfactory.contracts.MeetingMessageView
import nl.vdzon.productfactory.contracts.MeetingView
import nl.vdzon.productfactory.meeting.api.MeetingCatalog
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class SendMeetingMessageRequest(val content: String)

@RestController
@RequestMapping("/api/products/{slug}/meetings")
class MeetingController(private val catalog: MeetingCatalog, private val chat: MeetingChatService) {
    @GetMapping
    fun list(@PathVariable slug: String): List<MeetingView> = catalog.list(slug)

    @GetMapping("/{id}")
    fun get(@PathVariable slug: String, @PathVariable id: String): MeetingView = catalog.require(slug, id)

    @GetMapping("/{id}/messages")
    fun messages(@PathVariable slug: String, @PathVariable id: String): List<MeetingMessageView> = catalog.messages(slug, id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun start(@PathVariable slug: String): MeetingView = catalog.create(slug)

    /** Synchroon: staat open tot de AI heeft geantwoord (tot enkele minuten), zie MeetingChatService. */
    @PostMapping("/{id}/messages")
    fun sendMessage(@PathVariable slug: String, @PathVariable id: String, @RequestBody request: SendMeetingMessageRequest): MeetingMessageView =
        chat.sendTurn(slug, id, request.content)

    @PostMapping("/{id}/close")
    fun close(@PathVariable slug: String, @PathVariable id: String): MeetingView = chat.closeOut(slug, id)
}
