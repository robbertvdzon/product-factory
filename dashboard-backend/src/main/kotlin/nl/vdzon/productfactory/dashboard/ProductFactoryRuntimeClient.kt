package nl.vdzon.productfactory.dashboard

import nl.vdzon.productfactory.contracts.AgentTask
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException

@Component
class ProductFactoryRuntimeClient(@Value("\${product-factory.runtime-base-url}") runtimeBaseUrl: String) {
    private val runtime = RestClient.builder().baseUrl(runtimeBaseUrl)
        .defaultStatusHandler(HttpStatusCode::isError) { _, response ->
            throw ResponseStatusException(HttpStatusCode.valueOf(response.statusCode.value()), "Product Factory-runtime weigerde het verzoek")
        }.build()
    private val listType = object : ParameterizedTypeReference<List<Map<String, Any?>>>() {}

    fun products(): List<Map<String, Any?>> = runtime.get().uri("/api/products").retrieve().body(listType).orEmpty()

    fun product(slug: String): Map<String, Any?> = runtime.get().uri("/api/products/{slug}", slug).retrieve()
        .body(object : ParameterizedTypeReference<Map<String, Any?>>() {}) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    fun memory(slug: String, asOf: String?): List<Map<String, Any?>> = runtime.get()
        .uri { builder ->
            builder.path("/api/products/{slug}/memory").also {
                if (!asOf.isNullOrBlank()) it.queryParam("asOf", asOf)
            }.build(slug)
        }
        .retrieve().body(listType).orEmpty()

    fun memoryHistory(slug: String): List<Map<String, Any?>> = runtime.get()
        .uri("/api/products/{slug}/memory/history", slug)
        .retrieve().body(listType).orEmpty()

    fun stories(slug: String): List<Map<String, Any?>> = runtime.get()
        .uri { it.path("/api/story-candidates").queryParam("productSlug", slug).build() }
        .retrieve().body(listType).orEmpty()

    fun publications(slug: String): List<Map<String, Any?>> = runtime.get()
        .uri { it.path("/api/workspace/publications").queryParam("productSlug", slug).build() }
        .retrieve().body(listType).orEmpty()

    fun shadowIterations(slug: String): List<Map<String, Any?>> = runtime.get()
        .uri { it.path("/api/shadow-iterations").queryParam("productSlug", slug).build() }
        .retrieve().body(listType).orEmpty()

    fun shadowIteration(slug: String, id: String): Map<String, Any?> = runtime.get()
        .uri { it.path("/api/shadow-iterations/{id}").queryParam("productSlug", slug).build(id) }
        .retrieve().body(object : ParameterizedTypeReference<Map<String, Any?>>() {})
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    fun shadowIterationSteps(slug: String, id: String): List<Map<String, Any?>> = runtime.get()
        .uri { it.path("/api/shadow-iterations/{id}/steps").queryParam("productSlug", slug).build(id) }
        .retrieve().body(listType).orEmpty()

    fun shadowIterationArtifacts(slug: String, id: String): List<Map<String, Any?>> = runtime.get()
        .uri { it.path("/api/shadow-iterations/{id}/artifacts").queryParam("productSlug", slug).build(id) }
        .retrieve().body(listType).orEmpty()

    fun cancelShadowIteration(slug: String, id: String, reason: String?): Any = runtime.post()
        .uri { it.path("/api/shadow-iterations/{id}/cancel").queryParam("productSlug", slug).build(id) }
        .body(mapOf("reason" to reason))
        .retrieve().body(Any::class.java)!!

    fun resumeShadowIteration(slug: String, id: String): Any = runtime.post()
        .uri { it.path("/api/shadow-iterations/{id}/resume").queryParam("productSlug", slug).build(id) }
        .retrieve().body(Any::class.java)!!

    fun deliveries(slug: String): List<Map<String, Any?>> = runtime.get()
        .uri { it.path("/api/autonomy/deliveries").queryParam("productSlug", slug).build() }
        .retrieve().body(listType).orEmpty()

    fun humanActions(slug: String): List<Map<String, Any?>> = runtime.get()
        .uri { it.path("/api/autonomy/human-actions").queryParam("productSlug", slug).build() }
        .retrieve().body(listType).orEmpty()

