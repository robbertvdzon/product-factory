package nl.vdzon.productfactory.ai

import nl.vdzon.productfactory.api.ai.*
import nl.vdzon.productfactory.api.product.CreateProductCommand
import nl.vdzon.productfactory.api.product.ProductCommandService
import nl.vdzon.productfactory.api.product.ProductQueryService
import nl.vdzon.productfactory.api.product.StartMeetingCommand
import nl.vdzon.productfactory.api.product.MeetingStatus
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.product.CloseMeetingRequest
import nl.vdzon.productfactory.product.MeetingAiOrchestrator
import nl.vdzon.productfactory.product.MeetingMessageRequest
import nl.vdzon.productfactory.memory.AiTaskController
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@SpringBootTest(properties = ["PF_AUTH_REQUIRED=false"])
@ActiveProfiles("test")
@Import(AiExecutionRuntimeIntegrationTest.RuntimeTestConfiguration::class)
@Transactional
class AiExecutionRuntimeIntegrationTest @Autowired constructor(
    private val products: ProductCommandService,
    private val commands: AiExecutionService,
    private val queries: AiExecutionQueryService,
    private val implementation: AiExecutionApplicationService,
    private val runtime: FakeRuntime,
    private val productQueries: ProductQueryService,
    private val meetings: MeetingAiOrchestrator,
    private val taskController: AiTaskController,
) {
    private var productId = ProductId("not-initialized")

    @BeforeEach
    fun prepare() {
        runtime.reset()
        productId = ProductId("runtime-${UUID.randomUUID().toString().take(8)}")
        products.createProduct(CreateProductCommand(productId, "Runtime test", actor = ACTOR, idempotencyKey = "create-${productId.value}"))
    }

    @Test
    fun `verloren response en dubbele dispatch houden exact een externe job`() {
        runtime.loseFirstCreateResponse = true
        val command = taskCommand("lost-response")
        val taskId = commands.requestAiTask(command)
        assertThat(commands.requestAiTask(command)).isEqualTo(taskId)
        assertThat(queries.getAiTask(taskId).status).isEqualTo(AiTaskStatus.PENDING_SUBMISSION)

        implementation.dispatchPending(retryDelaySeconds = 0)
        assertThat(queries.getAiTask(taskId).runtimeJobId).isNull()
        implementation.dispatchPending()

        val projected = queries.getAiTask(taskId)
        assertThat(projected.runtimeJobId).isEqualTo(runtime.onlyJob().id)
        assertThat(projected.status).isEqualTo(AiTaskStatus.QUEUED)
        assertThat(runtime.distinctIdempotencyKeys()).containsExactly("pf-lost-response")
        assertThat(runtime.jobCount()).isEqualTo(1)
    }

    @Test
    fun `alleen actieve product en rolgrants verlaten de backend`() {
        runtime.environmentKeys += RuntimeEnvironmentKey("HKH__ACCEPTANCE_URL", "HKH", true, 1, Instant.parse("2026-08-26T12:00:00Z"))
        runtime.environmentKeys += RuntimeEnvironmentKey("HKH__PASSWORD", "HKH", false, 0, Instant.parse("2026-08-26T12:00:00Z"))
        commands.refreshEnvironmentCatalog(RefreshEnvironmentCatalogCommand("HKH"))
        commands.setProductEnvironmentKey(SetProductEnvironmentKeyCommand(productId, "HKH__ACCEPTANCE_URL", true, 0, ACTOR, "key-url"))
        commands.setProductEnvironmentKey(SetProductEnvironmentKeyCommand(productId, "HKH__PASSWORD", true, 0, ACTOR, "key-password"))
        commands.setAgentEnvironmentGrant(SetAgentEnvironmentGrantCommand(productId, "HKH__ACCEPTANCE_URL", "MEETING_AGENT", true, ACTOR, "grant-url"))
        commands.setAgentEnvironmentGrant(SetAgentEnvironmentGrantCommand(productId, "HKH__PASSWORD", "TESTER_MVP", true, ACTOR, "grant-password"))

        commands.requestAiTask(taskCommand("derived-keys"))
        implementation.dispatchPending()

        assertThat(runtime.requests.single().environmentKeys).containsExactly("HKH__ACCEPTANCE_URL")
        assertThat(runtime.requests.single().prompt).doesNotContain("HKH__ACCEPTANCE_URL")
        assertThatThrownBy {
            commands.setProductEnvironmentKey(SetProductEnvironmentKeyCommand(productId, "HKH__UNKNOWN", true, 0, ACTOR, "unknown-key"))
        }.isInstanceOf(InvalidCommand::class.java)
    }

    @Test
    fun `status resultaat artifact en annulering worden duurzaam gereconcilieerd`() {
        val taskId = commands.requestAiTask(taskCommand("result"))
        implementation.dispatchPending()
        val runtimeJob = runtime.onlyJob()
        runtime.jobs[runtimeJob.id] = runtimeJob.copy(status = "RUNNING", phase = "PROVIDER", attemptCount = 1, progressPercent = 40, progressMessage = "Veilige voortgang")
        implementation.reconcileActive()
        val running = queries.getAiTask(taskId)
        assertThat(running.status).isEqualTo(AiTaskStatus.RUNNING)
        assertThat(running.safeProgressPercent).isEqualTo(40)
        assertThat(running.runtimeAttemptCount).isEqualTo(1)

        runtime.resultArtifacts[runtimeJob.id] = listOf(
            RuntimeArtifactView("artifact-1", runtimeJob.id, "zoekscherm.png", "image/png", 6, "0".repeat(64), Instant.now()),
        )
        runtime.jobs[runtimeJob.id] = runtime.jobs.getValue(runtimeJob.id).copy(status = "SUCCEEDED", phase = "COMPLETED", progressPercent = 100)
        implementation.reconcileActive()
        assertThat(queries.getAiTask(taskId).status).isEqualTo(AiTaskStatus.SUCCEEDED)
        assertThat(queries.getAiTaskResult(taskId)?.responseJson).contains("antwoord")
        assertThat(queries.getAiTaskResult(taskId)?.artifacts?.single()?.uri).startsWith("/api/ai/tasks/")
        assertThat(taskController.artifact(taskId.value, "artifact-1").headers.contentType).isEqualTo(org.springframework.http.MediaType.IMAGE_PNG)
        assertThat(taskController.artifact(taskId.value, "artifact-1").body).isEqualTo("bewijs".toByteArray())

        val cancelTask = commands.requestAiTask(taskCommand("cancel"))
        implementation.dispatchPending()
        commands.cancelAiTask(cancelTask, "Stakeholder stopte de taak")
        implementation.reconcileActive()
        assertThat(queries.getAiTask(cancelTask).status).isEqualTo(AiTaskStatus.CANCELLED)
        assertThat(queries.getAiTask(cancelTask).cancelReason).isEqualTo("Stakeholder stopte de taak")
    }

    @Test
    fun `meeting agent en notulenagent hervatten idempotent via dezelfde facade`() {
        val meetingId = products.startMeeting(StartMeetingCommand(
            productId, "Bepaal de eerste productrichting", listOf("Doel"), emptyList(), actor = ACTOR, idempotencyKey = "meeting-start",
        ))
        val conversation = meetings.addStakeholderMessage(
            meetingId, MeetingMessageRequest("Wat is nu de beste vervolgstap?", 1, "meeting-message"), ACTOR,
        )
        implementation.dispatchPending()
        val conversationJob = runtime.jobs.getValue(queries.getAiTask(conversation.taskId).runtimeJobId!!)
        runtime.results[conversationJob.id] = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
            .put("message", "Werk eerst het kernpad uit.").put("representedAgentRole", "MEETING_AGENT")
        runtime.jobs[conversationJob.id] = conversationJob.copy(status = "SUCCEEDED", phase = "COMPLETED")
        implementation.reconcileActive()
        meetings.resumeReady()

        val answered = productQueries.getMeeting(meetingId)
        assertThat(answered.messages.map { it.senderRole.name }).containsExactly("STAKEHOLDER", "MEETING_AGENT")
        assertThat(answered.messages.last().representedAgentRole).isEqualTo("MEETING_AGENT")

        val minutes = meetings.requestMinutes(meetingId, CloseMeetingRequest(answered.version, "meeting-minutes"))
        implementation.dispatchPending()
        val minutesJob = runtime.jobs.getValue(queries.getAiTask(minutes.taskId).runtimeJobId!!)
        val minutesResult = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
        minutesResult.put("minutes", "De Stakeholder vroeg naar de vervolgstap; de Meeting Agent adviseerde het kernpad.")
        minutesResult.set<com.fasterxml.jackson.databind.JsonNode>("answers", com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode())
        minutesResult.set<com.fasterxml.jackson.databind.JsonNode>("decisions", com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode())
        minutesResult.set<com.fasterxml.jackson.databind.JsonNode>("memoryChanges", com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode())
        runtime.results[minutesJob.id] = minutesResult
        runtime.jobs[minutesJob.id] = minutesJob.copy(status = "SUCCEEDED", phase = "COMPLETED")
        implementation.reconcileActive()
        meetings.resumeReady()
        meetings.resumeReady()

        val closed = productQueries.getMeeting(meetingId)
        assertThat(closed.status).isEqualTo(MeetingStatus.CLOSED)
        assertThat(closed.minutes).contains("kernpad")
    }

    private fun taskCommand(key: String) = RequestAiTaskCommand(
        AiJobKey("MEETING.CONVERSE"), productId, "meeting", null, "MEETING_AGENT", AiProvider.CODEX, "gpt-5.6-sol", 0,
        1, "Beantwoord de overlegvraag zonder technische keynamen.", """{"type":"object"}""", executionTimeout = Duration.ofMinutes(5), idempotencyKey = key,
    )

    @TestConfiguration
    class RuntimeTestConfiguration {
        @Bean
        @Primary
        fun fakeRuntime() = FakeRuntime()
    }

    companion object {
        private val ACTOR = ActorReference(ActorType.STAKEHOLDER, "stakeholder@example.com")
    }
}

