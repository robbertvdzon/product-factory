package nl.vdzon.productfactory.advisor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import nl.vdzon.productfactory.dispatcher.DashboardHotfixAdapter
import nl.vdzon.productfactory.dispatcher.DashboardHotfixRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

class DashboardHotfixAdapterTest {
    private var server: HttpServer? = null

    @AfterEach
    fun stop() {
        server?.stop(0)
    }

    @Test
    fun `verloren create-response wordt via de vaste marker teruggevonden`() {
        val creates = AtomicInteger()
        start { exchange ->
            assertThat(exchange.requestHeaders.getFirst("Authorization")).isEqualTo("Bearer secret-token")
            if (exchange.requestMethod == "GET" && exchange.requestURI.path == "/api/v1/stories") {
                exchange.json(200, """{"issues":[{"key":"SF-42","description":"Product-Request: 123e4567-e89b-12d3-a456-426614174000:v2"}]}""")
            } else {
                creates.incrementAndGet()
                exchange.json(200, """{"key":"SF-new"}""")
            }
        }

        val result = adapter().createOrRecover(
            "Product-Request: 123e4567-e89b-12d3-a456-426614174000:v2",
            request(),
        )

        assertThat(result.storyKey).isEqualTo("SF-42")
        assertThat(result.created).isFalse()
        assertThat(creates).hasValue(0)
    }

    @Test
    fun `create verstuurt expliciet de bestaande hotfixketen`() {
        var payload = jacksonObjectMapper().createObjectNode()
        start { exchange ->
            if (exchange.requestMethod == "GET") {
                exchange.json(200, """{"issues":[]}""")
            } else {
                payload = jacksonObjectMapper().readTree(exchange.requestBody) as com.fasterxml.jackson.databind.node.ObjectNode
                exchange.json(200, """{"key":"SF-43"}""")
            }
        }

        val result = adapter().createOrRecover(
            "Product-Request: 123e4567-e89b-12d3-a456-426614174000:v1",
            request(),
        )

        assertThat(result.storyKey).isEqualTo("SF-43")
        assertThat(payload.path("hotfix").asBoolean()).isTrue()
        assertThat(payload.path("start").asBoolean()).isTrue()
        assertThat(payload.path("repo").asText()).isEqualTo("https://github.com/example/product.git")
    }

    @Test
    fun `geweigerd token wordt veilig als verlopen zichtbaar`() {
        start { exchange -> exchange.json(401, """{"message":"details blijven extern"}""") }

        val status = adapter().status()

        assertThat(status.configured).isTrue()
        assertThat(status.reachable).isFalse()
        assertThat(status.safeErrorCode).isEqualTo("HOTFIX_TOKEN_REJECTED")
    }

    private fun request() = DashboardHotfixRequest(
        title = "Kleine correctie",
        description = "Bevestigde inhoud",
        repo = "https://github.com/example/product.git",
    )

    private fun adapter() = DashboardHotfixAdapter(
        jacksonObjectMapper(),
        "http://127.0.0.1:${server!!.address.port}",
        "secret-token",
    )

    private fun start(handler: (HttpExchange) -> Unit) {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange -> handler(exchange) }
            start()
        }
    }

    private fun HttpExchange.json(status: Int, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
