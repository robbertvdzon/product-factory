package nl.vdzon.productfactory.iteration

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
class ShadowIterationCancelTest(
    @Autowired private val service: ShadowIterationService,
    @Autowired private val repository: ShadowIterationRepository,
) {
    @Test
    fun `cancel marks a running iteration as failed and frees the product for a new cycle`() {
        val iteration = repository.create("hkh-autopilot", "Vastgelopen onderzoek")
        repository.markRunning(iteration.id)
        assertEquals(true, repository.hasActive("hkh-autopilot"))

        val cancelled = service.cancel("hkh-autopilot", iteration.id, "Handmatig gestopt in test")

        assertEquals("FAILED", cancelled.status)
        assertEquals("Handmatig gestopt in test", cancelled.errorMessage)
        assertEquals(false, repository.hasActive("hkh-autopilot"))
    }

    @Test
    fun `cancel rejects an iteration that already finished`() {
        val iteration = repository.create("hkh-autopilot", "Al afgerond onderzoek")
        repository.markRunning(iteration.id)
        repository.markFailed(iteration.id, "Al eerder mislukt")

        assertFailsWith<ResponseStatusException> {
            service.cancel("hkh-autopilot", iteration.id, null)
        }
    }

    // Simuleert dat de achtergrondthread nog bezig is met de agentworker op het moment van annuleren:
    // die thread weet niets van de annulering en probeert later alsnog af te ronden. De write-once-guard
    // op de terminale status (zie ShadowIterationRepository.markAccepted) moet die late poging negeren.
    @Test
    fun `a late completion from the original run after cancel is ignored`() {
        val iteration = repository.create("hkh-autopilot", "Race met achtergrondthread")
        repository.markRunning(iteration.id)
        service.cancel("hkh-autopilot", iteration.id, null)

        repository.markAccepted(iteration.id, "ACCEPT", "late-run-id", null, null)

        assertEquals("FAILED", repository.require("hkh-autopilot", iteration.id).status)
    }
}
