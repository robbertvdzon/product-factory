package nl.vdzon.productfactory.ai

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.io.ByteArrayOutputStream
import java.time.Instant

class AgentRuntimeHttpContractTest {
    private lateinit var server: HttpServer
    private lateinit var client: HttpAgentRuntimeClient
    private val requests = mutableListOf<Pair<String, String>>()
    private val uploadBytes = mutableListOf<Byte>()

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

    @Test
    fun `v2 adapter uploadt ruwe bytes en volgt opaque URLs`() {
        val prompt = "# Volledige opdracht".toByteArray()
        val upload = client.createUpload(RuntimeCreateUploadRequest("prompt.md", "text/markdown", prompt.size.toLong(), "a".repeat(64)))
        assertThat(client.getUploadOffset(upload.uploadUrl)).isZero()
        assertThat(client.patchUpload(upload.uploadUrl, 0, prompt)).isEqualTo(prompt.size.toLong())
        assertThat(client.completeUpload(upload.uploadId).state).isEqualTo("READY")
        val request = RuntimeV2CreateJobRequest(
            idempotencyKey = "pf-v2-contract-1",
            execution = RuntimeV2Execution("mock", "mock", "MOCK"),
            input = RuntimeV2JobInput("Lees inputobject prompt.", listOf(RuntimeV2InputObjectRef(upload.objectId, "prompt", "PROMPT"))),
            output = RuntimeV2Output(jacksonObjectMapper().readTree("""{"type":"object"}"""), emptyList()),
            repositorySnapshot = null, environmentKeys = emptyList(), executionTimeoutSeconds = 60,
        )
        val job = client.createV2Job(request)
        assertThat(client.getV2Job(job.id).status).isEqualTo("SUCCEEDED")
        assertThat(client.getV2Result(job.id).usageSummary.usageQuality).isEqualTo("MOCK")
        val artifactCopy = ByteArrayOutputStream()
        assertThat(client.copyV2Artifact("/v2/jobs/${job.id}/objects/artifact-1/content", 2, 6, artifactCopy).complete).isTrue()
        assertThat(artifactCopy.toByteArray()).isEqualTo("wijs".toByteArray())
        assertThat(client.getV2Attempts(job.id)).hasSize(1)
        assertThat(client.getV2Events(job.id, 0).items).hasSize(1)
        assertThat(client.listV2ExecutionOptions("STRUCTURED_GENERATION").single().execution.vendorId).isEqualTo("mock")
        assertThat(client.listV2EnvironmentKeys("HKH").single().name).isEqualTo("HKH__URL")
        assertThat(uploadBytes.toByteArray()).isEqualTo(prompt)
        val submission = requests.single { it.first.startsWith("POST /v2/jobs ") }.first
        assertThat(submission).contains("\"objectId\"").doesNotContain("contentBase64").doesNotContain("# Volledige opdracht")
    }

    private fun handle(exchange: HttpExchange) {
        val body = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
        requests += "${exchange.requestMethod} ${exchange.requestURI} $body" to exchange.requestHeaders.getFirst("Authorization").orEmpty()
        val path = exchange.requestURI.path
        when {
            exchange.requestMethod == "HEAD" && path.startsWith("/v2/uploads/") -> noContent(exchange, 200, "Upload-Offset" to uploadBytes.size.toString())
            exchange.requestMethod == "PATCH" && path.startsWith("/v2/uploads/") -> {
                uploadBytes += body.toByteArray().toList()
                noContent(exchange, 204, "Upload-Offset" to uploadBytes.size.toString())
            }
            path == "/v2/uploads" -> respond(exchange, 201, """{"uploadId":"33333333-3333-3333-3333-333333333333","objectId":"44444444-4444-4444-4444-444444444444","state":"CREATED","protocol":"RESUMABLE_PATCH","chunkSizeBytes":1024,"uploadUrl":"/v2/uploads/33333333-3333-3333-3333-333333333333/content","offset":0,"sizeBytes":20,"expiresAt":"2026-08-26T13:00:00Z"}""")
            path == "/v2/uploads/33333333-3333-3333-3333-333333333333/complete" -> respond(exchange, 200, """{"objectId":"44444444-4444-4444-4444-444444444444","filename":"prompt.md","mimeType":"text/markdown","sizeBytes":20,"sha256":"${"a".repeat(64)}","state":"READY","createdAt":"2026-08-26T12:00:00Z","readyAt":"2026-08-26T12:00:01Z"}""")
            path == "/v2/execution-options" -> respond(exchange, 200, """[{"execution":{"vendorId":"mock","model":"mock","mode":"MOCK"},"taskTypes":["STRUCTURED_GENERATION"],"available":true,"matchingOnlineWorkers":1,"lastSeenAt":"2026-08-26T12:00:00Z"}]""")
            path == "/v2/environment-keys" -> respond(exchange, 200, """[{"name":"HKH__URL","projectPrefix":"HKH","available":true,"matchingOnlineWorkers":1,"lastSeenAt":"2026-08-26T12:00:00Z"}]""")
            path.endsWith("/attempts") -> respond(exchange, 200, """[{"id":"55555555-5555-5555-5555-555555555555","jobId":"11111111-1111-1111-1111-111111111111","number":1,"status":"SUCCEEDED","usageQuality":"MOCK","usageSummary":{"attemptCount":1,"usageQuality":"MOCK","metrics":[],"costs":[]},"errorCode":null,"startedAt":"2026-08-26T12:00:00Z","completedAt":"2026-08-26T12:00:01Z"}]""")
            path.endsWith("/events") -> respond(exchange, 200, """{"items":[{"sequence":1,"jobId":"11111111-1111-1111-1111-111111111111","type":"JOB_FINISHED","phase":"COMPLETED","message":"gereed","logKind":null,"logText":null,"progressPercent":100,"createdAt":"2026-08-26T12:00:01Z"}],"nextSequence":1,"active":false}""")
            path.startsWith("/v2/jobs/") && path.endsWith("/result") -> respond(exchange, 200, """{"jobId":"11111111-1111-1111-1111-111111111111","result":{"answer":"gereed"},"artifacts":[],"usageSummary":{"attemptCount":1,"usageQuality":"MOCK","metrics":[],"costs":[]},"completedAt":"2026-08-26T12:00:01Z"}""")
            path.startsWith("/v2/jobs/") && path.endsWith("/objects/artifact-1/content") -> respond(exchange, 206, "bewijs".substring(2), "application/octet-stream")
            path.startsWith("/v2/jobs/") && path.endsWith("/cancel") -> respond(exchange, 200, job("CANCELLED"))
            path == "/v2/jobs" -> respond(exchange, 202, job("QUEUED"))
            path == "/v2/jobs/11111111-1111-1111-1111-111111111111" -> respond(exchange, 200, job("SUCCEEDED"))
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

    private fun noContent(exchange: HttpExchange, status: Int, header: Pair<String, String>) {
        exchange.responseHeaders.add(header.first, header.second)
        exchange.sendResponseHeaders(status, -1)
        exchange.close()
    }
}
