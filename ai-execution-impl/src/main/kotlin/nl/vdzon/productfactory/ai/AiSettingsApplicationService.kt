package nl.vdzon.productfactory.ai

import nl.vdzon.productfactory.api.ai.*
import nl.vdzon.productfactory.api.shared.*
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.HexFormat

@Service
@Transactional
class AiSettingsApplicationService(
    private val jdbc: JdbcTemplate,
    private val clock: Clock,
    @Value("\${PF_ENVIRONMENT:local}") environment: String,
) {
    private val environment = environment.lowercase()

    fun updateAiJobConfiguration(command: UpdateAiJobConfigurationCommand): AiJobConfigurationDetails {
        validateActor(command.actor)
        val fingerprint = fingerprint(command)
        replay(command.idempotencyKey, fingerprint)?.let { return getAiJobConfiguration(command.jobKey) }
        definition(command.jobKey)
        val current = currentConfiguration(command.jobKey)
        val currentVersion = current?.version ?: 0L
        if (currentVersion != command.expectedVersion) throw VersionConflict("AI-jobconfiguratie is intussen gewijzigd.")
        validateSelection(command.provider, command.model)
        val now = clock.instant()
        val nextVersion = currentVersion + 1
        jdbc.update(
            "INSERT INTO pf_ai_job_configuration(job_key,version,provider,model,enabled,updated_at,actor_type,actor_id) VALUES (?,?,?,?,?,?,?,?)",
            command.jobKey.value, nextVersion, command.provider.name, command.model.trim(), command.enabled,
            now, command.actor.type.name, command.actor.id,
        )
        jdbc.update(
            "INSERT INTO pf_ai_settings_command(idempotency_key,job_key,request_fingerprint,result_version,actor_type,actor_id,applied_at) VALUES (?,?,?,?,?,?,?)",
            command.idempotencyKey, command.jobKey.value, fingerprint, nextVersion, command.actor.type.name, command.actor.id, now,
        )
        return currentConfiguration(command.jobKey)
            ?: error("De zojuist opgeslagen AI-jobconfiguratie ontbreekt.")
    }

    @Transactional(readOnly = true)
    fun getAiJobConfiguration(jobKey: AiJobKey): AiJobConfigurationDetails {
        val definition = definition(jobKey)
        return currentConfiguration(jobKey) ?: AiJobConfigurationDetails(
            jobKey, definition.displayName, definition.defaultProvider, definition.defaultModel,
            definition.defaultEnabled, 0, Instant.EPOCH, ActorReference(ActorType.SYSTEM, "trusted-default"),
        )
    }

    @Transactional(readOnly = true)
    fun getAiJobConfigurations(): List<AiJobConfigurationDetails> = definitions().map { getAiJobConfiguration(it.jobKey) }

    fun registerTrustedJobKeys() {
        TRUSTED_JOBS.forEach { definition ->
            val exists = jdbc.queryForObject("SELECT COUNT(*) FROM pf_ai_job_definition WHERE job_key=?", Long::class.java, definition.jobKey.value) ?: 0
            if (exists == 0L) {
                jdbc.update(
                    "INSERT INTO pf_ai_job_definition(job_key,display_name,default_provider,default_model,default_enabled) VALUES (?,?,?,?,?)",
                    definition.jobKey.value, definition.displayName, definition.defaultProvider.name, definition.defaultModel, definition.defaultEnabled,
                )
            }
        }
    }

    fun deleteAllOwnedConfiguration() {
        jdbc.update("DELETE FROM pf_ai_settings_command")
        jdbc.update("DELETE FROM pf_ai_job_configuration")
    }

    private fun currentConfiguration(jobKey: AiJobKey): AiJobConfigurationDetails? {
        val definition = definition(jobKey)
        return jdbc.query(
            "SELECT provider,model,enabled,version,updated_at,actor_type,actor_id FROM pf_ai_job_configuration WHERE job_key=? ORDER BY version DESC",
            { rs, _ ->
                AiJobConfigurationDetails(
                    jobKey, definition.displayName, AiProvider.valueOf(rs.getString("provider")), rs.getString("model"),
                    rs.getBoolean("enabled"), rs.getLong("version"), rs.getTimestamp("updated_at").toInstant(),
                    ActorReference(ActorType.valueOf(rs.getString("actor_type")), rs.getString("actor_id")),
                )
            }, jobKey.value,
        ).firstOrNull()
    }

    private fun definition(jobKey: AiJobKey) = definitions().singleOrNull { it.jobKey == jobKey }
        ?: throw InvalidCommand("Onbekende AI-jobkey ${jobKey.value}.")

    private fun definitions(): List<JobDefinition> = jdbc.query(
        "SELECT job_key,display_name,default_provider,default_model,default_enabled FROM pf_ai_job_definition ORDER BY job_key",
    ) { rs, _ ->
        JobDefinition(AiJobKey(rs.getString(1)), rs.getString(2), AiProvider.valueOf(rs.getString(3)), rs.getString(4), rs.getBoolean(5))
    }

    private fun validateSelection(provider: AiProvider, model: String) {
        val normalized = model.trim()
        if (normalized.isBlank() || normalized.length > 200) throw InvalidCommand("Model of mockprofiel is ongeldig.")
        when (provider) {
            AiProvider.CODEX -> if (!CODEX_MODEL.matches(normalized)) throw InvalidCommand("Ongeldig CODEX-model.")
            AiProvider.CLAUDE -> if (!CLAUDE_MODEL.matches(normalized)) throw InvalidCommand("Ongeldig CLAUDE-model.")
            AiProvider.MOCKED -> {
                if (environment == "production") throw InvalidCommand("MOCKED AI-uitvoering is in productie niet toegestaan.")
                if (!MOCK_PROFILE.matches(normalized)) throw InvalidCommand("Ongeldig mockprofiel.")
            }
        }
    }

    private fun validateActor(actor: ActorReference) {
        if (actor.id.isBlank() || actor.type !in setOf(ActorType.STAKEHOLDER, ActorType.SYSTEM)) {
            throw InvalidCommand("Alleen de Stakeholder of vertrouwde systeemcode mag globale AI-instellingen wijzigen.")
        }
    }

    private fun replay(key: String, fingerprint: String): Long? {
        if (key.isBlank() || key.length > 200) throw InvalidCommand("Ongeldige idempotentiesleutel.")
        val row = jdbc.query(
            "SELECT request_fingerprint,result_version FROM pf_ai_settings_command WHERE idempotency_key=?",
            { rs, _ -> rs.getString(1) to rs.getLong(2) }, key,
        ).singleOrNull() ?: return null
        if (row.first != fingerprint) throw IdempotencyConflict("Idempotentiesleutel is al voor andere instellingen gebruikt.")
        return row.second
    }

    private fun fingerprint(value: Any) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toString().toByteArray()))

    data class JobDefinition(
        val jobKey: AiJobKey,
        val displayName: String,
        val defaultProvider: AiProvider,
        val defaultModel: String,
        val defaultEnabled: Boolean,
    )

    companion object {
        private val CODEX_MODEL = Regex("(?:gpt|o)[A-Za-z0-9._-]{1,100}")
        private val CLAUDE_MODEL = Regex("claude-[A-Za-z0-9._-]{1,100}")
        private val MOCK_PROFILE = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")
        val TRUSTED_JOBS = listOf(
            JobDefinition(AiJobKey("MEETING.CONVERSE"), "Overleg voeren", AiProvider.CODEX, "gpt-5.6", true),
            JobDefinition(AiJobKey("MEETING.SUMMARIZE"), "Overleg samenvatten", AiProvider.CODEX, "gpt-5.6", true),
            JobDefinition(AiJobKey("PRODUCT_DESIGN.CREATE_EPIC"), "Epic ontwerpen", AiProvider.CODEX, "gpt-5.6", true),
            JobDefinition(AiJobKey("PLANNING.SLICE_EPIC"), "Epic opdelen in stories", AiProvider.CODEX, "gpt-5.6", true),
            JobDefinition(AiJobKey("QUALITY.VERIFY_EPIC"), "Kwaliteit verifiëren", AiProvider.CODEX, "gpt-5.6", true),
        )
    }
}

@Component
class TrustedAiJobCatalogInitializer(
    private val settings: AiSettingsApplicationService,
) : InitializingBean {
    override fun afterPropertiesSet() = settings.registerTrustedJobKeys()
}
