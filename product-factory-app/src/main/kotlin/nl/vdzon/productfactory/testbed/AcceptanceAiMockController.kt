package nl.vdzon.productfactory.testbed

import com.fasterxml.jackson.databind.JsonNode
import nl.vdzon.productfactory.ai.AgentRuntimeTestControlClient
import nl.vdzon.productfactory.ai.AiExecutionApplicationService
import nl.vdzon.productfactory.ai.RuntimeMockFixtureRequest
import nl.vdzon.productfactory.api.shared.AiTaskId
import nl.vdzon.productfactory.api.shared.InvalidCommand
import nl.vdzon.productfactory.api.testbed.TestControlService
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class PrepareAiMockResponseRequest(
    val taskId: String,
    val scenarioKey: String,
    val scenarioVersion: String,
    val step: String,
    val result: JsonNode? = null,
    val outputSequence: List<String> = emptyList(),
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val delayMillis: Long = 0,
    val outputArtifactNames: Set<String> = emptySet(),
)

@RestController
@Profile("acceptance")
@RequestMapping("/api/test-control/ai/mock-responses")
class AcceptanceAiMockController(
    private val execution: AiExecutionApplicationService,
    private val runtime: AgentRuntimeTestControlClient,
    private val scenarios: TestControlService,
) {
    @GetMapping
    fun list() = runtime.list()

    @PostMapping
    fun create(@RequestBody request: PrepareAiMockResponseRequest): Any {
        validate(request)
        val runtimeKey = execution.prepareFixture(AiTaskId(request.taskId), request.result, request.outputSequence, request.outputArtifactNames)
        return runtime.create(RuntimeMockFixtureRequest(
            idempotencyKey = runtimeKey,
            result = request.result,
            outputSequence = request.outputSequence,
            errorCode = request.errorCode,
            errorMessage = request.errorMessage,
            delayMillis = request.delayMillis,
            outputArtifactNames = request.outputArtifactNames,
        ))
    }

    @DeleteMapping("/{fixtureId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable fixtureId: String) = runtime.delete(fixtureId)

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun clear() = runtime.clear()

    private fun validate(request: PrepareAiMockResponseRequest) {
        if (!SAFE_KEY.matches(request.taskId) || !SAFE_KEY.matches(request.scenarioKey) || !SAFE_VERSION.matches(request.scenarioVersion) || !SAFE_KEY.matches(request.step)) {
            throw InvalidCommand("Mockfixture bevat onveilige scenario- of taakmetadata.")
        }
        val active = scenarios.getActiveScenario().scenario
        if (active.key != request.scenarioKey || active.version != request.scenarioVersion) {
            throw InvalidCommand("Mockfixture hoort niet bij het actieve acceptatiescenario.")
        }
        val outcomes = listOf(request.result != null, request.outputSequence.isNotEmpty(), request.errorCode != null).count { it }
        if (outcomes != 1) throw InvalidCommand("Kies exact één mockresultaat, outputsequence of foutcode.")
        if (request.errorCode == null && request.errorMessage != null) throw InvalidCommand("Een mockfoutmelding vereist een foutcode.")
        if (request.delayMillis !in 0..60_000 || request.outputSequence.size > 10 || request.outputArtifactNames.size > 50) {
            throw InvalidCommand("Mockfixture overschrijdt de veilige limieten.")
        }
    }

    companion object {
        private val SAFE_KEY = Regex("[A-Za-z0-9._-]{1,160}")
        private val SAFE_VERSION = Regex("[A-Za-z0-9._-]{1,40}")
    }
}
