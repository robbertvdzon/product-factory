package nl.vdzon.productfactory.dashboard

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.RemoteJWKSet
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.server.ResponseStatusException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class DashboardIdentity(val email: String, val name: String?)

fun interface GoogleTokenVerifier { fun verify(token: String): DashboardIdentity }

/**
 * Verifieert een rauw Google ID-token (allowlist + email_verified inbegrepen). Wordt uitsluitend nog
 * gebruikt door [AuthController] om een Google-login in te wisselen voor een eigen sessie-token — het
 * dashboard zelf stuurt Google-tokens nooit meer als Bearer-header mee (zie [SessionTokenService]).
 */
@Component
class NimbusGoogleTokenVerifier(
    @Value("\${product-factory.dashboard.google-client-id:}") private val clientId: String,
    @Value("\${product-factory.dashboard.allowed-emails:}") allowedEmails: String,
) : GoogleTokenVerifier {
    private val allowlist = allowedEmails.split(',').map(String::trim).filter(String::isNotBlank).toSet()
    private val processor = DefaultJWTProcessor<com.nimbusds.jose.proc.SecurityContext>().apply {
        @Suppress("DEPRECATION")
        jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, RemoteJWKSet(URL("https://www.googleapis.com/oauth2/v3/certs")))
    }

    override fun verify(token: String): DashboardIdentity {
        require(clientId.isNotBlank()) { "PF_GOOGLE_CLIENT_ID ontbreekt" }
        val claims: JWTClaimsSet = processor.process(token, null)
        require(claims.issuer in setOf("https://accounts.google.com", "accounts.google.com")) { "Ongeldige issuer" }
        require(clientId in claims.audience) { "Ongeldige audience" }
        require(claims.expirationTime?.toInstant()?.isAfter(Instant.now()) == true) { "Token is verlopen" }
        require(claims.getBooleanClaim("email_verified") == true) { "E-mailadres is niet geverifieerd" }
        val email = claims.getStringClaim("email")?.lowercase() ?: error("E-mailadres ontbreekt")
        require(allowlist.isNotEmpty() && email in allowlist) { "Account heeft geen toegang" }
        return DashboardIdentity(email, claims.getStringClaim("name"))
    }
}

/**
 * Geeft na een geslaagde Google-login een eigen, zelf-ondertekend sessie-token af dat 30 dagen geldig
 * blijft — losgekoppeld van Google's harde 1-uurslimiet op een ID-token, die anders bij elk verzoek
 * opnieuw geverifieerd zou moeten worden. Token-vorm: base64url(email:verlooptOp:hmacSha256Hex), zonder
 * serverside sessieopslag (dezelfde aanpak als de Software Factory's dashboard-backend).
 */
@Component
class SessionTokenService(
    @Value("\${product-factory.dashboard.remember-secret:}") private val rememberSecret: String,
) {
    fun issue(email: String): String {
        require(rememberSecret.isNotBlank()) { "PF_DASHBOARD_REMEMBER_SECRET ontbreekt" }
        val expiresAt = Instant.now().plusSeconds(SESSION_DURATION_SECONDS).epochSecond
        return encode(email, expiresAt)
    }

    fun verify(token: String): DashboardIdentity {
        require(rememberSecret.isNotBlank()) { "PF_DASHBOARD_REMEMBER_SECRET ontbreekt" }
        val parts = runCatching { String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8).split(":", limit = 3) }
            .getOrElse { throw IllegalArgumentException("Ongeldig sessie-token") }
        require(parts.size == 3) { "Ongeldig sessie-token" }
        val (email, expiresAtRaw, signature) = parts
        val expiresAt = expiresAtRaw.toLongOrNull() ?: throw IllegalArgumentException("Ongeldig sessie-token")
        require(constantTimeEquals(signature, hmac(email, expiresAt))) { "Ongeldige sessie-tokenhandtekening" }
        require(expiresAt >= Instant.now().epochSecond) { "Sessie is verlopen" }
        return DashboardIdentity(email, null)
    }

    private fun encode(email: String, expiresAt: Long): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString("$email:$expiresAt:${hmac(email, expiresAt)}".toByteArray(StandardCharsets.UTF_8))

    private fun hmac(email: String, expiresAt: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(rememberSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal("$email:$expiresAt".toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /** [MessageDigest.isEqual] is timing-safe op moderne JDK's: voorkomt dat een aanvaller de handtekening byte voor byte kan raden via responstijd. */
    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(StandardCharsets.UTF_8), b.toByteArray(StandardCharsets.UTF_8))

    companion object {
        private const val SESSION_DURATION_SECONDS = 60L * 60L * 24L * 30L
    }
}

data class GoogleLoginRequest(val idToken: String = "")
data class LoginResponse(val token: String, val username: String)

@RestController
class AuthController(
    private val googleVerifier: GoogleTokenVerifier,
    private val sessions: SessionTokenService,
) {
    /** Ruilt een Google ID-token (1 uur geldig) in voor een eigen, 30 dagen geldig sessie-token. */
    @PostMapping("/api/auth/google")
    fun google(@RequestBody request: GoogleLoginRequest): LoginResponse {
        val identity = runCatching { googleVerifier.verify(request.idToken) }
            .getOrElse { throw ResponseStatusException(HttpStatus.UNAUTHORIZED, it.message ?: "Google-token is ongeldig") }
        return LoginResponse(token = sessions.issue(identity.email), username = identity.email)
    }
}

/** Bewaakt alle /api-routes met het eigen sessie-token (zie [SessionTokenService]), niet meer met een rauw Google-token. */
@Component
class DashboardAuthenticationFilter(
    private val sessions: SessionTokenService,
    @Value("\${product-factory.dashboard.auth-required:false}") private val authRequired: Boolean,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest) =
        !authRequired ||
            request.method == HttpMethod.OPTIONS.name() ||
            !request.requestURI.startsWith("/api/") ||
            request.requestURI == "/api/version" ||
            request.requestURI == "/api/auth/google"
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val token = request.getHeader("Authorization")?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
        if (token == null) {
            response.sendError(401, "Sessie-token ontbreekt")
            return
        }
        runCatching { sessions.verify(token) }.onSuccess {
            request.setAttribute("dashboardIdentity", it)
            chain.doFilter(request, response)
        }.onFailure { response.sendError(401, "Sessie-token is ongeldig") }
    }
}
