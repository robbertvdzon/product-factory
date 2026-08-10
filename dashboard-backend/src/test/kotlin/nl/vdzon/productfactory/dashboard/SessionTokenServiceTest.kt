package nl.vdzon.productfactory.dashboard

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SessionTokenServiceTest {
    private val sessions = SessionTokenService("test-remember-secret")

    @Test fun `an issued token verifies back to the same email`() {
        val token = sessions.issue("owner@example.test")

        assertEquals("owner@example.test", sessions.verify(token).email)
    }

    @Test fun `a token signed with a different secret is rejected`() {
        val token = SessionTokenService("other-secret").issue("owner@example.test")

        assertFailsWith<IllegalArgumentException> { sessions.verify(token) }
    }

    @Test fun `a tampered email in an otherwise valid token is rejected`() {
        val token = sessions.issue("owner@example.test")
        val decoded = String(java.util.Base64.getUrlDecoder().decode(token))
        val tampered = decoded.replaceFirst("owner@example.test", "attacker@example.test")
        val reencoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(tampered.toByteArray())

        assertFailsWith<IllegalArgumentException> { sessions.verify(reencoded) }
    }

    @Test fun `garbage input is rejected`() {
        assertFailsWith<IllegalArgumentException> { sessions.verify("not-base64-or-anything-sensible") }
    }

    @Test fun `issuing without a configured secret fails fast`() {
        val unconfigured = SessionTokenService("")

        assertFailsWith<IllegalArgumentException> { unconfigured.issue("owner@example.test") }
    }
}
