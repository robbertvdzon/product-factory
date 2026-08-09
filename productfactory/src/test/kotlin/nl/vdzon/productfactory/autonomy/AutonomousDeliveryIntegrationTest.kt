package nl.vdzon.productfactory.autonomy

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(AutonomousDeliveryIntegrationTest.Fakes::class)
class AutonomousDeliveryIntegrationTest(
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val service: AutonomousDeliveryService,
    @Autowired private val repository: AutonomousDeliveryRepository,
    @Autowired private val fake: FakeSoftwareFactory,
) {
    @Test
    fun `null and missing remote errors are not treated as failures`() {
        val mapper = jacksonObjectMapper()
        assertFalse(mapper.readTree("null").hasNonBlankTextValue())
        assertFalse(mapper.createObjectNode().path("error").hasNonBlankTextValue())
        assertFalse(mapper.readTree("\"   \"").hasNonBlankTextValue())
        assertTrue(mapper.readTree("\"deploy failed\"").hasNonBlankTextValue())
    }

    @Test
    fun `accepted candidate with merged workspace becomes one real start-next story`() {
        jdbc.update(
            """insert into shadow_iteration(id, product_slug, sequence_number, focus, mode, status, workspace_run_id)
                values ('auto-test-1', 'hkh-autopilot', 991, 'test', 'autonomous', 'ACCEPTED', 'auto-test-1')""".trimIndent(),
        )
        jdbc.update(
            """insert into workspace_publication(run_id, product_slug, artifact_path, content_hash, status, commit_sha)
                values ('auto-test-1', 'hkh-autopilot', 'research/auto-test-1.md', 'hash', 'MERGED', 'abcdef1234567')""".trimIndent(),
        )
        jdbc.update(
            """insert into story_candidate(product_slug, title, description, status, iteration_id, fingerprint,
                acceptance_criteria, critic_status, critic_reason)
                values ('hkh-autopilot', 'Toon bron bij locatie', 'Maak een kleine bronkaart.', 'INTERNAL',
                'auto-test-1', 'fingerprint-auto-test-1', '- bron is zichtbaar', 'ACCEPT', 'Klein en toetsbaar')""".trimIndent(),
        )
        val candidateId = jdbc.queryForObject("select id from story_candidate where fingerprint = 'fingerprint-auto-test-1'", Long::class.java)!!

        service.deliverCandidate("hkh-autopilot", candidateId)
        service.deliverCandidate("hkh-autopilot", candidateId)

        assertEquals(1, fake.requests.size)
        assertEquals("hkh-autopilot:candidate:$candidateId", fake.requests.single().second)
        assertEquals("start-next", fake.requests.single().first.deliveryMode)
        assertEquals(setOf("ERROR"), fake.requests.single().first.notificationEvents)
        assertEquals(true, fake.requests.single().first.questionsAllowed)
        assertEquals("automatisch", fake.requests.single().first.approvalMode)
        assertEquals("SF-4242", jdbc.queryForObject("select external_story_key from story_delivery where candidate_id = ?", String::class.java, candidateId))
        assertEquals("PUBLISHED", jdbc.queryForObject("select status from story_candidate where id = ?", String::class.java, candidateId))
    }

    @Test
    fun `delivery with a remote story remains eligible for reconciliation after an error`() {
        jdbc.update(
            """insert into shadow_iteration(id, product_slug, sequence_number, focus, mode, status, workspace_run_id)
                values ('auto-test-recovery', 'hkh-autopilot', 992, 'test recovery', 'autonomous', 'ACCEPTED', 'auto-test-recovery')""".trimIndent(),
        )
        jdbc.update(
            """insert into workspace_publication(run_id, product_slug, artifact_path, content_hash, status, commit_sha)
                values ('auto-test-recovery', 'hkh-autopilot', 'research/auto-test-recovery.md', 'recovery-hash', 'MERGED', 'fedcba7654321')""".trimIndent(),
        )
        jdbc.update(
            """insert into story_candidate(product_slug, title, description, status, iteration_id, fingerprint,
                acceptance_criteria, critic_status, critic_reason)
                values ('hkh-autopilot', 'Herstel synchronisatie', 'Controleer een mislukte synchronisatie opnieuw.', 'INTERNAL',
                'auto-test-recovery', 'fingerprint-auto-test-recovery', '- status herstelt', 'ACCEPT', 'Testbaar')""".trimIndent(),
        )
        val candidateId = jdbc.queryForObject(
            "select id from story_candidate where fingerprint = 'fingerprint-auto-test-recovery'",
            Long::class.java,
        )!!
        val candidate = repository.eligible("hkh-autopilot", candidateId)!!
        val delivery = repository.reserve(candidate)!!
        repository.markDelivered(delivery.id, candidateId, "SF-RECOVERY")
        repository.markDeliveryError(delivery.id, "tijdelijke fout")

        assertTrue(repository.toReconcile("hkh-autopilot").any { it.id == delivery.id })
        assertFalse(repository.active("hkh-autopilot").any { it.id == delivery.id })

        val firstQuestion = repository.registerQuestion(delivery.id, "SF-RECOVERY-TEST", "tested-with-questions", "Is handmatige validatie nodig?")
        repository.answerQuestion(firstQuestion.id, "Gebruik geautomatiseerde validatie.")
        val changedQuestion = repository.registerQuestion(
            delivery.id,
            "SF-RECOVERY-TEST",
            "tested-with-questions",
            "Kunnen twee externe testomgevingen beschikbaar worden gesteld?",
        )

        assertEquals(firstQuestion.id, changedQuestion.id)
        assertEquals("NEW", changedQuestion.status)
        assertEquals("Kunnen twee externe testomgevingen beschikbaar worden gesteld?", changedQuestion.question)
        assertEquals(1, jdbc.queryForObject("select count(*) from story_question where id = ? and answer is null", Int::class.java, changedQuestion.id))

        repository.startQuestion(changedQuestion.id, "question-stalled")
        val recoveredQuestion = repository.registerQuestion(
            delivery.id,
            "SF-RECOVERY-TEST",
            "tested-with-questions",
            "Kunnen twee externe testomgevingen beschikbaar worden gesteld?",
        )
        assertEquals(changedQuestion.id, recoveredQuestion.id)
        assertEquals("NEW", recoveredQuestion.status)
        assertEquals(1, jdbc.queryForObject("select count(*) from story_question where id = ? and agent_run_id is null", Int::class.java, recoveredQuestion.id))
        assertEquals("manual-action-done", answerPhase("awaiting-human"))
    }

    @TestConfiguration
    class Fakes {
        @Bean @Primary fun fakeSoftwareFactory() = FakeSoftwareFactory()
    }
}

class FakeSoftwareFactory : SoftwareFactoryGateway {
    val requests = mutableListOf<Pair<SoftwareFactoryStoryRequest, String>>()
    override fun createStory(request: SoftwareFactoryStoryRequest, idempotencyKey: String): SoftwareFactoryStoryResponse {
        requests += request to idempotencyKey
        return SoftwareFactoryStoryResponse("SF-4242", true, request.deliveryMode)
    }
    override fun story(storyKey: String): JsonNode = jacksonObjectMapper().createObjectNode()
    override fun answer(storyKey: String, request: SoftwareFactoryAnswerRequest) = Unit
}
