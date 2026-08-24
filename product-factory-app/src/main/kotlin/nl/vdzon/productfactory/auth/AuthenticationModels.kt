package nl.vdzon.productfactory.auth

data class GoogleLoginRequest(
    val idToken: String,
)

data class AuthenticationStatus(
    val authenticated: Boolean,
    val authRequired: Boolean,
    val stakeholderEmail: String? = null,
    val csrfToken: String? = null,
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
