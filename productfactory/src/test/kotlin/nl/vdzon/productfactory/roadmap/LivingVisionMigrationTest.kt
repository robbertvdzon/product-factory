package nl.vdzon.productfactory.roadmap

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LivingVisionMigrationTest {
    @Test
    fun `migration upgrades a complete legacy schema without changing its process`() {
        val dataSource = DriverManagerDataSource(
            "jdbc:h2:mem:living-vision-migration-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa",
            "",
        )
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("28").load().migrate()
        val jdbc = JdbcTemplate(dataSource)
        jdbc.update(
            "insert into product_definition(id, slug, name, mission, guardrails) values ('legacy-product', 'legacy-product', 'Migratie', 'Test', 'Test')",
        )
        jdbc.update(
            "insert into roadmap_session(id, product_slug, sequence_number, status) values ('legacy-session', 'legacy-product', 1, 'QUEUED')",
        )

        val result = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()

        assertEquals("29", result.targetSchemaVersion.toString())
        assertEquals(
            "legacy-v1",
            jdbc.queryForObject(
                "select roadmap_process_version from product_definition where slug = 'legacy-product'",
                String::class.java,
            ),
        )
        assertEquals(
            "legacy-v1",
            jdbc.queryForObject(
                "select process_version from roadmap_session where id = 'legacy-session'",
                String::class.java,
            ),
        )
        assertNotNull(
            jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'ROADMAP_SESSION_STEP'",
                Long::class.java,
            ),
        )
    }
}