    fun publication(slug: String, runId: String): Any = runtime.get()
        .uri { it.path("/api/workspace/publications/{runId}").queryParam("productSlug", slug).build(runId) }
        .retrieve().body(Any::class.java)!!

    fun artifact(slug: String, runId: String): String = runtime.get()
        .uri { it.path("/api/workspace/publications/{runId}/artifact").queryParam("productSlug", slug).build(runId) }
        .retrieve().body(String::class.java)!!

    fun createProduct(body: Map<String, Any?>): Any = runtime.post().uri("/api/products").body(body).retrieve().body(Any::class.java)!!

    fun changeStatus(slug: String, action: String): Any = runtime.post().uri("/api/products/{slug}/{action}", slug, action)
        .retrieve().body(Any::class.java)!!

    fun updateProductSettings(slug: String, body: Map<String, Any?>): Any = runtime.put()
        .uri("/api/products/{slug}/settings", slug).body(body).retrieve().body(Any::class.java)!!

    fun aiCatalog(): Map<String, List<String>> = runtime.get().uri("/api/ai-catalog").retrieve()
        .body(object : ParameterizedTypeReference<Map<String, List<String>>>() {}).orEmpty()

    fun startCycle(slug: String, focus: String?): Any = runtime.post()
        .uri("/api/products/{slug}/cycles", slug)
        .body(mapOf("focus" to focus))
        .retrieve().body(Any::class.java)!!

    fun completeHumanAction(id: Long, result: String) = runtime.post()
        .uri("/api/autonomy/human-actions/{id}/complete", id)
        .body(mapOf("result" to result)).retrieve().toBodilessEntity()

    fun meetings(slug: String): List<Map<String, Any?>> = runtime.get()
        .uri("/api/products/{slug}/meetings", slug)
        .retrieve().body(listType).orEmpty()

    fun meeting(slug: String, id: String): Map<String, Any?> = runtime.get()
        .uri("/api/products/{slug}/meetings/{id}", slug, id)
        .retrieve().body(object : ParameterizedTypeReference<Map<String, Any?>>() {})
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    fun meetingMessages(slug: String, id: String): List<Map<String, Any?>> = runtime.get()
        .uri("/api/products/{slug}/meetings/{id}/messages", slug, id)
        .retrieve().body(listType).orEmpty()

    fun startMeeting(slug: String): Any = runtime.post()
        .uri("/api/products/{slug}/meetings", slug)
        .retrieve().body(Any::class.java)!!

    fun sendMeetingMessage(slug: String, id: String, content: String, imageAssetIds: List<String>): Any = runtime.post()
        .uri("/api/products/{slug}/meetings/{id}/messages", slug, id)
        .body(mapOf("content" to content, "imageAssetIds" to imageAssetIds))
        .retrieve().body(Any::class.java)!!

    fun uploadProductMedia(slug: String, filename: String, contentType: String, bytes: ByteArray, altText: String?): Any {
        val body = MultipartBodyBuilder().apply {
            part("file", object : ByteArrayResource(bytes) {
                override fun getFilename(): String = filename
            }).contentType(MediaType.parseMediaType(contentType))
            altText?.takeIf(String::isNotBlank)?.let { part("altText", it) }
        }.build()
        return runtime.post()
            .uri("/api/products/{slug}/media", slug)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve().body(Any::class.java)!!
    }

    fun productMediaContent(slug: String, id: String): ResponseEntity<ByteArray> = runtime.get()
        .uri("/api/products/{slug}/media/{id}/content", slug, id)
        .retrieve().toEntity(ByteArray::class.java)

    fun closeMeeting(slug: String, id: String): Any = runtime.post()
        .uri("/api/products/{slug}/meetings/{id}/close", slug, id)
        .retrieve().body(Any::class.java)!!

    fun roadmapEpics(slug: String): List<Map<String, Any?>> = runtime.get()
        .uri("/api/products/{slug}/roadmap/epics", slug)
        .retrieve().body(listType).orEmpty()

    fun roadmapVision(slug: String): Map<String, Any?>? = runtime.get()
        .uri("/api/products/{slug}/roadmap/vision", slug)
        .retrieve().body(object : ParameterizedTypeReference<Map<String, Any?>>() {})

