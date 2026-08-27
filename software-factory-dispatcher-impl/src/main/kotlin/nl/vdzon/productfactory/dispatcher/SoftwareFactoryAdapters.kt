package nl.vdzon.productfactory.dispatcher

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class FactoryAttachment(
    val id: String,
    val fileName: String,
    val mediaType: String,
    val sizeBytes: Long,
    val sha256: String,
    val contentBase64: String,
)

data class FactoryStoryRequest(
    val productId: String,
    val sourceStoryId: String,
    val sourceStoryVersion: Long,
    val type: String,
    val targetRepositoryUrl: String,
    val title: String,
    val description: String,
    val attachments: List<FactoryAttachment> = emptyList(),
)

data class FactoryConnectionStatus(val connected: Boolean, val factoryVersion: String?, val apiVersion: String)
data class FactoryCreateResult(val storyKey: String, val created: Boolean, val status: String)
data class FactoryWork(
    val storyKey: String,
    val productId: String,
    val sourceStoryId: String,
    val sourceStoryVersion: Long,
    val status: String,
    val deliveredCommitSha: String?,
    val cancelReason: String?,
    val updatedAt: String?,
)
data class FactoryWorkList(val items: List<FactoryWork>)
private data class FactoryErrorBody(val code: String = "UNKNOWN", val message: String = "Onbekende Software Factory-fout.", val retryable: Boolean = false)

interface SoftwareFactoryAdapter {
    val mode: String
    fun status(): FactoryConnectionStatus
    fun create(idempotencyKey: String, request: FactoryStoryRequest): FactoryCreateResult
    fun get(storyKey: String): FactoryWork?
    fun find(productId: String? = null, status: String? = null, idempotencyKey: String? = null): List<FactoryWork>
}

sealed class SoftwareFactoryFailure(val code: String, message: String) : RuntimeException(message)
class RetryableFactoryFailure(code: String, message: String) : SoftwareFactoryFailure(code, message)
class AuthorizationFactoryFailure(code: String, message: String) : SoftwareFactoryFailure(code, message)
class ContractFactoryFailure(code: String, message: String) : SoftwareFactoryFailure(code, message)
class ConfigurationFactoryFailure(code: String, message: String) : SoftwareFactoryFailure(code, message)

