package nl.vdzon.productfactory.meeting.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import nl.vdzon.productfactory.contracts.MeetingMessageView
import nl.vdzon.productfactory.contracts.MeetingView
import nl.vdzon.productfactory.contracts.MemoryChangeView
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

/**
 * Eigenaar en repository van het overleg-aggregaat. De 7-dagengate en idempotentie voor het
 * "ik wil overleg"-vlaggetje van een product zitten uitsluitend hier in [requestMeeting] — geen
 * enkele aanroeper (met name de SUMMARY-rol in ShadowIterationEngine) vertrouwt zelf op het oordeel
 * van de AI dat een overleg "mag".
 */
@Service
class MeetingCatalog(private val jdbc: JdbcTemplate, private val products: ProductCatalog, private val mapper: ObjectMapper) {
    fun list(productSlug: String): List<MeetingView> {
        val product = products.requireContext(productSlug)
        return jdbc.query(SELECT + " where product_slug = ? order by sequence_number desc", ::mapMeeting, product.slug)
    }

    fun require(productSlug: String, id: String): MeetingView {
        val product = products.requireContext(productSlug)
        return jdbc.query(SELECT + " where product_slug = ? and id = ?", ::mapMeeting, product.slug, id).singleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Onbekend overleg voor dit product")
    }

    fun requireOpen(productSlug: String, id: String): MeetingView = require(productSlug, id).also {
        if (it.status != "OPEN") throw ResponseStatusException(HttpStatus.CONFLICT, "Overleg is al afgesloten")
    }

    fun messages(productSlug: String, id: String): List<MeetingMessageView> {
        require(productSlug, id)
        return jdbc.query(
            """select id, meeting_id, sender, content, created_at, consulted_sources, memory_changes
                from meeting_message where meeting_id = ? order by id""".trimIndent(),
            ::mapMessage,
            id,
        )
    }

