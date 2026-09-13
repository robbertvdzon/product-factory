package nl.vdzon.productfactory.auth

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class ResolvedSession(
    val sessionId: String,
    val stakeholderEmail: String,
    val userId: String,
    val globalRoles: Set<String>,
    val productMemberships: Set<String>,
    val csrfToken: String?,
    val grantedGlobalRoles: Set<String> = globalRoles,
    val actingRole: String? = null,
    val availableRoles: Set<String> = emptySet(),
)

@Service
@ConditionalOnProperty(name = ["PF_AUTH_REQUIRED"], havingValue = "true")
class ProductFactorySessionService(
    private val repository: AuthenticationSessionRepository,
    private val users: UserIdentityRepository,
    @Value("\${PF_SESSION_SIGNING_SECRET}") signingSecret: String,
    @Value("\${PF_ENVIRONMENT:local}") environment: String,
    @Value("\${PF_FACTORY_OWNER_EMAILS:\${PF_STAKEHOLDER_EMAILS:}}") factoryOwnerEmails: String,
    private val clock: Clock,
) {
    private val signer = SessionSigner(signingSecret)
    private val secureCookies = environment != "local"
    private val random = SecureRandom()
    private val factoryOwners = factoryOwnerEmails.split(',', ';').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    @Transactional
    fun create(email: String, response: HttpServletResponse): AuthenticationStatus {
        val now = clock.instant()
        val user = users.resolveOrCreate(email, email.trim().lowercase() in factoryOwners)
        if (!user.active) throw LoginRejected("Account is niet actief.")
        val sessionId = randomTokenHex(32)
        val csrfToken = randomTokenUrlSafe(32)
        repository.create(
            AuthenticationSession(
                sessionId = sessionId,
                stakeholderEmail = email,
                userId = user.id.value,
                csrfTokenHash = sha256Hex(csrfToken),
                createdAt = now,
                expiresAt = now.plus(SESSION_LIFETIME),
            ),
        )
        addCookie(response, SESSION_COOKIE, signer.cookieValue(sessionId), httpOnly = true, SESSION_LIFETIME)
        addCookie(response, CSRF_COOKIE, csrfToken, httpOnly = false, SESSION_LIFETIME)
        return AuthenticationStatus(
            true, true, user.email, csrfToken, userId = user.id.value,
            globalRoles = user.effectiveGlobalRoles.map { it.name }.toSet(),
            productMemberships = user.memberships.filter { it.status.name == "ACTIVE" }.map { it.productId.value }.toSet(),
            grantedGlobalRoles = user.globalRoles.map { it.name }.toSet(),
            actingRole = user.actingRole.name,
            availableRoles = user.availableRoles,
        )
    }

    fun resolve(request: HttpServletRequest): ResolvedSession? {
        val sessionCookie = request.cookie(SESSION_COOKIE)?.value ?: return null
        val sessionId = signer.verifiedSessionId(sessionCookie) ?: return null
        val session = repository.findActive(sessionId, clock.instant()) ?: return null
        val csrfToken = request.cookie(CSRF_COOKIE)?.value
            ?.takeIf { constantTimeEquals(sha256Hex(it), session.csrfTokenHash) }
        val user = users.get(nl.vdzon.productfactory.api.advisor.UserId(session.userId))
        if (!user.active) return null
        return ResolvedSession(
            session.sessionId, user.email, user.id.value, user.effectiveGlobalRoles.map { it.name }.toSet(),
            user.memberships.filter { it.status.name == "ACTIVE" }.map { it.productId.value }.toSet(), csrfToken,
            grantedGlobalRoles = user.globalRoles.map { it.name }.toSet(),
            actingRole = user.actingRole.name,
            availableRoles = user.availableRoles,
        )
    }

    fun validateCsrf(request: HttpServletRequest, session: ResolvedSession): Boolean {
        val expected = session.csrfToken ?: return false
        val actual = request.getHeader(CSRF_HEADER) ?: return false
        return constantTimeEquals(actual, expected)
    }

    fun revoke(request: HttpServletRequest, response: HttpServletResponse) {
        resolve(request)?.let { repository.revoke(it.sessionId, clock.instant()) }
        addCookie(response, SESSION_COOKIE, "", httpOnly = true, Duration.ZERO)
        addCookie(response, CSRF_COOKIE, "", httpOnly = false, Duration.ZERO)
    }

    private fun addCookie(
        response: HttpServletResponse,
        name: String,
        value: String,
        httpOnly: Boolean,
        maxAge: Duration,
    ) {
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(secureCookies)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build()
                .toString(),
        )
    }

    private fun HttpServletRequest.cookie(name: String): Cookie? = cookies?.firstOrNull { it.name == name }

    private fun randomTokenHex(bytes: Int): String = ByteArray(bytes).also(random::nextBytes).toHex()

    private fun randomTokenUrlSafe(bytes: Int): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(bytes).also(random::nextBytes))

    companion object {
        const val SESSION_COOKIE = "PF_SESSION"
        const val CSRF_COOKIE = "PF_CSRF"
        const val CSRF_HEADER = "X-PF-CSRF"
        private val SESSION_LIFETIME: Duration = Duration.ofDays(30)
    }
}

class SessionSigner(secret: String) {
    private val key = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")

    fun cookieValue(sessionId: String): String = "$sessionId.${sign(sessionId)}"

    fun verifiedSessionId(cookieValue: String): String? {
        val separator = cookieValue.lastIndexOf('.')
        if (separator <= 0 || separator == cookieValue.lastIndex) return null
        val sessionId = cookieValue.substring(0, separator)
        val signature = cookieValue.substring(separator + 1)
        if (!SESSION_ID.matches(sessionId)) return null
        return sessionId.takeIf { constantTimeEquals(signature, sign(sessionId)) }
    }

    private fun sign(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.toByteArray(StandardCharsets.UTF_8)))
    }

    companion object {
        private val SESSION_ID = Regex("[0-9a-f]{64}")
    }
}

private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .toHex()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun constantTimeEquals(left: String, right: String): Boolean = MessageDigest.isEqual(
    left.toByteArray(StandardCharsets.UTF_8),
    right.toByteArray(StandardCharsets.UTF_8),
)
