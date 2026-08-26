package nl.vdzon.productfactory.dispatcher

import nl.vdzon.productfactory.api.dispatcher.*
import nl.vdzon.productfactory.api.planning.*
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.quality.QualityQueryService
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.planning.mvp.ProductPlanningMvpService
import nl.vdzon.productfactory.quality.mvp.QualityMvpService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Clock
import java.util.UUID

@SpringBootTest(properties = ["PF_AUTH_REQUIRED=false", "PF_SOFTWARE_FACTORY_MODE=MOCKED"])
@ActiveProfiles("test")
class SoftwareFactoryDispatcherIntegrationTest @Autowired constructor(
    private val productCommands: ProductCommandService,
    private val dispatcher: SoftwareFactoryDispatcherService,
    private val queries: SoftwareFactoryDispatcherQueryService,
    private val planningQueries: ProductPlanningQueryService,
    private val planningImpl: ProductPlanningMvpService,
    private val qualityImpl: QualityMvpService,
    private val qualityQueries: QualityQueryService,
    private val dispatcherImpl: SoftwareFactoryDispatcherMvpService,
    private val mock: MockSoftwareFactory,
    private val jdbc: JdbcTemplate,
    private val clock: Clock,
) {
    private var productId = ProductId("not-initialized")
    private var firstStory = StoryId("not-initialized")

    @BeforeEach
    fun prepare() {
        qualityImpl.deleteAllOwnedData()
        dispatcherImpl.deleteAllOwnedData()
        planningImpl.deleteAllOwnedData()
        mock.reset()
        productId = ProductId("dispatcher-${UUID.randomUUID().toString().take(8)}")
        productCommands.createProduct(CreateProductCommand(productId, "Dispatcher test", actor = STAKEHOLDER, idempotencyKey = "create-${productId.value}"))
        productCommands.updateProductAssignment(UpdateProductAssignmentCommand(
            productId, "Gebruikers", "Lever precies één zelfstandige story", listOf("Geen credentials"),
            "https://github.com/robbertvdzon/hkh-autopilot.git", 0, STAKEHOLDER, "assignment-${productId.value}",
        ))
        productCommands.setProductDispatching(SetProductDispatchingCommand(productId, true, 1, STAKEHOLDER, "dispatching-${productId.value}"))
        firstStory = insertStory(1)
        insertStory(2)
    }

    @AfterEach
    fun cleanDispatcherData() {
        dispatcherImpl.deleteAllOwnedData()
    }

    @Test
    fun `versturen en herhalen maken een externe story en reserveren alleen de eerste story`() {
        dispatcher.runDispatchSession(productId)
        dispatcher.runDispatchSession(productId)

        val attempts = queries.findDeliveryAttempts(DeliveryAttemptFilter(productId))
        assertThat(attempts).hasSize(1)
        assertThat(attempts.single().status).isEqualTo(DeliveryAttemptStatus.ACCEPTED)
        assertThat(attempts.single().externalStoryId).startsWith("SF-")
        assertThat(mock.find(productId.value, "OPEN")).hasSize(1)
        assertThat(planningQueries.getStory(firstStory).status).isEqualTo(StoryStatus.IN_PROGRESS)
        assertThat(planningQueries.getBacklog(productId).map { it.sequenceNumber }).containsExactly(1, 2)
    }

    @Test
    fun `verloren create response wordt via dezelfde idempotentiesleutel zonder duplicaat hersteld`() {
        mock.loseNextCreateResponse()
        dispatcher.runDispatchSession(productId)
        assertThat(queries.findDeliveryAttempts(DeliveryAttemptFilter(productId)).single().status).isEqualTo(DeliveryAttemptStatus.RETRYABLE_FAILURE)

        dispatcher.runDispatchSession(productId)
        val attempt = queries.findDeliveryAttempts(DeliveryAttemptFilter(productId)).single()
        assertThat(attempt.status).isEqualTo(DeliveryAttemptStatus.ACCEPTED)
        assertThat(attempt.attemptCount).isEqualTo(1)
        assertThat(mock.find(productId.value, "OPEN")).hasSize(1)
        assertThat(planningQueries.getStory(firstStory).externalStoryId).isEqualTo(attempt.externalStoryId)
    }

    @Test
    fun `done met commit verwerkt planning en maakt exact een kwaliteitsworkitem`() {
        dispatcher.runDispatchSession(productId)
        val storyKey = queries.findDeliveryAttempts(DeliveryAttemptFilter(productId)).single().externalStoryId!!
        mock.complete(storyKey, SHA)

        dispatcher.runDispatchSession(productId)

        val story = planningQueries.getStory(firstStory)
        assertThat(story.status).isEqualTo(StoryStatus.DONE)
        assertThat(story.deliveredCommitSha).isEqualTo(SHA)
        assertThat(queries.findDeliveryAttempts(DeliveryAttemptFilter(productId)).single().status).isEqualTo(DeliveryAttemptStatus.COMPLETED)
        assertThat(qualityQueries.findQualityWorkItems(productId)).hasSize(1)
    }

    @Test
    fun `cancelled annuleert lokaal zonder kwaliteitscontrole`() {
        dispatcher.runDispatchSession(productId)
        val storyKey = queries.findDeliveryAttempts(DeliveryAttemptFilter(productId)).single().externalStoryId!!
        mock.cancel(storyKey, "Werk bewust gestopt")

        dispatcher.runDispatchSession(productId)

        assertThat(planningQueries.getStory(firstStory).status).isEqualTo(StoryStatus.CANCELLED)
        assertThat(qualityQueries.findQualityWorkItems(productId)).isEmpty()
    }

    @Test
    fun `tijdelijke en contractfouten volgen afzonderlijk zichtbaar herstelbeleid`() {
        mock.failNextCall()
        dispatcher.runDispatchSession(productId)
        assertThat(queries.findDispatchSessions(ProcessSessionFilter(productId)).first().errorCode).isEqualTo("TEMPORARY_FAILURE")

        dispatcher.runDispatchSession(productId)
        mock.breakNextContract()
        dispatcher.runDispatchSession(productId)
        assertThat(queries.findDispatchSessions(ProcessSessionFilter(productId)).first().errorCode).isEqualTo("INVALID_RESPONSE")
        assertThat(queries.getDispatchStatus(productId).blocked).isTrue()
        assertThat(mock.find(productId.value, "OPEN")).hasSize(1)
    }

    private fun insertStory(sequence: Long): StoryId {
        val id = StoryId(UUID.randomUUID().toString())
        val epicId = UUID.randomUUID().toString()
        val now = clock.instant()
        jdbc.update(
            """INSERT INTO pf_story(id,product_id,epic_id,epic_version,type,status,current_version,sequence_number,priority_reason,
                bug_link_confirmed,created_at,updated_at) VALUES (?,?,?,?,?,'TODO',1,?,?,FALSE,?,?)""".trimIndent(),
            id.value, productId.value, epicId, 1L, "PRODUCT_STORY", sequence, "Vaste testvolgorde", now, now,
        )
        jdbc.update(
            """INSERT INTO pf_story_version(story_id,version,title,summary,content,acceptance_criteria_json,ux_design,dependencies_json,
                source_references_json,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
            id.value, 1L, "Story $sequence", "Gebruikers krijgen zelfstandige waarde uit story $sequence.",
            "Bouw een volledige gebruikersflow met zichtbare laad-, lege, succes- en fouttoestand voor deze story.",
            "[\"De gebruiker kan de complete story aantoonbaar gebruiken.\"]", "Rustige toegankelijke interface.", "[]", "[]", now,
        )
        return id
    }

    companion object {
        private val STAKEHOLDER = ActorReference(ActorType.STAKEHOLDER, "stakeholder@example.com")
        private const val SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
