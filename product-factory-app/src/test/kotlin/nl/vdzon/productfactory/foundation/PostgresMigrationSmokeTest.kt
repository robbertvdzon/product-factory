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

        assertThat(result.migrationsExecuted).isEqualTo(10)
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT version FROM flyway_schema_history WHERE success = TRUE").use { rows ->
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString(1)).isEqualTo("1")
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString(1)).isEqualTo("2")
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString(1)).isEqualTo("3")
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString(1)).isEqualTo("4")
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString(1)).isEqualTo("5")
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString(1)).isEqualTo("6")
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString(1)).isEqualTo("7")
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString(1)).isEqualTo("8")
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString(1)).isEqualTo("9")
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString(1)).isEqualTo("10")
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
                    assertThat(rows.getString(1)).isEqualTo("10")
                }
            }
        }
    }

    @Test
    fun `planning release migreert voorwaarts naar kwaliteit en dispatcher`() {
        val database = "productfactory_upgrade_v8"
        createDatabase(database)
        val url = databaseUrl(database)
        val old = Flyway.configure().dataSource(url, postgres.username, postgres.password).target("8").load().migrate()
        assertThat(old.targetSchemaVersion.toString()).isEqualTo("8")

        val upgraded = Flyway.configure().dataSource(url, postgres.username, postgres.password).load().migrate()
        assertThat(upgraded.migrationsExecuted).isEqualTo(2)
        DriverManager.getConnection(url, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM pf_quality_process_session").use { rows ->
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getLong(1)).isZero()
                }
                statement.executeQuery("SELECT COUNT(*) FROM pf_delivery_attempt").use { rows ->
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getLong(1)).isZero()
                }
            }
        }
    }

    @Test
    fun `kwaliteitsrelease migreert voorwaarts naar dispatcher`() {
        val database = "productfactory_upgrade_v9"
        createDatabase(database)
        val url = databaseUrl(database)
        Flyway.configure().dataSource(url, postgres.username, postgres.password).target("9").load().migrate()

        val upgraded = Flyway.configure().dataSource(url, postgres.username, postgres.password).load().migrate()
        assertThat(upgraded.migrationsExecuted).isEqualTo(1)
        DriverManager.getConnection(url, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM pf_dispatcher_process_session").use { rows ->
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getLong(1)).isZero()
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