class FakeRuntime : AgentRuntimeClient {
    val requests = mutableListOf<RuntimeCreateJobRequest>()
    val jobs = linkedMapOf<String, RuntimeJobView>()
    val environmentKeys = mutableListOf<RuntimeEnvironmentKey>()
    val results = mutableMapOf<String, com.fasterxml.jackson.databind.JsonNode>()
    val resultArtifacts = mutableMapOf<String, List<RuntimeArtifactView>>()
    var loseFirstCreateResponse = false
    private var lost = false

    override fun createJob(request: RuntimeCreateJobRequest): RuntimeJobView {
        requests += request
        val existing = jobs.values.singleOrNull { it.id == idFor(request.idempotencyKey) }
        val job = existing ?: RuntimeJobView(idFor(request.idempotencyKey), "QUEUED", "QUEUED", 0, null, null, null, null, Instant.now(), Instant.now()).also { jobs[it.id] = it }
        if (loseFirstCreateResponse && !lost) {
            lost = true
            throw RuntimeCallException("RUNTIME_SUBMISSION_FAILED", "Response verloren.", true)
        }
        return job
    }

    override fun getJob(jobId: String) = jobs.getValue(jobId)
    override fun getResult(jobId: String) = RuntimeJobResult(
        jobId, results[jobId] ?: com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("antwoord", "gereed"),
        resultArtifacts[jobId] ?: listOf(RuntimeArtifactView("artifact-1", jobId, "bewijs.txt", "text/plain", 6, "0".repeat(64), Instant.now())), Instant.now(),
    )
    override fun cancelJob(jobId: String): RuntimeJobView = jobs.getValue(jobId).copy(status = "CANCELLED", phase = "CANCELLED").also { jobs[jobId] = it }
    override fun listEnvironmentKeys(projectPrefix: String) = environmentKeys.filter { it.projectPrefix == projectPrefix }
    override fun downloadArtifact(jobId: String, artifactId: String) = "bewijs".toByteArray()

    fun onlyJob() = jobs.values.single()
    fun distinctIdempotencyKeys() = requests.map { it.idempotencyKey }.distinct()
    fun jobCount() = jobs.size
    fun reset() { requests.clear(); jobs.clear(); environmentKeys.clear(); results.clear(); resultArtifacts.clear(); loseFirstCreateResponse = false; lost = false }
    private fun idFor(key: String) = UUID.nameUUIDFromBytes(key.toByteArray()).toString()
}
