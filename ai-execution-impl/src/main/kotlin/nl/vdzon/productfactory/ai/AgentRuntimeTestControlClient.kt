package nl.vdzon.productfactory.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Instant

data class RuntimeMockFixtureRequest(
    val tenantId: String = "product-factory",
    val idempotencyKey: String,
    val result: JsonNode? = null,
    val outputSequence: List<String> = emptyList(),
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val delayMillis: Long = 0,
    val outputArtifactNames: Set<String> = emptySet(),
)

data class RuntimeMockFixtureView(
    val id: String,
    val tenantId: String,
    val idempotencyKey: String,
    val result: JsonNode?,
    val outputSequence: List<String>,
    val errorCode: String?,
    val errorMessage: String?,
    val delayMillis: Long,
    val outputArtifactNames: Set<String>,
    val createdAt: Instant,
)

interface AgentRuntimeTestControlClient {
    fun list(): List<RuntimeMockFixtureView>
    fun create(request: RuntimeMockFixtureRequest): RuntimeMockFixtureView
    fun delete(id: String)
    fun clear()
}

@Component
@Profile("acceptance")
class HttpAgentRuntimeTestControlClient(
    private val mapper: ObjectMapper,
    @Value("\${PF_AGENT_RUNTIME_URL:}") baseUrl: String,
    @Value("\${PF_AGENT_RUNTIME_TEST_CONTROL_TOKEN:}") token: String,
) : AgentRuntimeTestControlClient {
    private val configured = baseUrl.isNotBlank() && token.isNotBlank()
    private val client = RestClient.builder()
        .baseUrl(baseUrl.trimEnd('/'))
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build()

    override fun list(): List<RuntimeMockFixtureView> = call {
        val body = client.get().uri("/v2/test-control/mocks").retrieve().body(String::class.java) ?: "[]"
        mapper.readerForListOf(RuntimeMockFixtureView::class.java).readValue(body)
    }

    override fun create(request: RuntimeMockFixtureRequest): RuntimeMockFixtureView = call {
        client.post().uri("/v2/test-control/mocks").contentType(MediaType.APPLICATION_JSON).body(request).retrieve()
            .body(RuntimeMockFixtureView::class.java)
            ?: throw RuntimeCallException("RUNTIME_EMPTY_RESPONSE", "Agent Runtime gaf geen mockfixture terug.")
    }

    override fun delete(id: String) = call {
        client.delete().uri("/v2/test-control/mocks/{id}", id).retrieve().toBodilessEntity()
        Unit
    }

    override fun clear() = call {
        client.delete().uri("/v2/test-control/mocks").retrieve().toBodilessEntity()
        Unit
    }

    private fun <T> call(block: () -> T): T {
        if (!configured) throw RuntimeCallException("RUNTIME_TEST_CONTROL_NOT_CONFIGURED", "Runtime-test-control is niet geconfigureerd.")
        return try {
            block()
        } catch (error: RuntimeCallException) {
            throw error
        } catch (error: HttpClientErrorException) {
            val code = runCatching { mapper.readTree(error.responseBodyAsString).path("code").asText() }.getOrNull().orEmpty()
            throw RuntimeCallException(code.ifBlank { "RUNTIME_TEST_CONTROL_REJECTED" }, "Agent Runtime wees de mockfixture veilig af (${error.statusCode.value()}).")
        } catch (_: RestClientException) {
            throw RuntimeCallException("RUNTIME_TEST_CONTROL_UNAVAILABLE", "Runtime-test-control is tijdelijk niet bereikbaar.")
        }
    }
}
