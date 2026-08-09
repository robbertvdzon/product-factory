package nl.vdzon.productfactory.iteration

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals

/**
 * AC5/AC7 van product-25: een tweede, latere schrijfpoging op de conclusion-waarde
 * (status/critic_verdict) van een al terminale iteratie mag de eerste, afgeronde
 * uitkomst niet overschrijven.
 */
@SpringBootTest
class ShadowIterationRepositoryWriteOnceGuardTest(
    @Autowired private val repository: ShadowIterationRepository,
) {
    @Test
    fun `markAccepted negeert een tweede schrijfpoging na een al afgeronde acceptatie`() {
        val iteration = repository.create("hkh-autopilot", "Simuleer twee opeenvolgende afrondingen van dezelfde iteratie")
        repository.markAccepted(iteration.id, "ACCEPT", "run-1", "https://example.invalid/pr/1", "sha-1")

        repository.markAccepted(iteration.id, "ACCEPT-OPNIEUW", "run-2", "https://example.invalid/pr/2", "sha-2")

        val stored = repository.require("hkh-autopilot", iteration.id)
        assertEquals("ACCEPTED", stored.status)
        assertEquals("ACCEPT", stored.criticVerdict)
        assertEquals("run-1", stored.workspaceRunId)
        assertEquals("https://example.invalid/pr/1", stored.workspacePullRequestUrl)
        assertEquals("sha-1", stored.workspaceCommitSha)
    }

    @Test
    fun `markReviewed en markFailed negeren een schrijfpoging na een al terminale staat`() {
        val iteration = repository.create("hkh-autopilot", "Simuleer een tweede, ongerelateerde gebeurtenis na afronding")
        repository.markReviewed(iteration.id, "REVISE", "NEEDS_REVISION")

        repository.markFailed(iteration.id, "Ongerelateerde latere fout die de conclusie niet mag overschrijven")

        val stored = repository.require("hkh-autopilot", iteration.id)
        assertEquals("NEEDS_REVISION", stored.status)
        assertEquals("REVISE", stored.criticVerdict)
        assertEquals(null, stored.errorMessage)
    }
}
