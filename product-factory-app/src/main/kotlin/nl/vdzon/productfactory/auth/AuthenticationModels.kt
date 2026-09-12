package nl.vdzon.productfactory.auth

data class GoogleLoginRequest(
    val idToken: String,
)

data class DebugSessionRequest(
    val email: String? = null,
)

data class AuthenticationStatus(
    val authenticated: Boolean,
    val authRequired: Boolean,
    val stakeholderEmail: String? = null,
    val csrfToken: String? = null,
    val environment: String = "local",
    val googleClientId: String? = null,
    val userId: String? = null,
    val globalRoles: Set<String> = emptySet(),
    val productMemberships: Set<String> = emptySet(),
    val grantedGlobalRoles: Set<String> = emptySet(),
    val actingRole: String? = null,
)

data class ActingRoleRequest(
    val role: nl.vdzon.productfactory.api.advisor.ActingRole,
)

data class AuthenticationError(
    val code: String,
    val message: String,
)

class LoginRejected(message: String) : RuntimeException(message)

data class VerifiedGoogleIdentity(
    val subject: String,
    val email: String,
)
