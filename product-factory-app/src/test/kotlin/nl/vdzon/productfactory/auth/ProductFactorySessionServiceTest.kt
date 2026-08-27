package nl.vdzon.productfactory.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ProductFactorySessionServiceTest {
    private val now = Instant.parse("2026-08-24T18:00:00Z")
    private val repository = mock(AuthenticationSessionRepository::class.java)

    @Test
    fun `productiecookie is secure httpOnly sameSite en dertig dagen geldig`() {
        val response = MockHttpServletResponse()
        val service = ProductFactorySessionService(
            repository,
            "test-signing-secret-with-at-least-32-characters",
            "production",
            Clock.fixed(now, ZoneOffset.UTC),
        )

        val status = service.create("stakeholder@example.com", response)

        val sessionCookie = response.getHeaders(HttpHeaders.SET_COOKIE).first { it.startsWith("PF_SESSION=") }
        assertThat(sessionCookie)
            .contains("Secure", "HttpOnly", "SameSite=Lax", "Max-Age=2592000")
        assertThat(status.csrfToken).isNotBlank()
    }
}
