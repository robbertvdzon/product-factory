package nl.vdzon.productfactory.meeting.api

import nl.vdzon.productfactory.product.CreateProductRequest
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.server.ResponseStatusException
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
class MeetingCatalogTest(
    @Autowired private val meetings: MeetingCatalog,
    @Autowired private val products: ProductCatalog,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val slug = "meeting-catalog-test"

    @BeforeEach
    fun ensureTestProduct() {
        runCatching {
            products.create(
                CreateProductRequest(
                    slug = slug,
                    name = "Overlegtestproduct",
                    mission = "Test de overlegflow los van andere producten",
                    status = "active",
                    developmentMode = "manual",
                ).configuration(),
            )
        }
        products.clearMeetingRequested(slug)
        jdbc.update("delete from meeting_message where product_slug = ?", slug)
        jdbc.update("delete from product_media where product_slug = ?", slug)
        jdbc.update("delete from meeting where product_slug = ?", slug)
    }

    @Test
    fun `requestMeeting sets the pending flag when nothing blocks it`() {
        meetings.requestMeeting(slug, listOf(" Eerste onderwerp ", "  ", "Tweede onderwerp"))

        val product = products.requireProduct(slug)
        assertTrue(product.meetingRequestedAt != null)
        assertEquals(listOf("Eerste onderwerp", "Tweede onderwerp"), product.meetingRequestedTopics)
    }

    @Test
    fun `requestMeeting is a no-op when a pending flag already exists`() {
        meetings.requestMeeting(slug, listOf("Eerste"))
        val firstRequestedAt = products.requireProduct(slug).meetingRequestedAt

        meetings.requestMeeting(slug, listOf("Tweede, moet genegeerd worden"))

        val product = products.requireProduct(slug)
        assertEquals(firstRequestedAt, product.meetingRequestedAt)
        assertEquals(listOf("Eerste"), product.meetingRequestedTopics)
    }

    @Test
    fun `requestMeeting is a no-op within 7 days of the last closed meeting`() {
        insertClosedMeeting("meeting-test-recent", closedDaysAgo = 3)

        meetings.requestMeeting(slug, listOf("Te vroeg"))

        assertNull(products.requireProduct(slug).meetingRequestedAt)
    }

    @Test
    fun `requestMeeting succeeds again 7 days after the last closed meeting`() {
        insertClosedMeeting("meeting-test-old", closedDaysAgo = 8)

        meetings.requestMeeting(slug, listOf("Weer toegestaan"))

        assertTrue(products.requireProduct(slug).meetingRequestedAt != null)
    }

    @Test
    fun `create rejects a second open meeting for the same product`() {
        meetings.create(slug)
        assertFailsWith<ResponseStatusException> { meetings.create(slug) }
    }

    @Test
    fun `create attaches and clears a pending product-initiated topic list`() {
        meetings.requestMeeting(slug, listOf("Onderwerp A"))

        val meeting = meetings.create(slug)

        assertEquals("product", meeting.initiator)
        assertEquals(listOf("Onderwerp A"), meeting.requestedTopics)
        assertNull(products.requireProduct(slug).meetingRequestedAt)
    }

    @Test
    fun `create defaults to owner initiator without a pending flag`() {
        val meeting = meetings.create(slug)

        assertEquals("owner", meeting.initiator)
        assertEquals(emptyList(), meeting.requestedTopics)
    }

    @Test
    fun `recentOutcomes falls back to a fixed message when there is nothing to show`() {
        assertEquals("Nog geen eerdere overleggen met de eigenaar.", meetings.recentOutcomes(slug))
    }

    @Test
    fun `recentOutcomes orders closed meetings newest first`() {
        insertClosedMeeting("meeting-outcome-oud", closedDaysAgo = 10, outcome = "Oudere uitkomst")
        insertClosedMeeting("meeting-outcome-nieuw", closedDaysAgo = 1, outcome = "Nieuwere uitkomst")

        val outcomes = meetings.recentOutcomes(slug)

        assertTrue(outcomes.indexOf("Nieuwere uitkomst") < outcomes.indexOf("Oudere uitkomst"))
    }

    private fun insertClosedMeeting(id: String, closedDaysAgo: Long, outcome: String = "Testuitkomst") {
        val sequence = jdbc.queryForObject(
            "select coalesce(max(sequence_number), 0) + 1 from meeting where product_slug = ?",
            Int::class.java,
            slug,
        ) ?: 1
        jdbc.update(
            """insert into meeting(id, product_slug, sequence_number, initiator, status, outcome_summary, closed_at)
                values (?, ?, ?, 'owner', 'CLOSED', ?, ?)""".trimIndent(),
            id,
            slug,
            sequence,
            outcome,
            Timestamp.from(Instant.now().minus(Duration.ofDays(closedDaysAgo))),
        )
    }
}
