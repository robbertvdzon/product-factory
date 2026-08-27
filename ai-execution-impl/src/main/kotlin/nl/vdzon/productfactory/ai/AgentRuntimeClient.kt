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

class RuntimeCallException(val code: String, val safeMessage: String, val responseMayHaveBeenLost: Boolean = false) : RuntimeException(safeMessage)

interface AgentRuntimeClient {
    fun createJob(request: RuntimeCreateJobRequest): RuntimeJobView
    fun getJob(jobId: String): RuntimeJobView
    fun getResult(jobId: String): RuntimeJobResult
    fun cancelJob(jobId: String): RuntimeJobView
    fun listEnvironmentKeys(projectPrefix: String): List<RuntimeEnvironmentKey>
    fun listModels(provider: String?): List<RuntimeModelView>
    fun downloadArtifact(jobId: String, artifactId: String): ByteArray
}

@Component
class HttpAgentRuntimeClient(
    private val mapper: ObjectMapper,
    @Value("\${PF_AGENT_RUNTIME_URL:}") baseUrl: String,
    @Value("\${PF_AGENT_RUNTIME_TOKEN:}") token: String,
) : AgentRuntimeClient {
    private val configured = baseUrl.isNotBlank() && token.isNotBlank()
    private val client = RestClient.builder()
        .baseUrl(baseUrl.trimEnd('/'))
        .requestFactory(JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
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
}
