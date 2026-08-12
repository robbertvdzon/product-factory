package nl.vdzon.productfactory.iteration

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class ShadowIterationCancelTest(
    @Autowired private val service: ShadowIterationService,
    @Autowired private val repository: ShadowIterationRepository,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val mvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
) {
    @BeforeEach
    fun ensureIsolatedProduct() {
        val exists = jdbc.queryForObject(
            "select count(*) from product_definition where slug = ?",
            Long::class.java,
            PRODUCT_SLUG,
        ) ?: 0
        if (exists == 0L) {
            jdbc.update(
                "insert into product_definition(id, slug, name, mission, guardrails) values (?, ?, ?, ?, ?)",
                "product-$PRODUCT_SLUG",
                PRODUCT_SLUG,
                "Shadow iteration cancel test",
                "Test cancellation in isolation",
                "Test only",
            )
        }
    }

    @Test
    fun `cancel marks a running iteration as failed and frees the product for a new cycle`() {
        val iteration = repository.create(PRODUCT_SLUG, "Vastgelopen onderzoek")
        repository.markRunning(iteration.id)
        assertEquals(true, repository.hasActive(PRODUCT_SLUG))

        val cancelled = service.cancel(PRODUCT_SLUG, iteration.id, "Handmatig gestopt in test")

        assertEquals("FAILED", cancelled.status)
        assertEquals("Handmatig gestopt in test", cancelled.errorMessage)
        assertEquals(cancelled.completedAt, cancelled.decision?.decidedAt)
        assertEquals(iteration.id, cancelled.decision?.iterationId)
        assertEquals("HUMAN", cancelled.decision?.actorType)
        assertEquals("MANUAL_CANCELLATION", cancelled.decision?.mechanism)
        assertEquals("MANUALLY_CANCELLED", cancelled.decision?.reasonCode)
        assertEquals(false, repository.hasActive(PRODUCT_SLUG))
    }

    @Test
    fun `cancel API returns the atomic decision in detail and list without copying the supplied reason`() {
        val iteration = repository.create(PRODUCT_SLUG, "API-annulering")
        repository.markRunning(iteration.id)
        val suppliedReason = "Vrije invoer die niet in provenance thuishoort"

        val response = mvc.post("/api/shadow-iterations/${iteration.id}/cancel") {
            param("productSlug", PRODUCT_SLUG)
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("reason" to suppliedReason))
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("FAILED") }
            jsonPath("$.errorMessage") { value(suppliedReason) }
            jsonPath("$.decision.iterationId") { value(iteration.id) }
            jsonPath("$.decision.actorType") { value("HUMAN") }
            jsonPath("$.decision.mechanism") { value("MANUAL_CANCELLATION") }
            jsonPath("$.decision.reasonCode") { value("MANUALLY_CANCELLED") }
        }.andReturn()

        val cancelledJson = objectMapper.readTree(response.response.contentAsString)
        assertEquals(cancelledJson.path("completedAt").asText(), cancelledJson.path("decision").path("decidedAt").asText())
        assertEquals(
            setOf("iterationId", "actorType", "mechanism", "reasonCode", "decidedAt"),
            cancelledJson.path("decision").fieldNames().asSequence().toSet(),
        )
        assertTrue(cancelledJson.path("decision").toString().contains(suppliedReason).not())

        mvc.get("/api/shadow-iterations/${iteration.id}") {
            param("productSlug", PRODUCT_SLUG)
        }.andExpect {
            status { isOk() }
            jsonPath("$.completedAt") { value(cancelledJson.path("completedAt").asText()) }
            jsonPath("$.decision.decidedAt") { value(cancelledJson.path("completedAt").asText()) }
        }
        mvc.get("/api/shadow-iterations") {
            param("productSlug", PRODUCT_SLUG)
        }.andExpect {
            status { isOk() }
            jsonPath("$[?(@.id == '${iteration.id}')].decision.reasonCode") { value("MANUALLY_CANCELLED") }
        }
    }

    @Test
    fun `cancel rejects an iteration that already finished`() {
        val iteration = repository.create(PRODUCT_SLUG, "Al afgerond onderzoek")
        repository.markRunning(iteration.id)
        repository.markFailed(iteration.id, "Al eerder mislukt")

        assertFailsWith<ResponseStatusException> {
            service.cancel(PRODUCT_SLUG, iteration.id, null)
        }
        assertNull(repository.require(PRODUCT_SLUG, iteration.id).decision)
    }

    @Test
    fun `decision insert conflict rolls back the status transition`() {
        val iteration = repository.create(PRODUCT_SLUG, "Rollback bij beslisrecordconflict")
        repository.markRunning(iteration.id)
        jdbc.update(
            """insert into shadow_iteration_decision(iteration_id, actor_type, mechanism, reason_code, decided_at)
                values (?, 'HUMAN', 'MANUAL_CANCELLATION', 'MANUALLY_CANCELLED', current_timestamp)""".trimIndent(),
            iteration.id,
        )

        try {
            assertFailsWith<DataIntegrityViolationException> {
                service.cancel(PRODUCT_SLUG, iteration.id, "Mag niet half worden opgeslagen")
            }

            val afterConflict = repository.require(PRODUCT_SLUG, iteration.id)
            assertEquals("RUNNING", afterConflict.status)
            assertNull(afterConflict.completedAt)
            assertEquals(1, jdbc.queryForObject(
                "select count(*) from shadow_iteration_decision where iteration_id = ?",
                Int::class.java,
                iteration.id,
            ))
        } finally {
            repository.markFailed(iteration.id, "Testfixture opruimen")
        }
    }

    // Simuleert dat de achtergrondthread nog bezig is met de agentworker op het moment van annuleren:
    // die thread weet niets van de annulering en probeert later alsnog af te ronden. De write-once-guard
    // op de terminale status (zie ShadowIterationRepository.markAccepted) moet die late poging negeren.
    @Test
    fun `a late completion from the original run after cancel is ignored`() {
        val iteration = repository.create(PRODUCT_SLUG, "Race met achtergrondthread")
        repository.markRunning(iteration.id)
        service.cancel(PRODUCT_SLUG, iteration.id, null)

        repository.markAccepted(iteration.id, "ACCEPT", "late-run-id", null, null)

        assertEquals("FAILED", repository.require(PRODUCT_SLUG, iteration.id).status)
    }

    private companion object {
        const val PRODUCT_SLUG = "shadow-iteration-cancel-test"
    }
}
