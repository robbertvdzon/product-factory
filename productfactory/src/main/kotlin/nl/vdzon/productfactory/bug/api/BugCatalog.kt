package nl.vdzon.productfactory.bug.api

import nl.vdzon.productfactory.contracts.BugView
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.sql.ResultSet

data class BugMutation(
    val action: String,
    val bugId: Long?,
    val title: String,
    val description: String,
    val reproductionSteps: String,
    val expectedResult: String,
    val actualResult: String,
    val priority: String,
)

data class BugMutationResult(val action: String, val bug: BugView)

@Service
class BugCatalog(private val jdbc: JdbcTemplate, private val products: ProductCatalog) {
    fun list(productSlug: String): List<BugView> {
        val slug = products.requireContext(productSlug).slug
        return jdbc.query(
            SELECT + " where product_slug = ? order by case priority when 'P0' then 0 when 'P1' then 1 when 'P2' then 2 else 3 end, updated_at desc",
            ::mapBug,
            slug,
        )
    }

    fun require(productSlug: String, id: Long): BugView = list(productSlug).singleOrNull { it.id == id }
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Onbekende bug voor dit product")

    fun openForPlanning(productSlug: String): List<BugView> = list(productSlug).filter { it.status == "OPEN" }

    fun contextForIteration(productSlug: String): String = openForPlanning(productSlug).joinToString("\n\n") {
        "BUG-${it.id} | ${it.priority} | ${it.status} | ${it.title}\n${it.description}\nReproduceren: ${it.reproductionSteps}\nVerwacht: ${it.expectedResult}\nWerkelijk: ${it.actualResult}"
    }.ifBlank { "Geen open bugs." }

    @Transactional
    fun apply(productSlug: String, sourceType: String, sourceId: String, mutation: BugMutation): BugMutationResult {
        val slug = products.requireContext(productSlug).slug
        require(mutation.action in ACTIONS) { "Ongeldige bugactie '${mutation.action}'" }
        require(mutation.priority in PRIORITIES) { "Ongeldige bugprioriteit '${mutation.priority}'" }
        if (mutation.action == "CREATE") return upsert(slug, sourceType, sourceId, mutation)
        val id = mutation.bugId ?: throw IllegalArgumentException("Bugactie ${mutation.action} vereist een bugId")
        require(id in list(slug).map { it.id }) { "Bug $id hoort niet bij product '$slug'" }
        val current = require(slug, id)
        val status = when (mutation.action) {
            "RESOLVE" -> "RESOLVED"
            "OBSOLETE" -> "OBSOLETE"
            // Een nieuwe observatie tijdens een lopende fix mag die fix niet
            // opnieuw in de backlog zetten. Een mislukte verificatie doet dat wel.
            else -> if (current.status == "IN_PROGRESS") "IN_PROGRESS" else "OPEN"
        }
        jdbc.update(
            """update product_bug set title = ?, description = ?, reproduction_steps = ?, expected_result = ?,
                actual_result = ?, priority = ?, status = ?, source_type = ?, source_id = ?,
                occurrence_count = occurrence_count + 1, updated_at = current_timestamp,
                last_verified_at = current_timestamp,
                resolved_at = case when ? in ('RESOLVED', 'OBSOLETE') then current_timestamp else null end
                where id = ? and product_slug = ?""".trimIndent(),
            clean(mutation.title, 240), clean(mutation.description), clean(mutation.reproductionSteps),
            clean(mutation.expectedResult), clean(mutation.actualResult), mutation.priority, status,
            sourceType, sourceId, status, id, slug,
        )
        return BugMutationResult(mutation.action, require(slug, id))
    }

