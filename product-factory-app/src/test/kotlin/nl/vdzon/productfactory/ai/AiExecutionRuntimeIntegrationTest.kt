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

@SpringBootTest(properties = ["PF_AUTH_REQUIRED=false", "PF_AGENT_RUNTIME_API_VERSION=v2", "PF_AI_ARTIFACT_STORAGE_PATH=\${java.io.tmpdir}/product-factory-ai-test"])
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
    fun `onderbroken upload hervat vanaf bevestigde offset`() {
        runtime.loseFirstPatchResponse = true
        val taskId = commands.requestAiTask(taskCommand("upload-resume"))

        implementation.dispatchPending(retryDelaySeconds = 0)
        assertThat(queries.getAiTask(taskId).runtimeJobId).isNull()
        implementation.dispatchPending()

        assertThat(queries.getAiTask(taskId).runtimeJobId).isNotNull()
        assertThat(runtime.uploadedText()).isEqualTo("Beantwoord de overlegvraag zonder technische keynamen.")
    }

    @Test
    fun `definitief niet-gereed inputobject wordt veilig opnieuw geupload`() {
        runtime.rejectFirstJobForInput = true
        val taskId = commands.requestAiTask(taskCommand("object-regeneration"))

        implementation.dispatchPending(retryDelaySeconds = 0)
        implementation.dispatchPending()

        assertThat(queries.getAiTask(taskId).runtimeJobId).isNotNull()
        assertThat(runtime.createdUploadCount).isEqualTo(2)
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

        assertThat(runtime.v2Requests.single().environmentKeys).containsExactly("HKH__ACCEPTANCE_URL")
        assertThat(runtime.v2Requests.single().input.objects.map { it.name }).containsExactly("prompt")
        assertThat(runtime.uploadedText()).doesNotContain("HKH__ACCEPTANCE_URL")
        assertThatThrownBy {
            commands.setProductEnvironmentKey(SetProductEnvironmentKeyCommand(productId, "HKH__UNKNOWN", true, 0, ACTOR, "unknown-key"))
        }.isInstanceOf(InvalidCommand::class.java)
    }

    @Test
    fun `status resultaat artifact en annulering worden duurzaam gereconcilieerd`() {
        val taskId = commands.requestAiTask(taskCommand(
            "result", listOf(AiOutputArtifactDeclaration("artifact-1", false, setOf("image/png"), 1024)),
        ))
        implementation.dispatchPending()
        val runtimeJob = runtime.onlyJob()
        runtime.jobs[runtimeJob.id] = runtimeJob.copy(status = "RUNNING", phase = "PROVIDER", attemptCount = 1, progressPercent = 40, progressMessage = "Veilige voortgang")
        implementation.reconcileActive()
        val running = queries.getAiTask(taskId)
        assertThat(running.status).isEqualTo(AiTaskStatus.RUNNING)
        assertThat(running.safeProgressPercent).isEqualTo(40)
        assertThat(running.runtimeAttemptCount).isEqualTo(1)

        runtime.v2ResultArtifacts[runtimeJob.id] = listOf(
            RuntimeV2ArtifactView("artifact-1", "artifact-1", "zoekscherm.png", "image/png", 6, sha256("bewijs".toByteArray()), "READY", Instant.now(), Instant.now(), "/v2/jobs/${runtimeJob.id}/objects/artifact-1/content"),
        )
        runtime.jobs[runtimeJob.id] = runtime.jobs.getValue(runtimeJob.id).copy(status = "SUCCEEDED", phase = "COMPLETED", progressPercent = 100)
        implementation.reconcileActive()
        assertThat(queries.getAiTask(taskId).status).isEqualTo(AiTaskStatus.SUCCEEDED)
        assertThat(queries.getAiTaskResult(taskId)?.responseJson).contains("antwoord")
        assertThat(queries.getAiTaskResult(taskId)?.artifacts?.single()?.uri).startsWith("/api/ai/tasks/")
        val download = taskController.artifact(taskId.value, queries.getAiTaskResult(taskId)!!.artifacts.single().uri.substringAfterLast('/'), null)
        val downloaded = java.io.ByteArrayOutputStream()
        download.body!!.writeTo(downloaded)
        assertThat(download.headers.contentType).isEqualTo(org.springframework.http.MediaType.IMAGE_PNG)
        assertThat(downloaded.toByteArray()).isEqualTo("bewijs".toByteArray())

        val cancelTask = commands.requestAiTask(taskCommand("cancel"))
        implementation.dispatchPending()
        commands.cancelAiTask(cancelTask, "Stakeholder stopte de taak")
        implementation.reconcileActive()
        assertThat(queries.getAiTask(cancelTask).status).isEqualTo(AiTaskStatus.CANCELLED)
        assertThat(queries.getAiTask(cancelTask).cancelReason).isEqualTo("Stakeholder stopte de taak")
    }

    @Test
    fun `lokale schemavalidatie blokkeert afwijkend Runtime resultaat`() {
        val taskId = commands.requestAiTask(taskCommand("invalid-result"))
        implementation.dispatchPending()
        val job = runtime.onlyJob()
        runtime.results[job.id] = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode("geen object")
        runtime.jobs[job.id] = job.copy(status = "SUCCEEDED", phase = "COMPLETED")

        implementation.reconcileActive()

        assertThat(queries.getAiTask(taskId).status).isEqualTo(AiTaskStatus.FAILED)
        assertThat(queries.getAiTask(taskId).errorCode).isEqualTo("RUNTIME_RESULT_SCHEMA_INVALID")
    }

    @Test
    fun `mislukte Runtime attempts bewaren usage zonder prijsberekening`() {
        val taskId = commands.requestAiTask(taskCommand("failed-usage"))
        implementation.dispatchPending()
        val job = runtime.onlyJob()
        runtime.attempts[job.id] = listOf(RuntimeAttemptView(
            "attempt-1", job.id, 1, "FAILED", "PARTIAL",
            RuntimeUsageSummary(1, "PARTIAL", listOf(RuntimeUsageMetric("INPUT_TOKENS", "12", "TOKEN")), emptyList()),
            "PROVIDER_FAILED", Instant.now(), Instant.now(),
        ))
        runtime.jobs[job.id] = job.copy(status = "FAILED", phase = "FAILED", attemptCount = 1, errorCode = "PROVIDER_FAILED")

        implementation.reconcileActive()

        assertThat(queries.getAiTaskUsage(taskId)?.inputTokens).isEqualTo(12)
        assertThat(queries.getAiTaskUsage(taskId)?.quality).isEqualTo("PARTIAL")
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

    private fun taskCommand(key: String, outputArtifacts: List<AiOutputArtifactDeclaration> = emptyList()) = RequestAiTaskCommand(
        AiJobKey("MEETING.CONVERSE"), productId, "meeting", null, "MEETING_AGENT", DEFAULT_EXECUTION, 0,
        1, "Beantwoord de overlegvraag zonder technische keynamen.", """{"type":"object"}""", outputArtifacts = outputArtifacts,
        executionTimeout = Duration.ofMinutes(5), idempotencyKey = key,
    )

    @TestConfiguration
    class RuntimeTestConfiguration {
        @Bean
        @Primary
        fun fakeRuntime() = FakeRuntime()
    }

    companion object {
        private val DEFAULT_EXECUTION = AiExecutionSelection("openai", "gpt-5.6-sol", AiExecutionMode.SUBSCRIPTION)
        private val ACTOR = ActorReference(ActorType.STAKEHOLDER, "stakeholder@example.com")
        private fun sha256(bytes: ByteArray) = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))
    }
}

