package nl.vdzon.productfactory.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionSignerTest {
    private val signer = SessionSigner("test-signing-secret-with-at-least-32-characters")
    private val sessionId = "a".repeat(64)

    @Test
    fun `ondertekende sessie is verifieerbaar`() {
        assertThat(signer.verifiedSessionId(signer.cookieValue(sessionId))).isEqualTo(sessionId)
    }

    @Test
    fun `gewijzigde sessie of handtekening wordt geweigerd`() {
        val signed = signer.cookieValue(sessionId)
        assertThat(signer.verifiedSessionId("b${signed.drop(1)}")).isNull()
        assertThat(signer.verifiedSessionId("${signed.dropLast(1)}x")).isNull()
        assertThat(signer.verifiedSessionId("geen-geldige-cookie")).isNull()
    }
}
