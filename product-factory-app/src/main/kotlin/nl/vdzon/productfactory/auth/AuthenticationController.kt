package nl.vdzon.productfactory.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthenticationController(
    @Value("\${PF_AUTH_REQUIRED:false}") private val authRequired: Boolean,
    private val verifierProvider: ObjectProvider<GoogleIdentityVerifier>,
    private val sessionServiceProvider: ObjectProvider<ProductFactorySessionService>,
) {
    @PostMapping("/google")
    fun googleLogin(
        @RequestBody body: GoogleLoginRequest,
        response: HttpServletResponse,
    ): AuthenticationStatus {
        if (!authRequired) throw LoginRejected("Authenticatie is in deze omgeving uitgeschakeld.")
        val verifier = verifierProvider.getIfAvailable() ?: throw LoginRejected("Login is niet beschikbaar.")
        val sessionService = sessionServiceProvider.getIfAvailable() ?: throw LoginRejected("Login is niet beschikbaar.")
        return sessionService.create(verifier.verify(body.idToken).email, response)
    }

    @GetMapping("/session")
    fun session(request: HttpServletRequest): AuthenticationStatus {
        if (!authRequired) return AuthenticationStatus(authenticated = true, authRequired = false)
        val resolved = sessionServiceProvider.getIfAvailable()?.resolve(request)
            ?: return AuthenticationStatus(authenticated = false, authRequired = true)
        return AuthenticationStatus(
            authenticated = true,
            authRequired = true,
            stakeholderEmail = resolved.stakeholderEmail,
            csrfToken = resolved.csrfToken,
        )
    }

    @PostMapping("/logout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Void> {
        sessionServiceProvider.getIfAvailable()?.revoke(request, response)
        return ResponseEntity.noContent().build()
    }

    @ExceptionHandler(LoginRejected::class)
    fun rejected(exception: LoginRejected): ResponseEntity<AuthenticationError> = ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(AuthenticationError("LOGIN_REJECTED", exception.message ?: "Login is geweigerd."))
}
