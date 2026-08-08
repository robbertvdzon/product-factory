package nl.vdzon.productfactory.iteration

import nl.vdzon.productfactory.contracts.AgentResult
import nl.vdzon.productfactory.contracts.AgentTask
import nl.vdzon.productfactory.contracts.AgentTaskState
import nl.vdzon.productfactory.contracts.AgentTaskStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.Instant

fun interface ShadowAgentBridge {
    fun execute(task: AgentTask): AgentResult
}

@Component
@Profile("!mock-agent-bridge")
class HttpShadowAgentBridge(
    @Value("\${product-factory.agent-bridge.base-url}") baseUrl: String,
    @Value("\${PF_AGENT_WORKER_TOKEN:}") private val token: String,
    @Value("\${product-factory.agent-bridge.poll-interval:PT2S}") private val pollInterval: Duration,
) : ShadowAgentBridge {
    private val client = RestClient.builder().baseUrl(baseUrl)
        .defaultStatusHandler(HttpStatusCode::isError) { _, response ->
            error("Agentbridge gaf HTTP ${response.statusCode.value()}")
        }
        .build()

    override fun execute(task: AgentTask): AgentResult {
        require(token.isNotBlank()) { "PF_AGENT_WORKER_TOKEN ontbreekt" }
        client.post().uri("/internal/agent-worker/tasks")
            .header(TOKEN_HEADER, token)
            .body(task)
            .retrieve()
            .toBodilessEntity()

        val deadline = Instant.now().plusSeconds(task.timeoutSeconds + BRIDGE_GRACE_SECONDS)
        while (Instant.now().isBefore(deadline)) {
            Thread.sleep(pollInterval.toMillis().coerceAtLeast(50))
            val status = client.get().uri("/internal/agent-worker/tasks/{runId}", task.runId)
                .header(TOKEN_HEADER, token)
                .retrieve()
                .body(AgentTaskStatus::class.java) ?: error("Agentbridge gaf geen taakstatus")
            when (status.state) {
                AgentTaskState.COMPLETED -> return status.result ?: error("Voltooide agenttaak heeft geen resultaat")
                AgentTaskState.FAILED -> return status.result ?: AgentResult(task.runId, "FAILED", "Agenttaak is mislukt")
                AgentTaskState.DISCONNECTED -> error("Agentworker verloor de verbinding tijdens ${task.runId}")
                AgentTaskState.RUNNING -> Unit
            }
        }
        error("Agentbridge-time-out voor ${task.runId}")
    }

    companion object {
        private const val TOKEN_HEADER = "X-Product-Factory-Agent-Token"
        private const val BRIDGE_GRACE_SECONDS = 30L
    }
}
