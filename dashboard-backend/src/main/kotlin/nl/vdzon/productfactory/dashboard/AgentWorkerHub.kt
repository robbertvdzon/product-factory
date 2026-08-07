package nl.vdzon.productfactory.dashboard

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import nl.vdzon.productfactory.contracts.AgentResult
import nl.vdzon.productfactory.contracts.AgentTask
import nl.vdzon.productfactory.contracts.AgentTaskState
import nl.vdzon.productfactory.contracts.AgentTaskStatus
import nl.vdzon.productfactory.contracts.AgentWorkerHello
import nl.vdzon.productfactory.contracts.AgentWorkerResultFrame
import nl.vdzon.productfactory.contracts.AgentWorkerStatus
import nl.vdzon.productfactory.contracts.AgentWorkerTaskFrame
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class AgentWorkerOfflineException : RuntimeException("Geen agentworker verbonden")
class AgentTaskNotFoundException(runId: String) : RuntimeException("Onbekende agenttaak: $runId")

@Component
class AgentWorkerHub(
    @Value("\${PF_AGENT_WORKER_TOKEN:}") private val expectedToken: String,
) : TextWebSocketHandler() {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val writeLock = Any()
    private val tasks = ConcurrentHashMap<String, AgentTaskStatus>()

    @Volatile private var session: WebSocketSession? = null
    @Volatile private var hello: AgentWorkerHello? = null
    @Volatile private var connectedSince: Instant? = null

    fun status(): AgentWorkerStatus {
        val activeHello = hello
        val connected = session?.isOpen == true && activeHello != null
        return AgentWorkerStatus(
            connected = connected,
            workerId = activeHello?.workerId,
            workerVersion = activeHello?.workerVersion,
            capabilities = activeHello?.capabilities.orEmpty(),
            connectedSince = connectedSince,
            activeRuns = tasks.values.count { it.state == AgentTaskState.RUNNING },
        )
    }

    fun submit(task: AgentTask): AgentTaskStatus {
        require(task.runId.isNotBlank()) { "runId is verplicht" }
        require(task.productSlug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) { "Ongeldige productslug" }
        require(task.taskType.isNotBlank() && task.prompt.isNotBlank()) { "taskType en prompt zijn verplicht" }
        require(task.timeoutSeconds in 1..MAX_TASK_TIMEOUT_SECONDS) { "timeoutSeconds moet tussen 1 en $MAX_TASK_TIMEOUT_SECONDS liggen" }

        val activeSession = session?.takeIf { it.isOpen && hello != null } ?: throw AgentWorkerOfflineException()
        val accepted = AgentTaskStatus(task.runId, AgentTaskState.RUNNING)
        check(tasks.putIfAbsent(task.runId, accepted) == null) { "runId bestaat al" }
        try {
            synchronized(writeLock) {
                activeSession.sendMessage(TextMessage(mapper.writeValueAsString(AgentWorkerTaskFrame(task = task))))
            }
        } catch (exception: Exception) {
            tasks[task.runId] = AgentTaskStatus(task.runId, AgentTaskState.DISCONNECTED)
            throw AgentWorkerOfflineException()
        }
        return accepted
    }

    fun taskStatus(runId: String): AgentTaskStatus = tasks[runId] ?: throw AgentTaskNotFoundException(runId)

    override fun handleTextMessage(webSocketSession: WebSocketSession, message: TextMessage) {
        when (frameType(message.payload)) {
            "hello" -> handleHello(webSocketSession, message.payload)
            "result" -> handleResult(webSocketSession, message.payload)
            else -> logger.warn("Onbekend agentworker-frame genegeerd.")
        }
    }

    private fun handleHello(webSocketSession: WebSocketSession, raw: String) {
        val candidate = runCatching { mapper.readValue<AgentWorkerHello>(raw) }.getOrNull()
        if (candidate == null || expectedToken.isBlank() || !constantTimeEquals(candidate.token, expectedToken)) {
            logger.warn("Agentworker-hello geweigerd.")
            runCatching { webSocketSession.close(CloseStatus.POLICY_VIOLATION) }
            return
        }
        session?.takeIf { it.isOpen && it != webSocketSession }?.let { previous ->
            runCatching { previous.close(CloseStatus.NORMAL.withReason("replaced")) }
        }
        session = webSocketSession
        hello = candidate.copy(token = "")
        connectedSince = Instant.now()
        logger.info("Agentworker verbonden: workerId={}, version={}", candidate.workerId, candidate.workerVersion)
    }

    private fun handleResult(webSocketSession: WebSocketSession, raw: String) {
        if (session != webSocketSession || hello == null) return
        val result = runCatching { mapper.readValue<AgentWorkerResultFrame>(raw).result }.getOrNull() ?: return
        val current = tasks[result.runId] ?: return
        if (current.state != AgentTaskState.RUNNING) return
        val state = if (result.status.equals("COMPLETED", ignoreCase = true)) AgentTaskState.COMPLETED else AgentTaskState.FAILED
        tasks[result.runId] = AgentTaskStatus(result.runId, state, result)
    }

    override fun afterConnectionClosed(webSocketSession: WebSocketSession, status: CloseStatus) {
        if (session != webSocketSession) return
        session = null
        hello = null
        connectedSince = null
        tasks.replaceAll { _, task ->
            if (task.state == AgentTaskState.RUNNING) task.copy(state = AgentTaskState.DISCONNECTED, updatedAt = Instant.now()) else task
        }
        logger.info("Agentworker-verbinding gesloten: {}", status)
    }

    private fun frameType(raw: String): String = runCatching { mapper.readTree(raw).path("type").asText() }.getOrDefault("")

    private fun constantTimeEquals(first: String, second: String): Boolean = MessageDigest.isEqual(
        first.toByteArray(StandardCharsets.UTF_8),
        second.toByteArray(StandardCharsets.UTF_8),
    )

    companion object {
        const val MAX_TASK_TIMEOUT_SECONDS = 7200L
    }
}
