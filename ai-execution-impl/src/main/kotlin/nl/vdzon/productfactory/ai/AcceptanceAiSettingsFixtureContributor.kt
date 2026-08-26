package nl.vdzon.productfactory.ai

import nl.vdzon.productfactory.api.ai.AiProvider
import nl.vdzon.productfactory.api.ai.UpdateAiJobConfigurationCommand
import nl.vdzon.productfactory.api.shared.ActorReference
import nl.vdzon.productfactory.api.shared.ActorType
import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContext
import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContributor
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("acceptance")
class AcceptanceAiSettingsFixtureContributor(
    private val settings: AiSettingsApplicationService,
) : AcceptanceFixtureContributor {
    override val key = "ai-settings"
    override val order = 400

    override fun reset(context: AcceptanceFixtureContext) {
        settings.deleteAllOwnedConfiguration()
        settings.getAiJobConfigurations().forEach { configuration ->
            settings.updateAiJobConfiguration(UpdateAiJobConfigurationCommand(
                configuration.jobKey, AiProvider.MOCKED, "${context.scenarioKey}.${configuration.jobKey.value.lowercase()}",
                true, 0, SYSTEM, "fixture:${context.datasetVersion}:${context.scenarioKey}:ai:${configuration.jobKey.value}",
            ))
        }
    }

    companion object {
        private val SYSTEM = ActorReference(ActorType.SYSTEM, "acceptance-ai-settings-fixture")
    }
}
