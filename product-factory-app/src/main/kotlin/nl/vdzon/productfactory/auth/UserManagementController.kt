package nl.vdzon.productfactory.auth

import nl.vdzon.productfactory.api.advisor.UserId
import nl.vdzon.productfactory.api.shared.ProductId
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

data class CreateOrFindUserRequest(val email: String, val idempotencyKey: String)
data class MembershipMutationRequest(val expectedVersion: Long, val idempotencyKey: String)
data class RevokeMembershipRequest(val reason: String, val confirmation: Boolean, val expectedVersion: Long, val idempotencyKey: String)

@RestController
@RequestMapping("/api/admin/users")
class UserManagementController(
    private val users: UserIdentityRepository,
    private val authorization: ProductAuthorizationService,
) {
    @GetMapping fun users(authentication: Authentication?) = authorization.requireFactoryOwner(authentication).let { users.findAll() }

    @GetMapping("/membership-history")
    fun history(authentication: Authentication?) = authorization.requireFactoryOwner(authentication).let { users.findMembershipHistory() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateOrFindUserRequest, authentication: Authentication?) =
        authorization.requireFactoryOwner(authentication).let { users.createForAdministration(request.email, request.idempotencyKey) }

    @PutMapping("/{userId}/memberships/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun grant(
        @PathVariable userId: String,
        @PathVariable productId: String,
        @RequestBody request: MembershipMutationRequest,
        authentication: Authentication?,
    ) {
        authorization.requireFactoryOwner(authentication)
        users.grantProductOwner(
            UserId(userId), ProductId(productId), authorization.currentUserId(authentication), request.expectedVersion, request.idempotencyKey,
        )
    }

    @DeleteMapping("/{userId}/memberships/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(
        @PathVariable userId: String,
        @PathVariable productId: String,
        @RequestBody request: RevokeMembershipRequest,
        authentication: Authentication?,
    ) {
        authorization.requireFactoryOwner(authentication)
        require(request.confirmation) { "Expliciete bevestiging is verplicht." }
        users.revokeProductOwner(
            UserId(userId), ProductId(productId), request.reason, authorization.currentUserId(authentication), request.expectedVersion, request.idempotencyKey,
        )
    }
}
