package nl.vdzon.productfactory.roadmap

import nl.vdzon.productfactory.agentruntime.api.AgentDispatchPort
import nl.vdzon.productfactory.contracts.AgentResult
import nl.vdzon.productfactory.product.CreateProductRequest
import nl.vdzon.productfactory.product.api.ProductCatalog
import nl.vdzon.productfactory.roadmap.api.DeliveryVerificationRepository
import nl.vdzon.productfactory.roadmap.api.RoadmapCatalog
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Dekt de opleverchecker (zie DeliveryVerificationEngine): een bevestigd opgeleverde, aan een thema
 * gekoppelde kandidaat wordt geverifieerd, een conclusief rapport wordt niet herhaald en een tijdelijk
 * INCONCLUSIVE-rapport kan opnieuw worden geprobeerd.
 */
@SpringBootTest
@Import(DeliveryVerificationEngineTest.Fakes::class)
class DeliveryVerificationEngineTest(
    @Autowired private val engine: DeliveryVerificationEngine,
    @Autowired private val reports: DeliveryVerificationRepository,
    @Autowired private val roadmap: RoadmapCatalog,
    @Autowired private val products: ProductCatalog,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val agent: CapturingDeliveryVerificationAgent,
) {
    private val slug = "delivery-verification-test"

    @BeforeEach
    fun ensureTestProduct() {
        runCatching {
            products.create(
                CreateProductRequest(
                    slug = slug,
                    name = "Opleverchecker-testproduct",
                    mission = "Test de opleverchecker",
                    liveUrl = "https://example.test",
                    acceptanceUrl = "https://acceptance.example.test",
                    adminUrl = "https://admin.example.test",
                    status = "active",
                    developmentMode = "autonomous",
                ).configuration(),
            )
        }
        agent.tasks.clear()
        jdbc.update("delete from delivery_verification where product_slug = ?", slug)
        jdbc.update("delete from story_delivery where product_slug = ?", slug)
        jdbc.update("delete from story_candidate where product_slug = ?", slug)
        jdbc.update("delete from workspace_publication where product_slug = ?", slug)
        jdbc.update("delete from shadow_iteration where product_slug = ?", slug)
        jdbc.update("delete from roadmap_theme where product_slug = ?", slug)
        jdbc.update("delete from agent_run where product_slug = ?", slug)
    }

    @Test
    fun `a confirmed-deployed candidate linked to a theme is verified exactly once`() {
        val theme = roadmap.createTheme(slug, "Brontransparantie", "Toon rechten- en bronvermelding overal.", "HIGH")
        val candidateId = insertConfirmedDeployedCandidate(theme.id)

        val product = products.requireProduct(slug)
        val results = engine.verifyPending(product, "test-session-1")

        assertEquals(1, results.size)
        assertEquals("SATISFIES", results.single().verdict)
        assertEquals(candidateId, results.single().candidateId)
        val prompt = agent.tasks.single().prompt
        assertTrue(prompt.contains("PUBLIEKE PRODUCTIEAPP: https://example.test"))
        assertTrue(prompt.contains("ACCEPTATIEOMGEVING: https://acceptance.example.test"))
        assertTrue(prompt.contains("BEHEEROMGEVING (secundair): https://admin.example.test"))
        assertTrue(prompt.contains("probeer nooit in te loggen"))

        val stored = reports.forTheme(slug, theme.id)
        assertEquals(1, stored.size)
        assertTrue(stored.single().report!!.contains("Bronvermelding is zichtbaar"))

        // Tweede aanroep: de kandidaat heeft al een rapport, dus die wordt niet nogmaals geverifieerd.
        val second = engine.verifyPending(product, "test-session-2")
        assertEquals(0, second.size)
        assertEquals(1, reports.forTheme(slug, theme.id).size)
    }

    @Test
    fun `a candidate without a confirmed deploy is not offered for verification`() {
        val theme = roadmap.createTheme(slug, "Nog niet live", "Dit thema heeft nog geen bevestigde deploy.", "MEDIUM")
        insertCandidate(theme.id, confirmedDeployed = false)

        val product = products.requireProduct(slug)
        val results = engine.verifyPending(product, "test-session-3")

        assertEquals(0, results.size)
    }

    @Test
    fun `an inconclusive verification is replaced by a later conclusive retry`() {
        val theme = roadmap.createTheme(slug, "Opnieuw controleren", "Test opnieuw na een tijdelijke browserfout.", "HIGH")
        val candidateId = insertConfirmedDeployedCandidate(theme.id)
        reports.save(
            "inconclusive-run",
            slug,
            theme.id,
            candidateId,
            "INCONCLUSIVE",
            "De browseromgeving was tijdelijk niet bereikbaar.",
        )

        val results = engine.verifyPending(products.requireProduct(slug), "retry-session")

        assertEquals(1, results.size)
        assertEquals("SATISFIES", results.single().verdict)
        val stored = reports.forTheme(slug, theme.id)
        assertEquals(1, stored.size)
        assertEquals("SATISFIES", stored.single().verdict)
    }

    @Test
    fun `an inconclusive verification is retried at most once`() {
        val theme = roadmap.createTheme(slug, "Begrensd opnieuw controleren", "Blokkeer de wachtrij niet blijvend.", "HIGH")
        val candidateId = insertConfirmedDeployedCandidate(theme.id)
        reports.save("inconclusive-run-1", slug, theme.id, candidateId, "INCONCLUSIVE", "Eerste poging onzeker.")
        reports.save("inconclusive-run-2", slug, theme.id, candidateId, "INCONCLUSIVE", "Tweede poging onzeker.")

        val results = engine.verifyPending(products.requireProduct(slug), "third-session")

        assertEquals(0, results.size)
        assertEquals("INCONCLUSIVE", reports.forTheme(slug, theme.id).single().verdict)
    }

    private fun insertConfirmedDeployedCandidate(themeId: String) = insertCandidate(themeId, confirmedDeployed = true)

    private fun insertCandidate(themeId: String, confirmedDeployed: Boolean): Long {
        val iterationId = "verify-test-${java.util.UUID.randomUUID()}"
        val sequence = (jdbc.queryForObject("select coalesce(max(sequence_number),0)+1 from shadow_iteration where product_slug = ?", Int::class.java, slug) ?: 1)
        jdbc.update(
            """insert into shadow_iteration(id, product_slug, sequence_number, focus, mode, status, workspace_run_id)
                values (?, ?, ?, 'test', 'autonomous', 'ACCEPTED', ?)""".trimIndent(),
            iterationId, slug, sequence, iterationId,
        )
        jdbc.update(
            """insert into story_candidate(product_slug, title, description, status, iteration_id, fingerprint,
                acceptance_criteria, critic_status, critic_reason, theme_id)
                values (?, 'Themagekoppelde bronvermelding', 'Toon de bron bij elk resultaat.', 'PUBLISHED', ?, ?,
                '- bron is zichtbaar', 'ACCEPT', 'Klein en toetsbaar', ?)""".trimIndent(),
            slug, iterationId, "fingerprint-$iterationId", themeId,
        )
        val candidateId = jdbc.queryForObject("select id from story_candidate where fingerprint = ?", Long::class.java, "fingerprint-$iterationId")!!
        jdbc.update(
            """insert into story_delivery(product_slug, candidate_id, iteration_id, workspace_run_id, workspace_commit_sha,
                artifact_path, idempotency_key, status, external_story_key, confirmed_deployed, deployed_at)
                values (?, ?, ?, ?, 'abcdef1234567', 'research/x.md', ?, 'DONE', ?, ?, case when ? then current_timestamp else null end)""".trimIndent(),
            slug, candidateId, iterationId, iterationId, "$slug:candidate:$candidateId", "SF-$candidateId", confirmedDeployed, confirmedDeployed,
        )
        return candidateId
    }

    @TestConfiguration
    class Fakes {
        @Bean
        @Primary
        fun fakeAgentDispatch() = CapturingDeliveryVerificationAgent()
    }

    class CapturingDeliveryVerificationAgent : AgentDispatchPort {
        val tasks = mutableListOf<nl.vdzon.productfactory.contracts.AgentTask>()

        override fun execute(task: nl.vdzon.productfactory.contracts.AgentTask): AgentResult {
            tasks += task
            val summary = when (task.taskType) {
                "delivery-verification" -> """{"verdict":"SATISFIES","report":"Bronvermelding is zichtbaar bij elk resultaat, precies zoals bedoeld."}"""
                else -> """{"summary":"onbekend"}"""
            }
            return AgentResult(runId = task.runId, status = "COMPLETED", summary = summary)
        }
    }
}