@Service
class RealSoftwareFactoryAdapter(
    private val mapper: ObjectMapper,
    @Value("\${PF_SOFTWARE_FACTORY_URL:}") private val baseUrl: String,
    @Value("\${PF_SOFTWARE_FACTORY_TOKEN:}") private val token: String,
    @Value("\${PF_ENVIRONMENT:local}") private val runtimeEnvironment: String,
) : SoftwareFactoryAdapter {
    override val mode = "REAL"
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    override fun status(): FactoryConnectionStatus = call("GET", "/status", null, null, FactoryConnectionStatus::class.java)

    override fun create(idempotencyKey: String, request: FactoryStoryRequest): FactoryCreateResult =
        call("POST", "/stories", idempotencyKey, mapper.writeValueAsString(request), FactoryCreateResult::class.java)

    override fun get(storyKey: String): FactoryWork? = try {
        call("GET", "/stories/${encode(storyKey)}", null, null, FactoryWork::class.java)
    } catch (error: ContractFactoryFailure) {
        if (error.code == "STORY_NOT_FOUND") null else throw error
    }

    override fun find(productId: String?, status: String?, idempotencyKey: String?): List<FactoryWork> {
        val parameters = listOfNotNull(
            productId?.let { "productId=${encode(it)}" },
            status?.let { "status=${encode(it)}" },
            idempotencyKey?.let { "idempotencyKey=${encode(it)}" },
        )
        return call("GET", "/stories?${parameters.joinToString("&")}", null, null, FactoryWorkList::class.java).items
    }

    private fun <T> call(method: String, path: String, key: String?, json: String?, type: Class<T>): T {
        validateConfiguration()
        val request = HttpRequest.newBuilder(URI.create(baseUrl.trimEnd('/') + path))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .also { builder ->
                key?.let { builder.header("Idempotency-Key", it) }
                if (json != null) builder.header("Content-Type", "application/json")
            }
            .method(method, json?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
            .build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RetryableFactoryFailure("REQUEST_INTERRUPTED", "Software Factory-call werd onderbroken.")
        } catch (_: Exception) {
            throw RetryableFactoryFailure("TRANSPORT_FAILURE", "Software Factory is tijdelijk niet bereikbaar.")
        }
        if (response.statusCode() !in 200..299) {
            val error = runCatching { mapper.readValue(response.body(), FactoryErrorBody::class.java) }
                .getOrDefault(FactoryErrorBody("HTTP_${response.statusCode()}", "Software Factory weigerde de call.", response.statusCode() >= 500))
            when {
                response.statusCode() == 401 || response.statusCode() == 403 -> throw AuthorizationFactoryFailure(error.code, error.message)
                error.retryable || response.statusCode() >= 500 -> throw RetryableFactoryFailure(error.code, error.message)
                else -> throw ContractFactoryFailure(error.code, error.message)
            }
        }
        return runCatching { mapper.readValue(response.body(), type) }
            .getOrElse { throw ContractFactoryFailure("INVALID_RESPONSE", "Software Factory gaf geen geldige v2-response.") }
    }

    private fun validateConfiguration() {
        val uri = runCatching { URI(baseUrl) }.getOrNull()
        val safeUrl = uri != null && uri.host?.isNotBlank() == true && uri.userInfo == null && uri.query == null && uri.fragment == null &&
            uri.path.trimEnd('/').endsWith("/api/integrations/v2") && uri.path.contains("/v1").not() &&
            (uri.scheme == "https" || (runtimeEnvironment.equals("local", true) && uri.scheme == "http"))
        val productionSafe = !runtimeEnvironment.equals("production", true) || baseUrl == PRODUCTION_URL
        if (!safeUrl || !productionSafe || token.isBlank()) {
            throw ConfigurationFactoryFailure("INVALID_CONFIGURATION", "De echte Software Factory-v2-configuratie is niet volledig of veilig.")
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    companion object { const val PRODUCTION_URL = "https://dashboard.vdzonsoftware.nl/api/integrations/v2" }
}

interface MockSoftwareFactoryControl {
    fun complete(storyKey: String, commitSha: String)
    fun cancel(storyKey: String, reason: String)
    fun failNextCall()
    fun loseNextCreateResponse()
    fun breakNextContract()
    fun attachments(storyKey: String): List<FactoryAttachment>
    fun reset()
}

@Service
class MockSoftwareFactory(private val mapper: ObjectMapper) : SoftwareFactoryAdapter, MockSoftwareFactoryControl {
    override val mode = "MOCKED"
    private data class Stored(val key: String, val idempotencyKey: String, val packageHash: String, val request: FactoryStoryRequest, var status: String, var sha: String? = null, var reason: String? = null, var updatedAt: Instant = Instant.now())
    private val stories = ConcurrentHashMap<String, Stored>()
    private var nextNumber = 3000
    @Volatile private var failNext = false
    @Volatile private var loseNext = false
    @Volatile private var breakNext = false

    override fun status(): FactoryConnectionStatus { maybeFail(); return FactoryConnectionStatus(true, "testbed", "2") }

    override fun create(idempotencyKey: String, request: FactoryStoryRequest): FactoryCreateResult {
        maybeFail()
        val hash = sha256(mapper.writeValueAsBytes(request))
        val byKey = stories.values.singleOrNull { it.idempotencyKey == idempotencyKey }
        if (byKey != null && byKey.packageHash != hash) throw ContractFactoryFailure("IDEMPOTENCY_CONFLICT", "Dezelfde sleutel bevat andere inhoud.")
        val byHash = stories.values.singleOrNull { it.packageHash == hash }
        val existing = byKey ?: byHash
        val stored = existing ?: synchronized(this) {
            val key = "SF-${nextNumber++}"
            Stored(key, idempotencyKey, hash, request, "OPEN").also { stories[key] = it }
        }
        if (loseNext) {
            loseNext = false
            throw RetryableFactoryFailure("LOST_RESPONSE", "De geslaagde create-response ging verloren.")
        }
        return FactoryCreateResult(stored.key, existing == null, stored.status)
    }

    override fun get(storyKey: String): FactoryWork? { maybeFail(); maybeBreak(); return stories[storyKey]?.projection() }

    override fun find(productId: String?, status: String?, idempotencyKey: String?): List<FactoryWork> {
        maybeFail(); maybeBreak()
        return stories.values.filter {
            (productId == null || it.request.productId == productId) &&
                (status == null || it.status == status) && (idempotencyKey == null || it.idempotencyKey == idempotencyKey)
        }.sortedBy { it.key }.map { it.projection() }
    }

    override fun complete(storyKey: String, commitSha: String) {
        require(commitSha.matches(Regex("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}"))) { "Een volledige commit-SHA is verplicht." }
        stories[storyKey]?.apply { status = "DONE"; sha = commitSha; updatedAt = Instant.now() } ?: error("Onbekende mockstory $storyKey")
    }

    override fun cancel(storyKey: String, reason: String) {
        stories[storyKey]?.apply { status = "CANCELLED"; this.reason = reason; updatedAt = Instant.now() } ?: error("Onbekende mockstory $storyKey")
    }
    override fun failNextCall() { failNext = true }
    override fun loseNextCreateResponse() { loseNext = true }
    override fun breakNextContract() { breakNext = true }
    override fun attachments(storyKey: String): List<FactoryAttachment> = stories[storyKey]?.request?.attachments.orEmpty()
    override fun reset() { stories.clear(); nextNumber = 3000; failNext = false; loseNext = false; breakNext = false }
    private fun maybeFail() { if (failNext) { failNext = false; throw RetryableFactoryFailure("TEMPORARY_FAILURE", "Gesimuleerde tijdelijke storing.") } }
    private fun maybeBreak() { if (breakNext) { breakNext = false; throw ContractFactoryFailure("INVALID_RESPONSE", "Gesimuleerde contractbreuk.") } }
    private fun Stored.projection() = FactoryWork(key, request.productId, request.sourceStoryId, request.sourceStoryVersion, status, sha, reason, updatedAt.toString())
}

private fun sha256(bytes: ByteArray): String = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))
