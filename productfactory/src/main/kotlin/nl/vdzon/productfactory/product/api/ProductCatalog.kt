package nl.vdzon.productfactory.product.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import nl.vdzon.productfactory.contracts.MemoryChangeView
import nl.vdzon.productfactory.contracts.MemoryVersionView
import nl.vdzon.productfactory.contracts.ProductRecordView
import nl.vdzon.productfactory.contracts.ProductView
import nl.vdzon.productfactory.contracts.WeeklyScheduleView
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class ProductConfiguration(
    val slug: String,
    val name: String,
    val mission: String,
    val description: String,
    val guardrails: String,
    val softwareFactoryProjectKey: String,
    val targetRepositoryName: String,
    val allowedWritePaths: List<String>,
    val workspaceOwnership: String,
    val liveUrl: String?,
    val previewUrlPattern: String?,
    val acceptanceUrl: String?,
    val adminUrl: String?,
    val status: String,
    val developmentMode: String,
    val iterationTimes: List<String>,
    val roadmapSchedule: List<WeeklyScheduleView>,
    val timezone: String,
    val maxStoriesPerCycle: Int,
    val wipLimit: Int,
    val aiProvider: String,
    val aiModel: String,
    val dailyBudgetCents: Int,
    val monthlyBudgetCents: Int,
    val escalationPolicy: String,
    val privacyRules: String,
    val accessibilityRules: String,
    val qualityRules: String,
)

data class UpdateProductSettingsRequest(
    val developmentMode: String? = null,
    val iterationTimes: List<String>? = null,
    val roadmapSchedule: List<WeeklyScheduleView>? = null,
    val maxStoriesPerCycle: Int? = null,
    val wipLimit: Int? = null,
    val aiProvider: String? = null,
    val aiModel: String? = null,
    val targetRepositoryName: String? = null,
    val acceptanceUrl: String? = null,
)

data class ProductContext(
    val slug: String,
    val status: String,
    val developmentMode: String,
    val workspaceDirectory: String,
    val workspaceOwnership: String,
    val allowedWritePaths: List<String>,
)

data class MemoryMutation(
    val action: String,
    val productSlug: String,
    val targetMemoryId: Long?,
    val title: String?,
    val content: String?,
    val reason: String,
)

@Service
class ProductCatalog(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper) {
    fun list(): List<ProductView> = jdbc.query(PRODUCT_SELECT + " order by p.slug", ::mapProduct)

    fun requireProduct(slug: String): ProductView = jdbc.query(PRODUCT_SELECT + " where p.slug = ?", ::mapProduct, normalizeSlug(slug))
        .singleOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Onbekend product")

    fun requireContext(slug: String): ProductContext {
        val product = requireProduct(slug)
        return ProductContext(
            product.slug,
            product.status,
            product.developmentMode,
            product.workspaceDirectory,
            product.workspaceOwnership,
            product.allowedWritePaths,
        )
    }

    fun requireActive(slug: String): ProductContext = requireContext(slug).also {
        if (it.status != "active") throw ResponseStatusException(HttpStatus.CONFLICT, "Product is niet actief")
    }

    fun requireStoryPublication(slug: String): ProductContext = requireActive(slug).also {
        if (it.developmentMode != "autonomous") {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Alleen een autonoom product mag stories publiceren")
        }
    }

