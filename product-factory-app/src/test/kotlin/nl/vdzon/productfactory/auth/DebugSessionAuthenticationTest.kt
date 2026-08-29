package nl.vdzon.productfactory.auth

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest(
    properties = [
        "PF_ENVIRONMENT=local",
        "PF_AUTH_REQUIRED=true",
        "PF_GOOGLE_CLIENT_ID=product-factory-client",
        "PF_STAKEHOLDER_EMAILS=stakeholder@example.com,other@example.com",
        "PF_SESSION_SIGNING_SECRET=test-signing-secret-with-at-least-32-characters",
        "PF_PUBLIC_FRONTEND_URL=http://localhost:8082",
        "PF_DEBUG_TOKEN=test-debug-token",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DebugSessionAuthenticationTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `debug-session met geldig token en toegestaan mailadres bootstrapt een echte sessie`() {
        mockMvc.post("/api/auth/debug-session") {
            header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
            header(AuthenticationController.DEBUG_TOKEN_HEADER, "test-debug-token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"other@example.com"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.authenticated") { value(true) }
            jsonPath("$.stakeholderEmail") { value("other@example.com") }
            jsonPath("$.csrfToken") { isNotEmpty() }
        }
    }

    @Test
    fun `debug-session zonder e-mailadres kiest het eerste toegestane mailadres`() {
        mockMvc.post("/api/auth/debug-session") {
            header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
            header(AuthenticationController.DEBUG_TOKEN_HEADER, "test-debug-token")
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isOk() }
            jsonPath("$.stakeholderEmail") { value("stakeholder@example.com") }
        }
    }

    @Test
    fun `debug-session met verkeerd token wordt geweigerd`() {
        mockMvc.post("/api/auth/debug-session") {
            header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
            header(AuthenticationController.DEBUG_TOKEN_HEADER, "wrong-token")
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("LOGIN_REJECTED") }
        }
    }

    @Test
    fun `debug-session met niet-toegestaan mailadres wordt geweigerd`() {
        mockMvc.post("/api/auth/debug-session") {
            header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
            header(AuthenticationController.DEBUG_TOKEN_HEADER, "test-debug-token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"stranger@example.com"}"""
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    companion object {
        private const val FRONTEND_ORIGIN = "http://localhost:8082"
    }
}
