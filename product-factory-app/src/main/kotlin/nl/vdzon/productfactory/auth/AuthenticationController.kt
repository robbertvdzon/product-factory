package nl.vdzon.productfactory.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
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
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@RestController
@RequestMapping("/api/auth")
class AuthenticationController(
    @Value("\${PF_AUTH_REQUIRED:false}") private val authRequired: Boolean,
    @Value("\${PF_ENVIRONMENT:local}") private val environment: String,
    @Value("\${PF_GOOGLE_CLIENT_ID:}") private val googleClientId: String,
    @Value("\${PF_STAKEHOLDER_EMAILS:}") private val stakeholderEmailsRaw: String,
    @Value("\${PF_DEBUG_TOKEN:}") private val debugToken: String,
    private val verifierProvider: ObjectProvider<GoogleIdentityVerifier>,
    private val sessionServiceProvider: ObjectProvider<ProductFactorySessionService>,
    meterRegistry: MeterRegistry,
) {
    private val rejectedLogins = Counter.builder("product_factory_authentication_failures")
        .description("Aantal geweigerde loginpogingen")
        .register(meterRegistry)
    private val stakeholderEmails = stakeholderEmailsRaw
        .split(',', ';')
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toSet()

    @PostMapping("/google")
    fun googleLogin(
        @RequestBody body: GoogleLoginRequest,
        response: HttpServletResponse,
    ): AuthenticationStatus {
        if (!authRequired) throw LoginRejected("Authenticatie is in deze omgeving uitgeschakeld.")
        val verifier = verifierProvider.getIfAvailable() ?: throw LoginRejected("Login is niet beschikbaar.")
        val sessionService = sessionServiceProvider.getIfAvailable() ?: throw LoginRejected("Login is niet beschikbaar.")
        return withRuntimeConfiguration(sessionService.create(verifier.verify(body.idToken).email, response))
    }

    /**
     * Ongebruikelijke extra ingang naast de normale Google-login: een vast, apart geheim
     * (PF_DEBUG_TOKEN) waarmee een tooling-agent (geen mens) een echte sessie kan bootstrappen
     * zonder OAuth-redirect. Staat standaard uit (lege PF_DEBUG_TOKEN = endpoint geweigerd) en
     * mint alleen een sessie voor een e-mailadres dat al op de stakeholder-allowlist staat.
     */
    @PostMapping("/debug-session")
    fun debugSession(
        @RequestBody body: DebugSessionRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): AuthenticationStatus {
        if (!authRequired) throw LoginRejected("Authenticatie is in deze omgeving uitgeschakeld.")
        if (debugToken.isBlank()) throw LoginRejected("Debug-login is niet geconfigureerd.")
        val provided = request.getHeader(DEBUG_TOKEN_HEADER)
        if (provided.isNullOrBlank() || !constantTimeEquals(provided, debugToken)) {
            throw LoginRejected("Debug-login is geweigerd.")
        }
        val email = (body.email?.trim()?.lowercase() ?: stakeholderEmails.firstOrNull())
            ?: throw LoginRejected("Geen toegestaan e-mailadres geconfigureerd.")
        if (email !in stakeholderEmails) throw LoginRejected("E-mailadres niet toegestaan.")
        val sessionService = sessionServiceProvider.getIfAvailable() ?: throw LoginRejected("Login is niet beschikbaar.")
        return withRuntimeConfiguration(sessionService.create(email, response))
    }

    private fun constantTimeEquals(left: String, right: String): Boolean = MessageDigest.isEqual(
        left.toByteArray(StandardCharsets.UTF_8),
        right.toByteArray(StandardCharsets.UTF_8),
    )

    @GetMapping("/session")
    fun session(request: HttpServletRequest): AuthenticationStatus {
        if (!authRequired) {
            return withRuntimeConfiguration(AuthenticationStatus(authenticated = true, authRequired = false))
        }
        val resolved = sessionServiceProvider.getIfAvailable()?.resolve(request)
            ?: return withRuntimeConfiguration(AuthenticationStatus(authenticated = false, authRequired = true))
        return withRuntimeConfiguration(AuthenticationStatus(
            authenticated = true,
            authRequired = true,
            stakeholderEmail = resolved.stakeholderEmail,
            csrfToken = resolved.csrfToken,
        ))
    }

    @PostMapping("/logout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Void> {
        sessionServiceProvider.getIfAvailable()?.revoke(request, response)
        return ResponseEntity.noContent().build()
    }

    @ExceptionHandler(LoginRejected::class)
    fun rejected(exception: LoginRejected): ResponseEntity<AuthenticationError> {
        rejectedLogins.increment()
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(AuthenticationError("LOGIN_REJECTED", exception.message ?: "Login is geweigerd."))
    }

    private fun withRuntimeConfiguration(status: AuthenticationStatus) = status.copy(
        environment = environment,
        googleClientId = googleClientId.takeIf { authRequired && it.isNotBlank() },
    )

    companion object {
        const val DEBUG_TOKEN_HEADER = "X-PF-Debug-Token"
    }
}
