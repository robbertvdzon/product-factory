package nl.vdzon.productfactory.roadmap

import nl.vdzon.productfactory.contracts.RoadmapSessionView
import nl.vdzon.productfactory.product.api.ProductCatalog
import nl.vdzon.productfactory.roadmap.api.RoadmapSessionRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class RoadmapSessionStarted(val sessionId: String)

@Service
class RoadmapSessionService(
    private val repository: RoadmapSessionRepository,
    private val products: ProductCatalog,
    private val events: ApplicationEventPublisher,
) {
    @Transactional
    fun startSession(productSlug: String): RoadmapSessionView {
        val product = products.requireActive(productSlug)
        if (repository.hasActive(product.slug)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Er loopt al een roadmap-sessie voor dit product")
        }
        val session = repository.create(product.slug)
        events.publishEvent(RoadmapSessionStarted(session.id))
        return session
    }

    fun list(productSlug: String): List<RoadmapSessionView> = repository.list(products.requireContext(productSlug).slug)

    fun require(productSlug: String, id: String): RoadmapSessionView = repository.require(products.requireContext(productSlug).slug, id)
}

@RestController
@RequestMapping("/api/products/{slug}/roadmap/sessions")
class RoadmapSessionController(private val service: RoadmapSessionService) {
    @GetMapping
    fun list(@PathVariable slug: String): List<RoadmapSessionView> = service.list(slug)

    @GetMapping("/{id}")
    fun get(@PathVariable slug: String, @PathVariable id: String): RoadmapSessionView = service.require(slug, id)

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun start(@PathVariable slug: String): RoadmapSessionView = service.startSession(slug)
}
