package nl.vdzon.productfactory.api.design

import nl.vdzon.productfactory.api.advisor.*
import nl.vdzon.productfactory.api.shared.*
import java.time.Instant

enum class ResponsibilityMode { HUMAN, AI }
enum class ImpactCategory { DATABASE, MIGRATION, EXTERNAL_SYSTEM, FRONTEND, ACCESS, PRODUCT_AI, INFRASTRUCTURE }
enum class ImpactLevel { NONE, COMPATIBLE, MATERIAL, UNKNOWN }
enum class ReviewDecision { APPROVE, REQUEST_CHANGE, REQUEST_RESEARCH }
data class ProductGovernancePolicy(
    val productId: ProductId,
    val configured: Boolean = false,
    val productOwnerMode: ResponsibilityMode = ResponsibilityMode.HUMAN,
    val architectMode: ResponsibilityMode = ResponsibilityMode.HUMAN,
    val architectureRules: String = "",
    val productAiRules: String = "",
    val automaticCategories: Set<ImpactCategory> = emptySet(),
    val maximumAdditionalJobsPerDay: Long? = null,
    val monthlyProductBudgetEuro: String? = null,
    val maximumGrowthPercent: Int? = null,
    val version: Long = 0,
)
data class ImpactItem(val category: ImpactCategory, val level: ImpactLevel, val summary: String, val evidence: List<String>, val alternatives: String = "")
data class EpicRisk(val title: String, val likelihood: String, val impact: String, val mitigation: String, val uncertainty: String, val responsibleRole: ProductMembershipRole)
data class ProductAiImpact(
    val changed: Boolean = false, val currentBehavior: String = "", val proposedBehavior: String = "",
    val trigger: String = "", val frequency: String = "", val volume: String = "", val oneTimeWork: String = "",
    val estimatedAdditionalJobsPerDay: Long? = null, val estimatedMonthlyCostEuro: String? = null,
    val estimatedGrowthPercent: Int? = null, val assumptions: List<String> = emptyList(),
    val providerAndModel: String = "", val dataAndValidation: String = "", val limitsAndRetries: String = "",
)
data class EpicImpactAssessment(
    val items: List<ImpactItem> = emptyList(), val risks: List<EpicRisk> = emptyList(),
    val productAi: ProductAiImpact = ProductAiImpact(), val changeSummary: String = "",
)
data class EpicReviewRecord(val id: String, val contentVersion: Long, val policyVersion: Long, val role: ProductMembershipRole,
    val actorId: String, val decision: ReviewDecision, val reason: String, val automatic: Boolean, val createdAt: Instant)
data class EpicReviewState(
    val contentVersion: Long, val policyVersion: Long, val ready: Boolean,
    val productOwnerApproved: Boolean, val architectApproved: Boolean,
    val architectRequired: Boolean, val blockers: List<String>, val records: List<EpicReviewRecord>,
)
data class ReviewEpicCommand(val epicId: EpicId, val expectedVersion: Long, val role: ProductMembershipRole,
    val userId: UserId, val decision: ReviewDecision, val reason: String, val idempotencyKey: String)
data class UpdateGovernancePolicyCommand(val policy: ProductGovernancePolicy, val userId: UserId, val idempotencyKey: String)
interface ProductGovernanceService {
    fun getPolicy(productId: ProductId): ProductGovernancePolicy
    fun updatePolicy(command: UpdateGovernancePolicyCommand): ProductGovernancePolicy
}
interface ProductIdentityQuery {
    fun getIdentity(userId: UserId): UserDetails
    fun productMembers(productId: ProductId, role: ProductMembershipRole): List<UserDetails>
}
interface EpicGovernanceService {
    fun reviewState(epicId: EpicId): EpicReviewState
    fun review(command: ReviewEpicCommand)
    fun canDispatch(epicId: EpicId, plannedVersion: Long): Boolean
}
