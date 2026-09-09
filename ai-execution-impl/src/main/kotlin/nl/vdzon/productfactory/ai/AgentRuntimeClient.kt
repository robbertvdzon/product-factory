package nl.vdzon.productfactory.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant

data class RuntimeAttachmentRequest(val filename: String, val mimeType: String, val contentBase64: String)
data class RuntimeRepositorySnapshot(val url: String, val commitSha: String)
data class RuntimeCreateJobRequest(
    val jobKind: String = "APPLICATION_WORK",
    val idempotencyKey: String,
    val provider: String,
    val model: String,
    val prompt: String,
    val responseSchema: JsonNode?,
    val repositorySnapshot: RuntimeRepositorySnapshot?,
    val environmentKeys: List<String>,
    val attachments: List<RuntimeAttachmentRequest>,
    val executionTimeoutSeconds: Int,
)
data class RuntimeJobView(
    val id: String,
    val status: String,
    val phase: String,
    val attemptCount: Int,
    val progressPercent: Int?,
    val progressMessage: String?,
    val errorCode: String?,
    val errorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
data class RuntimeArtifactView(
    val id: String,
    val jobId: String,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val createdAt: Instant,
)
data class RuntimeJobResult(val jobId: String, val result: JsonNode, val artifacts: List<RuntimeArtifactView>, val completedAt: Instant)
data class RuntimeEnvironmentKey(
    val name: String,
    val projectPrefix: String,
    val available: Boolean,
    val matchingOnlineWorkers: Int,
    val lastSeenAt: Instant,
)
data class RuntimeModelView(
    val provider: String,
    val model: String,
    val available: Boolean,
    val matchingOnlineWorkers: Int,
    val lastSeenAt: Instant,
)
data class RuntimeV2Execution(val vendorId: String, val model: String, val mode: String)
data class RuntimeV2InputObjectRef(val objectId: String, val name: String, val role: String)
data class RuntimeV2JobInput(val instruction: String, val objects: List<RuntimeV2InputObjectRef>)
data class RuntimeV2ArtifactDeclaration(val name: String, val required: Boolean, val mimeTypes: Set<String>, val maxBytes: Long)
data class RuntimeV2Output(val resultSchema: JsonNode, val artifacts: List<RuntimeV2ArtifactDeclaration>)
data class RuntimeV2CreateJobRequest(
    val idempotencyKey: String,
    val jobKind: String = "APPLICATION_WORK",
    val taskType: String = "STRUCTURED_GENERATION",
    val execution: RuntimeV2Execution,
    val input: RuntimeV2JobInput,
    val output: RuntimeV2Output,
    val repositorySnapshot: RuntimeRepositorySnapshot?,
    val environmentKeys: List<String>,
    val executionTimeoutSeconds: Int,
)
data class RuntimeCreateUploadRequest(val filename: String, val mimeType: String, val sizeBytes: Long, val sha256: String)
data class RuntimeUploadView(
    val uploadId: String,
    val objectId: String,
    val state: String,
    val protocol: String,
    val chunkSizeBytes: Long,
    val uploadUrl: String,
    val offset: Long,
    val sizeBytes: Long,
    val expiresAt: Instant,
)
data class RuntimeObjectView(
    val objectId: String,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val state: String,
    val createdAt: Instant,
    val readyAt: Instant?,
)
data class RuntimeV2ArtifactView(
    val objectId: String,
    val name: String,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val state: String,
    val createdAt: Instant,
    val readyAt: Instant,
    val downloadUrl: String,
)
data class RuntimeUsageMetric(val metric: String, val quantity: String, val unit: String)
data class RuntimeCostValue(val kind: String, val status: String, val amount: String, val currency: String)
data class RuntimeUsageSummary(
    val attemptCount: Int,
    val usageQuality: String,
    val metrics: List<RuntimeUsageMetric> = emptyList(),
    val costs: List<RuntimeCostValue> = emptyList(),
)
data class RuntimeV2JobResult(
    val jobId: String,
    val result: JsonNode,
    val artifacts: List<RuntimeV2ArtifactView>,
    val usageSummary: RuntimeUsageSummary,
    val completedAt: Instant,
)
data class RuntimeAttemptView(
    val id: String,
    val jobId: String,
    val number: Int,
    val status: String,
    val usageQuality: String,
    val usageSummary: RuntimeUsageSummary,
    val errorCode: String?,
    val startedAt: Instant,
    val completedAt: Instant?,
)
data class RuntimeEventView(
    val sequence: Long,
    val jobId: String,
    val type: String,
    val phase: String?,
    val message: String?,
    val logKind: String?,
    val logText: String?,
    val progressPercent: Int?,
    val createdAt: Instant,
)
data class RuntimeEventPage(val items: List<RuntimeEventView>, val nextSequence: Long?, val active: Boolean)
data class RuntimeExecutionOption(
    val execution: RuntimeV2Execution,
    val taskTypes: Set<String>,
    val available: Boolean,
    val matchingOnlineWorkers: Int,
    val lastSeenAt: Instant,
)
data class RuntimeArtifactCopyResult(val confirmedBytes: Long, val complete: Boolean)

class RuntimeCallException(val code: String, val safeMessage: String, val responseMayHaveBeenLost: Boolean = false) : RuntimeException(safeMessage)

interface AgentRuntimeClient {
    fun createJob(request: RuntimeCreateJobRequest): RuntimeJobView
    fun getJob(jobId: String): RuntimeJobView
    fun getResult(jobId: String): RuntimeJobResult
    fun cancelJob(jobId: String): RuntimeJobView
    fun listEnvironmentKeys(projectPrefix: String): List<RuntimeEnvironmentKey>
    fun listModels(provider: String?): List<RuntimeModelView>
    fun downloadArtifact(jobId: String, artifactId: String): ByteArray
    fun copyV2Artifact(downloadUrl: String, offset: Long, expectedSize: Long, output: OutputStream): RuntimeArtifactCopyResult = unsupportedV2()
    fun createUpload(request: RuntimeCreateUploadRequest): RuntimeUploadView = unsupportedV2()
    fun getUploadOffset(uploadUrl: String): Long = unsupportedV2()
    fun patchUpload(uploadUrl: String, offset: Long, content: ByteArray): Long = unsupportedV2()
    fun completeUpload(uploadId: String): RuntimeObjectView = unsupportedV2()
    fun deleteUpload(uploadId: String) = unsupportedV2<Unit>()
    fun createV2Job(request: RuntimeV2CreateJobRequest): RuntimeJobView = unsupportedV2()
    fun getV2Job(jobId: String): RuntimeJobView = unsupportedV2()
    fun getV2Result(jobId: String): RuntimeV2JobResult = unsupportedV2()
    fun getV2Attempts(jobId: String): List<RuntimeAttemptView> = unsupportedV2()
    fun getV2Events(jobId: String, afterSequence: Long, limit: Int = 100): RuntimeEventPage = unsupportedV2()
    fun cancelV2Job(jobId: String): RuntimeJobView = unsupportedV2()
    fun deleteV2Content(jobId: String) = unsupportedV2<Unit>()
    fun listV2EnvironmentKeys(projectPrefix: String): List<RuntimeEnvironmentKey> = unsupportedV2()
    fun listV2ExecutionOptions(taskType: String): List<RuntimeExecutionOption> = unsupportedV2()

    private fun <T> unsupportedV2(): T = throw RuntimeCallException("RUNTIME_V2_NOT_IMPLEMENTED", "Agent Runtime v2 is niet beschikbaar.")
}

@Component
class HttpAgentRuntimeClient(
    private val mapper: ObjectMapper,
    @Value("\${PF_AGENT_RUNTIME_URL:}") baseUrl: String,
    @Value("\${PF_AGENT_RUNTIME_TOKEN:}") token: String,
) : AgentRuntimeClient {
    private val baseUri = URI.create(baseUrl.trimEnd('/'))
    private val bearerToken = token
    private val configured = baseUrl.isNotBlank() && token.isNotBlank()
    private val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val client = RestClient.builder()
        .baseUrl(baseUrl.trimEnd('/'))
        .requestFactory(JdkClientHttpRequestFactory(
            httpClient,
        ).apply {
            setReadTimeout(Duration.ofSeconds(20))
        })
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build()

    override fun createJob(request: RuntimeCreateJobRequest) = call("RUNTIME_SUBMISSION_FAILED", true) {
        client.post().uri("/v1/jobs").contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(RuntimeJobView::class.java)
            ?: throw RuntimeCallException("RUNTIME_EMPTY_RESPONSE", "Agent Runtime gaf geen taak terug.", true)
    }

    override fun getJob(jobId: String) = call("RUNTIME_STATUS_FAILED") {
        client.get().uri("/v1/jobs/{jobId}", jobId).retrieve().body(RuntimeJobView::class.java)
            ?: throw RuntimeCallException("RUNTIME_EMPTY_RESPONSE", "Agent Runtime gaf geen status terug.")
    }

    override fun getResult(jobId: String) = call("RUNTIME_RESULT_FAILED") {
        client.get().uri("/v1/jobs/{jobId}/result", jobId).retrieve().body(RuntimeJobResult::class.java)
            ?: throw RuntimeCallException("RUNTIME_EMPTY_RESPONSE", "Agent Runtime gaf geen resultaat terug.")
    }

    override fun cancelJob(jobId: String) = call("RUNTIME_CANCEL_FAILED") {
        client.post().uri("/v1/jobs/{jobId}/cancel", jobId).retrieve().body(RuntimeJobView::class.java)
            ?: throw RuntimeCallException("RUNTIME_EMPTY_RESPONSE", "Agent Runtime gaf geen annuleringsstatus terug.")
    }

    override fun listEnvironmentKeys(projectPrefix: String): List<RuntimeEnvironmentKey> = call("RUNTIME_CATALOG_FAILED") {
        val encoded = URLEncoder.encode(projectPrefix, StandardCharsets.UTF_8)
        val body = client.get().uri(URI.create("/v1/environment-keys?project=$encoded")).retrieve().body(String::class.java) ?: "[]"
        mapper.readerForListOf(RuntimeEnvironmentKey::class.java).readValue(body)
    }

    override fun listModels(provider: String?): List<RuntimeModelView> = call("RUNTIME_MODEL_CATALOG_FAILED") {
        val query = provider?.let { "?provider=${URLEncoder.encode(it, StandardCharsets.UTF_8)}" }.orEmpty()
        val body = client.get().uri(URI.create("/v1/models$query")).retrieve().body(String::class.java) ?: "[]"
        mapper.readerForListOf(RuntimeModelView::class.java).readValue(body)
    }

    override fun downloadArtifact(jobId: String, artifactId: String): ByteArray = call("RUNTIME_ARTIFACT_FAILED") {
        client.get().uri("/v1/jobs/{jobId}/artifacts/{artifactId}", jobId, artifactId).accept(MediaType.APPLICATION_OCTET_STREAM)
            .retrieve().body(ByteArray::class.java) ?: byteArrayOf()
    }

    override fun copyV2Artifact(downloadUrl: String, offset: Long, expectedSize: Long, output: OutputStream): RuntimeArtifactCopyResult =
        rawCall("RUNTIME_ARTIFACT_FAILED", true) {
            if (offset !in 0..expectedSize) throw RuntimeCallException("RUNTIME_ARTIFACT_OFFSET_INVALID", "De lokale artifactoffset is ongeldig.")
            val builder = rawRequest(downloadUrl).GET().timeout(Duration.ofMinutes(5))
            if (offset > 0) builder.header(HttpHeaders.RANGE, "bytes=$offset-")
            val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
            requireStatus(response.statusCode(), setOf(if (offset == 0L) 200 else 206), "RUNTIME_ARTIFACT_RANGE_FAILED")
            var confirmed = offset
            response.body().use { input ->
                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (confirmed + read > expectedSize) {
                        throw RuntimeCallException("RUNTIME_ARTIFACT_TOO_LARGE", "Agent Runtime stuurde meer artifactbytes dan gedeclareerd.")
                    }
                    output.write(buffer, 0, read)
                    confirmed += read
                }
            }
            output.flush()
            RuntimeArtifactCopyResult(confirmed, confirmed == expectedSize)
        }

    override fun createUpload(request: RuntimeCreateUploadRequest) = call("RUNTIME_UPLOAD_CREATE_FAILED", true) {
        client.post().uri("/v2/uploads").contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(RuntimeUploadView::class.java)
            ?: throw RuntimeCallException("RUNTIME_EMPTY_RESPONSE", "Agent Runtime gaf geen upload terug.", true)
    }

    override fun getUploadOffset(uploadUrl: String): Long = rawCall("RUNTIME_UPLOAD_HEAD_FAILED") {
        val response = httpClient.send(rawRequest(uploadUrl).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding())
        requireStatus(response.statusCode(), setOf(200, 204), "RUNTIME_UPLOAD_HEAD_FAILED")
        response.headers().firstValue("Upload-Offset").orElseThrow {
            RuntimeCallException("RUNTIME_UPLOAD_OFFSET_MISSING", "Agent Runtime gaf geen bevestigde uploadoffset terug.")
        }.toLong()
    }

    override fun patchUpload(uploadUrl: String, offset: Long, content: ByteArray): Long = rawCall("RUNTIME_UPLOAD_PATCH_FAILED", true) {
        val response = httpClient.send(
            rawRequest(uploadUrl)
                .header("Content-Type", "application/offset+octet-stream")
                .header("Upload-Offset", offset.toString())
                .method("PATCH", HttpRequest.BodyPublishers.ofByteArray(content))
                .build(),
            HttpResponse.BodyHandlers.discarding(),
        )
        requireStatus(response.statusCode(), setOf(204), "RUNTIME_UPLOAD_PATCH_FAILED")
        response.headers().firstValue("Upload-Offset").orElseThrow {
            RuntimeCallException("RUNTIME_UPLOAD_OFFSET_MISSING", "Agent Runtime gaf geen bevestigde uploadoffset terug.", true)
        }.toLong()
    }

    override fun completeUpload(uploadId: String) = call("RUNTIME_UPLOAD_COMPLETE_FAILED", true) {
        client.post().uri("/v2/uploads/{uploadId}/complete", uploadId).retrieve().body(RuntimeObjectView::class.java)
            ?: throw RuntimeCallException("RUNTIME_EMPTY_RESPONSE", "Agent Runtime gaf geen gereed object terug.", true)
    }

    override fun deleteUpload(uploadId: String) = call("RUNTIME_UPLOAD_DELETE_FAILED") {
        client.delete().uri("/v2/uploads/{uploadId}", uploadId).retrieve().toBodilessEntity()
        Unit
    }

    override fun createV2Job(request: RuntimeV2CreateJobRequest) = call("RUNTIME_SUBMISSION_FAILED", true) {
        client.post().uri("/v2/jobs").contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(RuntimeJobView::class.java)
            ?: throw RuntimeCallException("RUNTIME_EMPTY_RESPONSE", "Agent Runtime gaf geen taak terug.", true)
    }

    override fun getV2Job(jobId: String) = call("RUNTIME_STATUS_FAILED") {
        client.get().uri("/v2/jobs/{jobId}", jobId).retrieve().body(RuntimeJobView::class.java)
            ?: throw RuntimeCallException("RUNTIME_EMPTY_RESPONSE", "Agent Runtime gaf geen status terug.")
    }

    override fun getV2Result(jobId: String) = call("RUNTIME_RESULT_FAILED") {
        client.get().uri("/v2/jobs/{jobId}/result", jobId).retrieve().body(RuntimeV2JobResult::class.java)
            ?: throw RuntimeCallException("RUNTIME_EMPTY_RESPONSE", "Agent Runtime gaf geen resultaat terug.")
    }

    override fun getV2Attempts(jobId: String): List<RuntimeAttemptView> = call("RUNTIME_ATTEMPTS_FAILED") {
        val body = client.get().uri("/v2/jobs/{jobId}/attempts", jobId).retrieve().body(String::class.java) ?: "[]"
        mapper.readerForListOf(RuntimeAttemptView::class.java).readValue(body)
    }

    override fun getV2Events(jobId: String, afterSequence: Long, limit: Int): RuntimeEventPage = call("RUNTIME_EVENTS_FAILED") {
        client.get().uri("/v2/jobs/{jobId}/events?afterSequence={after}&limit={limit}", jobId, afterSequence, limit).retrieve().body(RuntimeEventPage::class.java)
            ?: throw RuntimeCallException("RUNTIME_EMPTY_RESPONSE", "Agent Runtime gaf geen events terug.")
    }

    override fun cancelV2Job(jobId: String) = call("RUNTIME_CANCEL_FAILED") {
        client.post().uri("/v2/jobs/{jobId}/cancel", jobId).retrieve().body(RuntimeJobView::class.java)
            ?: throw RuntimeCallException("RUNTIME_EMPTY_RESPONSE", "Agent Runtime gaf geen annuleringsstatus terug.")
    }

    override fun deleteV2Content(jobId: String) = call("RUNTIME_CONTENT_DELETE_FAILED") {
        client.delete().uri("/v2/jobs/{jobId}/content", jobId).retrieve().toBodilessEntity()
        Unit
    }

    override fun listV2EnvironmentKeys(projectPrefix: String): List<RuntimeEnvironmentKey> = call("RUNTIME_CATALOG_FAILED") {
        val encoded = URLEncoder.encode(projectPrefix, StandardCharsets.UTF_8)
        val body = client.get().uri(URI.create("/v2/environment-keys?project=$encoded")).retrieve().body(String::class.java) ?: "[]"
        mapper.readerForListOf(RuntimeEnvironmentKey::class.java).readValue(body)
    }

    override fun listV2ExecutionOptions(taskType: String): List<RuntimeExecutionOption> = call("RUNTIME_MODEL_CATALOG_FAILED") {
        val encoded = URLEncoder.encode(taskType, StandardCharsets.UTF_8)
        val body = client.get().uri(URI.create("/v2/execution-options?taskType=$encoded")).retrieve().body(String::class.java) ?: "[]"
        mapper.readerForListOf(RuntimeExecutionOption::class.java).readValue(body)
    }

    private fun rawRequest(path: String): HttpRequest.Builder {
        if (!configured) throw RuntimeCallException("RUNTIME_NOT_CONFIGURED", "Agent Runtime is niet geconfigureerd.")
        val relative = URI.create(path)
        if (relative.isAbsolute || !relative.path.startsWith("/v2/")) {
            throw RuntimeCallException("RUNTIME_URL_REJECTED", "Agent Runtime gaf geen toegestane relatieve v2-URL terug.")
        }
        val uri = baseUri.resolve(relative)
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header(HttpHeaders.AUTHORIZATION, "Bearer $bearerToken")
    }

    private fun requireStatus(statusCode: Int, expected: Set<Int>, code: String) {
        if (statusCode !in expected) throw RuntimeCallException(code, "Agent Runtime wees de byte-overdracht veilig af ($statusCode).")
    }

    private fun <T> rawCall(fallbackCode: String, responseMayHaveBeenLost: Boolean = false, block: () -> T): T {
        if (!configured) throw RuntimeCallException("RUNTIME_NOT_CONFIGURED", "Agent Runtime is niet geconfigureerd.")
        return try {
            block()
        } catch (error: RuntimeCallException) {
            throw error
        } catch (_: Exception) {
            throw RuntimeCallException(fallbackCode, "Agent Runtime is tijdelijk niet bereikbaar.", responseMayHaveBeenLost)
        }
    }

    private fun <T> call(fallbackCode: String, responseMayHaveBeenLost: Boolean = false, block: () -> T): T {
        if (!configured) throw RuntimeCallException("RUNTIME_NOT_CONFIGURED", "Agent Runtime is niet geconfigureerd.")
        return try {
            block()
        } catch (error: RuntimeCallException) {
            throw error
        } catch (error: HttpClientErrorException) {
            val remoteCode = runCatching { mapper.readTree(error.responseBodyAsString).path("code").asText() }.getOrNull().orEmpty()
            throw RuntimeCallException(remoteCode.ifBlank { fallbackCode }, "Agent Runtime wees de aanvraag veilig af (${error.statusCode.value()}).")
        } catch (error: RestClientException) {
            throw RuntimeCallException(fallbackCode, "Agent Runtime is tijdelijk niet bereikbaar.", responseMayHaveBeenLost)
        }
    }

    companion object {
        private const val STREAM_BUFFER_SIZE = 64 * 1024
    }
}
