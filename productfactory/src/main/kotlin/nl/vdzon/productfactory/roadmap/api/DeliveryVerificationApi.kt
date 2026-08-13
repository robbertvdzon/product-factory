package nl.vdzon.productfactory.roadmap.api

import nl.vdzon.productfactory.contracts.DeliveryVerificationView
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

internal data class PendingDeliveryVerification(
    val candidateId: Long,
    val themeId: String,
    val title: String,
    val description: String,
    val acceptanceCriteria: String?,
)

/**
 * Repository voor het opleverchecker-rapport (zie DeliveryVerificationEngine, module-intern). Leest
 * en schrijft rechtstreeks tegen `story_candidate`/`story_delivery` (eigendom van de `story`/`autonomy`-
 * modules) via JdbcTemplate in plaats van een Kotlin-afhankelijkheid daarop: zelfde bewuste uitweg als
 * RoadmapSessionEngine al gebruikt voor `shadow_iteration`, om een cyclus in de modulegrenzen te
 * vermijden.
 */
@Service
class DeliveryVerificationRepository(private val jdbc: JdbcTemplate) {
    internal fun pending(productSlug: String, limit: Int): List<PendingDeliveryVerification> = jdbc.query(
        """select c.id, c.theme_id, c.title, c.description, c.acceptance_criteria
            from story_candidate c
            join story_delivery d on d.candidate_id = c.id
            join roadmap_theme t on t.id = c.theme_id
            where c.product_slug = ? and c.theme_id is not null and d.confirmed_deployed = true and t.status <> 'DONE'
              and not exists (
                  select 1 from delivery_verification v
                  where v.candidate_id = c.id
                    and (v.verdict in ('SATISFIES', 'DOES_NOT_SATISFY') or v.attempt_count >= 2)
              )
            order by d.deployed_at asc
            limit ?""".trimIndent(),
        { row, _ ->
            PendingDeliveryVerification(
                row.getLong("id"), row.getString("theme_id"), row.getString("title"),
                row.getString("description"), row.getString("acceptance_criteria"),
            )
        },
        productSlug,
        limit,
    )

    @Transactional
    fun save(id: String, productSlug: String, themeId: String, candidateId: Long, verdict: String, report: String) {
        // Een eerdere INCONCLUSIVE kan door een tijdelijk onbereikbare browseromgeving zijn ontstaan.
        // Vervang die bij één volgende poging; conclusieve beoordelingen en twee mislukte pogingen
        // blijven via pending() uitgesloten, zodat oude kandidaten de verificatiewachtrij niet blokkeren.
        val updated = jdbc.update(
            """update delivery_verification
                set id = ?, status = 'COMPLETED', verdict = ?, report = ?, completed_at = current_timestamp,
                    attempt_count = attempt_count + 1
                where candidate_id = ? and verdict = 'INCONCLUSIVE'""".trimIndent(),
            id, verdict, report, candidateId,
        )
        if (updated == 0) {
            jdbc.update(
                """insert into delivery_verification(id, product_slug, theme_id, candidate_id, status, verdict, report, completed_at)
                    values (?, ?, ?, ?, 'COMPLETED', ?, ?, current_timestamp)""".trimIndent(),
                id, productSlug, themeId, candidateId, verdict, report,
            )
        }
    }

    fun recentReports(productSlug: String, limit: Int = 15): List<DeliveryVerificationView> = jdbc.query(
        SELECT + " where v.product_slug = ? order by v.created_at desc limit ?",
        ::mapView,
        productSlug,
        limit,
    )

    fun forTheme(productSlug: String, themeId: String): List<DeliveryVerificationView> = jdbc.query(
        SELECT + " where v.product_slug = ? and v.theme_id = ? order by v.created_at desc",
        ::mapView,
        productSlug,
        themeId,
    )

    private fun mapView(row: ResultSet, ignored: Int) = DeliveryVerificationView(
        id = row.getString("id"),
        productSlug = row.getString("product_slug"),
        themeId = row.getString("theme_id"),
        candidateId = row.getLong("candidate_id"),
        candidateTitle = row.getString("title"),
        status = row.getString("status"),
        verdict = row.getString("verdict"),
        report = row.getString("report"),
        createdAt = row.getTimestamp("created_at").toInstant(),
        completedAt = row.getTimestamp("completed_at")?.toInstant(),
    )

    companion object {
        private const val SELECT = """select v.id, v.product_slug, v.theme_id, v.candidate_id, c.title, v.status, v.verdict, v.report, v.created_at, v.completed_at
            from delivery_verification v join story_candidate c on c.id = v.candidate_id"""
    }
}
