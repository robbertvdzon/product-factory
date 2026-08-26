package nl.vdzon.productfactory.foundation

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.DriverManager
import java.time.Instant

@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationSmokeTest {
    @Test
    fun `JdbcTemplate bindt Instant als PostgreSQL timestamp`() {
        val database = "productfactory_instant_binding"
        createDatabase(database)
        val url = databaseUrl(database)
        Flyway.configure().dataSource(url, postgres.username, postgres.password).load().migrate()
        val jdbc = InstantAwareJdbcTemplate(
            DriverManagerDataSource(url, postgres.username, postgres.password),
        )
        val now = Instant.parse("2026-08-26T16:30:00Z")

        assertThat(
            jdbc.update(
                "INSERT INTO environment_metadata(metadata_key,metadata_value,updated_at) VALUES (?,?,?)",
                "instant.binding",
                "works",
                now,
            ),
        ).isEqualTo(1)
        assertThat(
            jdbc.queryForObject(
                "SELECT metadata_value FROM environment_metadata WHERE metadata_key=? AND updated_at<=?",
                String::class.java,
                "instant.binding",
                now,
            ),
        ).isEqualTo("works")
    }

    @Test
    fun `lege PostgreSQL database migreert vanaf nul`() {
        val result = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .cleanDisabled(true)
            .load()
            .migrate()

        assertThat(result.migrationsExecuted).isEqualTo(13)
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
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString(1)).isEqualTo("11")
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString(1)).isEqualTo("12")
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString(1)).isEqualTo("13")
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
                    assertThat(rows.getString(1)).isEqualTo("13")
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
        assertThat(upgraded.migrationsExecuted).isEqualTo(5)
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
                statement.executeQuery("SELECT COUNT(*) FROM pf_schedule_run").use { rows ->
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
        assertThat(upgraded.migrationsExecuted).isEqualTo(4)
        DriverManager.getConnection(url, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM pf_dispatcher_process_session").use { rows ->
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getLong(1)).isZero()
                }
                statement.executeQuery("SELECT COUNT(*) FROM pf_schedule_run").use { rows ->
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getLong(1)).isZero()
                }
            }
        }
    }

    @Test
    fun `dispatcherrelease migreert voorwaarts naar scheduler`() {
        val database = "productfactory_upgrade_v10"
        createDatabase(database)
        val url = databaseUrl(database)
        Flyway.configure().dataSource(url, postgres.username, postgres.password).target("10").load().migrate()

        val upgraded = Flyway.configure().dataSource(url, postgres.username, postgres.password).load().migrate()

        assertThat(upgraded.migrationsExecuted).isEqualTo(3)
        DriverManager.getConnection(url, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM pf_schedule_run").use { rows ->
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getLong(1)).isZero()
                }
            }
        }
    }

    @Test
    fun `schedulerrelease canonicaliseert bestaande Codex defaults`() {
        val database = "productfactory_upgrade_v11"
        createDatabase(database)
        val url = databaseUrl(database)
        Flyway.configure().dataSource(url, postgres.username, postgres.password).target("11").load().migrate()
        DriverManager.getConnection(url, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "INSERT INTO pf_ai_job_definition(job_key,display_name,default_provider,default_model,default_enabled) " +
                        "VALUES ('PRODUCT_DESIGN.CREATE_EPIC','Epic ontwerpen','CODEX','gpt-5.6',TRUE)",
                )
            }
        }

        val upgraded = Flyway.configure().dataSource(url, postgres.username, postgres.password).load().migrate()

        assertThat(upgraded.migrationsExecuted).isEqualTo(2)
        DriverManager.getConnection(url, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT default_model FROM pf_ai_job_definition WHERE job_key='PRODUCT_DESIGN.CREATE_EPIC'",
                ).use { rows ->
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString(1)).isEqualTo("gpt-5.6-sol")
                }
            }
        }
    }

    @Test
    fun `bestaande beschikbare epic wordt na readinessmigratie eerst onderzoekswerk`() {
        val database = "productfactory_upgrade_v12_epic"
        createDatabase(database)
        val url = databaseUrl(database)
        Flyway.configure().dataSource(url, postgres.username, postgres.password).target("12").load().migrate()
        DriverManager.getConnection(url, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """INSERT INTO pf_product(product_id,name,status,dispatching_enabled,created_at,updated_at,updated_by_type,updated_by_id,version)
                        VALUES ('migration-product','Migration product','ACTIVE',FALSE,NOW(),NOW(),'SYSTEM','migration',1)""".trimIndent(),
                )
                statement.executeUpdate(
                    """INSERT INTO pf_epic(id,product_id,current_version,status,created_at,updated_at)
                        VALUES ('00000000-0000-0000-0000-000000000013','migration-product',1,'AVAILABLE',NOW(),NOW())""".trimIndent(),
                )
                statement.executeUpdate(
                    """INSERT INTO pf_epic_version(epic_id,version,title,summary,problem,solution,direction_references_json,ux_design,
                        acceptance_criteria_json,slicability_rationale,source_references_json,status,actor_type,actor_id,created_at,supersedes_version)
                        VALUES ('00000000-0000-0000-0000-000000000013',1,'Bestaande epic','Bestaande epic zonder readiness.',
                        'Het onderzoek ontbreekt nog volledig.','Werk de oplossing later volledig uit.','[]',NULL,'["Onderzoek is compleet."]',
                        'De epic wordt pas na onderzoek gesliced.','[]','AVAILABLE','SYSTEM','migration',NOW(),NULL)""".trimIndent(),
                )
            }
        }

        val upgraded = Flyway.configure().dataSource(url, postgres.username, postgres.password).load().migrate()

        assertThat(upgraded.migrationsExecuted).isEqualTo(1)
        DriverManager.getConnection(url, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    """SELECT status,research_sources_json,readiness_json,ux_artifacts_json FROM pf_epic_version
                        WHERE epic_id='00000000-0000-0000-0000-000000000013' AND version=1""".trimIndent(),
                ).use { rows ->
                    assertThat(rows.next()).isTrue()
                    assertThat(rows.getString(1)).isEqualTo("NEEDS_RESEARCH")
                    assertThat(rows.getString(2)).isEqualTo("[]")
                    assertThat(rows.getString(3)).contains("nog niet opnieuw op gereedheid beoordeeld")
                    assertThat(rows.getString(4)).isEqualTo("[]")
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
