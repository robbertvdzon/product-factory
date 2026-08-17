package nl.vdzon.productfactory.agentworker

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PreDestroy
import nl.vdzon.productfactory.contracts.AgentResult
import nl.vdzon.productfactory.contracts.AgentWorkerHello
import nl.vdzon.productfactory.contracts.AgentWorkerResultFrame
import nl.vdzon.productfactory.contracts.AgentWorkerTaskFrame
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class AgentWorkerClient(
    private val settings: AgentWorkerSettings,
    private val taskExecutor: AgentTaskExecutor,
    private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(),
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val socket = AtomicReference<WebSocket?>()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val taskThreads = boundedAgentTaskPool(settings.parallelism)
    private val reconnectPending = AtomicBoolean(false)
    private val shuttingDown = AtomicBoolean(false)
    private val shutdown = CountDownLatch(1)
    private val backoffMs = AtomicLong(MIN_RECONNECT_MS)
    private val completed = ConcurrentHashMap<String, AgentResult>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val sendLock = Any()
    @Volatile private var heartbeat: ScheduledFuture<*>? = null

    fun start() {
        require(settings.url.startsWith("ws://") || settings.url.startsWith("wss://")) { "PF_AGENT_WORKER_URL moet ws:// of wss:// gebruiken" }
        require(settings.token.isNotBlank()) { "PF_AGENT_WORKER_TOKEN ontbreekt" }
        require(settings.workerId.isNotBlank()) { "PF_AGENT_WORKER_ID ontbreekt" }
        connect()
    }

    fun runUntilShutdown() {
        start()
        shutdown.await()
    }

    private fun connect() {
        if (shuttingDown.get()) return
        reconnectPending.set(false)
        logger.info("Verbinden met Product Factory-agentendpoint: {}", settings.url)
        httpClient.newWebSocketBuilder().buildAsync(URI.create(settings.url), Listener()).whenComplete { _, error ->
            if (error != null) {
                logger.warn("Agentworker-verbinding mislukt: {}", error.message)
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        if (shuttingDown.get() || !reconnectPending.compareAndSet(false, true)) return
        val delay = backoffMs.getAndUpdate { current -> (current * 2).coerceAtMost(MAX_RECONNECT_MS) }
        scheduler.schedule({ connect() }, delay, TimeUnit.MILLISECONDS)
    }

    private fun handleTask(frame: AgentWorkerTaskFrame) {
        completed[frame.task.runId]?.let { previous ->
            send(AgentWorkerResultFrame(result = previous))
            return
        }
        if (!inFlight.add(frame.task.runId)) return
        taskThreads.submit {
            val result = runCatching { taskExecutor.execute(frame.task) }
                .getOrElse { AgentResult(frame.task.runId, "FAILED", "Agenttaak faalde: ${it.message ?: it.javaClass.simpleName}") }
            if (result.status != "COMPLETED") {
                logger.warn("Agenttaak {} eindigde als {}: {}", result.runId, result.status, result.summary.take(1_000))
            }
            completed[frame.task.runId] = result
            inFlight.remove(frame.task.runId)
            send(AgentWorkerResultFrame(result = result))
        }
    }

    private fun send(frame: Any) {
        val active = socket.get()?.takeIf { !it.isOutputClosed } ?: return
        val payload = runCatching { mapper.writeValueAsString(frame) }.getOrElse {
            logger.error("Agentworker-frame kon niet worden geserialiseerd: {}", it.message)
            return
        }
        runCatching {
            synchronized(sendLock) { active.sendText(payload, true).join() }
        }.onFailure {
            logger.warn("Agentworker-frame kon niet worden verstuurd: {}", it.message)
            if (socket.compareAndSet(active, null)) {
                heartbeat?.cancel(true)
                active.abort()
                scheduleReconnect()
            }
        }
    }

    private fun startHeartbeat(active: WebSocket) {
        heartbeat?.cancel(true)
        heartbeat = scheduler.scheduleAtFixedRate({
            if (!active.isOutputClosed) runCatching { active.sendPing(ByteBuffer.wrap(byteArrayOf(1))).join() }
        }, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS)
    }

    @PreDestroy
    fun stop() {
        shuttingDown.set(true)
        heartbeat?.cancel(true)
        runCatching { socket.get()?.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown")?.join() }
        scheduler.shutdownNow()
        taskThreads.shutdownNow()
        shutdown.countDown()
    }

    private inner class Listener : WebSocket.Listener {
        private val text = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            socket.set(webSocket)
            backoffMs.set(MIN_RECONNECT_MS)
            send(
                AgentWorkerHello(
                    token = settings.token,
                    workerId = settings.workerId,
                    workerVersion = settings.version,
                    capabilities = listOf("codex-subscription", "claude-subscription"),
                ),
            )
            startHeartbeat(webSocket)
            webSocket.request(1)
            logger.info("Agentworker verbonden als {}", settings.workerId)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            synchronized(text) {
                text.append(data)
                if (last) {
                    val raw = text.toString()
                    text.setLength(0)
                    if (runCatching { mapper.readTree(raw).path("type").asText() }.getOrDefault("") == "task") {
                        runCatching { mapper.readValue<AgentWorkerTaskFrame>(raw) }.onSuccess(::handleTask)
                    }
                }
            }
            webSocket.request(1)
            return null
        }

        override fun onPing(webSocket: WebSocket, message: ByteBuffer): CompletionStage<*> {
            webSocket.request(1)
            return webSocket.sendPong(message)
        }

        override fun onPong(webSocket: WebSocket, message: ByteBuffer): CompletionStage<*>? {
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            logger.info("Agentworker-verbinding gesloten: {} {}", statusCode, reason)
            if (socket.compareAndSet(webSocket, null)) {
                heartbeat?.cancel(true)
                scheduleReconnect()
            }
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            logger.warn("Agentworker-transportfout: {}", error.message)
            if (socket.compareAndSet(webSocket, null)) {
                heartbeat?.cancel(true)
                scheduleReconnect()
            }
        }
    }

    companion object {
        const val MIN_RECONNECT_MS = 1_000L
        const val MAX_RECONNECT_MS = 60_000L
        const val HEARTBEAT_SECONDS = 30L
    }
}

internal fun boundedAgentTaskPool(parallelism: Int): ExecutorService {
    require(parallelism in 1..16) { "PF_AGENT_PARALLELISM moet tussen 1 en 16 liggen" }
    return Executors.newFixedThreadPool(parallelism)
}
