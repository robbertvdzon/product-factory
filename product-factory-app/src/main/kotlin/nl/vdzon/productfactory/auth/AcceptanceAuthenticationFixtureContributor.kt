package nl.vdzon.productfactory.auth

import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContext
import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContributor
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
@Profile("acceptance")
class AcceptanceAuthenticationFixtureContributor(
    private val jdbcTemplate: JdbcTemplate,
) : AcceptanceFixtureContributor {
    override val key: String = "authentication-sessions"
    override val order: Int = 50

    override fun reset(context: AcceptanceFixtureContext) {
        jdbcTemplate.update("DELETE FROM authentication_session")
    }
}
