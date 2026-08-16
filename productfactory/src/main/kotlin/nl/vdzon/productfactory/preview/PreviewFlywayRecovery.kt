package nl.vdzon.productfactory.preview

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.exception.FlywayValidateException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * PR-previewdatabases leven langer dan een afzonderlijke branchrevisie. Bij een merge met main kan
 * een nog niet gemergde migratieversie daardoor botsen met een inmiddels vastgelegde migratie.
 * Alleen de expliciet als wegwerpbaar gevalideerde PR-preview mag in dat geval opnieuw worden
 * opgebouwd; productie en de vaste acceptatieomgeving blijven de normale Flyway-fout geven.
 */
@Configuration
class PreviewFlywayRecoveryConfiguration {
    @Bean
    fun flywayMigrationStrategy(previewRuntimeConfig: PreviewRuntimeConfig): FlywayMigrationStrategy =
        PreviewFlywayMigrationStrategy(previewRuntimeConfig)
}

internal class PreviewFlywayMigrationStrategy(
    private val previewRuntimeConfig: PreviewRuntimeConfig,
) : FlywayMigrationStrategy {
    override fun migrate(flyway: Flyway) {
        try {
            flyway.migrate()
        } catch (exception: FlywayValidateException) {
            if (previewRuntimeConfig.dataset != SyntheticDataset.PR_PREVIEW) {
                throw exception
            }

            log.warn(
                "Flyway-validatie van wegwerpbare PR-preview {} faalde; databaseschema wordt opnieuw opgebouwd",
                previewRuntimeConfig.prNumber,
            )
            val recoveryFlyway = Flyway.configure()
                .configuration(flyway.configuration)
                .cleanDisabled(false)
                .load()
            recoveryFlyway.clean()
            recoveryFlyway.migrate()
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(PreviewFlywayMigrationStrategy::class.java)
    }
}
