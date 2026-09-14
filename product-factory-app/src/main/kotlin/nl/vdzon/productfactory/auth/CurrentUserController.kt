package nl.vdzon.productfactory.auth

import nl.vdzon.productfactory.api.advisor.ActingRole
import nl.vdzon.productfactory.api.advisor.GlobalRole
import nl.vdzon.productfactory.api.shared.InvalidCommand
import org.springframework.http.HttpStatus
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Instellingen van de ingelogde gebruiker zelf. Een factory owner kiest hier of hij als factory owner
 * of als product owner werkt; de backend dwingt daarna uitsluitend de rechten van die rol af.
 */
@RestController
@RequestMapping("/api/me")
class CurrentUserController(
    private val users: UserIdentityRepository,
    private val authorization: ProductAuthorizationService,
    private val sessions: ObjectProvider<ProductFactorySessionService>,
) {
    @PutMapping("/acting-role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun actingRole(@RequestBody request: ActingRoleRequest, authentication: Authentication?) {
        val user = authorization.current(authentication)
            ?: throw InvalidCommand("Rolkeuze is alleen beschikbaar wanneer authenticatie aanstaat.")
        if (request.role == ActingRole.FACTORY_OWNER && GlobalRole.FACTORY_OWNER !in user.globalRoles) {
            throw AccessDeniedException("Factory owner-rechten zijn vereist.")
        }
        users.setActingRole(user.id, request.role)
    }

    @PutMapping("/view-as")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun viewAs(@RequestBody request: ViewAsRequest, authentication: Authentication?) {
        val session = authorization.requireAuthenticatedFactoryOwner(authentication)
        sessions.getIfAvailable()?.viewAs(session, request.userId, request.role)
            ?: throw InvalidCommand("Sessieweergave is alleen beschikbaar wanneer authenticatie aanstaat.")
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/view-as")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun clearViewAs(authentication: Authentication?) {
        val session = authorization.requireAuthenticatedFactoryOwner(authentication)
        sessions.getIfAvailable()?.clearViewAs(session)
            ?: throw InvalidCommand("Sessieweergave is alleen beschikbaar wanneer authenticatie aanstaat.")
    }
}
