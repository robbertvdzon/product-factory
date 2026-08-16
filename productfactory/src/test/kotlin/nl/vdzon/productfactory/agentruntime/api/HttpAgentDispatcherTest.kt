package nl.vdzon.productfactory.agentruntime.api

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import nl.vdzon.productfactory.contracts.AgentTask
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpAgentDispatcherTest {
    @Test
    fun `task is resubmitted when a rolling backend loses its in-memory status`() {
        val submits = AtomicInteger()
        val statuses = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/internal/agent-worker/tasks") { exchange ->
                when {
                    exchange.requestMethod == "POST" -> {
                        submits.incrementAndGet()
                        exchange.respond(202, "")
                    }
                    statuses.incrementAndGet() == 1 -> exchange.respond(404, "")
                    else -> exchange.respond(
                        200,
                        """{"runId":"run-rollout","state":"COMPLETED","result":{"runId":"run-rollout","status":"COMPLETED","summary":"browsercontrole uitgevoerd"}}""",
                    )
                }
            }
            start()
        }
        try {
            val dispatcher = HttpAgentDispatcher(
                "http://127.0.0.1:${server.address.port}",
                "secret",
                Duration.ofMillis(1),
            )

            val result = dispatcher.execute(AgentTask("run-rollout", "hkh-autopilot", "test-session", "Test", timeoutSeconds = 5))

            assertEquals("COMPLETED", result.status)
            assertEquals(2, submits.get())
            assertEquals(2, statuses.get())
        } finally {
            server.stop(0)
        }
    }

    private fun HttpExchange.respond(status: Int, body: String) {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
