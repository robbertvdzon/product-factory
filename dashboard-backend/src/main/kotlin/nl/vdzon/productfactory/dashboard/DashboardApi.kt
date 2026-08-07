package nl.vdzon.productfactory.dashboard

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class DashboardApi(private val runtime: ProductFactoryRuntimeClient) {
    @GetMapping("/version") fun version() = mapOf("application" to "product-factory-dashboard")
    @GetMapping("/session") fun session(request: HttpServletRequest) =
        request.getAttribute("dashboardIdentity") ?: DashboardIdentity("local-development", "Local development")

    @GetMapping("/products") fun products(): Any = runtime.products()

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    fun createProduct(@RequestBody request: Map<String, Any?>): Any = runtime.createProduct(request)

    @PostMapping("/products/{slug}/pause") fun pause(@PathVariable slug: String): Any = runtime.changeStatus(slug, "pause")
    @PostMapping("/products/{slug}/resume") fun resume(@PathVariable slug: String): Any = runtime.changeStatus(slug, "resume")

    @GetMapping("/story-candidates")
    fun stories(): Any = runtime.products().flatMap { product -> runtime.stories(product["slug"].toString()) }

    @GetMapping("/workspace/publications")
    fun publications(): Any = runtime.products().flatMap { product -> runtime.publications(product["slug"].toString()) }

    @GetMapping("/workspace/publications/{runId}")
    fun publication(@PathVariable runId: String, @RequestParam productSlug: String): Any = runtime.publication(productSlug, runId)

    @GetMapping("/workspace/publications/{runId}/artifact", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun artifact(@PathVariable runId: String, @RequestParam productSlug: String): String = runtime.artifact(productSlug, runId)
}
