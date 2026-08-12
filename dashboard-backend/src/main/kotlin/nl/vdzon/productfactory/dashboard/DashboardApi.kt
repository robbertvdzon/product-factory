package nl.vdzon.productfactory.dashboard

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
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

    @PutMapping("/products/{slug}/settings")
    fun updateSettings(@PathVariable slug: String, @RequestBody body: Map<String, Any?>): Any = runtime.updateProductSettings(slug, body)

    @GetMapping("/ai-catalog") fun aiCatalog(): Map<String, List<String>> = runtime.aiCatalog()

    @GetMapping("/story-candidates")
    fun stories(): Any = runtime.products().flatMap { product -> runtime.stories(product["slug"].toString()) }

    @GetMapping("/workspace/publications")
    fun publications(): Any = runtime.products().flatMap { product -> runtime.publications(product["slug"].toString()) }

    @GetMapping("/shadow-iterations")
    fun shadowIterations(): Any = runtime.products().flatMap { product -> runtime.shadowIterations(product["slug"].toString()) }

    @GetMapping("/shadow-iterations/{id}")
    fun shadowIteration(@PathVariable id: String, @RequestParam productSlug: String): Any =
        runtime.shadowIteration(productSlug, id)

    @GetMapping("/shadow-iterations/{id}/steps")
    fun shadowIterationSteps(@PathVariable id: String, @RequestParam productSlug: String): Any =
        runtime.shadowIterationSteps(productSlug, id)

    @GetMapping("/shadow-iterations/{id}/artifacts")
    fun shadowIterationArtifacts(@PathVariable id: String, @RequestParam productSlug: String): Any =
        runtime.shadowIterationArtifacts(productSlug, id)

    @PostMapping("/shadow-iterations/{id}/cancel")
    fun cancelShadowIteration(@PathVariable id: String, @RequestParam productSlug: String, @RequestBody(required = false) request: Map<String, String>?): Any =
        runtime.cancelShadowIteration(productSlug, id, request?.get("reason"))

    @PostMapping("/shadow-iterations/{id}/resume")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun resumeShadowIteration(@PathVariable id: String, @RequestParam productSlug: String): Any =
        runtime.resumeShadowIteration(productSlug, id)

    @GetMapping("/autonomy/deliveries")
    fun deliveries(): Any = runtime.products().flatMap { product -> runtime.deliveries(product["slug"].toString()) }

    @GetMapping("/autonomy/human-actions")
    fun humanActions(): Any = runtime.products().flatMap { product -> runtime.humanActions(product["slug"].toString()) }

    @GetMapping("/meetings")
    fun meetings(): Any = runtime.products().flatMap { product -> runtime.meetings(product["slug"].toString()) }

    @GetMapping("/roadmap/themes")
    fun roadmapThemes(): Any = runtime.products().flatMap { product -> runtime.roadmapThemes(product["slug"].toString()) }

    @GetMapping("/roadmap/settled-questions")
    fun roadmapSettledQuestions(): Any = runtime.products().flatMap { product -> runtime.roadmapSettledQuestions(product["slug"].toString()) }

    @GetMapping("/roadmap/sessions")
    fun roadmapSessions(): Any = runtime.products().flatMap { product -> runtime.roadmapSessions(product["slug"].toString()) }

    @PostMapping("/products/{slug}/cycles")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun startCycle(@PathVariable slug: String, @RequestBody(required = false) request: Map<String, String>?): Any =
        runtime.startCycle(slug, request?.get("focus"))

    @PostMapping("/autonomy/human-actions/{id}/complete")
    fun completeHumanAction(@PathVariable id: Long, @RequestBody request: Map<String, String>) =
        runtime.completeHumanAction(id, request["result"].orEmpty())

    @GetMapping("/workspace/publications/{runId}")
    fun publication(@PathVariable runId: String, @RequestParam productSlug: String): Any = runtime.publication(productSlug, runId)

    @GetMapping("/workspace/publications/{runId}/artifact", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun artifact(@PathVariable runId: String, @RequestParam productSlug: String): String = runtime.artifact(productSlug, runId)
}
