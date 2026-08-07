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
        assertEquals("SF-4242", jdbc.queryForObject("select external_story_key from story_delivery where candidate_id = ?", String::class.java, candidateId))
        assertEquals("PUBLISHED", jdbc.queryForObject("select status from story_candidate where id = ?", String::class.java, candidateId))
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
