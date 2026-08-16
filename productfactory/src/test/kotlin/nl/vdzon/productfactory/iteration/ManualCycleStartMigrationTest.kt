package nl.vdzon.productfactory.iteration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class ManualCycleStartMigrationTest {
    @Test
    fun `migration adds nullable closed provenance without backfill`() {
        val dataSource = DriverManagerDataSource(
            "jdbc:h2:mem:manual-start-migration-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa",
            "",
        )
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("24").load().migrate()
        val jdbc = JdbcTemplate(dataSource)
        jdbc.update(
            "insert into product_definition(id, slug, name, mission, guardrails) values ('manual-start-product', 'manual-start-product', 'Migratie', 'Test', 'Test')",
        )
        jdbc.update(
            "insert into shadow_iteration(id, product_slug, sequence_number, focus, status) values ('historical-start', 'manual-start-product', 1, 'Historisch', 'FAILED')",
        )

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()

        assertNull(
            jdbc.queryForObject(
                "select manual_start_origin from shadow_iteration where id = 'historical-start'",
                String::class.java,
            ),
        )
        jdbc.update(
            "insert into shadow_iteration(id, product_slug, sequence_number, focus, status, manual_start_origin) values ('owner-start', 'manual-start-product', 2, 'Vraag', 'FAILED', 'OWNER_INPUT')",
        )
        assertEquals(
            "OWNER_INPUT",
            jdbc.queryForObject(
                "select manual_start_origin from shadow_iteration where id = 'owner-start'",
                String::class.java,
            ),
        )
        assertFails {
            jdbc.update(
                "insert into shadow_iteration(id, product_slug, sequence_number, focus, status, manual_start_origin) values ('invalid-start', 'manual-start-product', 3, 'Vraag', 'FAILED', 'UNKNOWN')",
            )
        }
    }
}
