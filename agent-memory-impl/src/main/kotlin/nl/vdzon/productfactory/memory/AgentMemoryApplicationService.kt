package nl.vdzon.productfactory.memory

import nl.vdzon.productfactory.api.memory.*
import nl.vdzon.productfactory.api.product.MeetingStatus
import nl.vdzon.productfactory.api.product.ProductQueryService
import nl.vdzon.productfactory.api.product.ProductStatus
import nl.vdzon.productfactory.api.shared.*
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.sql.ResultSet
import java.time.Clock
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

@Service
@Transactional
class AgentMemoryApplicationService(
    private val jdbc: JdbcTemplate,
    private val products: ProductQueryService,
    private val clock: Clock,
) : AgentMemoryService, AgentMemoryQueryService {

    override fun addAgentMemory(command: AddAgentMemoryCommand): MemoryItemId {
        validateWriteContext(command.context, allowMeetingAgent = false)
        val fingerprint = fingerprint(command)
        replay(command.idempotencyKey, "ADD", fingerprint)?.firstOrNull()?.let { return MemoryItemId(it) }
        requireActiveRole(command.context.productId, command.context.agentRole)
        val title = required(command.title, "Titel", MAX_TITLE)
        val content = required(command.content, "Geheugeninhoud", Int.MAX_VALUE)
        val reason = required(command.reason, "Wijzigingsreden", MAX_REASON)
        requireBudget(command.context.productId, command.context.agentRole, null, content)
        val itemId = MemoryItemId(UUID.randomUUID().toString())
        val versionId = MemoryVersionId(UUID.randomUUID().toString())
        val now = clock.instant()
        jdbc.update(
            "INSERT INTO pf_agent_memory_item(memory_item_id,product_id,role_key,created_at) VALUES (?,?,?,?)",
            itemId.value, command.context.productId.value, command.context.agentRole.value, now,
        )
        insertVersion(versionId, itemId, null, title, content, reason, command.context, now)
        remember(command.idempotencyKey, "ADD", itemId.value, fingerprint, listOf(itemId.value, versionId.value), command.context.actor, now)
        return itemId
    }

    override fun replaceAgentMemory(command: ReplaceAgentMemoryCommand): MemoryVersionId {
        validateWriteContext(command.context, allowMeetingAgent = false)
        val fingerprint = fingerprint(command)
        replay(command.idempotencyKey, "REPLACE", fingerprint)?.lastOrNull()?.let { return MemoryVersionId(it) }
        val item = requireCurrentItem(command.context.productId, command.context.agentRole, command.itemId)
        if (item.activeVersionId != command.expectedVersionId) throw VersionConflict("Het geheugenitem is intussen gewijzigd.")
        val title = required(command.title, "Titel", MAX_TITLE)
        val content = required(command.content, "Geheugeninhoud", Int.MAX_VALUE)
        val reason = required(command.reason, "Wijzigingsreden", MAX_REASON)
        requireBudget(command.context.productId, command.context.agentRole, command.itemId, content)
        val versionId = MemoryVersionId(UUID.randomUUID().toString())
        val now = clock.instant()
        try {
            insertVersion(versionId, command.itemId, command.expectedVersionId, title, content, reason, command.context, now)
        } catch (_: DataIntegrityViolationException) {
            throw VersionConflict("Het geheugenitem is intussen gewijzigd.")
        }
        remember(command.idempotencyKey, "REPLACE", command.itemId.value, fingerprint, listOf(versionId.value), command.context.actor, now)
        return versionId
    }

    override fun retractAgentMemory(command: RetractAgentMemoryCommand): MemoryVersionId {
        validateWriteContext(command.context, allowMeetingAgent = false)
        val fingerprint = fingerprint(command)
        replay(command.idempotencyKey, "RETRACT", fingerprint)?.lastOrNull()?.let { return MemoryVersionId(it) }
        val item = requireCurrentItem(command.context.productId, command.context.agentRole, command.itemId)
        if (item.activeVersionId != command.expectedVersionId) throw VersionConflict("Het geheugenitem is intussen gewijzigd.")
        val reason = required(command.reason, "Intrekkingsreden", MAX_REASON)
        val retractionId = MemoryVersionId(UUID.randomUUID().toString())
        val now = clock.instant()
        try {
            insertRetraction(retractionId, command.itemId, command.expectedVersionId, reason, command.context, now)
        } catch (_: DataIntegrityViolationException) {
            throw VersionConflict("Het geheugenitem is intussen gewijzigd of al ingetrokken.")
        }
        remember(command.idempotencyKey, "RETRACT", command.itemId.value, fingerprint, listOf(retractionId.value), command.context.actor, now)
        return retractionId
    }

    override fun applyMeetingMemoryChanges(command: ApplyMeetingMemoryChangesCommand): MeetingMemoryChangeResult {
        val context = command.context
        if (context.trustedActor.type != ActorType.MEETING_MINUTES_AGENT) {
            throw InvalidCommand("Alleen vertrouwde notulenafhandeling mag een meetingbatch toepassen.")
        }
        val meeting = products.getMeeting(context.meetingId)
        if (meeting.productId != context.productId || meeting.status != MeetingStatus.CLOSED) {
            throw InvalidCommand("De meetingbatch vereist een gesloten overleg van hetzelfde product.")
        }
        if (command.changes.isEmpty()) throw InvalidCommand("Een meetingbatch bevat minimaal één wijziging.")
        if (command.changes.size > MAX_BATCH) throw InvalidCommand("De meetingbatch bevat te veel wijzigingen.")
        val fingerprint = fingerprint(command)
        replay(command.idempotencyKey, "MEETING_BATCH", fingerprint)?.let { ids ->
            return MeetingMemoryChangeResult(ids.map(::MemoryVersionId))
        }

        val activeRoles = getAgentRoleCatalog(context.productId).map { it.key }.toSet()
        val projected = activeItems(context.productId, null, clock.instant()).associateBy { it.id }.toMutableMap()
        command.changes.forEach { change ->
            if (change.agentRole !in activeRoles) throw InvalidCommand("Doelrol ${change.agentRole.value} is niet actief.")
            when (change.type) {
                MemoryChangeType.ADD -> {
                    required(change.title, "Titel", MAX_TITLE)
                    required(change.content, "Geheugeninhoud", Int.MAX_VALUE)
                    required(change.reason, "Wijzigingsreden", MAX_REASON)
                }
                MemoryChangeType.REPLACE, MemoryChangeType.RETRACT -> {
                    val itemId = change.itemId ?: throw InvalidCommand("Doelitem ontbreekt.")
                    val expected = change.expectedVersionId ?: throw InvalidCommand("Verwachte versie ontbreekt.")
                    val item = projected[itemId] ?: throw VersionConflict("Het doelitem is niet actueel.")
                    if (item.productId != context.productId || item.agentRole != change.agentRole || item.activeVersionId != expected) {
                        throw VersionConflict("Het doelitem of de verwachte versie is intussen gewijzigd.")
                    }
                    required(change.reason, "Wijzigingsreden", MAX_REASON)
                    if (change.type == MemoryChangeType.REPLACE) {
                        required(change.title, "Titel", MAX_TITLE)
                        required(change.content, "Geheugeninhoud", Int.MAX_VALUE)
                    }
                }
            }
        }
        validateProjectedBudgets(context.productId, command.changes, projected)

        val now = clock.instant()
        val writeContext = MemoryWriteContext(
            productId = context.productId,
            agentRole = AgentRoleKey("meeting-batch-placeholder"),
            actor = context.trustedActor,
            aiTaskId = context.aiTaskId,
            sourceMeetingId = context.meetingId,
        )
        val resultIds = mutableListOf<MemoryVersionId>()
        command.changes.forEach { change ->
            val scoped = writeContext.copy(agentRole = change.agentRole)
            when (change.type) {
                MemoryChangeType.ADD -> {
                    val itemId = MemoryItemId(UUID.randomUUID().toString())
                    val versionId = MemoryVersionId(UUID.randomUUID().toString())
                    jdbc.update(
                        "INSERT INTO pf_agent_memory_item(memory_item_id,product_id,role_key,created_at) VALUES (?,?,?,?)",
                        itemId.value, context.productId.value, change.agentRole.value, now,
                    )
                    insertVersion(versionId, itemId, null, change.title!!.trim(), change.content!!.trim(), change.reason.trim(), scoped, now)
                    resultIds += versionId
                }
                MemoryChangeType.REPLACE -> {
                    val versionId = MemoryVersionId(UUID.randomUUID().toString())
                    insertVersion(versionId, change.itemId!!, change.expectedVersionId, change.title!!.trim(), change.content!!.trim(), change.reason.trim(), scoped, now)
                    resultIds += versionId
                }
                MemoryChangeType.RETRACT -> {
                    val retractionId = MemoryVersionId(UUID.randomUUID().toString())
                    insertRetraction(retractionId, change.itemId!!, change.expectedVersionId!!, change.reason.trim(), scoped, now)
                    resultIds += retractionId
                }
            }
        }
        remember(command.idempotencyKey, "MEETING_BATCH", context.meetingId.value, fingerprint, resultIds.map { it.value }, context.trustedActor, now)
        return MeetingMemoryChangeResult(resultIds)
    }

    @Transactional(readOnly = false)
    override fun getActiveMemory(context: AgentExecutionContext): List<AgentMemoryItemDetails> {
        requireActiveRole(context.productId, context.agentRole)
        val items = activeItems(context.productId, context.agentRole, clock.instant())
        recordReads(items, context.processSessionId, null, context.aiTaskId)
        return items
    }

    @Transactional(readOnly = true)
    override fun getMemoryAt(productId: ProductId, agentRole: AgentRoleKey, validAt: Instant): List<AgentMemoryItemDetails> {
        requireActiveRole(productId, agentRole)
        return activeItems(productId, agentRole, validAt)
    }

    @Transactional(readOnly = true)
    override fun getMemoryHistory(productId: ProductId, agentRole: AgentRoleKey, memoryItemId: MemoryItemId): List<AgentMemoryVersionDetails> {
        val itemScope = jdbc.query(
            "SELECT product_id,role_key FROM pf_agent_memory_item WHERE memory_item_id=?",
            { rs, _ -> ProductId(rs.getString(1)) to AgentRoleKey(rs.getString(2)) }, memoryItemId.value,
        ).singleOrNull() ?: throw AggregateNotFound("Geheugenitem bestaat niet.")
        if (itemScope.first != productId || itemScope.second != agentRole) throw AggregateNotFound("Geheugenitem bestaat niet binnen deze rol.")
        val rows = versionRows(memoryItemId)
        val retraction = retraction(memoryItemId)
        val successorByPredecessor = rows.filter { it.predecessorId != null }.associateBy { it.predecessorId!! }
        return rows.sortedBy { it.createdAt }.mapIndexed { index, row ->
            val successor = successorByPredecessor[row.id]
            val validUntil = listOfNotNull(successor?.createdAt, retraction?.createdAt).minOrNull()
            val status = when {
                retraction != null && successor == null -> MemoryVersionStatus.RETRACTED
                successor != null -> MemoryVersionStatus.SUPERSEDED
                else -> MemoryVersionStatus.ACTIVE
            }
            AgentMemoryVersionDetails(
                id = row.id, itemId = memoryItemId, productId = productId, agentRole = agentRole,
                versionNumber = index + 1, status = status, title = row.title, content = row.content,
                predecessorId = row.predecessorId, successorId = successor?.id, actor = row.actor,
                reason = row.reason, validFrom = row.createdAt, validUntil = validUntil,
                sourceMeetingId = row.sourceMeetingId,
                retractionActor = retraction?.actor.takeIf { successor == null },
                retractionReason = retraction?.reason.takeIf { successor == null },
                readBy = reads(row.id),
            )
        }
    }

    @Transactional(readOnly = true)
    override fun getAgentRoleCatalog(productId: ProductId): List<AgentRoleDefinitionDetails> {
        val product = products.getProduct(productId)
        if (product.status != ProductStatus.ACTIVE) return emptyList()
        return jdbc.query(
            "SELECT role_key,display_name,capability,implementation_variant,purpose,responsibilities_text,boundaries_text,active FROM pf_agent_role_definition WHERE active=TRUE ORDER BY capability,role_key",
        ) { rs, _ -> role(rs) }
    }

    override fun getMemoryBudget(productId: ProductId, agentRole: AgentRoleKey): AgentMemoryBudgetDetails {
        requireActiveRole(productId, agentRole)
        ensureBudget(productId, agentRole)
        val limits = jdbc.queryForMap(
            "SELECT maximum_active_items,maximum_item_characters,maximum_total_characters FROM pf_agent_memory_budget WHERE product_id=? AND role_key=?",
            productId.value, agentRole.value,
        )
        val items = activeItems(productId, agentRole, clock.instant())
        return AgentMemoryBudgetDetails(
            productId, agentRole,
            (limits["maximum_active_items"] as Number).toInt(),
            (limits["maximum_item_characters"] as Number).toInt(),
            (limits["maximum_total_characters"] as Number).toInt(),
            items.size, items.sumOf { it.content.length },
        )
    }

    override fun getMeetingMemorySnapshot(context: MeetingExecutionContext): MeetingMemorySnapshot {
        if (context.trustedActor.type !in setOf(ActorType.SYSTEM, ActorType.FACTORY, ActorType.MEETING_MINUTES_AGENT)) {
            throw InvalidCommand("Ongeldige vertrouwde overlegcontext.")
        }
        val meeting = products.getMeeting(context.meetingId)
        if (meeting.productId != context.productId || meeting.status != MeetingStatus.OPEN) {
            throw InvalidCommand("Een geheugensnapshot vereist een open overleg van hetzelfde product.")
        }
        val roles = getAgentRoleCatalog(context.productId)
        val items = activeItems(context.productId, null, clock.instant())
        recordReads(items, null, context.meetingId, context.aiTaskId)
        return MeetingMemorySnapshot(context.productId, context.meetingId, roles, items.groupBy { it.agentRole })
    }

    fun registerTrustedRoles() {
        TRUSTED_ROLES.forEach { definition ->
            val existing = jdbc.queryForObject("SELECT COUNT(*) FROM pf_agent_role_definition WHERE role_key=?", Long::class.java, definition.key.value) ?: 0
            if (existing == 0L) {
                jdbc.update(
                    "INSERT INTO pf_agent_role_definition(role_key,display_name,capability,implementation_variant,purpose,responsibilities_text,boundaries_text,active) VALUES (?,?,?,?,?,?,?,?)",
                    definition.key.value, definition.displayName, definition.capability, definition.implementationVariant,
                    definition.purpose, definition.responsibilities.joinToString("\n"), definition.boundaries.joinToString("\n"), definition.active,
                )
            }
        }
    }

    fun deleteAllOwnedData() {
        jdbc.update("DELETE FROM pf_agent_memory_read_audit")
        jdbc.update("DELETE FROM pf_agent_memory_retraction")
        jdbc.update("DELETE FROM pf_agent_memory_version")
        jdbc.update("DELETE FROM pf_agent_memory_item")
        jdbc.update("DELETE FROM pf_agent_memory_budget")
        jdbc.update("DELETE FROM pf_agent_memory_command")
    }

    private fun validateProjectedBudgets(productId: ProductId, changes: List<MeetingMemoryChange>, current: MutableMap<MemoryItemId, AgentMemoryItemDetails>) {
        val projectedByRole = current.values.groupBy { it.agentRole }.mapValues { (_, items) -> items.associateBy { it.id }.toMutableMap() }.toMutableMap()
        changes.forEachIndexed { index, change ->
            val roleItems = projectedByRole.getOrPut(change.agentRole) { mutableMapOf() }
            when (change.type) {
                MemoryChangeType.ADD -> roleItems[MemoryItemId("projected-$index")] = projectedItem(productId, change, index)
                MemoryChangeType.REPLACE -> {
                    val itemId = change.itemId ?: throw InvalidCommand("Doelitem ontbreekt.")
                    roleItems[itemId] = projectedItem(productId, change, index).copy(id = itemId)
                }
                MemoryChangeType.RETRACT -> roleItems.remove(change.itemId!!)
            }
        }
        projectedByRole.forEach { (role, items) -> validateBudgetValues(productId, role, items.values.toList()) }
    }

    private fun projectedItem(productId: ProductId, change: MeetingMemoryChange, index: Int) = AgentMemoryItemDetails(
        MemoryItemId("projected-$index"), productId, change.agentRole, MemoryVersionId("projected-$index"),
        change.title.orEmpty(), change.content.orEmpty(), clock.instant(), ActorReference(ActorType.MEETING_MINUTES_AGENT, "projection"), change.reason,
    )

    private fun requireBudget(productId: ProductId, role: AgentRoleKey, replacing: MemoryItemId?, content: String) {
        val projected = activeItems(productId, role, clock.instant()).filterNot { it.id == replacing }.toMutableList()
        projected += AgentMemoryItemDetails(
            replacing ?: MemoryItemId("projected"), productId, role, MemoryVersionId("projected"), "", content,
            clock.instant(), ActorReference(ActorType.SYSTEM, "budget-check"), "budget-check",
        )
        validateBudgetValues(productId, role, projected)
    }

    private fun validateBudgetValues(productId: ProductId, role: AgentRoleKey, items: List<AgentMemoryItemDetails>) {
        ensureBudget(productId, role)
        val limits = jdbc.queryForMap(
            "SELECT maximum_active_items,maximum_item_characters,maximum_total_characters FROM pf_agent_memory_budget WHERE product_id=? AND role_key=?",
            productId.value, role.value,
        )
        val maxItems = (limits["maximum_active_items"] as Number).toInt()
        val maxItem = (limits["maximum_item_characters"] as Number).toInt()
        val maxTotal = (limits["maximum_total_characters"] as Number).toInt()
        if (items.size > maxItems) throw InvalidCommand("Geheugenlimiet voor ${role.value}: maximaal $maxItems actieve items.")
        if (items.any { it.content.length > maxItem }) throw InvalidCommand("Geheugenlimiet voor ${role.value}: één item mag maximaal $maxItem tekens bevatten.")
        if (items.sumOf { it.content.length } > maxTotal) throw InvalidCommand("Geheugenlimiet voor ${role.value}: maximaal $maxTotal actieve tekens.")
    }

    private fun requireCurrentItem(productId: ProductId, role: AgentRoleKey, itemId: MemoryItemId): AgentMemoryItemDetails {
        requireActiveRole(productId, role)
        return activeItems(productId, role, clock.instant()).singleOrNull { it.id == itemId }
            ?: throw VersionConflict("Het geheugenitem is niet meer actueel.")
    }

    private fun activeItems(productId: ProductId, role: AgentRoleKey?, validAt: Instant): List<AgentMemoryItemDetails> {
        val params = mutableListOf<Any>(productId.value)
        val roleClause = if (role == null) "" else {
            params += role.value
            " AND i.role_key=?"
        }
        val items = jdbc.query(
            "SELECT i.memory_item_id,i.product_id,i.role_key FROM pf_agent_memory_item i WHERE i.product_id=?$roleClause ORDER BY i.created_at",
            { rs, _ -> Triple(MemoryItemId(rs.getString(1)), ProductId(rs.getString(2)), AgentRoleKey(rs.getString(3))) },
            *params.toTypedArray(),
        )
        return items.mapNotNull { (itemId, scopedProduct, scopedRole) ->
            val versions = versionRows(itemId).filter { !it.createdAt.isAfter(validAt) }
            val latest = versions.maxWithOrNull(compareBy<VersionRow> { it.createdAt }.thenBy { it.id.value }) ?: return@mapNotNull null
            val retracted = retraction(itemId)?.createdAt?.let { !it.isAfter(validAt) } ?: false
            if (retracted) return@mapNotNull null
            AgentMemoryItemDetails(
                itemId, scopedProduct, scopedRole, latest.id, latest.title, latest.content, latest.createdAt,
                latest.actor, latest.reason, latest.sourceMeetingId, reads(latest.id),
            )
        }
    }

    private fun versionRows(itemId: MemoryItemId): List<VersionRow> = jdbc.query(
        "SELECT memory_version_id,supersedes_id,title,content,created_at,actor_type,actor_id,change_reason,source_meeting_id FROM pf_agent_memory_version WHERE memory_item_id=? ORDER BY created_at,memory_version_id",
        { rs, _ ->
            VersionRow(
                MemoryVersionId(rs.getString("memory_version_id")), rs.getString("supersedes_id")?.let(::MemoryVersionId),
                rs.getString("title"), rs.getString("content"), rs.getTimestamp("created_at").toInstant(),
                ActorReference(ActorType.valueOf(rs.getString("actor_type")), rs.getString("actor_id")),
                rs.getString("change_reason"), rs.getString("source_meeting_id")?.let(::MeetingId),
            )
        }, itemId.value,
    )

    private fun retraction(itemId: MemoryItemId): RetractionRow? = jdbc.query(
        "SELECT created_at,actor_type,actor_id,reason FROM pf_agent_memory_retraction WHERE memory_item_id=?",
        { rs, _ -> RetractionRow(rs.getTimestamp(1).toInstant(), ActorReference(ActorType.valueOf(rs.getString(2)), rs.getString(3)), rs.getString(4)) }, itemId.value,
    ).singleOrNull()

    private fun reads(versionId: MemoryVersionId): List<MemoryReadReference> = jdbc.query(
        "SELECT process_session_id,meeting_id,ai_task_id,read_at FROM pf_agent_memory_read_audit WHERE memory_version_id=? ORDER BY read_at",
        { rs, _ -> MemoryReadReference(rs.getString(1)?.let(::ProcessSessionId), rs.getString(2)?.let(::MeetingId), AiTaskId(rs.getString(3)), rs.getTimestamp(4).toInstant()) },
        versionId.value,
    )

    private fun recordReads(items: List<AgentMemoryItemDetails>, processSessionId: ProcessSessionId?, meetingId: MeetingId?, aiTaskId: AiTaskId) {
        val now = clock.instant()
        items.forEach { item ->
            try {
                jdbc.update(
                    "INSERT INTO pf_agent_memory_read_audit(read_id,memory_version_id,process_session_id,meeting_id,ai_task_id,read_at) VALUES (?,?,?,?,?,?)",
                    UUID.randomUUID().toString(), item.activeVersionId.value, processSessionId?.value, meetingId?.value, aiTaskId.value, now,
                )
            } catch (_: DuplicateKeyException) {
                // Dezelfde bevroren AI-taak leest bij een retry exact dezelfde versie.
            }
        }
    }

    private fun insertVersion(id: MemoryVersionId, itemId: MemoryItemId, predecessor: MemoryVersionId?, title: String, content: String, reason: String, context: MemoryWriteContext, now: Instant) {
        jdbc.update(
            "INSERT INTO pf_agent_memory_version(memory_version_id,memory_item_id,supersedes_id,title,content,created_at,actor_type,actor_id,change_reason,source_meeting_id,process_session_id,ai_task_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            id.value, itemId.value, predecessor?.value, title, content, now, context.actor.type.name, context.actor.id,
            reason, context.sourceMeetingId?.value, context.processSessionId?.value, context.aiTaskId?.value,
        )
    }

    private fun insertRetraction(id: MemoryVersionId, itemId: MemoryItemId, expected: MemoryVersionId, reason: String, context: MemoryWriteContext, now: Instant) {
        jdbc.update(
            "INSERT INTO pf_agent_memory_retraction(retraction_id,memory_item_id,expected_version_id,created_at,actor_type,actor_id,reason,source_meeting_id,process_session_id,ai_task_id) VALUES (?,?,?,?,?,?,?,?,?,?)",
            id.value, itemId.value, expected.value, now, context.actor.type.name, context.actor.id, reason,
            context.sourceMeetingId?.value, context.processSessionId?.value, context.aiTaskId?.value,
        )
    }

    private fun requireActiveRole(productId: ProductId, role: AgentRoleKey) {
        products.getProduct(productId)
        if (getAgentRoleCatalog(productId).none { it.key == role }) throw InvalidCommand("Agentrol ${role.value} is niet actief voor dit product.")
        ensureBudget(productId, role)
    }

    private fun ensureBudget(productId: ProductId, role: AgentRoleKey) {
        val exists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pf_agent_memory_budget WHERE product_id=? AND role_key=?", Long::class.java, productId.value, role.value,
        ) ?: 0
        if (exists == 0L) {
            try {
                jdbc.update(
                    "INSERT INTO pf_agent_memory_budget(product_id,role_key,maximum_active_items,maximum_item_characters,maximum_total_characters) VALUES (?,?,?,?,?)",
                    productId.value, role.value, DEFAULT_MAX_ITEMS, DEFAULT_MAX_ITEM_CHARACTERS, DEFAULT_MAX_TOTAL_CHARACTERS,
                )
            } catch (_: DuplicateKeyException) {
                // Een parallelle eerste read heeft dezelfde vaste standaard al geregistreerd.
            }
        }
    }

    private fun validateWriteContext(context: MemoryWriteContext, allowMeetingAgent: Boolean) {
        if (context.actor.id.isBlank()) throw InvalidCommand("Actor ontbreekt.")
        when (context.actor.type) {
            ActorType.STAKEHOLDER, ActorType.SYSTEM -> Unit
            ActorType.PROCESS -> if (context.processSessionId == null || context.aiTaskId == null) {
                throw InvalidCommand("Een agentwijziging vereist een vertrouwde proces- en taakcontext.")
            }
            ActorType.MEETING_MINUTES_AGENT -> if (!allowMeetingAgent) throw InvalidCommand("Notulenwijzigingen gaan uitsluitend als atomische meetingbatch.")
            else -> throw InvalidCommand("Deze actor mag geen agentgeheugen wijzigen.")
        }
    }

    private fun role(rs: ResultSet) = AgentRoleDefinitionDetails(
        AgentRoleKey(rs.getString("role_key")), rs.getString("display_name"), rs.getString("capability"),
        rs.getString("implementation_variant"), rs.getString("purpose"), lines(rs.getString("responsibilities_text")),
        lines(rs.getString("boundaries_text")), rs.getBoolean("active"),
    )

    private fun lines(value: String) = value.lines().filter { it.isNotBlank() }
    private fun required(value: String?, label: String, maximum: Int): String {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) throw InvalidCommand("$label is verplicht.")
        if (normalized.length > maximum) throw InvalidCommand("$label is te lang.")
        return normalized
    }

    private fun fingerprint(command: Any) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(command.toString().toByteArray()))

    private fun replay(key: String, type: String, fingerprint: String): List<String>? {
        if (key.isBlank() || key.length > 200) throw InvalidCommand("Ongeldige idempotentiesleutel.")
        val row = jdbc.query(
            "SELECT command_type,request_fingerprint,result_ids FROM pf_agent_memory_command WHERE idempotency_key=?",
            { rs, _ -> Triple(rs.getString(1), rs.getString(2), rs.getString(3)) }, key,
        ).singleOrNull() ?: return null
        if (row.first != type || row.second != fingerprint) throw IdempotencyConflict("Idempotentiesleutel is al voor andere geheugeninput gebruikt.")
        return row.third?.split(',')?.filter { it.isNotBlank() }.orEmpty()
    }

    private fun remember(key: String, type: String, aggregateId: String, fingerprint: String, resultIds: List<String>, actor: ActorReference, now: Instant) {
        jdbc.update(
            "INSERT INTO pf_agent_memory_command(idempotency_key,command_type,aggregate_id,request_fingerprint,result_ids,actor_type,actor_id,applied_at) VALUES (?,?,?,?,?,?,?,?)",
            key, type, aggregateId, fingerprint, resultIds.joinToString(","), actor.type.name, actor.id, now,
        )
    }

    private data class VersionRow(
        val id: MemoryVersionId,
        val predecessorId: MemoryVersionId?,
        val title: String,
        val content: String,
        val createdAt: Instant,
        val actor: ActorReference,
        val reason: String,
        val sourceMeetingId: MeetingId?,
    )
    private data class RetractionRow(val createdAt: Instant, val actor: ActorReference, val reason: String)

    companion object {
        private const val DEFAULT_MAX_ITEMS = 40
        private const val DEFAULT_MAX_ITEM_CHARACTERS = 4_000
        private const val DEFAULT_MAX_TOTAL_CHARACTERS = 32_000
        private const val MAX_TITLE = 300
        private const val MAX_REASON = 2_000
        private const val MAX_BATCH = 100

        val TRUSTED_ROLES = listOf(
            AgentRoleDefinitionDetails(
                AgentRoleKey("MEETING_AGENT"), "Meeting Agent", "product-meetings", "meeting-v1",
                "Begeleidt het gesprek en antwoordt herkenbaar vanuit actieve rolperspectieven.",
                listOf("Verheldert vragen", "Combineert gecontroleerde rolperspectieven"),
                listOf("Start geen procesagent", "Wijzigt geen productobjecten", "Neemt geen besluit namens de Stakeholder"), true,
            ),
            AgentRoleDefinitionDetails(
                AgentRoleKey("MEETING_MINUTES_AGENT"), "Notulenagent", "product-meetings", "meeting-v1",
                "Legt expliciete afspraken en compacte blijvende lessen controleerbaar vast.",
                listOf("Maakt notulen", "Classificeert uitkomsten", "Stelt een atomische geheugenbatch samen"),
                listOf("Verzint geen uitkomsten", "Neemt geen Stakeholderbesluit", "Schrijft alleen via publieke commands"), true,
            ),
            AgentRoleDefinitionDetails(
                AgentRoleKey("PRODUCT_DESIGNER_MVP"), "Productontwerper", "product-design", "mvp",
                "Ontwerpt complete, richtingvaste epics uit publieke productwaarheid.",
                listOf("Onderzoekt productkansen", "Ontwerpt UX en acceptatiecriteria", "Publiceert complete epics"),
                listOf("Plant geen stories", "Wijzigt geen besluiten", "Levert niet aan Software Factory"), true,
            ),
            AgentRoleDefinitionDetails(
                AgentRoleKey("PLANNER_MVP"), "Planner", "product-planning", "mvp",
                "Verdeelt een bevroren epic in zelfstandige uitvoerbare stories.",
                listOf("Plant epics", "Verwerkt gericht planwerk", "Bewaakt afhankelijkheden en volgorde"),
                listOf("Ontwerpt geen nieuwe productrichting", "Test geen product", "Dispatcht niet zelf"), true,
            ),
            AgentRoleDefinitionDetails(
                AgentRoleKey("TESTER_MVP"), "Tester", "quality-assurance", "mvp",
                "Onderzoekt gerichte kwaliteitsvragen en publiceert controleerbaar bewijs.",
                listOf("Verifieert stories en epics", "Onderzoekt signalen", "Registreert bugs en kwaliteitshistorie"),
                listOf("Schrijft geen stories", "Bepaalt geen productrichting", "Wijzigt geen testuitkomst op verzoek"), true,
            ),
        )
    }
}
