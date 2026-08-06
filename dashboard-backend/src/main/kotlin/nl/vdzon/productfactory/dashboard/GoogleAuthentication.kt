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
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.net.URL
import java.time.Instant

data class DashboardIdentity(val email: String, val name: String?)

fun interface GoogleTokenVerifier { fun verify(token: String): DashboardIdentity }

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

@Component
class DashboardAuthenticationFilter(
    private val verifier: GoogleTokenVerifier,
    @Value("\${product-factory.dashboard.auth-required:false}") private val authRequired: Boolean,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest) = !authRequired || !request.requestURI.startsWith("/api/") || request.requestURI == "/api/version"
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val token = request.getHeader("Authorization")?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
        if (token == null) {
            response.sendError(401, "Google-token ontbreekt")
            return
        }
        runCatching { verifier.verify(token) }.onSuccess {
            request.setAttribute("dashboardIdentity", it)
            chain.doFilter(request, response)
        }.onFailure { response.sendError(401, "Google-token is ongeldig") }
    }
}
