package nl.vdzon.productfactory.foundation

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementSetter
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
class InstantAwareJdbcConfiguration {
    @Bean
    fun jdbcTemplate(dataSource: DataSource): JdbcTemplate = InstantAwareJdbcTemplate(dataSource)
}

/**
 * Spring JDBC does not assign a SQL type to java.time.Instant. PostgreSQL therefore rejects an
 * Instant passed through a vararg JdbcTemplate operation, while H2 accepts it. Normalize every
 * positional argument centrally so all capability modules use the same production-safe binding.
 */
internal class InstantAwareJdbcTemplate(dataSource: DataSource) : JdbcTemplate(dataSource) {
    override fun newArgPreparedStatementSetter(args: Array<out Any?>?): PreparedStatementSetter =
        super.newArgPreparedStatementSetter(args?.map { argument ->
            if (argument is Instant) Timestamp.from(argument) else argument
        }?.toTypedArray())
}
