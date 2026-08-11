package nl.vdzon.productfactory.roadmap

import nl.vdzon.productfactory.contracts.RoadmapSettledQuestionView
import nl.vdzon.productfactory.contracts.RoadmapThemeView
import nl.vdzon.productfactory.roadmap.api.RoadmapCatalog
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class CreateThemeRequest(val title: String, val description: String, val priority: String)
data class UpdateThemeRequest(val title: String? = null, val description: String? = null, val priority: String? = null, val status: String? = null)
data class AddSettledQuestionRequest(val content: String)

@RestController
@RequestMapping("/api/products")
class RoadmapController(private val catalog: RoadmapCatalog) {
    @GetMapping("/{slug}/roadmap/themes")
    fun themes(@PathVariable slug: String): List<RoadmapThemeView> = catalog.listThemes(slug)

    @GetMapping("/{slug}/roadmap/themes/{id}")
    fun theme(@PathVariable slug: String, @PathVariable id: String): RoadmapThemeView = catalog.requireTheme(slug, id)

    @PostMapping("/{slug}/roadmap/themes")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTheme(@PathVariable slug: String, @RequestBody request: CreateThemeRequest): RoadmapThemeView =
        catalog.createTheme(slug, request.title, request.description, request.priority)

    @PutMapping("/{slug}/roadmap/themes/{id}")
    fun updateTheme(@PathVariable slug: String, @PathVariable id: String, @RequestBody request: UpdateThemeRequest): RoadmapThemeView =
        catalog.updateTheme(slug, id, request.title, request.description, request.priority, request.status)

    @PostMapping("/{slug}/roadmap/themes/{id}/close")
    fun closeTheme(@PathVariable slug: String, @PathVariable id: String): RoadmapThemeView = catalog.closeTheme(slug, id)

    @GetMapping("/{slug}/roadmap/settled-questions")
    fun settledQuestions(@PathVariable slug: String): List<RoadmapSettledQuestionView> = catalog.listSettledQuestions(slug)

    @PostMapping("/{slug}/roadmap/settled-questions")
    @ResponseStatus(HttpStatus.CREATED)
    fun addSettledQuestion(@PathVariable slug: String, @RequestBody request: AddSettledQuestionRequest): RoadmapSettledQuestionView =
        catalog.addSettledQuestion(slug, request.content)
}
