package nl.vdzon.productfactory.roadmap

import nl.vdzon.productfactory.product.api.ProductCatalog
import nl.vdzon.productfactory.roadmap.api.RoadmapSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Start automatisch een nieuwe roadmap-sessie per actief product zodra het laatst afgeronde sessie
 * (of het product zelf) meer dan [sessionInterval] geleden is — een roadmap hoeft veel minder vaak
 * bijgewerkt te worden dan een dagelijkse productcyclus. Staat standaard uit
 * (`product-factory.roadmap.enabled`, net als de bestaande autonomiepoll): de handmatige
 * "Start roadmap-sessie nu"-knop werkt altijd, ongeacht deze vlag.
 */
@Component
class RoadmapCoordinator(
    private val products: ProductCatalog,
    private val sessions: RoadmapSessionService,
    private val repository: RoadmapSessionRepository,
    @Value("\${product-factory.roadmap.enabled:false}") private val enabled: Boolean,
    @Value("\${product-factory.roadmap.session-interval:P7D}") private val sessionInterval: Duration,
) {
    @Scheduled(fixedDelayString = "\${product-factory.roadmap.poll-delay:PT1H}")
    fun tick() {
        if (!enabled) return
        products.list().filter { it.status == "active" }.forEach { product ->
            runCatching { reconcile(product.slug) }.onFailure { logger.warn("Roadmap-reconciliatie mislukt voor {}: {}", product.slug, it.message) }
        }
    }

    private fun reconcile(productSlug: String) {
        if (repository.hasActive(productSlug)) return
        val lastCompleted = repository.lastCompletedAt(productSlug)
        val due = lastCompleted == null || lastCompleted.isBefore(Instant.now().minus(sessionInterval))
        if (due) sessions.startSession(productSlug)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RoadmapCoordinator::class.java)
    }
}