    fun requireWorkspacePublication(slug: String, relativePath: String): ProductContext = requireActive(slug).also { product ->
        if (product.workspaceOwnership != "product-factory") {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "De Product Factory is geen eigenaar van deze workspace")
        }
        val derivedDirectory = "products/${product.slug}"
        check(product.workspaceDirectory == derivedDirectory) { "Workspace-directory wijkt af van de geregistreerde productslug" }
        val normalized = normalizeRelativePath(relativePath)
        if (product.allowedWritePaths.none { normalized == it || normalized.startsWith("$it/") }) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Artefactpad is niet toegestaan voor dit product")
        }
    }

    @Transactional
    fun create(candidate: ProductConfiguration): ProductView {
        val product = validate(candidate)
        try {
            jdbc.update(
                """insert into product_definition (
                    id, slug, name, mission, description, guardrails, software_factory_project_key,
                    target_repository_name, workspace_directory, workspace_ownership, live_url,
                    preview_url_pattern, acceptance_url, admin_url, status, development_mode, timezone,
                    max_stories_per_cycle, wip_limit, ai_provider, ai_model, daily_budget_cents,
                    monthly_budget_cents, escalation_policy, privacy_rules,
                    accessibility_rules, quality_rules
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
                "pf-${UUID.randomUUID()}", product.slug, product.name.trim(), product.mission.trim(),
                product.description.trim(), product.guardrails.trim(), product.softwareFactoryProjectKey.trim(),
                product.targetRepositoryName.trim(), "products/${product.slug}", product.workspaceOwnership,
                product.liveUrl?.trim()?.ifBlank { null }, product.previewUrlPattern?.trim()?.ifBlank { null },
                product.acceptanceUrl?.trim()?.ifBlank { null }, product.adminUrl?.trim()?.ifBlank { null },
                product.status, product.developmentMode, product.timezone.trim(),
                product.maxStoriesPerCycle, product.wipLimit, product.aiProvider.trim(), product.aiModel.trim(),
                product.dailyBudgetCents, product.monthlyBudgetCents, product.escalationPolicy.trim(),
                product.privacyRules.trim(), product.accessibilityRules.trim(), product.qualityRules.trim(),
            )
        } catch (_: DuplicateKeyException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Productslug bestaat al")
        }
        product.allowedWritePaths.forEach {
            jdbc.update("insert into product_allowed_write_path(product_slug, relative_path) values (?, ?)", product.slug, it)
        }
        replaceIterationTimes(product.slug, product.iterationTimes)
        replaceRoadmapSchedule(product.slug, product.roadmapSchedule)
        return requireProduct(product.slug)
    }

    @Transactional
    fun updateSettings(slug: String, request: UpdateProductSettingsRequest): ProductView {
        val normalized = normalizeSlug(slug)
        val current = requireProduct(normalized)
        val developmentMode = request.developmentMode ?: current.developmentMode
        val maxStoriesPerCycle = request.maxStoriesPerCycle ?: current.maxStoriesPerCycle
        val wipLimit = request.wipLimit ?: current.wipLimit
        val aiProvider = (request.aiProvider ?: current.aiProvider).trim()
        val aiModel = (request.aiModel ?: current.aiModel).trim()
        val iterationTimes = request.iterationTimes ?: current.iterationTimes
        val roadmapSchedule = request.roadmapSchedule ?: current.roadmapSchedule
        val targetRepositoryName = (request.targetRepositoryName ?: current.targetRepositoryName).trim()
        val acceptanceUrl = request.acceptanceUrl?.trim()?.ifBlank { null } ?: current.acceptanceUrl

        require(developmentMode in DEVELOPMENT_MODES) { "Ongeldige ontwikkelmodus" }
        require(maxStoriesPerCycle in 1..20) { "Maximaal aantal stories per cyclus moet tussen 1 en 20 liggen" }
        require(wipLimit in 1..20) { "WIP-limiet moet tussen 1 en 20 liggen" }
        require(AiCatalog.isValid(aiProvider, aiModel)) { "Onbekende combinatie van AI-provider '$aiProvider' en model '$aiModel'" }
        require(iterationTimes.isNotEmpty()) { "Minimaal één cyclustijd is verplicht" }
        iterationTimes.forEach { require(it.matches(TIME_OF_DAY)) { "Ongeldige cyclustijd '$it', gebruik HH:mm" } }
        validateRoadmapSchedule(roadmapSchedule)
        require(targetRepositoryName.matches(REPOSITORY)) { "Ongeldige repositorynaam" }
        validateUrl(acceptanceUrl)

        jdbc.update(
            """update product_definition set development_mode = ?, max_stories_per_cycle = ?, wip_limit = ?,
                ai_provider = ?, ai_model = ?, target_repository_name = ?, acceptance_url = ?, updated_at = current_timestamp
                where slug = ?""".trimIndent(),
            developmentMode, maxStoriesPerCycle, wipLimit, aiProvider, aiModel, targetRepositoryName, acceptanceUrl, normalized,
        )
        replaceIterationTimes(normalized, iterationTimes)
        replaceRoadmapSchedule(normalized, roadmapSchedule)
        return requireProduct(normalized)
    }

    /**
     * Past de vaste identiteit van een product (GitHub-repo, acceptatie-URL) toe, zoals bijgehouden in het
     * repo-bestand `projects.yaml` in plaats van via het dashboard. Werkt alleen op een reeds bestaand product;
     * de aanroeper (de opstart-reconciler) beslist wat te doen als een slug nog niet is aangemaakt.
     */
    fun reconcileFixedFields(slug: String, targetRepositoryName: String?, acceptanceUrl: String?, adminUrl: String?): ProductView {
        val normalized = normalizeSlug(slug)
        val current = requireProduct(normalized)
        val repo = (targetRepositoryName?.trim()?.ifBlank { null } ?: current.targetRepositoryName)
        val acc = acceptanceUrl?.trim()?.ifBlank { null } ?: current.acceptanceUrl
        val admin = adminUrl?.trim()?.ifBlank { null } ?: current.adminUrl
        require(repo.matches(REPOSITORY)) { "Ongeldige repositorynaam" }
        validateUrl(acc)
        validateUrl(admin)

        jdbc.update(
            """update product_definition set target_repository_name = ?, acceptance_url = ?, admin_url = ?,
                updated_at = current_timestamp where slug = ?""".trimIndent(),
            repo, acc, admin, normalized,
        )
        return requireProduct(normalized)
    }

    private fun replaceIterationTimes(slug: String, times: List<String>) {
        jdbc.update("delete from product_iteration_time where product_slug = ?", slug)
        times.distinct().forEach {
            jdbc.update("insert into product_iteration_time(product_slug, time_of_day) values (?, ?)", slug, java.sql.Time.valueOf("$it:00"))
        }
    }

    private fun replaceRoadmapSchedule(slug: String, schedule: List<WeeklyScheduleView>) {
        jdbc.update("delete from product_roadmap_schedule where product_slug = ?", slug)
        schedule.distinct().forEach {
            jdbc.update(
                "insert into product_roadmap_schedule(product_slug, day_of_week, time_of_day) values (?, ?, ?)",
                slug,
                it.dayOfWeek,
                java.sql.Time.valueOf("${it.time}:00"),
            )
        }
    }

    fun changeStatus(slug: String, status: String): ProductView {
        require(status in STATUSES)
        val normalized = normalizeSlug(slug)
        if (jdbc.update("update product_definition set status = ?, updated_at = current_timestamp where slug = ?", status, normalized) == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Onbekend product")
        }
        return requireProduct(normalized)
    }

    /**
     * Zet de pending "ik wil overleg"-vlag op een product. Overschrijft nooit een reeds openstaande
     * vlag (de aanroeper, MeetingCatalog.requestMeeting, controleert dat vooraf) — hier alleen de
     * kolomupdate zelf, zodat product_definition-schrijven op één plek blijft.
     */
    fun setMeetingRequested(slug: String, topics: List<String>): ProductView {
        val normalized = normalizeSlug(slug)
        jdbc.update(
            "update product_definition set meeting_requested_at = current_timestamp, meeting_requested_topics = ? where slug = ?",
            mapper.writeValueAsString(topics),
            normalized,
        )
        return requireProduct(normalized)
    }

    /** Wist de pending overlegvlag, bijvoorbeeld zodra de bijbehorende onderwerpen in een nieuw overleg zijn opgepakt. */
    fun clearMeetingRequested(slug: String): ProductView {
        val normalized = normalizeSlug(slug)
        jdbc.update(
            "update product_definition set meeting_requested_at = null, meeting_requested_topics = null where slug = ?",
            normalized,
        )
        return requireProduct(normalized)
    }

    fun listRecords(slug: String, kind: String): List<ProductRecordView> {
        requireProduct(slug)
        val table = recordTable(kind)
        if (kind == "memory") {
            return memoryAt(slug, null)
        }
        val sourceColumn = if (kind == "research") ", source_url" else ""
        return jdbc.query(
            "select id, product_slug, title, content$sourceColumn, created_at from $table where product_slug = ? order by id",
            { row, _ -> mapRecord(row, kind == "research") }, normalizeSlug(slug),
        )
    }

    /**
     * Reconstrueert de actieve geheugenprojectie op [asOf]. Zonder peildatum is dit de actuele
     * projectie. Een opvolger of tombstone telt pas mee vanaf zijn eigen created_at.
     */
    fun memoryAt(slug: String, asOf: Instant?): List<ProductRecordView> {
        requireProduct(slug)
        val normalized = normalizeSlug(slug)
        val timeClause = if (asOf == null) {
            """and not exists (select 1 from product_memory successor where successor.supersedes_id = m.id)
                and not exists (select 1 from product_memory_retraction retraction where retraction.memory_id = m.id)"""
        } else {
            """and m.created_at <= ?
                and not exists (
                    select 1 from product_memory successor
                    where successor.supersedes_id = m.id and successor.created_at <= ?
                )
                and not exists (
                    select 1 from product_memory_retraction retraction
                    where retraction.memory_id = m.id and retraction.created_at <= ?
                )"""
        }
        val arguments = if (asOf == null) {
            arrayOf(normalized)
        } else {
            val timestamp = Timestamp.from(asOf)
            arrayOf(normalized, timestamp, timestamp, timestamp)
        }
        return jdbc.query(
            """select m.id, m.product_slug, m.title, m.content, m.created_at,
                m.supersedes_id, m.change_reason, m.created_by
                from product_memory m
                where m.product_slug = ?
                  $timeClause
                order by m.id""".trimIndent(),
            { row, _ -> mapMemoryRecord(row) },
            *arguments,
        )
    }

    /** Accepteert een ISO-instant of een lokale datum, waarbij een datum het einde van de productdag betekent. */
    fun memoryAt(slug: String, asOf: String): List<ProductRecordView> {
        val product = requireProduct(slug)
        val trimmed = asOf.trim()
        require(trimmed.isNotEmpty()) { "Peildatum mag niet leeg zijn" }
        val instant = runCatching { Instant.parse(trimmed) }.getOrElse {
            runCatching {
                LocalDate.parse(trimmed).plusDays(1).atStartOfDay(ZoneId.of(product.timezone)).toInstant().minusNanos(1)
            }.getOrElse { throw IllegalArgumentException("Ongeldige peildatum; gebruik YYYY-MM-DD of een ISO-8601-instant") }
        }
        return memoryAt(slug, instant)
    }

    /** Volledige read-only auditlijn; deze methode wordt nooit voor normale agentcontext gebruikt. */
    fun memoryHistory(slug: String): List<MemoryVersionView> {
        requireProduct(slug)
        data class StoredVersion(
            val record: ProductRecordView,
            val supersededById: Long?,
            val supersededAt: Instant?,
            val retractedAt: Instant?,
            val retirementReason: String?,
            val retiredBy: String?,
        )

        val stored = jdbc.query(
            """select m.id, m.product_slug, m.title, m.content, m.created_at,
                m.supersedes_id, m.change_reason, m.created_by,
                successor.id as superseded_by_id, successor.created_at as superseded_at,
                retraction.created_at as retracted_at,
                coalesce(retraction.reason, successor.change_reason) as retirement_reason,
                coalesce(retraction.created_by, successor.created_by) as retired_by
                from product_memory m
                left join product_memory successor on successor.supersedes_id = m.id
                left join product_memory_retraction retraction on retraction.memory_id = m.id
                where m.product_slug = ?
                order by m.created_at, m.id""".trimIndent(),
            { row, _ ->
                StoredVersion(
                    record = mapMemoryRecord(row),
                    supersededById = row.getLong("superseded_by_id").takeUnless { row.wasNull() },
                    supersededAt = row.getTimestamp("superseded_at")?.toInstant(),
                    retractedAt = row.getTimestamp("retracted_at")?.toInstant(),
                    retirementReason = row.getString("retirement_reason"),
                    retiredBy = row.getString("retired_by"),
                )
            },
            normalizeSlug(slug),
        )
        val byId = stored.associateBy { it.record.id }
        fun lineage(record: ProductRecordView): List<ProductRecordView> {
            val chain = mutableListOf<ProductRecordView>()
            val visited = mutableSetOf<Long>()
            var current: ProductRecordView? = record
            while (current != null) {
                check(visited.add(current.id)) { "Cyclische memoryversielijn voor '$slug'" }
                chain += current
                current = current.supersedesId?.let { byId[it]?.record }
            }
            return chain.asReversed()
        }

        return stored.map { version ->
            val chain = lineage(version.record)
            val retracted = version.retractedAt != null
            MemoryVersionView(
                id = version.record.id,
                productSlug = version.record.productSlug,
                rootMemoryId = chain.first().id,
                versionNumber = chain.size,
                title = version.record.title,
                content = version.record.content,
                status = when {
                    retracted -> "RETRACTED"
                    version.supersededById != null -> "SUPERSEDED"
                    else -> "ACTIVE"
                },
                createdAt = version.record.createdAt,
                effectiveUntil = version.retractedAt ?: version.supersededAt,
                supersedesId = version.record.supersedesId,
                supersededById = version.supersededById,
                changeReason = version.record.changeReason,
                createdBy = version.record.createdBy,
                retirementReason = version.retirementReason,
                retiredBy = version.retiredBy,
            )
        }.sortedWith(compareByDescending<MemoryVersionView> { it.createdAt }.thenByDescending { it.id })
    }

    fun addRecord(slug: String, kind: String, title: String, content: String, sourceUrl: String? = null): ProductRecordView {
        requireActive(slug)
        require(title.isNotBlank() && content.isNotBlank()) { "Titel en inhoud zijn verplicht" }
        validateUrl(sourceUrl)
        val table = recordTable(kind)
        if (kind == "research") {
            jdbc.update("insert into $table(product_slug, title, content, source_url) values (?, ?, ?, ?)", normalizeSlug(slug), title.trim(), content.trim(), sourceUrl)
        } else {
            jdbc.update("insert into $table(product_slug, title, content) values (?, ?, ?)", normalizeSlug(slug), title.trim(), content.trim())
        }
        val sourceColumn = if (kind == "research") ", source_url" else ""
        return jdbc.query(
            "select id, product_slug, title, content$sourceColumn, created_at from $table where product_slug = ? order by id desc",
            { row, _ -> mapRecord(row, kind == "research") }, normalizeSlug(slug),
        ).first()
    }

    /**
     * Past door een overlegagent voorgestelde geheugenwijzigingen atomair en append-only toe. Een
     * vervanging schrijft een nieuwe regel met een verwijzing naar de oude; een intrekking schrijft
     * uitsluitend een tombstone. [listRecords] laat beide oude vormen daarna volledig weg.
     */
    @Transactional
    fun applyMemoryMutations(mutations: List<MemoryMutation>, actor: String): List<MemoryChangeView> = mutations.map { mutation ->
        require(mutation.action in setOf("ADD", "REPLACE", "RETRACT")) { "Onbekende memoryactie '${mutation.action}'" }
        val slug = normalizeSlug(mutation.productSlug)
        requireProduct(slug)
        require(mutation.reason.isNotBlank() && mutation.reason.length <= 2_000) { "Een memorywijziging vereist een korte reden" }

        when (mutation.action) {
            "ADD" -> {
                val title = mutation.title?.trim().orEmpty()
                val content = mutation.content?.trim().orEmpty()
                require(title.isNotBlank() && content.isNotBlank()) { "Nieuwe memory vereist titel en inhoud" }
                val id = insertMemory(slug, title, content, null, mutation.reason.trim(), actor)
                MemoryChangeView("ADD", slug, id, title, mutation.reason.trim())
            }
            "REPLACE" -> {
                val target = requireActiveMemory(slug, mutation.targetMemoryId)
                val title = mutation.title?.trim().orEmpty()
                val content = mutation.content?.trim().orEmpty()
                require(title.isNotBlank() && content.isNotBlank()) { "Vervangende memory vereist titel en inhoud" }
                val id = insertMemory(slug, title, content, target.id, mutation.reason.trim(), actor)
                MemoryChangeView("REPLACE", slug, id, title, mutation.reason.trim())
            }
            else -> {
                val target = requireActiveMemory(slug, mutation.targetMemoryId)
                jdbc.update(
                    "insert into product_memory_retraction(memory_id, product_slug, reason, created_by) values (?, ?, ?, ?)",
                    target.id,
                    slug,
                    mutation.reason.trim(),
                    actor.take(120),
                )
                MemoryChangeView("RETRACT", slug, target.id, target.title, mutation.reason.trim())
            }
        }
    }

    fun activeMemoryForAllProducts(): List<ProductRecordView> = list().flatMap { listRecords(it.slug, "memory") }

    private fun requireActiveMemory(slug: String, id: Long?): ProductRecordView {
        require(id != null) { "Deze memoryactie vereist targetMemoryId" }
        return listRecords(slug, "memory").singleOrNull { it.id == id }
            ?: throw IllegalArgumentException("Memory-item $id is niet actief voor '$slug'")
    }

    private fun insertMemory(
        slug: String,
        title: String,
        content: String,
        supersedesId: Long?,
        reason: String,
        actor: String,
    ): Long {
        require(title.length <= 240) { "Memorytitel is te lang" }
        require(content.length <= 20_000) { "Memory-inhoud is te lang" }
        val keyHolder = GeneratedKeyHolder()
        jdbc.update(
            { connection ->
                connection.prepareStatement(
                    """insert into product_memory(product_slug, title, content, supersedes_id, change_reason, created_by)
                        values (?, ?, ?, ?, ?, ?)""".trimIndent(),
                    arrayOf("id"),
                ).apply {
                    setString(1, slug)
                    setString(2, title)
                    setString(3, content)
                    if (supersedesId == null) setNull(4, java.sql.Types.BIGINT) else setLong(4, supersedesId)
                    setString(5, reason)
                    setString(6, actor.take(120))
                }
            },
            keyHolder,
        )
        return keyHolder.key!!.toLong()
    }

    private fun validate(candidate: ProductConfiguration): ProductConfiguration {
        val slug = normalizeSlug(candidate.slug)
        require(candidate.name.isNotBlank() && candidate.mission.isNotBlank()) { "Naam en missie zijn verplicht" }
        require(candidate.softwareFactoryProjectKey.matches(SLUG)) { "Ongeldige Software Factory-projectsleutel" }
        require(candidate.targetRepositoryName.matches(REPOSITORY)) { "Ongeldige repositorynaam" }
        require(candidate.workspaceOwnership in OWNERSHIPS) { "Ongeldig workspace-eigenaarschap" }
        require(candidate.status in STATUSES) { "Ongeldige productstatus" }
        require(candidate.developmentMode in DEVELOPMENT_MODES) { "Ongeldige ontwikkelmodus" }
        require(candidate.maxStoriesPerCycle in 1..20) { "Maximaal aantal stories per cyclus moet tussen 1 en 20 liggen" }
        require(candidate.wipLimit in 1..20) { "WIP-limiet moet tussen 1 en 20 liggen" }
        require(candidate.dailyBudgetCents >= 0 && candidate.monthlyBudgetCents >= 0) { "AI-budgetten mogen niet negatief zijn" }
        require(candidate.timezone.isNotBlank()) { "Tijdzone is verplicht" }
        require(candidate.iterationTimes.isNotEmpty()) { "Minimaal één cyclustijd is verplicht" }
        candidate.iterationTimes.forEach { require(it.matches(TIME_OF_DAY)) { "Ongeldige cyclustijd '$it', gebruik HH:mm" } }
        validateRoadmapSchedule(candidate.roadmapSchedule)
        require(AiCatalog.isValid(candidate.aiProvider, candidate.aiModel)) {
            "Onbekende combinatie van AI-provider '${candidate.aiProvider}' en model '${candidate.aiModel}'"
        }
        validateUrl(candidate.liveUrl)
        validateUrl(candidate.previewUrlPattern?.replace("{number}", "1"))
        validateUrl(candidate.acceptanceUrl)
        validateUrl(candidate.adminUrl)
        val paths = candidate.allowedWritePaths.map(::normalizeRelativePath).distinct().sorted()
        if (candidate.workspaceOwnership == "product-factory") require(paths.isNotEmpty()) { "Minimaal één schrijfpad is verplicht" }
        return candidate.copy(slug = slug, allowedWritePaths = paths)
    }

    private fun mapProduct(row: ResultSet, ignored: Int): ProductView = ProductView(
        id = row.getString("id"),
        slug = row.getString("slug"),
        name = row.getString("name"),
        mission = row.getString("mission"),
        description = row.getString("description"),
        guardrails = row.getString("guardrails"),
        softwareFactoryProjectKey = row.getString("software_factory_project_key"),
        targetRepositoryName = row.getString("target_repository_name"),
        workspaceDirectory = row.getString("workspace_directory"),
        allowedWritePaths = jdbc.queryForList(
            "select relative_path from product_allowed_write_path where product_slug = ? order by relative_path",
            String::class.java,
            row.getString("slug"),
        ),
        workspaceOwnership = row.getString("workspace_ownership"),
        liveUrl = row.getString("live_url"),
        previewUrlPattern = row.getString("preview_url_pattern"),
        acceptanceUrl = row.getString("acceptance_url"),
        adminUrl = row.getString("admin_url"),
        status = row.getString("status"),
        developmentMode = row.getString("development_mode"),
        iterationTimes = jdbc.queryForList(
            "select time_of_day from product_iteration_time where product_slug = ? order by time_of_day",
            java.sql.Time::class.java,
            row.getString("slug"),
        ).map { it.toLocalTime().toString().take(5) },
        roadmapSchedule = jdbc.query(
            """select day_of_week, time_of_day from product_roadmap_schedule where product_slug = ?
                order by case day_of_week
                    when 'MONDAY' then 1 when 'TUESDAY' then 2 when 'WEDNESDAY' then 3
                    when 'THURSDAY' then 4 when 'FRIDAY' then 5 when 'SATURDAY' then 6 else 7 end,
                    time_of_day""".trimIndent(),
            { scheduleRow, _ ->
                WeeklyScheduleView(
                    dayOfWeek = scheduleRow.getString("day_of_week"),
                    time = scheduleRow.getTime("time_of_day").toLocalTime().toString().take(5),
                )
            },
            row.getString("slug"),
        ),
        timezone = row.getString("timezone"),
        maxStoriesPerCycle = row.getInt("max_stories_per_cycle"),
        wipLimit = row.getInt("wip_limit"),
        aiProvider = row.getString("ai_provider"),
        aiModel = row.getString("ai_model"),
        dailyBudgetCents = row.getInt("daily_budget_cents"),
        monthlyBudgetCents = row.getInt("monthly_budget_cents"),
        escalationPolicy = row.getString("escalation_policy"),
        privacyRules = row.getString("privacy_rules"),
        accessibilityRules = row.getString("accessibility_rules"),
        qualityRules = row.getString("quality_rules"),
        createdAt = row.getTimestamp("created_at").toInstant(),
        updatedAt = row.getTimestamp("updated_at").toInstant(),
        meetingRequestedAt = row.getTimestamp("meeting_requested_at")?.toInstant(),
        meetingRequestedTopics = row.getString("meeting_requested_topics")?.let { json ->
            runCatching { mapper.readValue<List<String>>(json) }.getOrDefault(emptyList())
        } ?: emptyList(),
    )

    private fun mapRecord(row: ResultSet, hasSource: Boolean): ProductRecordView {
        val createdAtIndex = if (hasSource) 6 else 5
        return ProductRecordView(
            id = row.getLong(1),
            productSlug = row.getString(2),
            title = row.getString(3),
            content = row.getString(4),
            sourceUrl = if (hasSource) row.getString(5) else null,
            createdAt = row.getTimestamp(createdAtIndex).toInstant(),
        )
    }

    private fun mapMemoryRecord(row: ResultSet) = ProductRecordView(
        id = row.getLong("id"),
        productSlug = row.getString("product_slug"),
        title = row.getString("title"),
        content = row.getString("content"),
        createdAt = row.getTimestamp("created_at").toInstant(),
        supersedesId = row.getLong("supersedes_id").takeUnless { row.wasNull() },
        changeReason = row.getString("change_reason"),
        createdBy = row.getString("created_by"),
    )

    private fun recordTable(kind: String) = when (kind) {
        "research" -> "product_research"
        "memory" -> "product_memory"
        "decision" -> "product_decision"
        else -> throw IllegalArgumentException("Onbekend productrecordtype")
    }

    private fun normalizeSlug(value: String): String = value.trim().lowercase().also {
        require(it.matches(SLUG)) { "Ongeldige productslug" }
    }

    private fun normalizeRelativePath(value: String): String {
        val normalized = value.trim().replace('\\', '/').trim('/')
        require(normalized.isNotBlank() && normalized.length <= 240) { "Ongeldig relatief pad" }
        require(!normalized.startsWith(".") && normalized.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "Ongeldig relatief pad"
        }
        require(normalized.matches(Regex("[A-Za-z0-9._/-]+"))) { "Ongeldig relatief pad" }
        return normalized
    }

    private fun validateUrl(value: String?) {
        if (value.isNullOrBlank()) return
        val uri = runCatching { URI(value) }.getOrElse { throw IllegalArgumentException("Ongeldige URL") }
        require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) { "Ongeldige URL" }
    }

    private fun validateRoadmapSchedule(schedule: List<WeeklyScheduleView>) {
        schedule.forEach {
            require(it.dayOfWeek in DAYS_OF_WEEK) { "Ongeldige roadmap-weekdag '${it.dayOfWeek}'" }
            require(it.time.matches(TIME_OF_DAY)) { "Ongeldige roadmap-tijd '${it.time}', gebruik HH:mm" }
        }
        require(schedule.distinct().size == schedule.size) { "Dubbele roadmap-planning is niet toegestaan" }
    }

    companion object {
        private val SLUG = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
        private val REPOSITORY = Regex("[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)?")
        private val STATUSES = setOf("draft", "active", "paused", "archived")
        private val DEVELOPMENT_MODES = setOf("manual", "autonomous", "observe-only")
        private val OWNERSHIPS = setOf("owner", "product-factory")
        private val DAYS_OF_WEEK = setOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
        private val TIME_OF_DAY = Regex("([01]\\d|2[0-3]):[0-5]\\d")
        private const val PRODUCT_SELECT = """select p.* from product_definition p"""
    }
}
