package nl.vdzon.productfactory.dashboard

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/products/{slug}/roadmap")
class RoadmapApi(private val runtime: ProductFactoryRuntimeClient) {
    @GetMapping("/vision")
    fun vision(@PathVariable slug: String): Any? = runtime.roadmapVision(slug)

    @GetMapping("/epics", "/themes")
    fun epics(@PathVariable slug: String): Any = runtime.roadmapEpics(slug)

    @GetMapping("/epics/{id}", "/themes/{id}")
    fun epic(@PathVariable slug: String, @PathVariable id: String): Any = runtime.roadmapEpic(slug, id)

    @PostMapping("/epics", "/themes")
    @ResponseStatus(HttpStatus.CREATED)
    fun createEpic(@PathVariable slug: String, @RequestBody body: Map<String, Any?>): Any = runtime.createRoadmapEpic(slug, body)

    @PutMapping("/epics/{id}", "/themes/{id}")
    fun updateEpic(@PathVariable slug: String, @PathVariable id: String, @RequestBody body: Map<String, Any?>): Any =
        runtime.updateRoadmapEpic(slug, id, body)

    @PostMapping("/epics/{id}/close", "/themes/{id}/close")
    fun closeEpic(@PathVariable slug: String, @PathVariable id: String): Any = runtime.closeRoadmapEpic(slug, id)

    @GetMapping("/epics/{id}/verifications", "/themes/{id}/verifications")
    fun epicVerifications(@PathVariable slug: String, @PathVariable id: String): Any = runtime.roadmapEpicVerifications(slug, id)

    @GetMapping("/settled-questions")
    fun settledQuestions(@PathVariable slug: String): Any = runtime.roadmapSettledQuestions(slug)

    @PostMapping("/settled-questions")
    @ResponseStatus(HttpStatus.CREATED)
    fun addSettledQuestion(@PathVariable slug: String, @RequestBody body: Map<String, String>): Any =
        runtime.addRoadmapSettledQuestion(slug, body["content"].orEmpty())

    @GetMapping("/sessions")
    fun sessions(@PathVariable slug: String): Any = runtime.roadmapSessions(slug)

    @GetMapping("/sessions/{id}")
    fun session(@PathVariable slug: String, @PathVariable id: String): Any = runtime.roadmapSession(slug, id)

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun startSession(@PathVariable slug: String): Any = runtime.startRoadmapSession(slug)

    @GetMapping("/living-vision/portfolio")
    fun livingVisionPortfolio(@PathVariable slug: String): Any = runtime.livingVisionPortfolio(slug)

    @GetMapping("/living-vision/ideas/{ideaKey}/history")
    fun livingVisionIdeaHistory(@PathVariable slug: String, @PathVariable ideaKey: String): Any =
        runtime.livingVisionIdeaHistory(slug, ideaKey)

    @GetMapping("/living-vision/concepts/{conceptKey}/history")
    fun livingVisionConceptHistory(@PathVariable slug: String, @PathVariable conceptKey: String): Any =
        runtime.livingVisionConceptHistory(slug, conceptKey)

    @GetMapping("/living-vision/sessions/{id}/steps")
    fun livingVisionSteps(@PathVariable slug: String, @PathVariable id: String): Any =
        runtime.livingVisionSessionSteps(slug, id)

    @GetMapping("/living-vision/media/{mediaId}")
    fun livingVisionMedia(@PathVariable slug: String, @PathVariable mediaId: String): ResponseEntity<ByteArray> =
        runtime.productMediaContent(slug, mediaId)

    @PostMapping("/living-vision/migrate-legacy")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun migrateLegacy(@PathVariable slug: String): Any = runtime.migrateLegacyVision(slug)
}
