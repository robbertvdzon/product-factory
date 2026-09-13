package nl.vdzon.productfactory.api.advisor

import nl.vdzon.productfactory.api.shared.ProductId
import java.time.Instant

@JvmInline value class UserId(val value: String)
@JvmInline value class ProductConversationId(val value: String)
@JvmInline value class ProductConversationMessageId(val value: String)
@JvmInline value class ProductRequestId(val value: String)
@JvmInline value class DesignWorkItemId(val value: String)

enum class GlobalRole { FACTORY_OWNER }
enum class ActingRole { FACTORY_OWNER, PRODUCT_OWNER, ARCHITECT }
enum class ProductMembershipRole { PRODUCT_OWNER, ARCHITECT }
enum class MembershipStatus { ACTIVE, REVOKED }
enum class ConversationStatus { OPEN, PROCESSING, WAITING_FOR_USER, PROPOSAL_READY, BLOCKED, CLOSED }
enum class ConversationSender { USER, PRODUCT_ADVISOR, SYSTEM }
enum class AdvisorOutcome { ANSWER, ASK_FOLLOW_UP, PROPOSE_CHANGE }
enum class ProductRequestType { HOTFIX, BUGFIX, EPIC_CANDIDATE }
enum class ProductRequestStatus { PROPOSED, APPROVED, ROUTING, ROUTED, ROUTING_FAILED, CANCELLED }
enum class RequestDeliveryStatus { NOT_STARTED, OPEN, DONE, CANCELLED, FAILED }
enum class DesignWorkItemStatus { PENDING, IN_PROGRESS, WAITING_FOR_USER, DONE, BLOCKED, FAILED }
enum class ApprovalRole { PRODUCT_OWNER, FACTORY_OWNER }

data class UserDetails(
    val id: UserId,
    val email: String,
    val displayName: String?,
    val active: Boolean,
    val globalRoles: Set<GlobalRole>,
    val memberships: List<ProductMembershipDetails>,
    val actingRole: ActingRole = if (GlobalRole.FACTORY_OWNER in globalRoles) ActingRole.FACTORY_OWNER else ActingRole.PRODUCT_OWNER,
) {
    val availableRoles: Set<String>
        get() = globalRoles.map { it.name }.toSet() + memberships.filter { it.status == MembershipStatus.ACTIVE }.map { it.role.name }
    /** Rollen die nu gelden: een factory owner die als product owner werkt, heeft geen globale rollen. */
    val effectiveGlobalRoles: Set<GlobalRole>
        get() = if (actingRole != ActingRole.FACTORY_OWNER) emptySet() else globalRoles
}
data class ProductMembershipDetails(
    val productId: ProductId,
    val role: ProductMembershipRole,
    val status: MembershipStatus,
    val grantedAt: Instant,
    val revokedAt: Instant? = null,
    val reason: String? = null,
    val version: Long,
)
data class ProductMembershipHistoryDetails(
    val id: String,
    val userId: UserId,
    val productId: ProductId,
    val role: ProductMembershipRole,
    val action: String,
    val reason: String?,
    val actorUserId: UserId,
    val occurredAt: Instant,
)
data class ProductConversationMessageDetails(
    val id: ProductConversationMessageId,
    val sequence: Long,
    val sender: ConversationSender,
    val text: String,
    val createdBy: UserId?,
    val createdAt: Instant,
)
data class ProductConversationDetails(
    val id: ProductConversationId,
    val productId: ProductId,
    val title: String,
    val createdBy: UserId,
    val status: ConversationStatus,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val messages: List<ProductConversationMessageDetails> = emptyList(),
    val request: ProductRequestDetails? = null,
    val epicId: String? = null,
    val audienceRole: ProductMembershipRole = ProductMembershipRole.PRODUCT_OWNER,
)
data class ProductRequestVersionDetails(
    val version: Long,
    val type: ProductRequestType,
    val title: String,
    val summary: String,
    val problem: String,
    val userImpact: String,
    val currentBehavior: String,
    val desiredBehavior: String,
    val evidence: List<String>,
    val gitCommitSha: String,
    val acceptanceCriteria: List<String>,
    val scope: List<String>,
    val boundaries: List<String>,
    val excludedHotfixCategories: Set<String>,
    val createdAt: Instant,
)
data class ProductRequestApprovalDetails(val userId: UserId, val requestVersion: Long, val approvedAt: Instant)
data class ProductRequestDetails(
    val id: ProductRequestId,
    val productId: ProductId,
    val conversationId: ProductConversationId,
    val requestedBy: UserId,
    val status: ProductRequestStatus,
    val currentVersion: Long,
    val content: ProductRequestVersionDetails,
    val approvals: List<ProductRequestApprovalDetails>,
    val linkedEpicId: String? = null,
    val externalStoryKey: String? = null,
    val deliveryStatus: RequestDeliveryStatus = RequestDeliveryStatus.NOT_STARTED,
    val deliveredCommitSha: String? = null,
    val safeErrorCode: String? = null,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)
data class PersonalActionDetails(
    val key: String,
    val productId: ProductId,
    val kind: String,
    val title: String,
    val targetType: String,
    val targetId: String,
    val createdAt: Instant,
)
data class NotificationDetails(
    val id: String,
    val productId: ProductId,
    val kind: String,
    val title: String,
    val targetType: String,
    val targetId: String,
    val readAt: Instant?,
    val createdAt: Instant,
    val version: Long,
)

data class CreateConversationCommand(val productId: ProductId, val title: String, val userId: UserId, val idempotencyKey: String,
    val epicId: String? = null, val audienceRole: ProductMembershipRole = ProductMembershipRole.PRODUCT_OWNER)
data class AddConversationMessageCommand(val conversationId: ProductConversationId, val text: String, val expectedVersion: Long, val userId: UserId, val idempotencyKey: String)
data class CloseConversationCommand(val conversationId: ProductConversationId, val expectedVersion: Long, val userId: UserId, val idempotencyKey: String)
data class ApproveProductRequestCommand(val requestId: ProductRequestId, val requestVersion: Long, val expectedVersion: Long, val userId: UserId, val idempotencyKey: String)
data class CancelProductRequestCommand(val requestId: ProductRequestId, val expectedVersion: Long, val userId: UserId, val idempotencyKey: String)

interface ProductAdvisorService {
    fun createConversation(command: CreateConversationCommand): ProductConversationId
    fun addMessage(command: AddConversationMessageCommand): ProductConversationMessageId
    fun closeConversation(command: CloseConversationCommand)
    fun approveRequest(command: ApproveProductRequestCommand)
    fun cancelRequest(command: CancelProductRequestCommand)
    fun retryConversation(conversationId: ProductConversationId, expectedVersion: Long, userId: UserId, idempotencyKey: String)
    fun routeApprovedRequests(limit: Int = 20)
    fun resumeAdvisorTurns(limit: Int = 20)
}

interface ProductAdvisorQueryService {
    fun findConversations(productId: ProductId): List<ProductConversationDetails>
    fun getConversation(id: ProductConversationId): ProductConversationDetails
    fun findRequests(productId: ProductId? = null): List<ProductRequestDetails>
    fun getRequest(id: ProductRequestId): ProductRequestDetails
    fun findActions(userId: UserId): List<PersonalActionDetails>
    fun findNotifications(userId: UserId): List<NotificationDetails>
}