    @Transactional
    fun create(productSlug: String): MeetingView {
        val product = products.requireProduct(productSlug)
        if (product.status != "active") throw ResponseStatusException(HttpStatus.CONFLICT, "Product is niet actief")
        if ((jdbc.queryForObject("select count(*) from meeting where product_slug = ? and status = 'OPEN'", Long::class.java, product.slug) ?: 0) > 0) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Er loopt al een overleg voor dit product")
        }
        val sequence = jdbc.queryForObject("select coalesce(max(sequence_number), 0) + 1 from meeting where product_slug = ?", Int::class.java, product.slug) ?: 1
        val id = "meeting-${product.slug}-${sequence.toString().padStart(4, '0')}"
        val pendingTopics = product.meetingRequestedTopics
        val initiator = if (product.meetingRequestedAt != null) "product" else "owner"
        jdbc.update(
            "insert into meeting(id, product_slug, sequence_number, initiator, status, requested_topics) values (?, ?, ?, ?, 'OPEN', ?)",
            id,
            product.slug,
            sequence,
            initiator,
            pendingTopics.takeIf { it.isNotEmpty() }?.let { mapper.writeValueAsString(it) },
        )
        if (product.meetingRequestedAt != null) products.clearMeetingRequested(product.slug)
        return require(product.slug, id)
    }

    fun addMessage(
        productSlug: String,
        meetingId: String,
        sender: String,
        content: String,
        consultedSources: List<String> = emptyList(),
        memoryChanges: List<MemoryChangeView> = emptyList(),
    ): MeetingMessageView {
        require(sender in setOf("owner", "ai")) { "Ongeldige afzender" }
        jdbc.update(
            """insert into meeting_message(
                meeting_id, product_slug, sender, content, consulted_sources, memory_changes
            ) values (?, ?, ?, ?, ?, ?)""".trimIndent(),
            meetingId,
            productSlug,
            sender,
            content,
            consultedSources.takeIf { it.isNotEmpty() }?.let(mapper::writeValueAsString),
            memoryChanges.takeIf { it.isNotEmpty() }?.let(mapper::writeValueAsString),
        )
        return jdbc.query(
            """select id, meeting_id, sender, content, created_at, consulted_sources, memory_changes
                from meeting_message where meeting_id = ? order by id desc limit 1""".trimIndent(),
            ::mapMessage,
            meetingId,
        ).first()
    }

    /**
     * De workspace-velden zijn optioneel: notulenpublicatie is best-effort (zie
     * MeetingChatService.closeOut) en mag het afsluiten van het overleg zelf nooit blokkeren.
     */
    fun close(
        productSlug: String,
        id: String,
        outcomeSummary: String,
        workspaceRunId: String? = null,
        workspacePullRequestUrl: String? = null,
        workspaceCommitSha: String? = null,
    ): MeetingView {
        val updated = jdbc.update(
            """update meeting set status = 'CLOSED', closed_at = current_timestamp, outcome_summary = ?,
                workspace_run_id = ?, workspace_pull_request_url = ?, workspace_commit_sha = ?
                where product_slug = ? and id = ? and status = 'OPEN'""".trimIndent(),
            outcomeSummary,
            workspaceRunId,
            workspacePullRequestUrl,
            workspaceCommitSha,
            productSlug,
            id,
        )
        if (updated == 0) throw ResponseStatusException(HttpStatus.CONFLICT, "Overleg kan niet worden afgesloten")
        return require(productSlug, id)
    }

    /**
     * Zet het pending "ik wil overleg"-vlaggetje op het product, mits er nog geen openstaand verzoek
     * is en het laatst afgesloten overleg (indien aanwezig) minstens 7 dagen geleden is. Faalt nooit
     * hard: een geweigerd verzoek is een stille no-op, zodat een aanroeper (de cyclus) hier nooit op
     * hoeft te reageren.
     */
    fun requestMeeting(productSlug: String, topics: List<String>) {
        val product = runCatching { products.requireProduct(productSlug) }.getOrNull() ?: return
        if (product.meetingRequestedAt != null) {
            log.debug("Overlegverzoek voor {} genegeerd: er staat al een verzoek open", productSlug)
            return
        }
        val lastClosed: Timestamp? = jdbc.queryForObject(
            "select max(closed_at) from meeting where product_slug = ? and status = 'CLOSED'",
            Timestamp::class.java,
            productSlug,
        )
        if (lastClosed != null && lastClosed.toInstant().isAfter(Instant.now().minus(SEVEN_DAYS))) {
            log.debug("Overlegverzoek voor {} genegeerd: laatste overleg was minder dan 7 dagen geleden", productSlug)
            return
        }
        val cleaned = topics.map { it.trim() }.filter { it.isNotBlank() }.take(5)
        if (cleaned.isEmpty()) return
        products.setMeetingRequested(productSlug, cleaned)
    }

    /** Opgemaakte, geknipte context van eerder afgesloten overleggen, voor injectie in researchPrompt. */
    fun recentOutcomes(productSlug: String, limit: Int = 3): String = jdbc.query(
        """select closed_at, outcome_summary from meeting
            where product_slug = ? and status = 'CLOSED' and outcome_summary is not null
            order by closed_at desc limit ?""".trimIndent(),
        { row, _ -> "Overleg gesloten op ${row.getTimestamp("closed_at").toInstant()}: ${row.getString("outcome_summary").take(3000)}" },
        productSlug,
        limit,
    ).joinToString("\n\n").take(12_000).ifBlank { "Nog geen eerdere overleggen met de eigenaar." }

    private fun mapMeeting(row: ResultSet, ignored: Int) = MeetingView(
        id = row.getString("id"),
        productSlug = row.getString("product_slug"),
        sequenceNumber = row.getInt("sequence_number"),
        initiator = row.getString("initiator"),
        status = row.getString("status"),
        requestedTopics = row.getString("requested_topics")?.let { json ->
            runCatching { mapper.readValue<List<String>>(json) }.getOrDefault(emptyList())
        } ?: emptyList(),
        outcomeSummary = row.getString("outcome_summary"),
        createdAt = row.getTimestamp("created_at").toInstant(),
        closedAt = row.getTimestamp("closed_at")?.toInstant(),
        workspaceRunId = row.getString("workspace_run_id"),
        workspacePullRequestUrl = row.getString("workspace_pull_request_url"),
        workspaceCommitSha = row.getString("workspace_commit_sha"),
    )

    private fun mapMessage(row: ResultSet, ignored: Int) = MeetingMessageView(
        id = row.getLong("id"),
        meetingId = row.getString("meeting_id"),
        sender = row.getString("sender"),
        content = row.getString("content"),
        createdAt = row.getTimestamp("created_at").toInstant(),
        consultedSources = row.getString("consulted_sources")?.let { json ->
            runCatching { mapper.readValue<List<String>>(json) }.getOrDefault(emptyList())
        } ?: emptyList(),
        memoryChanges = row.getString("memory_changes")?.let { json ->
            runCatching { mapper.readValue<List<MemoryChangeView>>(json) }.getOrDefault(emptyList())
        } ?: emptyList(),
    )

    companion object {
        private val log = LoggerFactory.getLogger(MeetingCatalog::class.java)
        private val SEVEN_DAYS: Duration = Duration.ofDays(7)
        private const val SELECT = "select * from meeting"
    }
}
