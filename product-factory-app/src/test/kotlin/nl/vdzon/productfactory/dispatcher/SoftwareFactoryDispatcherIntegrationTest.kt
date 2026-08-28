package nl.vdzon.productfactory.dispatcher

import nl.vdzon.productfactory.ai.AiExecutionApplicationService
import nl.vdzon.productfactory.ai.AiExecutionRuntimeIntegrationTest
import nl.vdzon.productfactory.ai.FakeRuntime
import nl.vdzon.productfactory.ai.RuntimeArtifactView
import nl.vdzon.productfactory.api.ai.*
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
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

@SpringBootTest(properties = ["PF_AUTH_REQUIRED=false", "PF_SOFTWARE_FACTORY_MODE=MOCKED"])
@ActiveProfiles("test")
@Import(AiExecutionRuntimeIntegrationTest.RuntimeTestConfiguration::class)
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
    private val aiCommands: AiExecutionService,
    private val aiQueries: AiExecutionQueryService,
    private val aiImpl: AiExecutionApplicationService,
    private val runtime: FakeRuntime,
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
        runtime.reset()
        productId = ProductId("dispatcher-${UUID.randomUUID().toString().take(8)}")
        productCommands.createProduct(CreateProductCommand(productId, "Dispatcher test", actor = STAKEHOLDER, idempotencyKey = "create-${productId.value}"))
        productCommands.updateProductAssignment(UpdateProductAssignmentCommand(
            productId, "Gebruikers", "Lever precies één zelfstandige story", listOf("Geen credentials"),
            "https://github.com/robbertvdzon/hkh-autopilot.git", 0, STAKEHOLDER, "assignment-${productId.value}",
        ))
        productCommands.setProductDispatching(SetProductDispatchingCommand(productId, true, 1, STAKEHOLDER, "dispatching-${productId.value}"))
        firstStory = insertStory(1, listOf(createUxArtifact()))
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
        val attachment = mock.attachments(attempts.single().externalStoryId!!).single()
        assertThat(attachment.fileName).isEqualTo("zoekscherm.png")
        assertThat(Base64.getDecoder().decode(attachment.contentBase64)).isEqualTo("bewijs".toByteArray())
        assertThat(attachment.sha256).hasSize(64)
        assertThat(mock.description(attempts.single().externalStoryId!!))
            .contains("# Uit te voeren story — normatieve opdracht")
            .contains("# Epiccontext — uitsluitend informatief")
            .contains("Testprobleem.")
            .contains("Testoplossing.")
            .contains("richtinggevend")
            .contains("pixel-perfecte kopie is niet vereist")
        assertThat(planningQueries.getStory(firstStory).status).isEqualTo(StoryStatus.IN_PROGRESS)
        assertThat(planningQueries.getBacklog(productId).map { it.sequenceNumber }).containsExactly(1, 2)
    }

    @Test
    fun `zonder ingestelde AI-voorkeur blijft supplier en model leeg in de verstuurde story`() {
        dispatcher.runDispatchSession(productId)

        val storyKey = queries.findDeliveryAttempts(DeliveryAttemptFilter(productId)).single().externalStoryId!!
        assertThat(mock.aiSupplier(storyKey)).isNull()
        assertThat(mock.aiModel(storyKey)).isNull()
    }

    @Test
    fun `ingestelde AI-supplier en -model op de productopdracht komen terug in de verstuurde story`() {
        productCommands.updateProductAssignment(UpdateProductAssignmentCommand(
            productId, "Gebruikers", "Lever precies één zelfstandige story", listOf("Geen credentials"),
            "https://github.com/robbertvdzon/hkh-autopilot.git", 1, STAKEHOLDER, "assignment-ai-${productId.value}",
            aiSupplier = "copilot", aiModel = "claude-sonnet-4.5",
        ))

        dispatcher.runDispatchSession(productId)

        val storyKey = queries.findDeliveryAttempts(DeliveryAttemptFilter(productId)).single().externalStoryId!!
        assertThat(mock.aiSupplier(storyKey)).isEqualTo("copilot")
        assertThat(mock.aiModel(storyKey)).isEqualTo("claude-sonnet-4.5")
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
    fun `teruggestuurde epic annuleert lopende story ook in Software Factory`() {
        dispatcher.runDispatchSession(productId)
        val storyKey = queries.findDeliveryAttempts(DeliveryAttemptFilter(productId)).single().externalStoryId!!
        jdbc.update(
            "UPDATE pf_story SET refinement_cancel_requested=TRUE,refinement_cancel_sent=FALSE,cancellation_reason=? WHERE id=?",
            "De epic mist een concreet invoerscherm.", firstStory.value,
        )

        dispatcher.runDispatchSession(productId)

        assertThat(mock.get(storyKey)?.status).isEqualTo("CANCELLED")
        assertThat(planningQueries.getStory(firstStory).status).isEqualTo(StoryStatus.CANCELLED)
        assertThat(planningQueries.getStory(firstStory).cancellationReason)
            .isEqualTo("De epic mist een concreet invoerscherm.")
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

    @Test
    fun `dispatchen na een eerder gecancelde poging voor dezelfde storyversie hergebruikt de rij i-p-v-te botsen`() {
        // Zet neer wat een operator overhoudt na het handmatig annuleren van een permanent
        // mislukte (CONTRACT_FAILURE) poging: de aanmaak bij Software Factory is nooit gelukt
        // (geen externalStoryId), de story bleef TODO en dus ongewijzigd van versie — dezelfde
        // idempotency_key als een verse poging voor exact deze storyversie zou genereren.
        val key = "product-factory:${productId.value}:story:${firstStory.value}:v1"
        val cancelledId = UUID.randomUUID().toString()
        val now = clock.instant()
        jdbc.update(
            """INSERT INTO pf_delivery_attempt(id,product_id,story_id,story_version,reservation_id,idempotency_key,package_hash,package_json,
                status,attempt_count,local_command_status,created_at,updated_at)
                VALUES (?,?,?,1,?,?,'deadbeef','{}','CANCELLED',1,'APPLIED',?,?)""".trimIndent(),
            cancelledId, productId.value, firstStory.value, UUID.randomUUID().toString(), key, now, now,
        )

        dispatcher.runDispatchSession(productId)

        val attempts = queries.findDeliveryAttempts(DeliveryAttemptFilter(productId))
        assertThat(attempts).hasSize(1)
        assertThat(attempts.single().id).isEqualTo(DeliveryAttemptId(cancelledId))
        assertThat(attempts.single().status).isEqualTo(DeliveryAttemptStatus.ACCEPTED)
        assertThat(planningQueries.getStory(firstStory).status).isEqualTo(StoryStatus.IN_PROGRESS)
    }

    private fun createUxArtifact(): ArtifactReference {
        val taskId = aiCommands.requestAiTask(RequestAiTaskCommand(
            AiJobKey("PLANNING.SLICE_EPIC"), productId, "planning", null, "PLANNER_MVP", AiProvider.CODEX, "gpt-5.6-sol", 0,
            1, "Maak een UX-model.", """{"type":"object"}""", executionTimeout = Duration.ofMinutes(5), idempotencyKey = "ux-${productId.value}",
        ))
        aiImpl.dispatchPending()
        val job = runtime.onlyJob()
        runtime.resultArtifacts[job.id] = listOf(
            RuntimeArtifactView("ux-artifact", job.id, "zoekscherm.png", "image/png", 6, "0".repeat(64), Instant.now()),
        )
        runtime.jobs[job.id] = job.copy(status = "SUCCEEDED", phase = "COMPLETED", progressPercent = 100)
        aiImpl.reconcileActive()
        return aiQueries.getAiTaskResult(taskId)!!.artifacts.single()
    }

    private fun insertStory(sequence: Long, uxArtifacts: List<ArtifactReference> = emptyList()): StoryId {
        val id = StoryId(UUID.randomUUID().toString())
        val epicId = UUID.randomUUID().toString()
        val now = clock.instant()
        jdbc.update(
            "INSERT INTO pf_epic(id,product_id,current_version,status,created_at,updated_at) VALUES (?,?,1,'ACTIVE',?,?)",
            epicId, productId.value, now, now,
        )
        jdbc.update(
            """INSERT INTO pf_epic_version(epic_id,version,title,summary,problem,solution,direction_references_json,ux_design,
                acceptance_criteria_json,slicability_rationale,source_references_json,status,actor_type,actor_id,created_at,ux_artifacts_json)
                VALUES (?,1,?,?,?,?,?,?,?,?,?,'ACTIVE','PROCESS','dispatcher-test',?,?)""".trimIndent(),
            epicId, "Epic $sequence", "Zelfstandig testepic.", "Testprobleem.", "Testoplossing.", "[]", "UX-ontwerp.",
            "[\"De route werkt.\"]", "Zelfstandig te bouwen.", "[]", now, com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(uxArtifacts),
        )
        jdbc.update(
            """INSERT INTO pf_story(id,product_id,epic_id,epic_version,type,status,current_version,sequence_number,priority_reason,
                bug_link_confirmed,created_at,updated_at) VALUES (?,?,?,?,?,'TODO',1,?,?,FALSE,?,?)""".trimIndent(),
            id.value, productId.value, epicId, 1L, "PRODUCT_STORY", sequence, "Vaste testvolgorde", now, now,
        )
        jdbc.update(
            """INSERT INTO pf_story_version(story_id,version,title,summary,content,acceptance_criteria_json,ux_design,dependencies_json,
                source_references_json,created_at,ux_artifacts_json) VALUES (?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
            id.value, 1L, "Story $sequence", "Gebruikers krijgen zelfstandige waarde uit story $sequence.",
            "Bouw een volledige gebruikersflow met zichtbare laad-, lege, succes- en fouttoestand voor deze story.",
            "[\"De gebruiker kan de complete story aantoonbaar gebruiken.\"]", "Rustige toegankelijke interface.", "[]", "[]", now,
            com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(uxArtifacts),
        )
        return id
    }

    companion object {
        private val STAKEHOLDER = ActorReference(ActorType.STAKEHOLDER, "stakeholder@example.com")
        private const val SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
