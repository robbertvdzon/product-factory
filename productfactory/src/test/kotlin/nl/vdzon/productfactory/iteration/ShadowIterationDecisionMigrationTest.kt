package nl.vdzon.productfactory.iteration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFails

class ShadowIterationDecisionMigrationTest {
    @Test
    fun `migration adds only the five decision fields without backfilling and enforces one record per iteration`() {
        val dataSource = DriverManagerDataSource(
            "jdbc:h2:mem:decision-migration-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa",
            "",
        )
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target("19")
            .load()
            .migrate()
        val jdbc = JdbcTemplate(dataSource)
        jdbc.update(
            "insert into product_definition(id, slug, name, mission, guardrails) values ('migration-product', 'migration-product', 'Migratie', 'Test', 'Test')",
        )
        jdbc.update(
            "insert into shadow_iteration(id, product_slug, sequence_number, focus, status) values ('historical-iteration', 'migration-product', 1, 'Historisch', 'FAILED')",
        )

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        val columns = jdbc.queryForList(
            """select lower(column_name) from information_schema.columns
                where lower(table_name) = 'shadow_iteration_decision' order by ordinal_position""".trimIndent(),
            String::class.java,
        )
        assertEquals(listOf("iteration_id", "actor_type", "mechanism", "reason_code", "decided_at"), columns)
        assertEquals(0, jdbc.queryForObject("select count(*) from shadow_iteration_decision", Int::class.java))

        val decidedAt = Timestamp.from(Instant.parse("2026-08-12T12:34:56Z"))
        jdbc.update(
            """insert into shadow_iteration_decision(iteration_id, actor_type, mechanism, reason_code, decided_at)
                values ('historical-iteration', 'HUMAN', 'MANUAL_CANCELLATION', 'MANUALLY_CANCELLED', ?)""".trimIndent(),
            decidedAt,
        )
        val stored = jdbc.queryForMap(
            "select iteration_id, actor_type, mechanism, reason_code, decided_at from shadow_iteration_decision where iteration_id = 'historical-iteration'",
        )
        assertEquals("HUMAN", stored["ACTOR_TYPE"] ?: stored["actor_type"])
        assertEquals("MANUAL_CANCELLATION", stored["MECHANISM"] ?: stored["mechanism"])
        assertEquals("MANUALLY_CANCELLED", stored["REASON_CODE"] ?: stored["reason_code"])
        assertFails {
            jdbc.update(
                """insert into shadow_iteration_decision(iteration_id, actor_type, mechanism, reason_code, decided_at)
                    values ('historical-iteration', 'HUMAN', 'MANUAL_CANCELLATION', 'MANUALLY_CANCELLED', ?)""".trimIndent(),
                decidedAt,
            )
        }
    }
}