class FakeRuntime : AgentRuntimeClient {
    val requests = mutableListOf<RuntimeCreateJobRequest>()
    val v2Requests = mutableListOf<RuntimeV2CreateJobRequest>()
    val jobs = linkedMapOf<String, RuntimeJobView>()
    val environmentKeys = mutableListOf<RuntimeEnvironmentKey>()
    val models = mutableListOf<RuntimeModelView>()
    val results = mutableMapOf<String, com.fasterxml.jackson.databind.JsonNode>()
    val resultArtifacts = mutableMapOf<String, List<RuntimeArtifactView>>()
    val v2ResultArtifacts = mutableMapOf<String, List<RuntimeV2ArtifactView>>()
    val attempts = mutableMapOf<String, List<RuntimeAttemptView>>()
    private val uploads = linkedMapOf<String, FakeUpload>()
    var loseFirstCreateResponse = false
    var loseFirstPatchResponse = false
    var rejectFirstJobForInput = false
    var createdUploadCount = 0
    private var lost = false
    private var patchLost = false
    private var inputRejected = false

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
    override fun listModels(provider: String?) = models.filter { provider == null || it.provider == provider }
    override fun downloadArtifact(jobId: String, artifactId: String) = "bewijs".toByteArray()
    override fun copyV2Artifact(downloadUrl: String, offset: Long, expectedSize: Long, output: java.io.OutputStream): RuntimeArtifactCopyResult {
        val bytes = "bewijs".toByteArray()
        output.write(bytes, offset.toInt(), bytes.size - offset.toInt())
        return RuntimeArtifactCopyResult(bytes.size.toLong(), true)
    }

