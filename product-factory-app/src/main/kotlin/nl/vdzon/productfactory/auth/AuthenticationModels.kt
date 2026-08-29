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
