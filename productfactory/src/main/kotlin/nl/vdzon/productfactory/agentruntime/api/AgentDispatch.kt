package nl.vdzon.productfactory.agentruntime.api

import nl.vdzon.productfactory.contracts.AgentResult
import nl.vdzon.productfactory.contracts.AgentTask
import nl.vdzon.productfactory.contracts.AgentTaskState
import nl.vdzon.productfactory.contracts.AgentTaskStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.ResourceAccessException
import java.time.Duration
import java.time.Instant

fun interface AgentDispatchPort {
    fun execute(task: AgentTask): AgentResult
}

@Component
@Profile("!mock-agent-bridge")
class HttpAgentDispatcher(
    @Value("\${product-factory.agent-bridge.base-url}") baseUrl: String,
    @Value("\${PF_AGENT_WORKER_TOKEN:}") private val token: String,
    @Value("\${product-factory.agent-bridge.poll-interval:PT2S}") private val pollInterval: Duration,
) : AgentDispatchPort {
    private val client = RestClient.builder().baseUrl(baseUrl).build()

    override fun execute(task: AgentTask): AgentResult {
        require(token.isNotBlank()) { "PF_AGENT_WORKER_TOKEN ontbreekt" }
        val deadline = Instant.now().plusSeconds(task.timeoutSeconds + BRIDGE_GRACE_SECONDS)
        var mustSubmit = true
        var lastBridgeError: String? = null
        while (Instant.now().isBefore(deadline)) {
            if (mustSubmit) {
                try {
                    client.post().uri("/internal/agent-worker/tasks")
                        .header(TOKEN_HEADER, token)
                        .body(task)
                        .retrieve()
                        .toBodilessEntity()
                    mustSubmit = false
                    lastBridgeError = null
                } catch (exception: RestClientException) {
                    if (!exception.isRecoverableBridgeFailure()) throw exception
                    lastBridgeError = exception.message
                    Thread.sleep(pollInterval.toMillis().coerceAtLeast(50))
                    continue
                }
            }
            Thread.sleep(pollInterval.toMillis().coerceAtLeast(50))
            val status = try {
                client.get().uri("/internal/agent-worker/tasks/{runId}", task.runId)
                    .header(TOKEN_HEADER, token)
                    .retrieve()
                    .body(AgentTaskStatus::class.java) ?: error("Agentbridge gaf geen taakstatus")
            } catch (exception: RestClientException) {
                if (!exception.isRecoverableBridgeFailure()) throw exception
                // Een rolling deployment kan de in-memory status wissen. Opnieuw aanbieden is veilig:
                // backend en worker dedupliceren beide op runId.
                mustSubmit = true
                lastBridgeError = exception.message
                continue
            }
            when (status.state) {
                AgentTaskState.COMPLETED -> return status.result ?: error("Voltooide agenttaak heeft geen resultaat")
                AgentTaskState.FAILED -> return status.result ?: AgentResult(task.runId, "FAILED", "Agenttaak is mislukt")
                AgentTaskState.DISCONNECTED -> mustSubmit = true
                AgentTaskState.RUNNING -> Unit
            }
        }
        error("Agentbridge-time-out voor ${task.runId}${lastBridgeError?.let { ": $it" }.orEmpty()}")
    }

    private fun RestClientException.isRecoverableBridgeFailure(): Boolean =
        this is ResourceAccessException ||
            this is RestClientResponseException && (statusCode.value() == 404 || statusCode.is5xxServerError)

    companion object {
        private const val TOKEN_HEADER = "X-Product-Factory-Agent-Token"
        private const val BRIDGE_GRACE_SECONDS = 30L
    }
}
