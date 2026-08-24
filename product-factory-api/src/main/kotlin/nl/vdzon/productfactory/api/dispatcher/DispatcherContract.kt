package nl.vdzon.productfactory.api.dispatcher

import nl.vdzon.productfactory.api.shared.*
import java.time.Instant

enum class DeliveryAttemptStatus { PENDING, ACCEPTED, RETRYABLE_FAILURE, CONTRACT_FAILURE, COMPLETED, CANCELLED }
data class DispatcherProductStatusDetails(val productId: ProductId, val running: Boolean, val blocked: Boolean, val blockedReason: String?, val externalStoryId: String?, val updatedAt: Instant)
data class DeliveryAttemptFilter(val productId: ProductId? = null, val storyId: StoryId? = null, val statuses: Set<DeliveryAttemptStatus> = emptySet(), val timeRange: TimeRange = TimeRange())
data class DeliveryAttemptDetails(
    val id: DeliveryAttemptId,
    val productId: ProductId,
    val storyId: StoryId,
    val reservationId: String,
    val externalStoryId: String?,
    val idempotencyKey: String,
    val status: DeliveryAttemptStatus,
    val attemptCount: Int,
    val lastErrorCode: String?,
    val retryAfter: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
interface SoftwareFactoryDispatcherService { fun runDispatchSession(productId: ProductId) }
interface SoftwareFactoryDispatcherQueryService {
    fun getDispatchStatus(productId: ProductId): DispatcherProductStatusDetails
    fun findDeliveryAttempts(filter: DeliveryAttemptFilter): List<DeliveryAttemptDetails>
    fun getDispatchSession(processSessionId: ProcessSessionId): ProcessSessionDetails
    fun findDispatchSessions(filter: ProcessSessionFilter): List<ProcessSessionDetails>
}
