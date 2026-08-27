package nl.vdzon.productfactory.auth

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

data class AuthenticationSession(
    val sessionId: String,
    val stakeholderEmail: String,
    val csrfTokenHash: String,
    val createdAt: Instant,
    val expiresAt: Instant,
)

@Repository
class AuthenticationSessionRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun create(session: AuthenticationSession) {
        jdbcTemplate.update(
            "INSERT INTO authentication_session " +
                "(session_id, stakeholder_email, csrf_token_hash, created_at, expires_at, revoked_at) " +
                "VALUES (?, ?, ?, ?, ?, NULL)",
            session.sessionId,
            session.stakeholderEmail,
            session.csrfTokenHash,
            Timestamp.from(session.createdAt),
            Timestamp.from(session.expiresAt),
        )
    }

    fun findActive(sessionId: String, now: Instant): AuthenticationSession? = jdbcTemplate.query(
        "SELECT session_id, stakeholder_email, csrf_token_hash, created_at, expires_at " +
            "FROM authentication_session WHERE session_id = ? AND revoked_at IS NULL AND expires_at > ?",
        { resultSet, _ ->
            AuthenticationSession(
                sessionId = resultSet.getString("session_id"),
                stakeholderEmail = resultSet.getString("stakeholder_email"),
                csrfTokenHash = resultSet.getString("csrf_token_hash"),
                createdAt = resultSet.getTimestamp("created_at").toInstant(),
                expiresAt = resultSet.getTimestamp("expires_at").toInstant(),
            )
        },
        sessionId,
        Timestamp.from(now),
    ).singleOrNull()

    fun revoke(sessionId: String, now: Instant) {
        jdbcTemplate.update(
            "UPDATE authentication_session SET revoked_at = ? WHERE session_id = ? AND revoked_at IS NULL",
            Timestamp.from(now),
            sessionId,
        )
    }
}
