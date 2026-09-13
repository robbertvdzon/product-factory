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
import org.springframework.test.web.servlet.put
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

    @Test
    fun `factory owner kan als product owner werken en weer terugschakelen`() {
        val ownerLogin = login()
        val session = cookie(ownerLogin, ProductFactorySessionService.SESSION_COOKIE)
        val csrf = cookie(ownerLogin, ProductFactorySessionService.CSRF_COOKIE)
        val token = objectMapper.readTree(ownerLogin.contentAsByteArray).get("csrfToken").asText()
        fun createProduct(id: String) = mockMvc.post("/api/products") {
            header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
            header(ProductFactorySessionService.CSRF_HEADER, token)
            cookie(session, csrf)
            contentType = MediaType.APPLICATION_JSON
            content = """{"requestedId":"$id","name":"$id","idempotencyKey":"create-$id"}"""
        }.andExpect { status { isCreated() } }
        fun switchTo(role: String) = mockMvc.put("/api/me/acting-role") {
            header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
            header(ProductFactorySessionService.CSRF_HEADER, token)
            cookie(session, csrf)
            contentType = MediaType.APPLICATION_JSON
            content = """{"role":"$role"}"""
        }
        createProduct("role-own-product")
        createProduct("role-other-product")
        val factoryOwner = userIdentities.findByEmail("stakeholder@example.com")!!
        userIdentities.grantProductOwner(
            factoryOwner.id, nl.vdzon.productfactory.api.shared.ProductId("role-own-product"), factoryOwner.id,
            0, "grant-role-own-product",
        )

        try {
            switchTo("PRODUCT_OWNER").andExpect { status { isNoContent() } }

            mockMvc.get("/api/auth/session") { cookie(session, csrf) }.andExpect {
                status { isOk() }
                jsonPath("$.actingRole") { value("PRODUCT_OWNER") }
                jsonPath("$.globalRoles.length()") { value(0) }
                jsonPath("$.grantedGlobalRoles[0]") { value("FACTORY_OWNER") }
            }
            mockMvc.get("/api/admin/users") { cookie(session, csrf) }.andExpect { status { isForbidden() } }
            mockMvc.get("/api/products/role-other-product") { cookie(session, csrf) }.andExpect { status { isForbidden() } }
            mockMvc.get("/api/products/role-own-product") { cookie(session, csrf) }.andExpect { status { isOk() } }
            mockMvc.get("/api/products") { cookie(session, csrf) }.andExpect {
                status { isOk() }
                jsonPath("$[*].id") { value(org.hamcrest.Matchers.contains("role-own-product")) }
            }

            switchTo("FACTORY_OWNER").andExpect { status { isNoContent() } }

            mockMvc.get("/api/auth/session") { cookie(session, csrf) }.andExpect {
                jsonPath("$.actingRole") { value("FACTORY_OWNER") }
                jsonPath("$.globalRoles[0]") { value("FACTORY_OWNER") }
            }
            mockMvc.get("/api/admin/users") { cookie(session, csrf) }.andExpect { status { isOk() } }
            mockMvc.get("/api/products/role-other-product") { cookie(session, csrf) }.andExpect { status { isOk() } }
        } finally {
            userIdentities.setActingRole(factoryOwner.id, nl.vdzon.productfactory.api.advisor.ActingRole.FACTORY_OWNER)
        }
    }

    @Test
    fun `product owner kan zichzelf geen factory owner-rol geven`() {
        val login = mockMvc.post("/api/auth/google") {
            header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"idToken":"unassigned-google-token"}"""
        }.andReturn().response
        val token = objectMapper.readTree(login.contentAsByteArray).get("csrfToken").asText()
        mockMvc.put("/api/me/acting-role") {
            header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
            header(ProductFactorySessionService.CSRF_HEADER, token)
            cookie(
                cookie(login, ProductFactorySessionService.SESSION_COOKIE),
                cookie(login, ProductFactorySessionService.CSRF_COOKIE),
            )
            contentType = MediaType.APPLICATION_JSON
            content = """{"role":"FACTORY_OWNER"}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `uitgenodigde architect buiten allowlist logt in en ingetrokken rol blokkeert API`() {
        val factoryLogin=login()
        val factory=userIdentities.findByEmail("stakeholder@example.com")!!
        val id="architect-${java.util.UUID.randomUUID().toString().take(8)}"
        val factoryCsrf=objectMapper.readTree(factoryLogin.contentAsByteArray).path("csrfToken").asText()
        mockMvc.post("/api/products") {
            header(HttpHeaders.ORIGIN,FRONTEND_ORIGIN)
            header(ProductFactorySessionService.CSRF_HEADER,factoryCsrf)
            cookie(cookie(factoryLogin,ProductFactorySessionService.SESSION_COOKIE),cookie(factoryLogin,ProductFactorySessionService.CSRF_COOKIE))
            contentType=MediaType.APPLICATION_JSON
            content="""{"requestedId":"$id","name":"Architect project","idempotencyKey":"$id"}"""
        }.andExpect { status { isCreated() } }
        val invited=userIdentities.createForAdministration("$id@example.test","invite-$id")
        userIdentities.grantProductOwner(invited.id,nl.vdzon.productfactory.api.shared.ProductId(id),factory.id,0,"grant-$id",nl.vdzon.productfactory.api.advisor.ProductMembershipRole.ARCHITECT)
        val now=Instant.now()
        `when`(jwtDecoder.decode("invited-token")).thenReturn(Jwt.withTokenValue("invited-token").header("alg","RS256")
            .subject(id).issuer("https://accounts.google.com").audience(listOf("product-factory-client"))
            .issuedAt(now.minusSeconds(10)).expiresAt(now.plusSeconds(300)).claim("email","$id@example.test").claim("email_verified",true).build())
        val response=mockMvc.post("/api/auth/google") {
            header(HttpHeaders.ORIGIN,FRONTEND_ORIGIN);contentType=MediaType.APPLICATION_JSON
            content="""{"idToken":"invited-token"}"""
        }.andExpect { status { isOk() };jsonPath("$.actingRole") { value("ARCHITECT") };jsonPath("$.availableRoles[0]") { value("ARCHITECT") } }.andReturn().response
        val session=cookie(response,ProductFactorySessionService.SESSION_COOKIE)
        mockMvc.get("/api/products/$id/governance") { cookie(session) }.andExpect { status { isOk() } }
        mockMvc.get("/api/admin/users") { cookie(session) }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/ai/tasks") { cookie(session) }.andExpect { status { isForbidden() } }
        val token=objectMapper.readTree(response.contentAsByteArray).path("csrfToken").asText()
        mockMvc.post("/api/products/$id/conversations") {
            header(HttpHeaders.ORIGIN,FRONTEND_ORIGIN);header(ProductFactorySessionService.CSRF_HEADER,token)
            cookie(session,cookie(response,ProductFactorySessionService.CSRF_COOKIE));contentType=MediaType.APPLICATION_JSON
            content="""{"title":"Geen PO rol","idempotencyKey":"idea-$id"}"""
        }.andExpect { status { isForbidden() } }
        userIdentities.revokeProductOwner(invited.id,nl.vdzon.productfactory.api.shared.ProductId(id),"Toegang ingetrokken",factory.id,1,"revoke-$id",nl.vdzon.productfactory.api.advisor.ProductMembershipRole.ARCHITECT)
        mockMvc.get("/api/products/$id/governance") { cookie(session) }.andExpect { status { isForbidden() } }
        mockMvc.post("/api/auth/google") {
            header(HttpHeaders.ORIGIN,FRONTEND_ORIGIN);contentType=MediaType.APPLICATION_JSON;content="""{"idToken":"invited-token"}"""
        }.andExpect { status { isUnauthorized() } }
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
