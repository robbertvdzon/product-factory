package nl.vdzon.productfactory.api.decisions

import nl.vdzon.productfactory.api.shared.ActorReference
import nl.vdzon.productfactory.api.shared.DecisionId
import nl.vdzon.productfactory.api.shared.ProductId
import java.time.Instant

enum class DecisionState { ACTIVE, WITHDRAWN, SUPERSEDED }
enum class DecisionOrigin { STAKEHOLDER, FACTORY }

data class CreateDecisionCommand(
    val productId: ProductId,
    val decision: String,
    val origin: DecisionOrigin,
    val actor: ActorReference,
    val idempotencyKey: String,
)

data class ReviseDecisionCommand(
    val decisionId: DecisionId,
    val decision: String,
    val expectedVersion: Long,
    val actor: ActorReference,
    val idempotencyKey: String,
)

data class WithdrawDecisionCommand(
    val decisionId: DecisionId,
    val reason: String,
    val expectedVersion: Long,
    val actor: ActorReference,
    val idempotencyKey: String,
)

data class SupersedeDecisionsCommand(
    val productId: ProductId,
    val supersededIds: Set<DecisionId>,
    val replacementDecision: String,
    val origin: DecisionOrigin,
    val expectedVersions: Map<DecisionId, Long>,
    val actor: ActorReference,
    val idempotencyKey: String,
)

data class DecisionDto(
    val id: DecisionId,
    val productId: ProductId,
    val origin: DecisionOrigin,
    val decision: String,
    val validFrom: Instant,
    val validUntil: Instant?,
    val version: Long,
)

data class DecisionDetailsDto(
    val id: String,
    val validFrom: Instant,
    val validUntil: Instant?,
    val decision: String,
    val actor: ActorReference,
)

data class DecisionHistoryDto(
    val id: DecisionId,
    val productId: ProductId,
    val origin: DecisionOrigin,
    val state: DecisionState,
    val supersededByDecisionId: DecisionId?,
    val withdrawalReason: String?,
    val version: Long,
    val history: List<DecisionDetailsDto>,
)

interface DecisionService {
    fun createDecision(command: CreateDecisionCommand): DecisionId
    fun reviseDecision(command: ReviseDecisionCommand)
    fun withdrawDecision(command: WithdrawDecisionCommand)
    fun supersedeDecisions(command: SupersedeDecisionsCommand): DecisionId
}

interface DecisionQueryService {
    fun getDecisions(productId: ProductId, validAt: Instant = Instant.now()): List<DecisionDto>
    fun getDecisionArchive(productId: ProductId): List<DecisionHistoryDto>
}
