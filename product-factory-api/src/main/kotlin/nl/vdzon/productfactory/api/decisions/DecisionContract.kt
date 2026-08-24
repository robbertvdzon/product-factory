package nl.vdzon.productfactory.api.decisions

import nl.vdzon.productfactory.api.shared.*
import java.time.Instant

enum class DecisionStatus { ACTIVE, SUPERSEDED, RETRACTED }
enum class DecisionOrigin { STAKEHOLDER, FACTORY }
data class CreateDecisionCommand(val productId: ProductId, val text: String, val reason: String, val origin: DecisionOrigin, val idempotencyKey: String)
data class SupersedeDecisionsCommand(val productId: ProductId, val supersededIds: Set<DecisionId>, val replacementText: String, val reason: String, val idempotencyKey: String)
data class RetractDecisionCommand(val decisionId: DecisionId, val reason: String, val expectedVersion: Long, val idempotencyKey: String)
data class DecisionDetails(
    val id: DecisionId,
    val productId: ProductId,
    val text: String,
    val reason: String,
    val origin: DecisionOrigin,
    val status: DecisionStatus,
    val validFrom: Instant,
    val validUntil: Instant? = null,
    val supersedes: Set<DecisionId> = emptySet(),
    val version: Long,
)
data class DecisionHistoryDetails(val decision: DecisionDetails, val changedAt: Instant, val changeReason: String)
interface DecisionService {
    fun createDecision(command: CreateDecisionCommand): DecisionId
    fun supersedeDecisions(command: SupersedeDecisionsCommand): DecisionId
    fun retractDecision(command: RetractDecisionCommand)
}
interface DecisionQueryService {
    fun getDecision(decisionId: DecisionId): DecisionDetails
    fun getDecisions(productId: ProductId, validAt: Instant = Instant.now()): List<DecisionDetails>
    fun getDecisionArchive(productId: ProductId): List<DecisionHistoryDetails>
}
