package nl.vdzon.productfactory.advisor

import nl.vdzon.productfactory.api.advisor.*
import nl.vdzon.productfactory.api.shared.ProductId
import nl.vdzon.productfactory.auth.ProductAuthorizationService
import nl.vdzon.productfactory.dispatcher.DashboardTokenStatus
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

data class CreateConversationRequest(val title: String, val idempotencyKey: String)
data class AddConversationMessageRequest(val text: String, val expectedVersion: Long, val idempotencyKey: String)
data class ConversationActionRequest(val expectedVersion: Long, val idempotencyKey: String)
data class RequestApprovalRequest(val requestVersion: Long, val expectedVersion: Long, val idempotencyKey: String)
data class EpicApprovalRequest(val expectedVersion: Long, val idempotencyKey: String)
data class EpicRefinementRequest(val reason: String, val expectedVersion: Long, val idempotencyKey: String)
data class NotificationReadRequest(val expectedVersion: Long, val idempotencyKey: String)

@RestController
class ProductAdvisorController(
    private val service: ProductAdvisorApplicationService,
    private val authorization: ProductAuthorizationService,
    private val jdbc: JdbcTemplate,
) {
    @GetMapping("/api/products/{productId}/conversations")
    fun conversations(@PathVariable productId: String, authentication: Authentication?) =
        authorization.requireProduct(ProductId(productId), authentication).let { service.findConversations(ProductId(productId)) }

    @PostMapping("/api/products/{productId}/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    fun createConversation(
        @PathVariable productId: String,
        @RequestBody request: CreateConversationRequest,
        authentication: Authentication?,
    ): Map<String, String> {
        authorization.requireRole(ProductId(productId), ProductMembershipRole.PRODUCT_OWNER, authentication)
        val id = service.createConversation(CreateConversationCommand(ProductId(productId), request.title, authorization.currentUserId(authentication), request.idempotencyKey))
        return mapOf("id" to id.value)
    }

    @GetMapping("/api/conversations/{conversationId}")
    fun conversation(@PathVariable conversationId: String, authentication: Authentication?) = service.getConversation(ProductConversationId(conversationId)).also {
        authorization.requireProduct(it.productId, authentication)
    }

    @PostMapping("/api/conversations/{conversationId}/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun message(
        @PathVariable conversationId: String,
        @RequestBody request: AddConversationMessageRequest,
        authentication: Authentication?,
    ): Map<String, String> {
        val id = ProductConversationId(conversationId)
        service.getConversation(id).let { authorization.requireRole(it.productId, it.audienceRole, authentication) }
        val messageId = service.addMessage(AddConversationMessageCommand(id, request.text, request.expectedVersion, authorization.currentUserId(authentication), request.idempotencyKey))
        return mapOf("id" to messageId.value)
    }

    @PostMapping("/api/conversations/{conversationId}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun close(@PathVariable conversationId: String, @RequestBody request: ConversationActionRequest, authentication: Authentication?) {
        val id = ProductConversationId(conversationId)
        service.getConversation(id).let { authorization.requireRole(it.productId, it.audienceRole, authentication) }
        service.closeConversation(CloseConversationCommand(id, request.expectedVersion, authorization.currentUserId(authentication), request.idempotencyKey))
    }

    @PostMapping("/api/conversations/{conversationId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun retry(@PathVariable conversationId: String, @RequestBody request: ConversationActionRequest, authentication: Authentication?) {
        val id = ProductConversationId(conversationId)
        service.getConversation(id).let { authorization.requireRole(it.productId, it.audienceRole, authentication) }
        service.retryConversation(id, request.expectedVersion, authorization.currentUserId(authentication), request.idempotencyKey)
    }

    @GetMapping("/api/products/{productId}/product-requests")
    fun requests(@PathVariable productId: String, authentication: Authentication?) =
        authorization.requireProduct(ProductId(productId), authentication).let { service.findRequests(ProductId(productId)) }

    @GetMapping("/api/product-requests/{requestId}")
    fun request(@PathVariable requestId: String, authentication: Authentication?) = service.getRequest(ProductRequestId(requestId)).also {
        authorization.requireProduct(it.productId, authentication)
    }

    @PostMapping("/api/product-requests/{requestId}/approve")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun approveRequest(@PathVariable requestId: String, @RequestBody request: RequestApprovalRequest, authentication: Authentication?) {
        val id = ProductRequestId(requestId)
        authorization.requireRole(service.getRequest(id).productId, ProductMembershipRole.PRODUCT_OWNER, authentication)
        service.approveRequest(ApproveProductRequestCommand(id, request.requestVersion, request.expectedVersion, authorization.currentUserId(authentication), request.idempotencyKey))
    }

    @PostMapping("/api/product-requests/{requestId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelRequest(@PathVariable requestId: String, @RequestBody request: ConversationActionRequest, authentication: Authentication?) {
        val id = ProductRequestId(requestId)
        authorization.requireRole(service.getRequest(id).productId, ProductMembershipRole.PRODUCT_OWNER, authentication)
        service.cancelRequest(CancelProductRequestCommand(id, request.expectedVersion, authorization.currentUserId(authentication), request.idempotencyKey))
    }

    @PostMapping("/api/epics/{epicId}/product-owner-approval")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun productApproval(@PathVariable epicId: String, @RequestBody request: EpicApprovalRequest, authentication: Authentication?) {
        val productId = epicProduct(epicId)
        authorization.requireRole(productId, ProductMembershipRole.PRODUCT_OWNER, authentication)
        val user = authorization.current(authentication)
        if (user != null && user.memberships.none { it.productId == productId && it.status == MembershipStatus.ACTIVE && it.role == ProductMembershipRole.PRODUCT_OWNER }) {
            throw AccessDeniedException("Een actief product owner-lidmaatschap is vereist.")
        }
        service.approveEpic(epicId, ApprovalRole.PRODUCT_OWNER, authorization.currentUserId(authentication), request.expectedVersion, request.idempotencyKey)
    }

    @PostMapping("/api/epics/{epicId}/factory-owner-approval")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun factoryApproval(@PathVariable epicId: String, @RequestBody request: EpicApprovalRequest, authentication: Authentication?) {
        authorization.requireFactoryOwner(authentication)
        throw nl.vdzon.productfactory.api.shared.InvalidCommand("Factory owners keuren epics niet goed. Stel productrollen in en gebruik de architectwerkplek.")
    }

    @PostMapping("/api/epics/{epicId}/product-request-refinement")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun refinement(@PathVariable epicId: String, @RequestBody request: EpicRefinementRequest, authentication: Authentication?) {
        authorization.requireProduct(epicProduct(epicId), authentication)
        service.requestEpicRefinement(epicId, request.reason, authorization.currentUserId(authentication), request.expectedVersion, request.idempotencyKey)
    }

    @GetMapping("/api/my/actions")
    fun actions(authentication: Authentication?) = service.findActions(authorization.currentUserId(authentication))

    @GetMapping("/api/my/notifications")
    fun notifications(authentication: Authentication?) = service.findNotifications(authorization.currentUserId(authentication))

    @PostMapping("/api/my/notifications/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun readNotification(
        @PathVariable notificationId: String,
        @RequestBody request: NotificationReadRequest,
        authentication: Authentication?,
    ) = service.markNotificationRead(
        notificationId, authorization.currentUserId(authentication), request.expectedVersion, request.idempotencyKey,
    )

    @GetMapping("/api/operations/product-advisor/hotfix-token")
    fun hotfixToken(authentication: Authentication?): DashboardTokenStatus =
        authorization.requireFactoryOwner(authentication).let { service.dashboardTokenStatus() }

    private fun epicProduct(epicId: String): ProductId = jdbc.query(
        "SELECT product_id FROM pf_epic WHERE id=?", { rs, _ -> ProductId(rs.getString(1)) }, epicId,
    ).singleOrNull() ?: throw IllegalArgumentException("Epic niet gevonden.")
}