    private fun upsert(slug: String, sourceType: String, sourceId: String, mutation: BugMutation): BugMutationResult {
        val fingerprint = fingerprint(mutation.title, mutation.reproductionSteps)
        val existing = jdbc.query(SELECT + " where product_slug = ? and fingerprint = ?", ::mapBug, slug, fingerprint).firstOrNull()
        if (existing != null) {
            jdbc.update(
                """update product_bug set description = ?, reproduction_steps = ?, expected_result = ?, actual_result = ?,
                    priority = case when ? < priority then ? else priority end,
                    status = case when status in ('RESOLVED', 'OBSOLETE') then 'OPEN' else status end,
                    source_type = ?, source_id = ?, occurrence_count = occurrence_count + 1,
                    updated_at = current_timestamp, last_verified_at = current_timestamp, resolved_at = null where id = ?""".trimIndent(),
                clean(mutation.description), clean(mutation.reproductionSteps), clean(mutation.expectedResult), clean(mutation.actualResult),
                mutation.priority, mutation.priority, sourceType, sourceId, existing.id,
            )
            return BugMutationResult("UPDATE", require(slug, existing.id))
        }
        val key = GeneratedKeyHolder()
        jdbc.update({ connection ->
            connection.prepareStatement(
                """insert into product_bug(product_slug, title, description, reproduction_steps, expected_result,
                    actual_result, priority, status, source_type, source_id, fingerprint, last_verified_at)
                    values (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, current_timestamp)""".trimIndent(),
                arrayOf("id"),
            ).apply {
                setString(1, slug); setString(2, clean(mutation.title, 240)); setString(3, clean(mutation.description))
                setString(4, clean(mutation.reproductionSteps)); setString(5, clean(mutation.expectedResult))
                setString(6, clean(mutation.actualResult)); setString(7, mutation.priority); setString(8, sourceType)
                setString(9, sourceId); setString(10, fingerprint)
            }
        }, key)
        val id = key.key?.toLong() ?: error("Database gaf geen bug-ID terug")
        return BugMutationResult("CREATE", require(slug, id))
    }

    fun updateManually(productSlug: String, id: Long, priority: String?, status: String?): BugView {
        val bug = require(productSlug, id)
        val nextPriority = priority ?: bug.priority
        val nextStatus = status ?: bug.status
        require(nextPriority in PRIORITIES) { "Ongeldige bugprioriteit" }
        require(nextStatus in STATUSES) { "Ongeldige bugstatus" }
        jdbc.update(
            """update product_bug set priority = ?, status = ?, updated_at = current_timestamp,
                resolved_at = case when ? in ('RESOLVED', 'OBSOLETE') then current_timestamp else null end
                where id = ? and product_slug = ?""".trimIndent(),
            nextPriority, nextStatus, nextStatus, id, bug.productSlug,
        )
        return require(bug.productSlug, id)
    }

    fun linkCandidate(productSlug: String, bugId: Long, candidateId: Long) {
        require(productSlug, bugId)
        jdbc.update(
            "update product_bug set linked_candidate_id = ?, updated_at = current_timestamp where id = ? and product_slug = ? and status in ('OPEN', 'IN_PROGRESS')",
            candidateId, bugId, productSlug,
        )
    }

    private fun fingerprint(title: String, steps: String): String {
        val normalized = "${title.trim().lowercase()}|${steps.trim().lowercase()}".replace(Regex("\\s+"), " ")
        return MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun clean(value: String, max: Int = 20_000): String = value.trim().also {
        require(it.isNotBlank()) { "Bugvelden mogen niet leeg zijn" }
        require(it.length <= max) { "Bugveld is te lang" }
    }

    private fun mapBug(row: ResultSet, ignored: Int) = BugView(
        row.getLong("id"), row.getString("product_slug"), row.getString("title"), row.getString("description"),
        row.getString("reproduction_steps"), row.getString("expected_result"), row.getString("actual_result"),
        row.getString("priority"), row.getString("status"), row.getString("source_type"), row.getString("source_id"),
        row.getInt("occurrence_count"), row.getLong("linked_candidate_id").takeUnless { row.wasNull() },
        row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant(),
        row.getTimestamp("last_verified_at")?.toInstant(), row.getTimestamp("resolved_at")?.toInstant(),
    )

    companion object {
        private const val SELECT = "select * from product_bug"
        val PRIORITIES = setOf("P0", "P1", "P2", "P3")
        val STATUSES = setOf("OPEN", "IN_PROGRESS", "READY_FOR_VERIFICATION", "RESOLVED", "OBSOLETE")
        val ACTIVE_STATUSES = setOf("OPEN", "IN_PROGRESS", "READY_FOR_VERIFICATION")
        private val ACTIONS = setOf("CREATE", "UPDATE", "RESOLVE", "OBSOLETE")
    }
}
