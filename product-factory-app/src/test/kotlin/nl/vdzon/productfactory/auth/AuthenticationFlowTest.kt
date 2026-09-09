package nl.vdzon.productfactory.auth

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant

@SpringBootTest(
    properties = [
        "PF_ENVIRONMENT=local",
        "PF_AUTH_REQUIRED=true",
        "PF_GOOGLE_CLIENT_ID=product-factory-client",
        "PF_STAKEHOLDER_EMAILS=stakeholder@example.com,owner-without-product@example.com",
        "PF_FACTORY_OWNER_EMAILS=stakeholder@example.com",
        "PF_SESSION_SIGNING_SECRET=test-signing-secret-with-at-least-32-characters",
        "PF_PUBLIC_FRONTEND_URL=http://localhost:8082",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationFlowTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val sessionRepository: AuthenticationSessionRepository,
    @Autowired private val userIdentities: UserIdentityRepository,
) {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @BeforeEach
    fun validGoogleToken() {
        val now = Instant.now()
        `when`(jwtDecoder.decode("valid-google-token")).thenReturn(
            Jwt.withTokenValue("valid-google-token")
                .header("alg", "RS256")
                .subject("google-subject")
                .issuer("https://accounts.google.com")
                .audience(listOf("product-factory-client"))
                .issuedAt(now.minusSeconds(10))
                .expiresAt(now.plusSeconds(300))
                .claim("email", "stakeholder@example.com")
                .claim("email_verified", true)
                .build(),
        )
        `when`(jwtDecoder.decode("unassigned-google-token")).thenReturn(
            Jwt.withTokenValue("unassigned-google-token")
                .header("alg", "RS256")
                .subject("unassigned-subject")
                .issuer("https://accounts.google.com")
                .audience(listOf("product-factory-client"))
                .issuedAt(now.minusSeconds(10))
                .expiresAt(now.plusSeconds(300))
                .claim("email", "owner-without-product@example.com")
                .claim("email_verified", true)
                .build(),
        )
    }

    @Test
    fun `login sessiestatus en logout vormen een begrensde flow`() {
        val login = mockMvc.post("/api/auth/google") {
            header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"idToken":"valid-google-token"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.authenticated") { value(true) }
            jsonPath("$.authRequired") { value(true) }
            jsonPath("$.stakeholderEmail") { value("stakeholder@example.com") }
            jsonPath("$.csrfToken") { isNotEmpty() }
            jsonPath("$.environment") { value("local") }
            jsonPath("$.googleClientId") { value("product-factory-client") }
        }.andReturn().response

        val sessionCookie = cookie(login, ProductFactorySessionService.SESSION_COOKIE)
        val csrfCookie = cookie(login, ProductFactorySessionService.CSRF_COOKIE)
        val csrfToken = objectMapper.readTree(login.contentAsByteArray).get("csrfToken").asText()
        val setCookies = login.getHeaders(HttpHeaders.SET_COOKIE)
        assertThat(setCookies.first { it.startsWith("PF_SESSION=") })
            .contains("HttpOnly", "SameSite=Lax")
            .doesNotContain("Secure")
        assertThat(setCookies.first { it.startsWith("PF_CSRF=") })
            .doesNotContain("HttpOnly")

        mockMvc.get("/api/auth/session") {
            cookie(sessionCookie, csrfCookie)
        }.andExpect {
            status { isOk() }
            jsonPath("$.authenticated") { value(true) }
            jsonPath("$.csrfToken") { value(csrfToken) }
        }

        val logout = mockMvc.post("/api/auth/logout") {
            header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
            header(ProductFactorySessionService.CSRF_HEADER, csrfToken)
            cookie(sessionCookie, csrfCookie)
        }.andExpect {
            status { isNoContent() }
        }.andReturn().response
        assertThat(logout.getHeaders(HttpHeaders.SET_COOKIE)).hasSize(2)

        mockMvc.get("/api/auth/session") {
            cookie(sessionCookie, csrfCookie)
        }.andExpect {
            status { isOk() }
            jsonPath("$.authenticated") { value(false) }
        }
    }

    @Test
    fun `een nieuwe login laat een bestaande browsersessie actief`() {
        val firstLogin = login()
        val firstSessionCookie = cookie(firstLogin, ProductFactorySessionService.SESSION_COOKIE)
        val firstCsrfCookie = cookie(firstLogin, ProductFactorySessionService.CSRF_COOKIE)

        login()

        mockMvc.get("/api/auth/session") {
            cookie(firstSessionCookie, firstCsrfCookie)
        }.andExpect {
            status { isOk() }
            jsonPath("$.authenticated") { value(true) }
            jsonPath("$.stakeholderEmail") { value("stakeholder@example.com") }
        }
    }

    @Test
    fun `mutatie met verkeerde origin wordt geweigerd`() {
        mockMvc.post("/api/auth/google") {
            header(HttpHeaders.ORIGIN, "https://attacker.invalid")
            contentType = MediaType.APPLICATION_JSON
            content = """{"idToken":"valid-google-token"}"""
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `verlopen sessie wordt niet meer geaccepteerd`() {
        val expiredId = "c".repeat(64)
        sessionRepository.create(
            AuthenticationSession(
                sessionId = expiredId,
                stakeholderEmail = "stakeholder@example.com",
                userId = userIdentities.resolveOrCreate("stakeholder@example.com", true).id.value,
                csrfTokenHash = "d".repeat(64),
                createdAt = Instant.EPOCH,
                expiresAt = Instant.EPOCH.plusSeconds(60),
            ),
        )
        val cookieValue = SessionSigner("test-signing-secret-with-at-least-32-characters").cookieValue(expiredId)

        mockMvc.get("/api/auth/session") {
            cookie(Cookie(ProductFactorySessionService.SESSION_COOKIE, cookieValue))
        }.andExpect {
            status { isOk() }
            jsonPath("$.authenticated") { value(false) }
        }
    }

    @Test
    fun `ontbrekende sessie geeft uniforme 401 op beschermde routes`() {
        mockMvc.get("/api/private-route-that-does-not-exist")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("UNAUTHENTICATED") }
            }
    }

    @Test
    fun `logout met fout csrf token wordt geweigerd`() {
        val login = login()
        mockMvc.post("/api/auth/logout") {
            header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
            header(ProductFactorySessionService.CSRF_HEADER, "wrong-token")
            cookie(
                cookie(login, ProductFactorySessionService.SESSION_COOKIE),
                cookie(login, ProductFactorySessionService.CSRF_COOKIE),
            )
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `debug-session zonder geconfigureerd token wordt geweigerd`() {
        mockMvc.post("/api/auth/debug-session") {
            header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
            header(AuthenticationController.DEBUG_TOKEN_HEADER, "whatever")
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("LOGIN_REJECTED") }
        }
    }

    @Test
    fun `ingelogde gebruiker zonder lidmaatschap ziet geen product en krijgt 403 op directe toegang`() {
        val ownerLogin = login()
        val ownerSession = cookie(ownerLogin, ProductFactorySessionService.SESSION_COOKIE)
        val ownerCsrf = cookie(ownerLogin, ProductFactorySessionService.CSRF_COOKIE)
        val ownerToken = objectMapper.readTree(ownerLogin.contentAsByteArray).get("csrfToken").asText()
        mockMvc.post("/api/products") {
            header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
            header(ProductFactorySessionService.CSRF_HEADER, ownerToken)
            cookie(ownerSession, ownerCsrf)
            contentType = MediaType.APPLICATION_JSON
            content = """{"requestedId":"private-product","name":"Privéproduct","idempotencyKey":"create-private-product"}"""
        }.andExpect { status { isCreated() } }

        val login = mockMvc.post("/api/auth/google") {
            header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"idToken":"unassigned-google-token"}"""
        }.andReturn().response
        val session = cookie(login, ProductFactorySessionService.SESSION_COOKIE)
        val csrf = cookie(login, ProductFactorySessionService.CSRF_COOKIE)

        mockMvc.get("/api/products") { cookie(session, csrf) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
        mockMvc.get("/api/products/private-product") { cookie(session, csrf) }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/products/private-product/conversations") { cookie(session, csrf) }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/admin/users") { cookie(session, csrf) }.andExpect { status { isForbidden() } }

        val factoryOwner = userIdentities.findByEmail("stakeholder@example.com")!!
        val productOwner = userIdentities.findByEmail("owner-without-product@example.com")!!
        userIdentities.grantProductOwner(
            productOwner.id, nl.vdzon.productfactory.api.shared.ProductId("private-product"), factoryOwner.id,
            0, "grant-private-product",
        )
        mockMvc.get("/api/products/private-product/conversations") { cookie(session, csrf) }.andExpect { status { isOk() } }

        userIdentities.revokeProductOwner(
            productOwner.id, nl.vdzon.productfactory.api.shared.ProductId("private-product"), "Toegangstest afgerond.", factoryOwner.id,
            1, "revoke-private-product",
        )
        mockMvc.get("/api/products/private-product/conversations") { cookie(session, csrf) }.andExpect { status { isForbidden() } }
    }

    private fun login() = mockMvc.post("/api/auth/google") {
        header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
        contentType = MediaType.APPLICATION_JSON
        content = """{"idToken":"valid-google-token"}"""
    }.andReturn().response

    private fun cookie(response: org.springframework.mock.web.MockHttpServletResponse, name: String): Cookie {
        val header = response.getHeaders(HttpHeaders.SET_COOKIE).first { it.startsWith("$name=") }
        return Cookie(name, header.substringAfter('=').substringBefore(';'))
    }

    companion object {
        private const val FRONTEND_ORIGIN = "http://localhost:8082"
    }
}