    override fun createUpload(request: RuntimeCreateUploadRequest): RuntimeUploadView {
        createdUploadCount++
        val uploadId = UUID.nameUUIDFromBytes("upload-${uploads.size}-${request.sha256}".toByteArray()).toString()
        val objectId = UUID.nameUUIDFromBytes("object-$uploadId".toByteArray()).toString()
        uploads[uploadId] = FakeUpload(request, objectId)
        return RuntimeUploadView(uploadId, objectId, "CREATED", "RESUMABLE_PATCH", 7, "/uploads/$uploadId", 0, request.sizeBytes, Instant.now().plusSeconds(600))
    }

    override fun getUploadOffset(uploadUrl: String): Long = upload(uploadUrl).content.size.toLong()

    override fun patchUpload(uploadUrl: String, offset: Long, content: ByteArray): Long {
        val upload = upload(uploadUrl)
        if (upload.content.size.toLong() != offset) throw RuntimeCallException("UPLOAD_OFFSET_MISMATCH", "Offset klopt niet.")
        upload.content += content.toList()
        if (loseFirstPatchResponse && !patchLost) {
            patchLost = true
            throw RuntimeCallException("RUNTIME_UPLOAD_PATCH_FAILED", "Patchresponse verloren.", true)
        }
        return upload.content.size.toLong()
    }

    override fun completeUpload(uploadId: String): RuntimeObjectView {
        val upload = uploads.getValue(uploadId)
        return RuntimeObjectView(
            upload.objectId, upload.request.filename, upload.request.mimeType, upload.request.sizeBytes, upload.request.sha256,
            "READY", Instant.now(), Instant.now(),
        )
    }

    override fun deleteUpload(uploadId: String) { uploads.remove(uploadId) }

    override fun createV2Job(request: RuntimeV2CreateJobRequest): RuntimeJobView {
        v2Requests += request
        if (rejectFirstJobForInput && !inputRejected) {
            inputRejected = true
            throw RuntimeCallException("INPUT_OBJECT_NOT_READY", "Inputobject is verlopen.")
        }
        val existing = jobs.values.singleOrNull { it.id == idFor(request.idempotencyKey) }
        val job = existing ?: RuntimeJobView(idFor(request.idempotencyKey), "QUEUED", "QUEUED", 0, null, null, null, null, Instant.now(), Instant.now()).also { jobs[it.id] = it }
        if (loseFirstCreateResponse && !lost) {
            lost = true
            throw RuntimeCallException("RUNTIME_SUBMISSION_FAILED", "Response verloren.", true)
        }
        return job
    }

    override fun getV2Job(jobId: String) = jobs.getValue(jobId)
    override fun getV2Result(jobId: String) = RuntimeV2JobResult(
        jobId, results[jobId] ?: com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("antwoord", "gereed"),
        v2ResultArtifacts[jobId].orEmpty(), RuntimeUsageSummary(1, "MOCK"), Instant.now(),
    )
    override fun getV2Attempts(jobId: String) = attempts[jobId].orEmpty()
    override fun getV2Events(jobId: String, afterSequence: Long, limit: Int) = RuntimeEventPage(emptyList(), null, jobs.getValue(jobId).status !in setOf("SUCCEEDED", "FAILED", "CANCELLED"))
    override fun cancelV2Job(jobId: String): RuntimeJobView = cancelJob(jobId)
    override fun deleteV2Content(jobId: String) = Unit
    override fun listV2EnvironmentKeys(projectPrefix: String) = environmentKeys.filter { it.projectPrefix == projectPrefix }
    override fun listV2ExecutionOptions(taskType: String) = emptyList<RuntimeExecutionOption>()

    fun onlyJob() = jobs.values.single()
    fun distinctIdempotencyKeys() = (requests.map { it.idempotencyKey } + v2Requests.map { it.idempotencyKey }).distinct()
    fun jobCount() = jobs.size
    fun uploadedText() = uploads.values.flatMap { it.content }.toByteArray().toString(Charsets.UTF_8)
    fun reset() { requests.clear(); v2Requests.clear(); jobs.clear(); environmentKeys.clear(); models.clear(); results.clear(); resultArtifacts.clear(); v2ResultArtifacts.clear(); attempts.clear(); uploads.clear(); loseFirstCreateResponse = false; loseFirstPatchResponse = false; rejectFirstJobForInput = false; createdUploadCount = 0; lost = false; patchLost = false; inputRejected = false }
    private fun upload(url: String) = uploads.getValue(url.substringAfterLast('/'))
    private fun idFor(key: String) = UUID.nameUUIDFromBytes(key.toByteArray()).toString()
    private data class FakeUpload(val request: RuntimeCreateUploadRequest, val objectId: String, val content: MutableList<Byte> = mutableListOf())
}
