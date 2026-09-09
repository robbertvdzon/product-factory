package nl.vdzon.productfactory.auth

import nl.vdzon.productfactory.api.advisor.GlobalRole
import nl.vdzon.productfactory.api.advisor.UserDetails
import nl.vdzon.productfactory.api.advisor.UserId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ProductFactorySessionServiceTest {
    private val now = Instant.parse("2026-08-24T18:00:00Z")
    private val repository = mock(AuthenticationSessionRepository::class.java)
    private val users = mock(UserIdentityRepository::class.java)

    @Test
    fun `productiecookie is secure httpOnly sameSite en dertig dagen geldig`() {
        val response = MockHttpServletResponse()
        `when`(users.resolveOrCreate("stakeholder@example.com", true)).thenReturn(
            UserDetails(UserId("user-1"), "stakeholder@example.com", null, true, setOf(GlobalRole.FACTORY_OWNER), emptyList()),
        )
        val service = ProductFactorySessionService(
            repository,
            users,
            "test-signing-secret-with-at-least-32-characters",
            "production",
            "stakeholder@example.com",
            Clock.fixed(now, ZoneOffset.UTC),
        )

        val status = service.create("stakeholder@example.com", response)

        val sessionCookie = response.getHeaders(HttpHeaders.SET_COOKIE).first { it.startsWith("PF_SESSION=") }
        assertThat(sessionCookie)
            .contains("Secure", "HttpOnly", "SameSite=Lax", "Max-Age=2592000")
        assertThat(status.csrfToken).isNotBlank()
    }
}
