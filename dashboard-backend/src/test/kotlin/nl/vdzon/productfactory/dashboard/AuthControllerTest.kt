package nl.vdzon.productfactory.dashboard

import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthControllerTest {
    private val sessions = SessionTokenService("test-remember-secret")

    @Test fun `exchanges a valid google token for a long-lived session token that verifies back`() {
        val google = GoogleTokenVerifier { DashboardIdentity("owner@example.test", "Owner") }
        val controller = AuthController(google, sessions)

        val response = controller.google(GoogleLoginRequest("any-google-token"))

        assertEquals("owner@example.test", response.username)
        assertEquals("owner@example.test", sessions.verify(response.token).email)
    }

    @Test fun `a rejected google token becomes a 401, not a 500`() {
        val google = GoogleTokenVerifier { throw IllegalArgumentException("Account heeft geen toegang") }
        val controller = AuthController(google, sessions)

        val exception = assertFailsWith<ResponseStatusException> { controller.google(GoogleLoginRequest("any-google-token")) }
        assertEquals(401, exception.statusCode.value())
    }
}
