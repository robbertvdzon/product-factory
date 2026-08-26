package nl.vdzon.productfactory.decisions

import nl.vdzon.productfactory.api.decisions.*
import nl.vdzon.productfactory.api.shared.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.sql.ResultSet
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.util.HexFormat
import java.util.UUID

@Service
@Transactional
class DecisionApplicationService(
    private val jdbc: JdbcTemplate,
    private val clock: Clock,
) : DecisionService, DecisionQueryService {

    override fun createDecision(command: CreateDecisionCommand): DecisionId {
        validateOrigin(command.origin, command.actor)
        val fingerprint = fingerprint(command)
        replay(command.idempotencyKey, "CREATE_DECISION", fingerprint)?.let { return DecisionId(it) }
        val id = DecisionId(UUID.randomUUID().toString())
        val now = clock.instant()
        insertDecision(id, command.productId, command.origin, command.decision, command.actor, now)
        remember(command.idempotencyKey, "CREATE_DECISION", id.value, fingerprint, id.value, command.actor, now)
        return id
    }

    override fun reviseDecision(command: ReviseDecisionCommand) {
        validateActor(command.actor)
        val fingerprint = fingerprint(command)
        if (replay(command.idempotencyKey, "REVISE_DECISION", fingerprint) != null) return
        val decision = aggregate(command.decisionId)
        requireActive(decision)
        requireVersion(decision.version, command.expectedVersion)
        val now = clock.instant()
        closeCurrent(command.decisionId, now)
        insertDetails(command.decisionId, command.decision, command.actor, now)
        updateAggregateVersion(command.decisionId, command.expectedVersion, now)
        remember(command.idempotencyKey, "REVISE_DECISION", command.decisionId.value, fingerprint, null, command.actor, now)
    }

    override fun withdrawDecision(command: WithdrawDecisionCommand) {
        validateActor(command.actor)
        val fingerprint = fingerprint(command)
        if (replay(command.idempotencyKey, "WITHDRAW_DECISION", fingerprint) != null) return
        val decision = aggregate(command.decisionId)
        requireActive(decision)
        requireVersion(decision.version, command.expectedVersion)
        val now = clock.instant()
        closeCurrent(command.decisionId, now)
        val updated = jdbc.update(
            """UPDATE pf_decision SET state=?,withdrawal_reason=?,updated_at=?,version=version+1
               WHERE decision_id=? AND version=? AND state='ACTIVE'""",
            DecisionState.WITHDRAWN.name, requiredText(command.reason, "Intrekkingsreden"), now,
            command.decisionId.value, command.expectedVersion,
        )
        if (updated != 1) throw VersionConflict("Besluit is intussen gewijzigd.")
        remember(command.idempotencyKey, "WITHDRAW_DECISION", command.decisionId.value, fingerprint, null, command.actor, now)
    }

    override fun supersedeDecisions(command: SupersedeDecisionsCommand): DecisionId {
        validateOrigin(command.origin, command.actor)
        if (command.supersededIds.isEmpty()) throw InvalidCommand("Minimaal één besluit moet worden vervangen.")
        if (command.expectedVersions.keys != command.supersededIds) {
            throw InvalidCommand("Voor ieder vervangen besluit is exact één verwachte versie verplicht.")
        }
        val fingerprint = fingerprint(command)
        replay(command.idempotencyKey, "SUPERSEDE_DECISIONS", fingerprint)?.let { return DecisionId(it) }
        val aggregates = command.supersededIds.map(::aggregate)
        if (aggregates.any { it.productId != command.productId }) throw InvalidCommand("Alle besluiten moeten bij hetzelfde product horen.")
        aggregates.forEach { decision ->
            requireActive(decision)
            requireVersion(decision.version, command.expectedVersions.getValue(decision.id))
        }
        val replacement = DecisionId(UUID.randomUUID().toString())
        val now = clock.instant()
        insertDecision(replacement, command.productId, command.origin, command.replacementDecision, command.actor, now)
        aggregates.forEach { decision ->
            closeCurrent(decision.id, now)
            val updated = jdbc.update(
                """UPDATE pf_decision SET state=?,superseded_by_decision_id=?,updated_at=?,version=version+1
                   WHERE decision_id=? AND version=? AND state='ACTIVE'""",
                DecisionState.SUPERSEDED.name, replacement.value, now, decision.id.value, decision.version,
            )
            if (updated != 1) throw VersionConflict("Besluit ${decision.id.value} is intussen gewijzigd.")
        }
        remember(command.idempotencyKey, "SUPERSEDE_DECISIONS", command.productId.value, fingerprint, replacement.value, command.actor, now)
        return replacement
    }

    @Transactional(readOnly = true)
    override fun getDecisions(productId: ProductId, validAt: Instant): List<DecisionDto> = jdbc.query(
        """SELECT d.decision_id,d.product_id,d.origin,d.version,v.decision_text,v.valid_from,v.valid_until
           FROM pf_decision d JOIN pf_decision_details v ON v.decision_id=d.decision_id
           WHERE d.product_id=? AND v.valid_from<=? AND (v.valid_until IS NULL OR v.valid_until>?)
           ORDER BY v.valid_from,d.decision_id""",
        { rs, _ -> DecisionDto(
            DecisionId(rs.getString("decision_id")), ProductId(rs.getString("product_id")),
            DecisionOrigin.valueOf(rs.getString("origin")), rs.getString("decision_text"),
            instant(rs, "valid_from")!!, instant(rs, "valid_until"), rs.getLong("version"),
        ) }, productId.value, validAt, validAt,
    )

    @Transactional(readOnly = true)
    override fun getDecisionArchive(productId: ProductId): List<DecisionHistoryDto> = jdbc.query(
        "SELECT * FROM pf_decision WHERE product_id=? ORDER BY created_at,decision_id",
        { rs, _ -> history(rs) }, productId.value,
    )

    fun deleteAllOwnedData() {
        jdbc.update("DELETE FROM pf_decision_command")
        jdbc.update("DELETE FROM pf_decision_details")
        jdbc.update("UPDATE pf_decision SET superseded_by_decision_id=NULL")
        jdbc.update("DELETE FROM pf_decision")
    }

    private fun insertDecision(
        id: DecisionId,
        productId: ProductId,
        origin: DecisionOrigin,
        text: String,
        actor: ActorReference,
        now: Instant,
    ) {
        val decision = requiredText(text, "Besluittekst")
        jdbc.update(
            """INSERT INTO pf_decision(decision_id,product_id,origin,state,created_at,updated_at,version)
               VALUES (?,?,?,?,?,?,?)""",
            id.value, productId.value, origin.name, DecisionState.ACTIVE.name, now, now, 1L,
        )
        insertDetails(id, decision, actor, now)
    }

    private fun insertDetails(id: DecisionId, text: String, actor: ActorReference, now: Instant) {
        jdbc.update(
            """INSERT INTO pf_decision_details(details_id,decision_id,valid_from,valid_until,current_marker,decision_text,actor_type,actor_id)
               VALUES (?,?,?,?,?,?,?,?)""",
            UUID.randomUUID().toString(), id.value, now, null, 1, requiredText(text, "Besluittekst"), actor.type.name, actor.id,
        )
    }

    private fun closeCurrent(id: DecisionId, now: Instant) {
        val updated = jdbc.update(
            "UPDATE pf_decision_details SET valid_until=?,current_marker=NULL WHERE decision_id=? AND current_marker=1",
            now, id.value,
        )
        if (updated != 1) throw InvalidCommand("Besluit heeft geen eenduidige actuele versie.")
    }

    private fun updateAggregateVersion(id: DecisionId, expectedVersion: Long, now: Instant) {
        val updated = jdbc.update(
            "UPDATE pf_decision SET updated_at=?,version=version+1 WHERE decision_id=? AND version=? AND state='ACTIVE'",
            now, id.value, expectedVersion,
        )
        if (updated != 1) throw VersionConflict("Besluit is intussen gewijzigd.")
    }

    private fun aggregate(id: DecisionId): DecisionAggregate = jdbc.query(
        "SELECT * FROM pf_decision WHERE decision_id=?",
        { rs, _ -> DecisionAggregate(
            id, ProductId(rs.getString("product_id")), DecisionOrigin.valueOf(rs.getString("origin")),
            DecisionState.valueOf(rs.getString("state")), rs.getLong("version"),
        ) }, id.value,
    ).singleOrNull() ?: throw AggregateNotFound("Besluit ${id.value} bestaat niet.")

    private fun history(rs: ResultSet): DecisionHistoryDto {
        val id = DecisionId(rs.getString("decision_id"))
        val versions = jdbc.query(
            "SELECT * FROM pf_decision_details WHERE decision_id=? ORDER BY valid_from,details_id",
            { details, _ -> DecisionDetailsDto(
                details.getString("details_id"), instant(details, "valid_from")!!, instant(details, "valid_until"),
                details.getString("decision_text"), ActorReference(ActorType.valueOf(details.getString("actor_type")), details.getString("actor_id")),
            ) }, id.value,
        )
        return DecisionHistoryDto(
            id, ProductId(rs.getString("product_id")), DecisionOrigin.valueOf(rs.getString("origin")),
            DecisionState.valueOf(rs.getString("state")), rs.getString("superseded_by_decision_id")?.let(::DecisionId),
            rs.getString("withdrawal_reason"), rs.getLong("version"), versions,
        )
    }

    private fun validateOrigin(origin: DecisionOrigin, actor: ActorReference) {
        validateActor(actor)
        when (origin) {
            DecisionOrigin.STAKEHOLDER -> if (actor.type !in setOf(ActorType.STAKEHOLDER, ActorType.MEETING_MINUTES_AGENT, ActorType.SYSTEM)) {
                throw InvalidCommand("Een Stakeholderbesluit vereist de Stakeholder of vertrouwde notulenafhandeling.")
            }
            DecisionOrigin.FACTORY -> if (actor.type !in setOf(ActorType.FACTORY, ActorType.PROCESS, ActorType.SYSTEM)) {
                throw InvalidCommand("Een Factorybesluit vereist vertrouwde Factorycode.")
            }
        }
    }

    private fun requireActive(decision: DecisionAggregate) {
        if (decision.state != DecisionState.ACTIVE) throw InvalidCommand("Alleen een actief besluit kan worden gewijzigd.")
    }

    private fun requireVersion(actual: Long, expected: Long) {
        if (actual != expected) throw VersionConflict("Besluit is intussen gewijzigd: verwacht $expected, actueel $actual.")
    }

    private fun validateActor(actor: ActorReference) {
        if (actor.id.isBlank() || actor.id.length > 320) throw InvalidCommand("Actor is ongeldig.")
    }

    private fun requiredText(value: String, label: String): String = value.trim().takeIf { it.isNotEmpty() && it.length <= 200_000 }
        ?: throw InvalidCommand("$label is verplicht en mag maximaal 200000 tekens bevatten.")

    private fun replay(idempotencyKey: String, commandType: String, fingerprint: String): String? {
        validateIdempotencyKey(idempotencyKey)
        val row = jdbc.query(
            "SELECT command_type,request_fingerprint,result_id FROM pf_decision_command WHERE idempotency_key=?",
            { rs, _ -> Triple(rs.getString(1), rs.getString(2), rs.getString(3)) }, idempotencyKey,
        ).singleOrNull() ?: return null
        if (row.first != commandType || row.second != fingerprint) throw IdempotencyConflict("Idempotentiesleutel is al voor andere inhoud gebruikt.")
        return row.third ?: REPLAYED_WITHOUT_RESULT
    }

    private fun remember(
        key: String,
        type: String,
        aggregateId: String,
        fingerprint: String,
        resultId: String?,
        actor: ActorReference,
        now: Instant,
    ) {
        jdbc.update(
            """INSERT INTO pf_decision_command(idempotency_key,command_type,aggregate_id,request_fingerprint,result_id,actor_type,actor_id,applied_at)
               VALUES (?,?,?,?,?,?,?,?)""",
            key, type, aggregateId, fingerprint, resultId, actor.type.name, actor.id, now,
        )
    }

    private fun validateIdempotencyKey(key: String) {
        if (!IDEMPOTENCY_KEY.matches(key)) throw InvalidCommand("Idempotentiesleutel is ongeldig.")
    }

    private fun fingerprint(value: Any): String = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(value.toString().toByteArray()),
    )

    private fun instant(rs: ResultSet, column: String): Instant? = rs.getObject(column, OffsetDateTime::class.java)?.toInstant()

    private data class DecisionAggregate(
        val id: DecisionId,
        val productId: ProductId,
        val origin: DecisionOrigin,
        val state: DecisionState,
        val version: Long,
    )

    companion object {
        private const val REPLAYED_WITHOUT_RESULT = "__REPLAYED__"
        private val IDEMPOTENCY_KEY = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{2,199}")
    }
}
