package nl.vdzon.productfactory.dashboard

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestClient

@RestController
@RequestMapping("/api")
class DashboardApi(@Value("\${product-factory.runtime-base-url}") runtimeBaseUrl: String) {
    private val runtime = RestClient.builder().baseUrl(runtimeBaseUrl).build()

    @GetMapping("/version") fun version() = mapOf("application" to "product-factory-dashboard")
    @GetMapping("/session") fun session(request: HttpServletRequest) = request.getAttribute("dashboardIdentity") ?: DashboardIdentity("local-development", "Local development")
    @GetMapping("/products") fun products(): Any = runtime.get().uri("/api/products").retrieve().body(Any::class.java)!!
    @GetMapping("/story-candidates") fun stories(): Any = runtime.get().uri("/api/story-candidates").retrieve().body(Any::class.java)!!
    @GetMapping("/workspace/publications") fun publications(): Any = runtime.get().uri("/api/workspace/publications").retrieve().body(Any::class.java)!!
    @GetMapping("/workspace/publications/{runId}") fun publication(@PathVariable runId: String): Any = runtime.get().uri("/api/workspace/publications/{runId}", runId).retrieve().body(Any::class.java)!!
    @GetMapping("/workspace/publications/{runId}/artifact", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun artifact(@PathVariable runId: String): String = runtime.get().uri("/api/workspace/publications/{runId}/artifact", runId).retrieve().body(String::class.java)!!
}
