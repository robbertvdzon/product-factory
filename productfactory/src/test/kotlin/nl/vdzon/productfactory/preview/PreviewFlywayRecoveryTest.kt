package nl.vdzon.productfactory.preview

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.exception.FlywayValidateException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class PreviewFlywayRecoveryTest {
    @Test
    fun `PR preview rebuilds its disposable schema after validation mismatch`(@TempDir migrations: Path) {
        val dataSource = reportingDataSource(dataSource(), VALID_PREVIEW_DATABASE)
        val migration = migrations.resolve("V1__preview_state.sql")
        Files.writeString(migration, OLD_MIGRATION)
        flyway(dataSource, migrations).migrate()
        Files.writeString(migration, CURRENT_MIGRATION)

        PreviewFlywayMigrationStrategy(prPreviewConfig()).migrate(flyway(dataSource, migrations))

        val jdbc = JdbcTemplate(dataSource)
        assertEquals("current", jdbc.queryForObject("select marker from preview_current_state", String::class.java))
        assertFails { jdbc.queryForObject("select marker from preview_old_state", String::class.java) }
    }

    @Test
    fun `PR preview never cleans a Flyway target with a different JDBC URL`(@TempDir migrations: Path) {
        val dataSource = dataSource()
        val migration = migrations.resolve("V1__preview_state.sql")
        Files.writeString(migration, OLD_MIGRATION)
        flyway(dataSource, migrations).migrate()
        Files.writeString(migration, CURRENT_MIGRATION)

        assertFailsWith<FlywayValidateException> {
            PreviewFlywayMigrationStrategy(prPreviewConfig()).migrate(flyway(dataSource, migrations))
        }

        val jdbc = JdbcTemplate(dataSource)
        assertEquals("old", jdbc.queryForObject("select marker from preview_old_state", String::class.java))
        assertFails { jdbc.queryForObject("select marker from preview_current_state", String::class.java) }
    }

    @Test
    fun `PR preview never cleans Flyway schemas outside public`(@TempDir migrations: Path) {
        val dataSource = reportingDataSource(dataSource(), VALID_PREVIEW_DATABASE)
        JdbcTemplate(dataSource).execute("create schema preview_other")
        val migration = migrations.resolve("V1__preview_state.sql")
        Files.writeString(migration, OLD_MIGRATION)
        flyway(dataSource, migrations, "preview_other").migrate()
        Files.writeString(migration, CURRENT_MIGRATION)

        assertFailsWith<FlywayValidateException> {
            PreviewFlywayMigrationStrategy(prPreviewConfig()).migrate(
                flyway(dataSource, migrations, "preview_other"),
            )
        }

        val jdbc = JdbcTemplate(dataSource)
        assertEquals(
            "old",
            jdbc.queryForObject("select marker from preview_other.preview_old_state", String::class.java),
        )
        assertFails {
            jdbc.queryForObject("select marker from preview_other.preview_current_state", String::class.java)
        }
    }

    @Test
    fun `standing acceptance remains fail closed after validation mismatch`(@TempDir migrations: Path) {
        val dataSource = dataSource()
        val migration = migrations.resolve("V1__preview_state.sql")
        Files.writeString(migration, OLD_MIGRATION)
        flyway(dataSource, migrations).migrate()
        Files.writeString(migration, CURRENT_MIGRATION)

        assertFailsWith<FlywayValidateException> {
            PreviewFlywayMigrationStrategy(acceptanceConfig()).migrate(flyway(dataSource, migrations))
        }

        val jdbc = JdbcTemplate(dataSource)
        assertEquals("old", jdbc.queryForObject("select marker from preview_old_state", String::class.java))
        assertFails { jdbc.queryForObject("select marker from preview_current_state", String::class.java) }
    }

    @Test
    fun `non preview runtime remains fail closed after validation mismatch`(@TempDir migrations: Path) {
        val dataSource = dataSource()
        val migration = migrations.resolve("V1__preview_state.sql")
        Files.writeString(migration, OLD_MIGRATION)
        flyway(dataSource, migrations).migrate()
        Files.writeString(migration, CURRENT_MIGRATION)

        assertFailsWith<FlywayValidateException> {
            PreviewFlywayMigrationStrategy(nonPreviewConfig()).migrate(flyway(dataSource, migrations))
        }

        assertEquals(
            "old",
            JdbcTemplate(dataSource).queryForObject("select marker from preview_old_state", String::class.java),
        )
    }

    private fun dataSource() = DriverManagerDataSource(
        "jdbc:h2:mem:preview-flyway-recovery-${UUID.randomUUID()};" +
            "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "",
    )

    private fun reportingDataSource(delegate: DataSource, reportedUrl: String): DataSource = object : DataSource by delegate {
        override fun getConnection(): Connection = reportingConnection(delegate.connection, reportedUrl)

        override fun getConnection(username: String, password: String): Connection =
            reportingConnection(delegate.getConnection(username, password), reportedUrl)
    }

    private fun reportingConnection(delegate: Connection, reportedUrl: String): Connection =
        object : Connection by delegate {
            override fun getMetaData() = object : java.sql.DatabaseMetaData by delegate.metaData {
                override fun getURL(): String = reportedUrl
            }
        }

    private fun flyway(
        dataSource: DataSource,
        migrations: Path,
        schema: String = "public",
    ): Flyway = Flyway.configure()
        .dataSource(dataSource)
        .locations("filesystem:${migrations.toAbsolutePath()}")
        .defaultSchema(schema)
        .schemas(schema)
        .cleanDisabled(true)
        .load()

    private fun prPreviewConfig() = PreviewRuntimeConfig(
        enabled = true,
        marker = PreviewRuntimeConfig.REQUIRED_MARKER,
        databaseUrl = VALID_PREVIEW_DATABASE,
        previewPrNumber = "38",
    )

    private fun acceptanceConfig() = PreviewRuntimeConfig(
        enabled = true,
        marker = PreviewRuntimeConfig.ACCEPTANCE_MARKER,
        databaseUrl = VALID_PREVIEW_DATABASE,
        previewPrNumber = "",
    )

    private fun nonPreviewConfig() = PreviewRuntimeConfig(
        enabled = false,
        marker = "",
        databaseUrl = VALID_PREVIEW_DATABASE,
        previewPrNumber = "",
    )

    private companion object {
        const val VALID_PREVIEW_DATABASE = "jdbc:postgresql://postgres:5432/productfactory"
        const val OLD_MIGRATION = """
            create table preview_old_state(marker varchar(20));
            insert into preview_old_state(marker) values ('old');
        """
        const val CURRENT_MIGRATION = """
            create table preview_current_state(marker varchar(20));
            insert into preview_current_state(marker) values ('current');
        """
    }
}
