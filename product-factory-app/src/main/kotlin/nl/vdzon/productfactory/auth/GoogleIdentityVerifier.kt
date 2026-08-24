package nl.vdzon.productfactory.auth

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

interface GoogleIdentityVerifier {
    fun verify(idToken: String): VerifiedGoogleIdentity
}

@Configuration
class GoogleIdentityConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    @ConditionalOnProperty(name = ["PF_AUTH_REQUIRED"], havingValue = "true")
    fun googleJwtDecoder(restTemplateBuilder: RestTemplateBuilder): JwtDecoder = NimbusJwtDecoder
        .withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
        .restOperations(
            restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .build(),
        )
        .build()
        .also { it.setJwtValidator(JwtTimestampValidator(Duration.ofSeconds(60))) }
}

@Component
@ConditionalOnProperty(name = ["PF_AUTH_REQUIRED"], havingValue = "true")
class SpringGoogleIdentityVerifier(
    private val jwtDecoder: JwtDecoder,
    @Value("\${PF_GOOGLE_CLIENT_ID}") private val clientId: String,
    @Value("\${PF_STAKEHOLDER_EMAILS}") stakeholderEmails: String,
    private val clock: Clock,
) : GoogleIdentityVerifier {
    private val allowedEmails = stakeholderEmails
        .split(',', ';')
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toSet()

    override fun verify(idToken: String): VerifiedGoogleIdentity {
        if (idToken.isBlank()) reject()
        val jwt = try {
            jwtDecoder.decode(idToken)
        } catch (_: JwtException) {
            reject()
        }
        val email = try {
            validateClaims(jwt)
            jwt.getClaimAsString("email")?.trim()?.lowercase().orEmpty()
        } catch (rejected: LoginRejected) {
            throw rejected
        } catch (_: RuntimeException) {
            reject()
        }
        if (email !in allowedEmails) reject()
        return VerifiedGoogleIdentity(
            subject = jwt.subject?.takeIf { it.isNotBlank() } ?: reject(),
            email = email,
        )
    }

    private fun validateClaims(jwt: Jwt) {
        if (jwt.issuer?.toString() !in GOOGLE_ISSUERS) reject()
        if (clientId !in jwt.audience) reject()
        if (jwt.expiresAt == null || !jwt.expiresAt!!.isAfter(clock.instant())) reject()
        if (jwt.getClaimAsBoolean("email_verified") != true) reject()
        if (jwt.getClaimAsString("email").isNullOrBlank()) reject()
    }

    private fun reject(): Nothing = throw LoginRejected("Google-login kon niet worden geverifieerd.")

    companion object {
        private val GOOGLE_ISSUERS = setOf("accounts.google.com", "https://accounts.google.com")
    }
}
