package nl.vdzon.productfactory.dashboard

import nl.vdzon.productfactory.contracts.AgentTask
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
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

    fun stories(slug: String): List<Map<String, Any?>> = runtime.get()
        .uri { it.path("/api/story-candidates").queryParam("productSlug", slug).build() }
        .retrieve().body(listType).orEmpty()

    fun publications(slug: String): List<Map<String, Any?>> = runtime.get()
        .uri { it.path("/api/workspace/publications").queryParam("productSlug", slug).build() }
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
