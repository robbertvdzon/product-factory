package nl.vdzon.productfactory.dispatcher

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

data class DashboardHotfixRequest(
    val projectKey: String? = null,
    val title: String,
    val description: String,
    val repo: String,
    val aiSupplier: String? = null,
    val aiModel: String? = null,
    val start: Boolean = true,
    val questionsAllowed: Boolean = true,
    val hotfix: Boolean = true,
    val approvalMode: String = "automatisch",
    val notificationEvents: Set<String> = setOf("DEPLOYED", "QUESTION", "MANUAL_ACTION_REQUIRED", "ERROR"),
)
data class DashboardHotfixResult(val storyKey: String, val created: Boolean)
data class DashboardTokenStatus(val configured: Boolean, val reachable: Boolean, val checkedAt: String?, val safeErrorCode: String?)

interface DashboardHotfixGateway {
    fun status(): DashboardTokenStatus
    fun createOrRecover(marker: String, request: DashboardHotfixRequest): DashboardHotfixResult
}

@Service
class DashboardHotfixAdapter(
    private val mapper: ObjectMapper,
    @Value("\${PF_SOFTWARE_FACTORY_DASHBOARD_URL:}") private val baseUrl: String,
    @Value("\${PF_SOFTWARE_FACTORY_DASHBOARD_TOKEN:}") private val token: String,
) : DashboardHotfixGateway {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val lastSuccessfulCheck = AtomicReference<Instant?>()

    override fun status(): DashboardTokenStatus = try {
        call("GET", "/api/v1/status", null)
        val checkedAt = Instant.now().also(lastSuccessfulCheck::set)
        DashboardTokenStatus(true, true, checkedAt.toString(), null)
    } catch (error: SoftwareFactoryFailure) {
        DashboardTokenStatus(token.isNotBlank(), false, lastSuccessfulCheck.get()?.toString(), error.code)
    }

    override fun createOrRecover(marker: String, request: DashboardHotfixRequest): DashboardHotfixResult {
        require(marker.matches(Regex("Product-Request: [0-9a-f-]{36}:v[1-9][0-9]*"))) { "Ongeldige requestmarker." }
        find(marker)?.let { return DashboardHotfixResult(it, false) }
        val body = mapper.valueToTree<JsonNode>(request)
        val response = call("POST", "/api/v1/stories", mapper.writeValueAsString(body))
        val key = response.path("key").asText().ifBlank { response.path("storyKey").asText() }
        if (key.isBlank()) throw ContractFactoryFailure("HOTFIX_RESPONSE_INVALID", "Software Factory gaf geen storykey terug.")
        return DashboardHotfixResult(key, true)
    }

    private fun find(marker: String): String? {
        val response = call("GET", "/api/v1/stories", null)
        val issues = if (response.isArray) response else response.path("issues")
        return issues.firstOrNull { it.path("description").asText().contains(marker) }
            ?.path("key")?.asText()?.takeIf { it.isNotBlank() }
    }

    private fun call(method: String, path: String, json: String?): JsonNode {
        if (baseUrl.isBlank() || token.isBlank()) throw ConfigurationFactoryFailure("HOTFIX_TOKEN_MISSING", "Hotfixbediening is niet geconfigureerd.")
        val request = HttpRequest.newBuilder(URI.create(baseUrl.trimEnd('/') + path))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .also { if (json != null) it.header("Content-Type", "application/json") }
            .method(method, json?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
            .build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RetryableFactoryFailure("HOTFIX_INTERRUPTED", "Hotfixcall werd onderbroken.")
        } catch (_: Exception) {
            throw RetryableFactoryFailure("HOTFIX_UNREACHABLE", "Software Factory-dashboard is tijdelijk niet bereikbaar.")
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) throw AuthorizationFactoryFailure("HOTFIX_TOKEN_REJECTED", "Hotfixtoken is geweigerd of verlopen.")
        if (response.statusCode() !in 200..299) {
            val retryable = response.statusCode() >= 500
            if (retryable) throw RetryableFactoryFailure("HOTFIX_HTTP_${response.statusCode()}", "Software Factory is tijdelijk niet beschikbaar.")
            throw ContractFactoryFailure("HOTFIX_HTTP_${response.statusCode()}", "Software Factory weigerde het hotfixcontract.")
        }
        return runCatching { mapper.readTree(response.body()) }
            .getOrElse { throw ContractFactoryFailure("HOTFIX_RESPONSE_INVALID", "Software Factory gaf ongeldige JSON terug.") }
    }
}
