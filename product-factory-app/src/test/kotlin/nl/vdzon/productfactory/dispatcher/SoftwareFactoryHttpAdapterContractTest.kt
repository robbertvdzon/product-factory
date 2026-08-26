package nl.vdzon.productfactory.dispatcher

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class SoftwareFactoryHttpAdapterContractTest {
    private lateinit var server: HttpServer
    private lateinit var adapter: RealSoftwareFactoryAdapter
    private val observedAuthorization = mutableListOf<String?>()
    private val observedKeys = mutableListOf<String?>()

    @BeforeEach
    fun startStub() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/api/integrations/v2", ::handle)
        server.start()
        adapter = RealSoftwareFactoryAdapter(
            jacksonObjectMapper(), "http://127.0.0.1:${server.address.port}/api/integrations/v2", "test-token", "local",
        )
    }

    @AfterEach fun stopStub() = server.stop(0)

    @Test
    fun `echte adapter gebruikt uitsluitend het v2 contract met bearer en idempotentiesleutel`() {
        assertThat(adapter.status()).isEqualTo(FactoryConnectionStatus(true, "stub", "2"))
        val request = FactoryStoryRequest(
            "hkh-autopilot", "550e8400-e29b-41d4-a716-446655440000", 3, "PRODUCT_STORY",
            "https://github.com/robbertvdzon/hkh-autopilot.git", "Volledige story", "## Gedrag\nWerk zelfstandig.",
        )
        val created = adapter.create("product-factory:hkh-autopilot:story:550e8400-e29b-41d4-a716-446655440000:v3", request)

        assertThat(created).isEqualTo(FactoryCreateResult("SF-3000", true, "OPEN"))
        assertThat(adapter.get("SF-3000")?.status).isEqualTo("OPEN")
        assertThat(adapter.find(idempotencyKey = "product-factory:hkh-autopilot:story:550e8400-e29b-41d4-a716-446655440000:v3")).hasSize(1)
        assertThat(observedAuthorization).allMatch { it == "Bearer test-token" }
        assertThat(observedKeys.filterNotNull()).containsExactly("product-factory:hkh-autopilot:story:550e8400-e29b-41d4-a716-446655440000:v3")
    }

    private fun handle(exchange: HttpExchange) {
        observedAuthorization += exchange.requestHeaders.getFirst("Authorization")
        observedKeys += exchange.requestHeaders.getFirst("Idempotency-Key")
        val path = exchange.requestURI.path
        val response = when {
            path.endsWith("/status") -> """{"connected":true,"factoryVersion":"stub","apiVersion":"2"}"""
            exchange.requestMethod == "POST" && path.endsWith("/stories") -> """{"storyKey":"SF-3000","created":true,"status":"OPEN"}"""
            path.endsWith("/stories/SF-3000") -> story()
            path.endsWith("/stories") -> """{"items":[${story()}]}"""
            else -> """{"code":"STORY_NOT_FOUND","message":"Niet gevonden","retryable":false}"""
        }
        val status = if (response.contains("STORY_NOT_FOUND")) 404 else if (exchange.requestMethod == "POST") 201 else 200
        val bytes = response.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun story() = """{"storyKey":"SF-3000","productId":"hkh-autopilot","sourceStoryId":"550e8400-e29b-41d4-a716-446655440000","sourceStoryVersion":3,"status":"OPEN","deliveredCommitSha":null,"cancelReason":null,"updatedAt":"2026-08-26T08:00:00Z"}"""
}
