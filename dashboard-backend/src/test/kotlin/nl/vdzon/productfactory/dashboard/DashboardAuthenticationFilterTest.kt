package nl.vdzon.productfactory.dashboard

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals

class DashboardAuthenticationFilterTest {
    private val verifier = GoogleTokenVerifier { DashboardIdentity("admin@example.test", "Admin") }
    @Test fun `production api fails closed without bearer token`() {
        val response = MockHttpServletResponse()
        DashboardAuthenticationFilter(verifier, true).doFilter(MockHttpServletRequest("GET", "/api/products"), response, MockFilterChain())
        assertEquals(401, response.status)
    }
    @Test fun `valid token reaches api`() {
        val request = MockHttpServletRequest("GET", "/api/products").apply { addHeader("Authorization", "Bearer valid") }
        val response = MockHttpServletResponse()
        DashboardAuthenticationFilter(verifier, true).doFilter(request, response, MockFilterChain())
        assertEquals(200, response.status)
    }
    @Test fun `cors preflight reaches api without bearer token`() {
        val request = MockHttpServletRequest("OPTIONS", "/api/session")
        val response = MockHttpServletResponse()
        DashboardAuthenticationFilter(verifier, true).doFilter(request, response, MockFilterChain())
        assertEquals(200, response.status)
    }
}
