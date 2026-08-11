package nl.vdzon.productfactory.roadmap.api

import nl.vdzon.productfactory.contracts.RoadmapSessionView
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.sql.ResultSet
import java.time.Instant

/**
 * Repository voor één sessie van de Product Manager-rol (zie RoadmapSessionEngine, module-intern).
 * Bewust een eigen klasse naast [RoadmapCatalog]: een sessie is een agentaanroep met een levenscyclus
 * (QUEUED/RUNNING/COMPLETED/FAILED), geen CRUD-aggregaat zoals een thema.
 */
@Service
class RoadmapSessionRepository(private val jdbc: JdbcTemplate, private val products: ProductCatalog) {
    fun hasActive(productSlug: String): Boolean = (jdbc.queryForObject(
        "select count(*) from roadmap_session where product_slug = ? and status in ('QUEUED', 'RUNNING')",
        Long::class.java,
        productSlug,
    ) ?: 0) > 0

    /** Laatste keer dat een sessie voor dit product succesvol is afgerond, of `null` als dat nog nooit is gebeurd. */
    fun lastCompletedAt(productSlug: String): Instant? = jdbc.queryForObject(
        "select max(completed_at) from roadmap_session where product_slug = ? and status = 'COMPLETED'",
        java.sql.Timestamp::class.java,
        productSlug,
    )?.toInstant()

    fun create(productSlug: String): RoadmapSessionView {
        val product = products.requireContext(productSlug)
        val sequence = jdbc.queryForObject("select coalesce(max(sequence_number), 0) + 1 from roadmap_session where product_slug = ?", Int::class.java, product.slug) ?: 1
        val id = "roadmap-session-${product.slug}-${sequence.toString().padStart(4, '0')}"
        jdbc.update(
            "insert into roadmap_session(id, product_slug, sequence_number, status) values (?, ?, ?, 'QUEUED')",
            id,
            product.slug,
            sequence,
        )
        return require(product.slug, id)
    }

    fun list(productSlug: String): List<RoadmapSessionView> {
        val product = products.requireContext(productSlug)
        return jdbc.query(SELECT + " where product_slug = ? order by sequence_number desc", ::mapSession, product.slug)
    }

    fun require(productSlug: String, id: String): RoadmapSessionView {
        val product = products.requireContext(productSlug)
        return jdbc.query(SELECT + " where product_slug = ? and id = ?", ::mapSession, product.slug, id).singleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Onbekende roadmap-sessie voor dit product")
    }

    fun requireById(id: String): RoadmapSessionView = jdbc.query(SELECT + " where id = ?", ::mapSession, id).singleOrNull()
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Onbekende roadmap-sessie")

    fun markRunning(id: String) {
        jdbc.update("update roadmap_session set status = 'RUNNING', started_at = current_timestamp where id = ? and status = 'QUEUED'", id)
    }

    fun markCompleted(id: String, summary: String, workspaceRunId: String?, workspacePullRequestUrl: String?, workspaceCommitSha: String?) {
        val updated = jdbc.update(
            """update roadmap_session set status = 'COMPLETED', summary = ?, completed_at = current_timestamp,
                workspace_run_id = ?, workspace_pull_request_url = ?, workspace_commit_sha = ?
                where id = ? and status not in ('COMPLETED', 'FAILED')""".trimIndent(),
            summary,
            workspaceRunId,
            workspacePullRequestUrl,
            workspaceCommitSha,
            id,
        )
        if (updated == 0) log.warn("Genegeerde schrijfpoging: roadmap-sessie {} staat al in een terminale staat", id)
    }

    fun markFailed(id: String, error: String) {
        val updated = jdbc.update(
            "update roadmap_session set status = 'FAILED', error_message = ?, completed_at = current_timestamp where id = ? and status not in ('COMPLETED', 'FAILED')",
            error.take(4_000),
            id,
        )
        if (updated == 0) log.warn("Genegeerde schrijfpoging: roadmap-sessie {} staat al in een terminale staat", id)
    }

    private fun mapSession(row: ResultSet, ignored: Int) = RoadmapSessionView(
        id = row.getString("id"),
        productSlug = row.getString("product_slug"),
        sequenceNumber = row.getInt("sequence_number"),
        status = row.getString("status"),
        summary = row.getString("summary"),
        errorMessage = row.getString("error_message"),
        createdAt = row.getTimestamp("created_at").toInstant(),
        startedAt = row.getTimestamp("started_at")?.toInstant(),
        completedAt = row.getTimestamp("completed_at")?.toInstant(),
        workspaceRunId = row.getString("workspace_run_id"),
        workspacePullRequestUrl = row.getString("workspace_pull_request_url"),
        workspaceCommitSha = row.getString("workspace_commit_sha"),
    )

    companion object {
        private val log = LoggerFactory.getLogger(RoadmapSessionRepository::class.java)
        private const val SELECT = "select * from roadmap_session"
    }
}
