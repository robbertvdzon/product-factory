package nl.vdzon.productfactory.roadmap

import nl.vdzon.productfactory.contracts.DeliveryVerificationView
import nl.vdzon.productfactory.contracts.RoadmapEpicView
import nl.vdzon.productfactory.contracts.RoadmapSettledQuestionView
import nl.vdzon.productfactory.roadmap.api.DeliveryVerificationRepository
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

data class CreateEpicRequest(val title: String, val description: String)
data class UpdateEpicRequest(
    val title: String? = null,
    val description: String? = null,
    val customerRank: Int? = null,
    val dependencyIds: Set<String>? = null,
    val status: String? = null,
)
data class AddSettledQuestionRequest(val content: String)

@RestController
@RequestMapping("/api/products")
class RoadmapController(
    private val catalog: RoadmapCatalog,
    private val deliveryVerifications: DeliveryVerificationRepository,
) {
    @GetMapping("/{slug}/roadmap/epics", "/{slug}/roadmap/themes")
    fun epics(@PathVariable slug: String): List<RoadmapEpicView> = catalog.listEpics(slug)

    @GetMapping("/{slug}/roadmap/epics/{id}", "/{slug}/roadmap/themes/{id}")
    fun epic(@PathVariable slug: String, @PathVariable id: String): RoadmapEpicView = catalog.requireEpic(slug, id)

    @GetMapping("/{slug}/roadmap/epics/{id}/verifications", "/{slug}/roadmap/themes/{id}/verifications")
    fun verifications(@PathVariable slug: String, @PathVariable id: String): List<DeliveryVerificationView> {
        catalog.requireEpic(slug, id)
        return deliveryVerifications.forTheme(slug, id)
    }

    @PostMapping("/{slug}/roadmap/epics", "/{slug}/roadmap/themes")
    @ResponseStatus(HttpStatus.CREATED)
    fun createEpic(@PathVariable slug: String, @RequestBody request: CreateEpicRequest): RoadmapEpicView =
        catalog.createEpic(slug, request.title, request.description)

    @PutMapping("/{slug}/roadmap/epics/{id}", "/{slug}/roadmap/themes/{id}")
    fun updateEpic(@PathVariable slug: String, @PathVariable id: String, @RequestBody request: UpdateEpicRequest): RoadmapEpicView =
        catalog.updateEpicFromCustomer(
            productSlug = slug,
            id = id,
            title = request.title,
            description = request.description,
            customerRank = request.customerRank,
            dependencyIds = request.dependencyIds,
            status = request.status,
        )

    @PostMapping("/{slug}/roadmap/epics/{id}/close", "/{slug}/roadmap/themes/{id}/close")
    fun closeEpic(@PathVariable slug: String, @PathVariable id: String): RoadmapEpicView = catalog.closeEpic(slug, id)

    @GetMapping("/{slug}/roadmap/settled-questions")
    fun settledQuestions(@PathVariable slug: String): List<RoadmapSettledQuestionView> = catalog.listSettledQuestions(slug)

    @PostMapping("/{slug}/roadmap/settled-questions")
    @ResponseStatus(HttpStatus.CREATED)
    fun addSettledQuestion(@PathVariable slug: String, @RequestBody request: AddSettledQuestionRequest): RoadmapSettledQuestionView =
        catalog.addSettledQuestion(slug, request.content)
}
