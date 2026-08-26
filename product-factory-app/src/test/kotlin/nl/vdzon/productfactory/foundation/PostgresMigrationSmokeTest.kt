package nl.vdzon.productfactory.foundation

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager

@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationSmokeTest {
    @Test
    fun `lege PostgreSQL database migreert vanaf nul`() {
        val result = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .cleanDisabled(true)
            .load()
            .migrate()

        assertThat(result.migrationsExecuted).isEqualTo(3)
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT version FROM flyway_schema_history WHERE success = TRUE").use { rows ->
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString(1)).isEqualTo("1")
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString(1)).isEqualTo("2")
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString(1)).isEqualTo("3")
                    assertThat(rows.next()).isFalse()
                }
            }
        }
    }

    @Test
    fun `custom format backup kan echt worden teruggezet`() {
        val sourceDatabase = "productfactory_backup_source"
        createDatabase(sourceDatabase)
        val sourceUrl = databaseUrl(sourceDatabase)
        Flyway.configure()
            .dataSource(sourceUrl, postgres.username, postgres.password)
            .cleanDisabled(true)
            .load()
            .migrate()

        DriverManager.getConnection(sourceUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "INSERT INTO environment_metadata (metadata_key, metadata_value, updated_at) " +
                        "VALUES ('backup.marker', 'present', TIMESTAMPTZ '1970-01-01 00:00:00+00')",
                )
            }
        }

        assertThat(
            postgres.execInContainer(
                "pg_dump",
                "--format=custom",
                "--no-owner",
                "--no-privileges",
                "--username=${postgres.username}",
                "--dbname=$sourceDatabase",
                "--file=/tmp/productfactory.dump",
            ).exitCode,
        ).isZero()
        assertThat(postgres.execInContainer("pg_restore", "--list", "/tmp/productfactory.dump").exitCode).isZero()
        assertThat(postgres.execInContainer("sha256sum", "/tmp/productfactory.dump").exitCode).isZero()

        createDatabase("productfactory_restore")
        assertThat(
            postgres.execInContainer(
                "pg_restore",
                "--no-owner",
                "--no-privileges",
                "--username=${postgres.username}",
                "--dbname=productfactory_restore",
                "/tmp/productfactory.dump",
            ).exitCode,
        ).isZero()

        DriverManager.getConnection(
            databaseUrl("productfactory_restore"),
            postgres.username,
            postgres.password,
        ).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT metadata_value FROM environment_metadata WHERE metadata_key = 'backup.marker'",
                ).use { rows ->
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString(1)).isEqualTo("present")
                }
                statement.executeQuery(
                    "SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1",
                ).use { rows ->
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString(1)).isEqualTo("3")
                }
            }
        }
    }

    private fun createDatabase(name: String) {
        DriverManager.getConnection(databaseUrl("postgres"), postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("CREATE DATABASE $name")
            }
        }
    }

    private fun databaseUrl(name: String): String =
        postgres.jdbcUrl.replace("/${postgres.databaseName}", "/$name")

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17.6-alpine")
            .withDatabaseName("productfactory_v2")
            .withUsername("productfactory_v2")
            .withPassword("test-only-password")
    }
}
