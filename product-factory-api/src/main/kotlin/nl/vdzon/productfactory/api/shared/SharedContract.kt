package nl.vdzon.productfactory.api.shared

import java.time.Instant

@JvmInline value class ProductId(val value: String)
@JvmInline value class EpicId(val value: String)
@JvmInline value class StoryId(val value: String)
@JvmInline value class BugId(val value: String)
@JvmInline value class VerificationId(val value: String)
@JvmInline value class UserSignalId(val value: String)
@JvmInline value class DecisionId(val value: String)
@JvmInline value class MeetingId(val value: String)
@JvmInline value class StakeholderQuestionId(val value: String)
@JvmInline value class ProcessSessionId(val value: String)
@JvmInline value class PlanningWorkItemId(val value: String)
@JvmInline value class QualityWorkItemId(val value: String)
@JvmInline value class AiTaskId(val value: String)
@JvmInline value class MemoryItemId(val value: String)
@JvmInline value class MemoryVersionId(val value: String)
@JvmInline value class DeliveryAttemptId(val value: String)

enum class ProductFactoryEnvironment { LOCAL, ACCEPTANCE, PRODUCTION }
enum class ActorType { STAKEHOLDER, PROCESS, MEETING_MINUTES_AGENT, FACTORY, SYSTEM }
enum class ProcessSessionStatus { RUNNING, WAITING_FOR_AI, BLOCKED, SUCCEEDED, FAILED, CANCELLED }
enum class WorkItemStatus { PENDING, IN_PROGRESS, DONE, BLOCKED, FAILED }
enum class ScheduledProcess { PRODUCT_DESIGN, PRODUCT_PLANNING, QUALITY_ASSURANCE, SOFTWARE_FACTORY_DISPATCHER }

data class TimeRange(val from: Instant? = null, val until: Instant? = null)
data class ActorReference(val type: ActorType, val id: String)
data class SourceReference(val type: String, val id: String, val version: Long)
data class ArtifactReference(val name: String, val mediaType: String, val uri: String)
data class EvidenceDetails(val description: String, val artifacts: List<ArtifactReference> = emptyList())
data class ImplementationIdentity(val artifact: String, val variant: String, val version: String, val sourceRevision: String)

data class ProcessSessionFilter(
    val productId: ProductId? = null,
    val statuses: Set<ProcessSessionStatus> = emptySet(),
    val timeRange: TimeRange = TimeRange(),
    /** Maximaal aantal sessies (nieuwste eerst); null = alles. */
    val limit: Int? = null,
    /** Alleen sessies met startedAt strikt vóór dit tijdstip (paginering). */
    val before: Instant? = null,
    /** Laat succesvolle no-op-sessies weg (zie [isNoOpProcessSession]). */
    val excludeNoOps: Boolean = false,
)

/** Marker in resultSummary waarmee een processessie aangeeft dat er niets te doen was. */
const val NO_OP_RESULT_MARKER = "succesvolle no-op."

/** Eén definitie van een no-op-sessie: SUCCEEDED en resultSummary bevat [NO_OP_RESULT_MARKER]. */
fun isNoOpProcessSession(status: ProcessSessionStatus, resultSummary: String?): Boolean =
    status == ProcessSessionStatus.SUCCEEDED && resultSummary?.contains(NO_OP_RESULT_MARKER) == true

/** SQL-variant van [isNoOpProcessSession] voor tabellen met de kolommen status en result_summary. */
const val NO_OP_PROCESS_SESSION_SQL = "(status='SUCCEEDED' AND COALESCE(result_summary,'') LIKE '%$NO_OP_RESULT_MARKER%')"

data class ProcessSessionSqlQuery(val where: String, val args: List<Any>, val limit: Int?)

/**
 * Vertaalt het filter naar een WHERE-clausule voor de sessietabellen van de procesmodules
 * (kolommen product_id, status, started_at en result_summary), zodat filteren en begrenzen in SQL gebeurt.
 */
fun ProcessSessionFilter.toSqlQuery(): ProcessSessionSqlQuery {
    val conditions = mutableListOf<String>()
    val args = mutableListOf<Any>()
    productId?.let { conditions += "product_id=?"; args += it.value }
    if (statuses.isNotEmpty()) {
        conditions += "status IN (${statuses.joinToString(",") { "?" }})"
        args.addAll(statuses.sortedBy { it.ordinal }.map { it.name })
    }
    timeRange.from?.let { conditions += "started_at>=?"; args += it }
    timeRange.until?.let { conditions += "started_at<?"; args += it }
    before?.let { conditions += "started_at<?"; args += it }
    if (excludeNoOps) conditions += "NOT $NO_OP_PROCESS_SESSION_SQL"
    val where = if (conditions.isEmpty()) "" else conditions.joinToString(" AND ", prefix = "WHERE ")
    return ProcessSessionSqlQuery(where, args, limit)
}

data class ProcessSessionDetails(
    val id: ProcessSessionId,
    val productId: ProductId,
    val status: ProcessSessionStatus,
    val implementation: ImplementationIdentity,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val inputs: List<SourceReference> = emptyList(),
    val aiTaskIds: List<AiTaskId> = emptyList(),
    val publications: List<SourceReference> = emptyList(),
    val resultSummary: String? = null,
    val blockedReason: String? = null,
    val errorCode: String? = null,
    val repositoryUrl: String? = null,
    val repositoryCommitSha: String? = null,
    /** Berekend bij het lezen: true voor een succesvolle no-op-sessie. */
    val noOp: Boolean = false,
)

class ProcessAlreadyRunning(val productId: ProductId) : RuntimeException("Er draait al een processessie voor ${productId.value}")
class AggregateNotFound(message: String) : RuntimeException(message)
class VersionConflict(message: String) : RuntimeException(message)
class IdempotencyConflict(message: String) : RuntimeException(message)
class InvalidCommand(message: String) : RuntimeException(message)
class CapabilityNotAvailable(message: String) : RuntimeException(message)
