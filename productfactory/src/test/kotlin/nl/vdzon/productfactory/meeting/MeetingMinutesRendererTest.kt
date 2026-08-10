package nl.vdzon.productfactory.meeting

import nl.vdzon.productfactory.contracts.MeetingMessageView
import nl.vdzon.productfactory.contracts.MeetingView
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertTrue

class MeetingMinutesRendererTest {
    @Test
    fun `renders front matter, topics, summary and the full transcript`() {
        val meeting = MeetingView(
            id = "meeting-castle-guide-0001",
            productSlug = "castle-guide",
            sequenceNumber = 1,
            initiator = "product",
            status = "CLOSED",
            requestedTopics = listOf("UI-overload", "onzichtbare afkeuringsreden"),
            outcomeSummary = null,
            createdAt = Instant.now(),
            closedAt = Instant.now(),
        )
        val messages = listOf(
            MeetingMessageView(1, meeting.id, "owner", "Wat vind je van de huidige richting?", Instant.now()),
            MeetingMessageView(2, meeting.id, "ai", "Ik zou eerst de UI-overload aanpakken.", Instant.now()),
        )

        val markdown = MeetingMinutesRenderer.render(meeting, messages, "Besloten om eerst UI-overload aan te pakken.", LocalDate.of(2026, 8, 10))

        assertTrue(markdown.startsWith("---\nproduct: castle-guide\nartifact_type: meeting\nrun_id: meeting-castle-guide-0001\ndate: 2026-08-10\nstatus: closed\n---"))
        assertTrue(markdown.contains("# Overleg 1"))
        assertTrue(markdown.contains("het product zelf (aangevraagd)"))
        assertTrue(markdown.contains("- UI-overload"))
        assertTrue(markdown.contains("## Samenvatting"))
        assertTrue(markdown.contains("Besloten om eerst UI-overload aan te pakken."))
        assertTrue(markdown.contains("**Eigenaar:** Wat vind je van de huidige richting?"))
        assertTrue(markdown.contains("**AI:** Ik zou eerst de UI-overload aanpakken."))
    }
}
