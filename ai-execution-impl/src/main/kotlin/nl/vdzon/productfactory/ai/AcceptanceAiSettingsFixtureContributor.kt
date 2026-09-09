package nl.vdzon.productfactory.ai

import nl.vdzon.productfactory.api.ai.AiExecutionMode
import nl.vdzon.productfactory.api.ai.AiExecutionSelection
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
    private val execution: AiExecutionApplicationService,
) : AcceptanceFixtureContributor {
    override val key = "ai-settings"
    override val order = 100

    override fun reset(context: AcceptanceFixtureContext) {
        execution.deleteAllOwnedExecutionData()
        settings.deleteAllOwnedConfiguration()
        settings.getAiJobConfigurations().forEach { configuration ->
            settings.updateAiJobConfiguration(UpdateAiJobConfigurationCommand(
                configuration.jobKey, AiExecutionSelection("mock", "mock", AiExecutionMode.MOCK),
                true, 0, SYSTEM, "fixture:${context.datasetVersion}:${context.scenarioKey}:ai:${configuration.jobKey.value}",
            ))
        }
    }

    companion object {
        private val SYSTEM = ActorReference(ActorType.SYSTEM, "acceptance-ai-settings-fixture")
    }
}
