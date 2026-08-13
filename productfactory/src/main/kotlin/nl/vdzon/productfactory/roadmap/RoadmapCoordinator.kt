package nl.vdzon.productfactory.roadmap

import nl.vdzon.productfactory.product.api.ProductCatalog
import nl.vdzon.productfactory.roadmap.api.RoadmapSessionRepository
import nl.vdzon.productfactory.contracts.WeeklyScheduleView
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Start automatisch een nieuwe roadmap-sessie per actief product op de geconfigureerde combinaties
 * van weekdag en tijd, in de tijdzone van het product. Een lege weekplanning betekent alleen
 * handmatig starten. De knop "Start roadmap-sessie nu" blijft altijd werken.
 */
@Component
class RoadmapCoordinator(
    private val products: ProductCatalog,
    private val sessions: RoadmapSessionService,
    private val repository: RoadmapSessionRepository,
    @Value("\${product-factory.roadmap.enabled:false}") private val enabled: Boolean,
) {
    @Scheduled(fixedDelayString = "\${product-factory.roadmap.poll-delay:PT1H}")
    fun tick() {
        if (!enabled) return
        products.list().filter { it.status == "active" }.forEach { product ->
            runCatching {
                if (!repository.hasActive(product.slug) && isRoadmapSessionDue(
                        product.roadmapSchedule,
                        ZonedDateTime.now(ZoneId.of(product.timezone)),
                        repository.lastCreatedAt(product.slug),
                    )
                ) {
                    sessions.startSession(product.slug)
                }
            }.onFailure { logger.warn("Roadmap-reconciliatie mislukt voor {}: {}", product.slug, it.message) }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RoadmapCoordinator::class.java)
    }
}

/**
 * Het meest recente verstreken weekslot is due zolang er na dat slot nog geen roadmap-sessie is
 * aangemaakt. Daardoor wordt ieder slot precies eenmaal geclaimd en blijven gemiste slots na een
 * korte storing alsnog uitvoerbaar.
 */
internal fun isRoadmapSessionDue(
    schedule: List<WeeklyScheduleView>,
    now: ZonedDateTime,
    lastSessionCreatedAt: Instant?,
): Boolean {
    val mostRecentSlot = schedule.mapNotNull { entry ->
        val day = runCatching { DayOfWeek.valueOf(entry.dayOfWeek) }.getOrNull() ?: return@mapNotNull null
        val time = runCatching { LocalTime.parse(entry.time) }.getOrNull() ?: return@mapNotNull null
        var occurrence = now.toLocalDate()
            .with(TemporalAdjusters.previousOrSame(day))
            .atTime(time)
            .atZone(now.zone)
        if (occurrence.isAfter(now)) occurrence = occurrence.minusWeeks(1)
        occurrence.toInstant()
    }.maxOrNull() ?: return false
    return lastSessionCreatedAt == null || lastSessionCreatedAt.isBefore(mostRecentSlot)
}
