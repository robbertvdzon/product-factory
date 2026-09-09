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
        validateSelection(command.execution)
        val now = clock.instant()
        val nextVersion = currentVersion + 1
        jdbc.update(
            "INSERT INTO pf_ai_job_configuration(job_key,version,provider,vendor_id,model,execution_mode,enabled,updated_at,actor_type,actor_id) VALUES (?,?,?,?,?,?,?,?,?,?)",
            command.jobKey.value, nextVersion, legacyProvider(command.execution), command.execution.vendorId.trim(), command.execution.model.trim(), command.execution.mode.name, command.enabled,
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
            jobKey, definition.displayName, definition.defaultExecution,
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
                    "INSERT INTO pf_ai_job_definition(job_key,display_name,default_provider,default_vendor_id,default_model,default_execution_mode,default_enabled) VALUES (?,?,?,?,?,?,?)",
                    definition.jobKey.value, definition.displayName, legacyProvider(definition.defaultExecution), definition.defaultExecution.vendorId,
                    definition.defaultExecution.model, definition.defaultExecution.mode.name, definition.defaultEnabled,
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
            "SELECT vendor_id,model,execution_mode,enabled,version,updated_at,actor_type,actor_id FROM pf_ai_job_configuration WHERE job_key=? ORDER BY version DESC",
            { rs, _ ->
                AiJobConfigurationDetails(
                    jobKey, definition.displayName,
                    AiExecutionSelection(rs.getString("vendor_id"), rs.getString("model"), AiExecutionMode.valueOf(rs.getString("execution_mode"))),
                    rs.getBoolean("enabled"), rs.getLong("version"), rs.getTimestamp("updated_at").toInstant(),
                    ActorReference(ActorType.valueOf(rs.getString("actor_type")), rs.getString("actor_id")),
                )
            }, jobKey.value,
        ).firstOrNull()
    }

    private fun definition(jobKey: AiJobKey) = definitions().singleOrNull { it.jobKey == jobKey }
        ?: throw InvalidCommand("Onbekende AI-jobkey ${jobKey.value}.")

    private fun definitions(): List<JobDefinition> = jdbc.query(
        "SELECT job_key,display_name,default_vendor_id,default_model,default_execution_mode,default_enabled FROM pf_ai_job_definition ORDER BY job_key",
    ) { rs, _ ->
        JobDefinition(
            AiJobKey(rs.getString(1)), rs.getString(2),
            AiExecutionSelection(rs.getString(3), rs.getString(4), AiExecutionMode.valueOf(rs.getString(5))),
            rs.getBoolean(6),
        )
    }

    private fun validateSelection(selection: AiExecutionSelection) {
        val vendor = selection.vendorId.trim()
        val model = selection.model.trim()
        if (!IDENTIFIER.matches(vendor) || model.isBlank() || model.length > 200) throw InvalidCommand("Leverancier of model is ongeldig.")
        if (selection.mode == AiExecutionMode.MOCK) {
            if (environment == "production") throw InvalidCommand("Mock-AI-uitvoering is in productie niet toegestaan.")
            if (vendor != "mock" || model != "mock") throw InvalidCommand("Mockuitvoering vereist exact mock / mock / MOCK.")
        } else if (vendor == "mock" || model == "mock") {
            throw InvalidCommand("De mockleverancier en het mockmodel vereisen uitvoeringswijze MOCK.")
        }
    }

    private fun legacyProvider(selection: AiExecutionSelection): String = when {
        selection.mode == AiExecutionMode.MOCK -> "MOCKED"
        selection.vendorId == "anthropic" -> "CLAUDE"
        else -> "CODEX"
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
        val defaultExecution: AiExecutionSelection,
        val defaultEnabled: Boolean,
    )

    companion object {
        private val IDENTIFIER = Regex("[a-z0-9][a-z0-9._-]{0,119}")
        private val DEFAULT_EXECUTION = AiExecutionSelection("openai", "gpt-5.6-sol", AiExecutionMode.SUBSCRIPTION)
        val TRUSTED_JOBS = listOf(
            JobDefinition(AiJobKey("MEETING.CONVERSE"), "Overleg voeren", DEFAULT_EXECUTION, true),
            JobDefinition(AiJobKey("MEETING.SUMMARIZE"), "Overleg samenvatten", DEFAULT_EXECUTION, true),
            JobDefinition(AiJobKey("PRODUCT_DESIGN.CREATE_EPIC"), "Epic ontwerpen", DEFAULT_EXECUTION, true),
            JobDefinition(AiJobKey("PLANNING.SELECT_WORK"), "Planningswerk selecteren", DEFAULT_EXECUTION, true),
            JobDefinition(AiJobKey("PLANNING.SLICE_EPIC"), "Epic opdelen in stories", DEFAULT_EXECUTION, true),
            JobDefinition(AiJobKey("QUALITY.VERIFY_EPIC"), "Gericht kwaliteitswerk uitvoeren", DEFAULT_EXECUTION, true),
        )
    }
}

@Component
class TrustedAiJobCatalogInitializer(
    private val settings: AiSettingsApplicationService,
) : InitializingBean {
    override fun afterPropertiesSet() = settings.registerTrustedJobKeys()
}