    fun roadmapEpic(slug: String, id: String): Map<String, Any?> = runtime.get()
        .uri("/api/products/{slug}/roadmap/epics/{id}", slug, id)
        .retrieve().body(object : ParameterizedTypeReference<Map<String, Any?>>() {})
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    fun createRoadmapEpic(slug: String, body: Map<String, Any?>): Any = runtime.post()
        .uri("/api/products/{slug}/roadmap/epics", slug)
        .body(body)
        .retrieve().body(Any::class.java)!!

    fun updateRoadmapEpic(slug: String, id: String, body: Map<String, Any?>): Any = runtime.put()
        .uri("/api/products/{slug}/roadmap/epics/{id}", slug, id)
        .body(body)
        .retrieve().body(Any::class.java)!!

    fun closeRoadmapEpic(slug: String, id: String): Any = runtime.post()
        .uri("/api/products/{slug}/roadmap/epics/{id}/close", slug, id)
        .retrieve().body(Any::class.java)!!

    fun roadmapEpicVerifications(slug: String, id: String): List<Map<String, Any?>> = runtime.get()
        .uri("/api/products/{slug}/roadmap/epics/{id}/verifications", slug, id)
        .retrieve().body(listType).orEmpty()

    fun roadmapThemes(slug: String) = roadmapEpics(slug)
    fun roadmapTheme(slug: String, id: String) = roadmapEpic(slug, id)
    fun createRoadmapTheme(slug: String, body: Map<String, Any?>) = createRoadmapEpic(slug, body)
    fun updateRoadmapTheme(slug: String, id: String, body: Map<String, Any?>) = updateRoadmapEpic(slug, id, body)
    fun closeRoadmapTheme(slug: String, id: String) = closeRoadmapEpic(slug, id)
    fun roadmapThemeVerifications(slug: String, id: String) = roadmapEpicVerifications(slug, id)

    fun roadmapSettledQuestions(slug: String): List<Map<String, Any?>> = runtime.get()
        .uri("/api/products/{slug}/roadmap/settled-questions", slug)
        .retrieve().body(listType).orEmpty()

    fun addRoadmapSettledQuestion(slug: String, content: String): Any = runtime.post()
        .uri("/api/products/{slug}/roadmap/settled-questions", slug)
        .body(mapOf("content" to content))
        .retrieve().body(Any::class.java)!!

    fun roadmapSessions(slug: String): List<Map<String, Any?>> = runtime.get()
        .uri("/api/products/{slug}/roadmap/sessions", slug)
        .retrieve().body(listType).orEmpty()

    fun roadmapSession(slug: String, id: String): Map<String, Any?> = runtime.get()
        .uri("/api/products/{slug}/roadmap/sessions/{id}", slug, id)
        .retrieve().body(object : ParameterizedTypeReference<Map<String, Any?>>() {})
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    fun startRoadmapSession(slug: String): Any = runtime.post()
        .uri("/api/products/{slug}/roadmap/sessions", slug)
        .retrieve().body(Any::class.java)!!

    fun bugs(slug: String): List<Map<String, Any?>> = runtime.get()
        .uri("/api/products/{slug}/bugs", slug)
        .retrieve().body(listType).orEmpty()

    fun updateBug(slug: String, id: Long, body: Map<String, Any?>): Any = runtime.put()
        .uri("/api/products/{slug}/bugs/{id}", slug, id)
        .body(body)
        .retrieve().body(Any::class.java)!!

    fun testSessions(slug: String): List<Map<String, Any?>> = runtime.get()
        .uri("/api/products/{slug}/test-sessions", slug)
        .retrieve().body(listType).orEmpty()

    fun startTestSession(slug: String): Any = runtime.post()
        .uri("/api/products/{slug}/test-sessions", slug)
        .retrieve().body(Any::class.java)!!

    fun requireRunnableProduct(slug: String) {
        val product = product(slug)
        if (product["status"] != "active") throw ResponseStatusException(HttpStatus.CONFLICT, "Product is niet actief")
    }

    fun registerAgentRun(task: AgentTask) {
        runtime.post().uri("/api/agent-runs").body(
            mapOf("runId" to task.runId, "productSlug" to task.productSlug, "taskType" to task.taskType),
        ).retrieve().toBodilessEntity()
    }

    fun requireAgentRun(productSlug: String, runId: String) {
        runtime.get().uri {
            it.path("/api/agent-runs/{runId}").queryParam("productSlug", productSlug).build(runId)
        }.retrieve().toBodilessEntity()
    }
}
