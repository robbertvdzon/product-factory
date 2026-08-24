package nl.vdzon.productfactory.auth

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class GoogleIdentityVerifierTest {
    private val now = Instant.parse("2026-08-24T18:00:00Z")
    private val decoder = mock(JwtDecoder::class.java)
    private val verifier = SpringGoogleIdentityVerifier(
        decoder,
        "product-factory-client",
        "stakeholder@example.com",
        Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `geldig token levert alleen de toegestane identiteit`() {
        `when`(decoder.decode("valid-token")).thenReturn(jwt())

        assertThat(verifier.verify("valid-token")).isEqualTo(
            VerifiedGoogleIdentity("google-subject", "stakeholder@example.com"),
        )
    }

    @Test
    fun `ongeldige handtekening wordt geweigerd`() {
        `when`(decoder.decode("invalid-signature")).thenThrow(JwtException("signature"))
        assertRejected("invalid-signature")
    }

    @Test
    fun `verkeerde audience wordt geweigerd`() {
        `when`(decoder.decode("wrong-audience")).thenReturn(jwt(audience = listOf("another-client")))
        assertRejected("wrong-audience")
    }

    @Test
    fun `verlopen token wordt geweigerd`() {
        `when`(decoder.decode("expired")).thenReturn(jwt(expiresAt = now.minusSeconds(1)))
        assertRejected("expired")
    }

    @Test
    fun `niet geverifieerd emailadres wordt geweigerd`() {
        `when`(decoder.decode("unverified")).thenReturn(jwt(emailVerified = false))
        assertRejected("unverified")
    }

    @Test
    fun `email buiten gesloten allowlist wordt geweigerd`() {
        `when`(decoder.decode("not-allowed")).thenReturn(jwt(email = "outsider@example.com"))
        assertRejected("not-allowed")
    }

    @Test
    fun `verkeerde issuer wordt geweigerd`() {
        `when`(decoder.decode("wrong-issuer")).thenReturn(jwt(issuer = "https://attacker.invalid"))
        assertRejected("wrong-issuer")
    }

    private fun assertRejected(token: String) {
        assertThatThrownBy { verifier.verify(token) }
            .isInstanceOf(LoginRejected::class.java)
            .hasMessage("Google-login kon niet worden geverifieerd.")
    }

    private fun jwt(
        audience: List<String> = listOf("product-factory-client"),
        expiresAt: Instant = now.plusSeconds(300),
        emailVerified: Boolean = true,
        email: String = "stakeholder@example.com",
        issuer: String = "https://accounts.google.com",
    ): Jwt = Jwt.withTokenValue("test-token")
        .header("alg", "RS256")
        .subject("google-subject")
        .issuer(issuer)
        .audience(audience)
        .issuedAt(now.minusSeconds(10))
        .expiresAt(expiresAt)
        .claim("email", email)
        .claim("email_verified", emailVerified)
        .build()
}
