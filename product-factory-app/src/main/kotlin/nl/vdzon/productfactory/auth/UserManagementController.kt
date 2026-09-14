package nl.vdzon.productfactory.auth

import nl.vdzon.productfactory.api.advisor.UserId
import nl.vdzon.productfactory.api.advisor.ProductMembershipRole
import nl.vdzon.productfactory.api.shared.ProductId
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

data class CreateOrFindUserRequest(val email: String, val idempotencyKey: String)
data class MembershipMutationRequest(val expectedVersion: Long, val idempotencyKey: String, val role: ProductMembershipRole = ProductMembershipRole.PRODUCT_OWNER)
data class RevokeMembershipRequest(val reason: String, val confirmation: Boolean, val expectedVersion: Long, val idempotencyKey: String, val role: ProductMembershipRole = ProductMembershipRole.PRODUCT_OWNER)
data class DeleteUserRequest(val confirmation: String, val reason: String = "Verwijderd door factory owner")

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

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable userId: String, @RequestBody request: DeleteUserRequest, authentication: Authentication?) {
        authorization.requireFactoryOwner(authentication)
        val target = users.get(UserId(userId))
        if (target.email == ProductFactorySessionService.DEBUG_AGENT_EMAIL) throw IllegalArgumentException("Het technische debug-account kan niet worden verwijderd.")
        if (request.confirmation.trim().lowercase() != target.email) throw IllegalArgumentException("Typ het exacte e-mailadres om de gebruiker te verwijderen.")
        val actor = authorization.currentUserId(authentication)
        if (actor == target.id) throw IllegalArgumentException("Je kunt je eigen actieve factory-owneraccount niet verwijderen.")
        users.deleteForAdministration(target.id, actor, request.reason)
    }

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
            UserId(userId), ProductId(productId), authorization.currentUserId(authentication), request.expectedVersion, request.idempotencyKey, request.role,
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
            UserId(userId), ProductId(productId), request.reason, authorization.currentUserId(authentication), request.expectedVersion, request.idempotencyKey, request.role,
        )
    }
}
