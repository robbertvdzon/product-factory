package nl.vdzon.productfactory.api.testbed

import java.time.Instant

data class TestScenarioSummary(val key: String, val version: String, val title: String, val description: String)
data class TestScenarioDetails(val scenario: TestScenarioSummary, val datasetVersion: String, val testbedVersion: String, val activatedAt: Instant, val currentStep: Int)
data class ResetAcceptanceEnvironmentCommand(val scenarioKey: String, val browserSessionId: String)
data class ActivateTestScenarioCommand(val scenarioKey: String, val browserSessionId: String)
data class AdvanceTestScenarioCommand(val expectedStep: Int, val browserSessionId: String)
data class InjectTestFaultCommand(val faultKey: String, val browserSessionId: String)
interface TestControlService {
    fun getActiveScenario(): TestScenarioDetails
    fun getAvailableScenarios(): List<TestScenarioSummary>
    fun resetAcceptanceEnvironment(command: ResetAcceptanceEnvironmentCommand)
    fun activateScenario(command: ActivateTestScenarioCommand)
    fun advanceScenario(command: AdvanceTestScenarioCommand)
    fun injectTestFault(command: InjectTestFaultCommand)
}
