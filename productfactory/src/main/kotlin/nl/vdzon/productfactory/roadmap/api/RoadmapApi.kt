package nl.vdzon.productfactory.roadmap.api

import nl.vdzon.productfactory.contracts.RoadmapSettledQuestionView
import nl.vdzon.productfactory.contracts.RoadmapThemeView
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.sql.ResultSet

/**
 * Eigenaar en repository van het roadmap-aggregaat: langlevende thema's/epics per product (in
 * tegenstelling tot de losse "focus" van één shadow-iteratie) plus een aparte lijst afgehandelde
 * onderzoeksvragen. Fase 1 bevat alleen de datalaag; het bijwerken door een Product Manager-agentrol
 * volgt in een latere fase.
 */
@Service
class RoadmapCatalog(private val jdbc: JdbcTemplate, private val products: ProductCatalog) {
    fun listThemes(productSlug: String): List<RoadmapThemeView> {
        val product = products.requireContext(productSlug)
        return jdbc.query(THEME_SELECT + " where product_slug = ? order by sequence_number desc", ::mapTheme, product.slug)
    }

    fun requireTheme(productSlug: String, id: String): RoadmapThemeView {
        val product = products.requireContext(productSlug)
        return jdbc.query(THEME_SELECT + " where product_slug = ? and id = ?", ::mapTheme, product.slug, id).singleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Onbekend roadmapthema voor dit product")
    }

    fun createTheme(productSlug: String, title: String, description: String, priority: String): RoadmapThemeView {
        val product = products.requireActive(productSlug)
        require(title.isNotBlank()) { "Titel is verplicht" }
        require(description.isNotBlank()) { "Beschrijving is verplicht" }
        require(priority in PRIORITIES) { "Ongeldige prioriteit" }
        val sequence = jdbc.queryForObject("select coalesce(max(sequence_number), 0) + 1 from roadmap_theme where product_slug = ?", Int::class.java, product.slug) ?: 1
        val id = "theme-${product.slug}-${sequence.toString().padStart(4, '0')}"
        jdbc.update(
            "insert into roadmap_theme(id, product_slug, sequence_number, title, description, priority, status) values (?, ?, ?, ?, ?, ?, 'OPEN')",
            id,
            product.slug,
            sequence,
            title.trim(),
            description.trim(),
            priority,
        )
        return requireTheme(product.slug, id)
    }

    fun updateTheme(
        productSlug: String,
        id: String,
        title: String? = null,
        description: String? = null,
        priority: String? = null,
        status: String? = null,
    ): RoadmapThemeView {
        val current = requireTheme(productSlug, id)
        val newTitle = (title ?: current.title).trim()
        val newDescription = (description ?: current.description).trim()
        val newPriority = priority ?: current.priority
        val newStatus = status ?: current.status
        require(newTitle.isNotBlank()) { "Titel is verplicht" }
        require(newDescription.isNotBlank()) { "Beschrijving is verplicht" }
        require(newPriority in PRIORITIES) { "Ongeldige prioriteit" }
        require(newStatus in STATUSES) { "Ongeldige status" }
        val closingNow = newStatus == "DONE" && current.status != "DONE"
        jdbc.update(
            """update roadmap_theme set title = ?, description = ?, priority = ?, status = ?, updated_at = current_timestamp
                ${if (closingNow) ", closed_at = current_timestamp" else ""}
                where product_slug = ? and id = ?""".trimIndent(),
            newTitle,
            newDescription,
            newPriority,
            newStatus,
            productSlug,
            id,
        )
        return requireTheme(productSlug, id)
    }

    fun closeTheme(productSlug: String, id: String): RoadmapThemeView = updateTheme(productSlug, id, status = "DONE")

    fun listSettledQuestions(productSlug: String): List<RoadmapSettledQuestionView> {
        val product = products.requireContext(productSlug)
        return jdbc.query(
            "select id, product_slug, content, created_at from roadmap_settled_question where product_slug = ? order by id desc",
            ::mapSettledQuestion,
            product.slug,
        )
    }

    /**
     * Opgemaakte roadmapcontext voor cyclusprompts (RESEARCHER/PRODUCT_OWNER/STORY_WRITER): open
     * thema's met hun ID (zodat STORY_WRITER exact kan verwijzen) plus afgehandelde onderzoeksvragen,
     * zodat een cyclus niet nogmaals hoeft te onderzoeken wat al is uitgezocht.
     */
    fun contextForCycle(productSlug: String): String {
        val openThemes = listThemes(productSlug).filter { it.status != "DONE" }
        val settled = listSettledQuestions(productSlug)
        val themesBlock = if (openThemes.isEmpty()) {
            "Er zijn nog geen roadmapthema's vastgesteld."
        } else {
            openThemes.joinToString("\n") {
                "themaId=${it.id} [${it.priority}, ${it.status}] ${it.title}: ${it.description}"
            }
        }
        val settledBlock = if (settled.isEmpty()) {
            "Geen afgehandelde onderzoeksvragen."
        } else {
            settled.joinToString("\n") { "- ${it.content}" }
        }
        return "OPEN ROADMAPTHEMA'S:\n$themesBlock\n\nAFGEHANDELDE ONDERZOEKSVRAGEN (niet opnieuw onderzoeken):\n$settledBlock"
    }

    fun addSettledQuestion(productSlug: String, content: String): RoadmapSettledQuestionView {
        val product = products.requireActive(productSlug)
        require(content.isNotBlank()) { "Inhoud is verplicht" }
        jdbc.update(
            "insert into roadmap_settled_question(product_slug, content) values (?, ?)",
            product.slug,
            content.trim(),
        )
        return listSettledQuestions(product.slug).first()
    }

    private fun mapTheme(row: ResultSet, ignored: Int) = RoadmapThemeView(
        id = row.getString("id"),
        productSlug = row.getString("product_slug"),
        sequenceNumber = row.getInt("sequence_number"),
        title = row.getString("title"),
        description = row.getString("description"),
        priority = row.getString("priority"),
        status = row.getString("status"),
        createdAt = row.getTimestamp("created_at").toInstant(),
        updatedAt = row.getTimestamp("updated_at").toInstant(),
        closedAt = row.getTimestamp("closed_at")?.toInstant(),
    )

    private fun mapSettledQuestion(row: ResultSet, ignored: Int) = RoadmapSettledQuestionView(
        id = row.getLong("id"),
        productSlug = row.getString("product_slug"),
        content = row.getString("content"),
        createdAt = row.getTimestamp("created_at").toInstant(),
    )

    companion object {
        private val PRIORITIES = setOf("HIGH", "MEDIUM", "LOW")
        private val STATUSES = setOf("OPEN", "IN_PROGRESS", "DONE")
        private const val THEME_SELECT = "select * from roadmap_theme"
    }
}
