package nl.vdzon.productfactory.dashboard

import org.springframework.http.HttpStatus
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
    @GetMapping("/themes")
    fun themes(@PathVariable slug: String): Any = runtime.roadmapThemes(slug)

    @GetMapping("/themes/{id}")
    fun theme(@PathVariable slug: String, @PathVariable id: String): Any = runtime.roadmapTheme(slug, id)

    @PostMapping("/themes")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTheme(@PathVariable slug: String, @RequestBody body: Map<String, Any?>): Any = runtime.createRoadmapTheme(slug, body)

    @PutMapping("/themes/{id}")
    fun updateTheme(@PathVariable slug: String, @PathVariable id: String, @RequestBody body: Map<String, Any?>): Any =
        runtime.updateRoadmapTheme(slug, id, body)

    @PostMapping("/themes/{id}/close")
    fun closeTheme(@PathVariable slug: String, @PathVariable id: String): Any = runtime.closeRoadmapTheme(slug, id)

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
}
