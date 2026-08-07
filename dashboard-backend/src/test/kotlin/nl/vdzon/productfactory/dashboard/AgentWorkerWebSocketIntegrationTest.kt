package nl.vdzon.productfactory.dashboard

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import nl.vdzon.productfactory.contracts.AgentWorkerHello
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "PF_AGENT_WORKER_TOKEN=bridge-secret",
        "PF_DASHBOARD_AUTH_REQUIRED=false",
    ],
)
class AgentWorkerWebSocketIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var hub: AgentWorkerHub

    @Test
    fun `worker authenticates over real websocket endpoint`() {
        val hello = AgentWorkerHello(token = "bridge-secret", workerId = "integration-mac", workerVersion = "test")
        val socket = HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .buildAsync(URI.create("ws://localhost:$port/agent-worker"), object : WebSocket.Listener {})
            .get(5, TimeUnit.SECONDS)

        socket.sendText(jacksonObjectMapper().writeValueAsString(hello), true).get(5, TimeUnit.SECONDS)
        val connected = generateSequence { hub.status().connected }
            .take(100)
            .onEach { if (!it) Thread.sleep(10) }
            .any { it }

        assertTrue(connected)
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "test complete").get(5, TimeUnit.SECONDS)
    }
}
