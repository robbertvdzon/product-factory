package nl.vdzon.productfactory.roadmap.api

import nl.vdzon.productfactory.contracts.RoadmapEpicView
import nl.vdzon.productfactory.contracts.RoadmapSettledQuestionView
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.sql.ResultSet

/** Eigenaar van de langlevende roadmap-epics, hun twee rangordes en harde dependencies. */
@Service
class RoadmapCatalog(
    private val jdbc: JdbcTemplate,
    private val products: ProductCatalog,
    private val visions: RoadmapVisionCatalog,
) {
    fun listEpics(productSlug: String): List<RoadmapEpicView> {
        val product = products.requireContext(productSlug)
        return views(product.slug)
    }

    fun requireEpic(productSlug: String, id: String): RoadmapEpicView {
        val product = products.requireContext(productSlug)
        return views(product.slug).firstOrNull { it.id == id }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Onbekende roadmap-epic voor dit product")
    }

    /** Compatibiliteit voor bestaande story- en oplevercode; de producttaal is voortaan epic. */
    fun listThemes(productSlug: String): List<RoadmapEpicView> = listEpics(productSlug)
    fun requireTheme(productSlug: String, id: String): RoadmapEpicView = requireEpic(productSlug, id)

    @Transactional
    fun createEpic(
        productSlug: String,
        title: String,
        description: String,
        processRank: Int? = null,
        dependencyIds: Set<String> = emptySet(),
        horizon: String = "UNPLACED",
        kind: String = "DELIVERY",
        capabilityKey: String? = null,
    ): RoadmapEpicView {
        val product = products.requireActive(productSlug)
        validateText(title, description)
        validateStrategyFields(horizon, kind, capabilityKey)
        lock(product.slug)
        val current = records(product.slug)
        requireDependencies(product.slug, null, dependencyIds, current)
        val sequence = (current.maxOfOrNull { it.sequenceNumber } ?: 0) + 1
        val id = "epic-${product.slug}-${sequence.toString().padStart(4, '0')}"
        val appendRank = current.size + 1
        jdbc.update(
            """insert into roadmap_theme(
                id, product_slug, sequence_number, title, description, priority, status, customer_rank, process_rank,
                horizon, kind, capability_key
            ) values (?, ?, ?, ?, ?, 'MEDIUM', 'OPEN', ?, ?, ?, ?, ?)""".trimIndent(),
            id,
            product.slug,
            sequence,
            title.trim(),
            description.trim(),
            appendRank,
            appendRank,
            horizon,
            kind,
            capabilityKey?.trim()?.ifBlank { null },
        )
        replaceDependencies(id, dependencyIds)
        processRank?.let { reorder(product.slug, id, it, "process_rank") }
        views(product.slug) // valideert ook dependencycycli
        return requireEpic(product.slug, id)
    }

    /** Oude aanroepvorm blijft broncompatibel voor bestaande tests/consumenten. */
    @Transactional
    fun createTheme(productSlug: String, title: String, description: String, priority: String): RoadmapEpicView {
        require(priority in LEGACY_PRIORITIES) { "Ongeldige prioriteit" }
        return createEpic(productSlug, title, description)
    }

    @Transactional
    fun updateEpicFromCustomer(
        productSlug: String,
        id: String,
        title: String? = null,
        description: String? = null,
        customerRank: Int? = null,
        dependencyIds: Set<String>? = null,
        status: String? = null,
    ): RoadmapEpicView = updateEpic(
        productSlug = productSlug,
        id = id,
        title = title,
        description = description,
        customerRank = customerRank,
        dependencyIds = dependencyIds,
        status = status,
    )

    @Transactional
    fun updateEpicFromProcess(
        productSlug: String,
        id: String,
        title: String? = null,
        description: String? = null,
        processRank: Int? = null,
        dependencyIds: Set<String>? = null,
        status: String? = null,
        horizon: String? = null,
        kind: String? = null,
        capabilityKey: String? = null,
    ): RoadmapEpicView = updateEpic(
        productSlug = productSlug,
        id = id,
        title = title,
        description = description,
        processRank = processRank,
        dependencyIds = dependencyIds,
        status = status,
        horizon = horizon,
        kind = kind,
        capabilityKey = capabilityKey,
        updateStrategyFields = horizon != null || kind != null || capabilityKey != null,
    )

    /** Compatibiliteitsvorm voor bestaande interne code; priority wordt niet meer gebruikt. */
    @Transactional
    fun updateTheme(
        productSlug: String,
        id: String,
        title: String? = null,
        description: String? = null,
        priority: String? = null,
        status: String? = null,
    ): RoadmapEpicView {
        priority?.let { require(it in LEGACY_PRIORITIES) { "Ongeldige prioriteit" } }
        return updateEpicFromProcess(productSlug, id, title, description, status = status)
    }

    @Transactional
    fun closeEpic(productSlug: String, id: String): RoadmapEpicView =
        updateEpicFromProcess(productSlug, id, status = "DONE")

    @Transactional
    fun closeTheme(productSlug: String, id: String): RoadmapEpicView = closeEpic(productSlug, id)

    private fun updateEpic(
        productSlug: String,
        id: String,
        title: String? = null,
        description: String? = null,
        customerRank: Int? = null,
        processRank: Int? = null,
        dependencyIds: Set<String>? = null,
        status: String? = null,
        horizon: String? = null,
        kind: String? = null,
        capabilityKey: String? = null,
        updateStrategyFields: Boolean = false,
    ): RoadmapEpicView {
        val product = products.requireActive(productSlug)
        lock(product.slug)
        val current = requireEpic(product.slug, id)
        val newTitle = (title ?: current.title).trim()
        val newDescription = (description ?: current.description).trim()
        val newStatus = status ?: current.status
        val newHorizon = horizon ?: current.horizon
        val newKind = kind ?: current.kind
        val newCapabilityKey = if (updateStrategyFields) capabilityKey?.trim()?.ifBlank { null } else current.capabilityKey
        validateText(newTitle, newDescription)
        require(newStatus in STATUSES) { "Ongeldige status" }
        validateStrategyFields(newHorizon, newKind, newCapabilityKey)
        val closingNow = newStatus == "DONE" && current.status != "DONE"
        val reopening = newStatus != "DONE" && current.status == "DONE"
        jdbc.update(
            """update roadmap_theme
                set title = ?, description = ?, status = ?, horizon = ?, kind = ?, capability_key = ?, updated_at = current_timestamp,
                    closed_at = case when ? then current_timestamp when ? then null else closed_at end
                where product_slug = ? and id = ?""".trimIndent(),
            newTitle,
            newDescription,
            newStatus,
            newHorizon,
            newKind,
            newCapabilityKey,
            closingNow,
            reopening,
            product.slug,
            id,
        )
        val allRecords = records(product.slug)
        dependencyIds?.let {
            requireDependencies(product.slug, id, it, allRecords)
            replaceDependencies(id, it)
        }
        customerRank?.let { reorder(product.slug, id, it, "customer_rank") }
        processRank?.let { reorder(product.slug, id, it, "process_rank") }
        views(product.slug) // gooit bij een cyclus; @Transactional maakt de hele update atomair
        return requireEpic(product.slug, id)
    }

    fun listSettledQuestions(productSlug: String): List<RoadmapSettledQuestionView> {
        val product = products.requireContext(productSlug)
        return jdbc.query(
            "select id, product_slug, content, created_at from roadmap_settled_question where product_slug = ? order by id desc",
            ::mapSettledQuestion,
            product.slug,
        )
    }

    fun contextForCycle(productSlug: String): String {
        val openEpics = listEpics(productSlug).filter { it.status != "DONE" }
        val settled = listSettledQuestions(productSlug)
        val epicsBlock = if (openEpics.isEmpty()) {
            "Er zijn nog geen roadmap-epics vastgesteld."
        } else {
            openEpics.joinToString("\n") {
                "epicId=${it.id} [${it.horizon}, ${it.kind}, capability=${it.capabilityKey ?: "geen"}, roadmap #${it.roadmapRank}, klant #${it.customerRank}, proces #${it.processRank}, score ${it.priorityScore}, ${it.status}] ${it.title}: ${it.description} dependencies=${it.dependencyIds}"
            }
        }
        val settledBlock = if (settled.isEmpty()) "Geen afgehandelde onderzoeksvragen." else settled.joinToString("\n") { "- ${it.content}" }
        return "${visions.contextForCycle(productSlug)}\n\nOPEN ROADMAP-EPICS:\n$epicsBlock\n\nAFGEHANDELDE ONDERZOEKSVRAGEN (niet opnieuw onderzoeken):\n$settledBlock"
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

    private fun views(productSlug: String): List<RoadmapEpicView> {
        val rows = rows(productSlug)
        val records = rows.map { it.record }
        val ranking = RoadmapRanking.rank(records).associateBy { it.record.id }
        val blocks = records.flatMap { epic -> epic.dependencyIds.map { dependency -> dependency to epic.id } }
            .groupBy({ it.first }, { it.second })
        val statusById = records.associate { it.id to it.status }
        return rows.map { row ->
            val ranked = ranking.getValue(row.record.id)
            RoadmapEpicView(
                id = row.record.id,
                productSlug = productSlug,
                sequenceNumber = row.record.sequenceNumber,
                title = row.title,
                description = row.description,
                priority = legacyPriority(ranked.score),
                status = row.record.status,
                customerRank = row.record.customerRank,
                processRank = row.record.processRank,
                priorityScore = ranked.score,
                roadmapRank = ranked.roadmapRank,
                dependencyIds = row.record.dependencyIds.sorted(),
                blockedByIds = row.record.dependencyIds.filter { statusById[it] != "DONE" }.sorted(),
                blocksIds = blocks[row.record.id].orEmpty().sorted(),
                horizon = row.horizon,
                kind = row.kind,
                capabilityKey = row.capabilityKey,
                createdAt = row.createdAt,
                updatedAt = row.updatedAt,
                closedAt = row.closedAt,
            )
        }.sortedBy { it.roadmapRank }
    }

    private fun records(productSlug: String): List<RoadmapEpicRecord> = rows(productSlug).map { it.record }

    private fun rows(productSlug: String): List<EpicRow> {
        val dependencies = jdbc.query(
            """select d.epic_id, d.dependency_id
                from roadmap_epic_dependency d
                join roadmap_theme e on e.id = d.epic_id
                where e.product_slug = ?""".trimIndent(),
            { row, _ -> row.getString(1) to row.getString(2) },
            productSlug,
        ).groupBy({ it.first }, { it.second })
        return jdbc.query(
            "select * from roadmap_theme where product_slug = ? order by sequence_number",
            { row, _ -> mapEpicRow(row, dependencies[row.getString("id")].orEmpty().toSet()) },
            productSlug,
        )
    }

    private fun lock(productSlug: String) {
        jdbc.query("select id from product_definition where slug = ? for update", { row, _ -> row.getString(1) }, productSlug)
    }

    private fun reorder(productSlug: String, epicId: String, requestedRank: Int, column: String) {
        require(column == "customer_rank" || column == "process_rank")
        val ids = jdbc.query(
            "select id from roadmap_theme where product_slug = ? order by $column, sequence_number",
            { row, _ -> row.getString(1) },
            productSlug,
        ).toMutableList()
        require(epicId in ids) { "Onbekende roadmap-epic" }
        ids.remove(epicId)
        ids.add((requestedRank.coerceIn(1, ids.size + 1) - 1), epicId)
        jdbc.update("update roadmap_theme set $column = -$column where product_slug = ?", productSlug)
        ids.forEachIndexed { index, id ->
            jdbc.update("update roadmap_theme set $column = ?, updated_at = current_timestamp where id = ?", index + 1, id)
        }
    }

    private fun requireDependencies(
        productSlug: String,
        epicId: String?,
        dependencyIds: Set<String>,
        records: List<RoadmapEpicRecord>,
    ) {
        require(epicId !in dependencyIds) { "Een epic kan niet van zichzelf afhankelijk zijn" }
        val known = records.mapTo(mutableSetOf()) { it.id }
        require(dependencyIds.all(known::contains)) { "Een dependency bestaat niet binnen product $productSlug" }
    }

    private fun replaceDependencies(epicId: String, dependencyIds: Set<String>) {
        jdbc.update("delete from roadmap_epic_dependency where epic_id = ?", epicId)
        dependencyIds.forEach { dependencyId ->
            jdbc.update(
                "insert into roadmap_epic_dependency(epic_id, dependency_id) values (?, ?)",
                epicId,
                dependencyId,
            )
        }
    }

    private fun validateText(title: String, description: String) {
        require(title.isNotBlank()) { "Titel is verplicht" }
        require(title.length <= 80) { "De korte epic-titel mag maximaal 80 tekens bevatten" }
        require(description.isNotBlank()) { "Beschrijving is verplicht" }
        require(description.length <= 10_000) { "Beschrijving mag maximaal 10.000 tekens bevatten" }
    }

    private fun mapEpicRow(row: ResultSet, dependencyIds: Set<String>) = EpicRow(
        record = RoadmapEpicRecord(
            id = row.getString("id"),
            sequenceNumber = row.getInt("sequence_number"),
            customerRank = row.getInt("customer_rank"),
            processRank = row.getInt("process_rank"),
            status = row.getString("status"),
            dependencyIds = dependencyIds,
        ),
        title = row.getString("title"),
        description = row.getString("description"),
        horizon = row.getString("horizon"),
        kind = row.getString("kind"),
        capabilityKey = row.getString("capability_key"),
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

    private fun legacyPriority(score: Int): String = when {
        score >= 67 -> "HIGH"
        score >= 34 -> "MEDIUM"
        else -> "LOW"
    }

    private data class EpicRow(
        val record: RoadmapEpicRecord,
        val title: String,
        val description: String,
        val horizon: String,
        val kind: String,
        val capabilityKey: String?,
        val createdAt: java.time.Instant,
        val updatedAt: java.time.Instant,
        val closedAt: java.time.Instant?,
    )

    companion object {
        private val LEGACY_PRIORITIES = setOf("HIGH", "MEDIUM", "LOW")
        private val STATUSES = setOf("OPEN", "IN_PROGRESS", "DONE")
        private val HORIZONS = setOf("UNPLACED", "NOW", "NEXT", "LATER", "HORIZON")
        private val KINDS = setOf("DELIVERY", "DISCOVERY")
    }

    private fun validateStrategyFields(horizon: String, kind: String, capabilityKey: String?) {
        require(horizon in HORIZONS) { "Ongeldige roadmaphorizon" }
        require(kind in KINDS) { "Ongeldig epictype" }
        require(capabilityKey == null || capabilityKey.matches(Regex("^[a-z0-9]+(-[a-z0-9]+)*$"))) {
            "Capability-sleutel moet kebab-case zijn"
        }
    }
}
