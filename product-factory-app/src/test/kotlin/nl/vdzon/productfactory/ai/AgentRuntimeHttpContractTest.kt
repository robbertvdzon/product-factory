package nl.vdzon.productfactory.ai

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Instant

class AgentRuntimeHttpContractTest {
    private lateinit var server: HttpServer
    private lateinit var client: HttpAgentRuntimeClient
    private val requests = mutableListOf<Pair<String, String>>()

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/", ::handle)
        server.start()
        client = HttpAgentRuntimeClient(
            jacksonObjectMapper().findAndRegisterModules(),
            "http://127.0.0.1:${server.address.port}",
            "scoped-product-factory-token",
        )
    }

    @AfterEach
    fun stop() = server.stop(0)

    @Test
    fun `adapter volgt het complete actuele Runtime contract`() {
        val created = client.createJob(RuntimeCreateJobRequest(
            idempotencyKey = "pf-contract-1", provider = "MOCKED", model = "scenario",
            prompt = "Volledige opdracht", responseSchema = jacksonObjectMapper().readTree("""{"type":"object"}"""),
            repositorySnapshot = null, environmentKeys = listOf("HKH__URL"), attachments = emptyList(), executionTimeoutSeconds = 60,
        ))
        assertThat(created.id).isEqualTo("11111111-1111-1111-1111-111111111111")
        assertThat(client.getJob(created.id).status).isEqualTo("SUCCEEDED")
        assertThat(client.getResult(created.id).result.path("answer").asText()).isEqualTo("gereed")
        assertThat(client.cancelJob(created.id).status).isEqualTo("CANCELLED")
        val key = client.listEnvironmentKeys("HKH").single()
        assertThat(key.name).isEqualTo("HKH__URL")
        assertThat(key.available).isTrue()
        assertThat(client.downloadArtifact(created.id, "22222222-2222-2222-2222-222222222222")).isEqualTo("bewijs".toByteArray())
        assertThat(requests).allSatisfy { (_, auth) -> assertThat(auth).isEqualTo("Bearer scoped-product-factory-token") }
        val submission = requests.single { it.first.startsWith("POST /v1/jobs ") }.first
        assertThat(submission).contains("\"jobKind\":\"APPLICATION_WORK\"").contains("\"environmentKeys\":[\"HKH__URL\"]")
    }

    private fun handle(exchange: HttpExchange) {
        val body = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
        requests += "${exchange.requestMethod} ${exchange.requestURI} $body" to exchange.requestHeaders.getFirst("Authorization").orEmpty()
        val path = exchange.requestURI.path
        when {
            path.endsWith("/artifacts/22222222-2222-2222-2222-222222222222") -> respond(exchange, 200, "bewijs", "application/octet-stream")
            path == "/v1/environment-keys" -> respond(exchange, 200, """[{"name":"HKH__URL","projectPrefix":"HKH","available":true,"matchingOnlineWorkers":1,"lastSeenAt":"2026-08-26T12:00:00Z"}]""")
            path.endsWith("/result") -> respond(exchange, 200, """{"jobId":"11111111-1111-1111-1111-111111111111","result":{"answer":"gereed"},"artifacts":[],"completedAt":"2026-08-26T12:00:00Z"}""")
            path.endsWith("/cancel") -> respond(exchange, 200, job("CANCELLED"))
            path == "/v1/jobs" -> respond(exchange, 202, job("QUEUED"))
            path == "/v1/jobs/11111111-1111-1111-1111-111111111111" -> respond(exchange, 200, job("SUCCEEDED"))
            else -> respond(exchange, 404, """{"code":"NOT_FOUND","message":"Not found"}""")
        }
    }

    private fun job(status: String) = """{"id":"11111111-1111-1111-1111-111111111111","status":"$status","phase":"$status","attemptCount":1,"progressPercent":100,"progressMessage":"gereed","errorCode":null,"errorMessage":null,"createdAt":"${Instant.parse("2026-08-26T12:00:00Z")}","updatedAt":"${Instant.parse("2026-08-26T12:00:00Z")}"}"""

    private fun respond(exchange: HttpExchange, status: Int, body: String, contentType: String = "application/json") {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
