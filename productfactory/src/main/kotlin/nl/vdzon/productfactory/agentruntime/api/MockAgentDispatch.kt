package nl.vdzon.productfactory.agentruntime.api

import nl.vdzon.productfactory.contracts.AgentArtifact
import nl.vdzon.productfactory.contracts.AgentResult
import nl.vdzon.productfactory.contracts.AgentTask
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.ConcurrentLinkedQueue

data class MockAgentResponse(
    val status: String = "COMPLETED",
    val summary: String = "Mockrespons (geen override ingesteld)",
    val artifacts: List<AgentArtifact> = emptyList(),
)

/**
 * Houdt de door de tester ingestelde volgende AI-antwoorden bij, in volgorde van aanroep. Bestaat
 * alleen in het "mock-agent-bridge"-profiel, dat nooit in productie actief is.
 */
@Component
@Profile("mock-agent-bridge")
class MockAgentResponseStore {
    private val queue = ConcurrentLinkedQueue<MockAgentResponse>()

    fun enqueue(response: MockAgentResponse) = queue.add(response)

    fun pollOrDefault(): MockAgentResponse = queue.poll() ?: MockAgentResponse()

    fun peekAll(): List<MockAgentResponse> = queue.toList()

    fun clear() = queue.clear()
}

@Component
@Profile("mock-agent-bridge")
class MockAgentDispatch(private val responses: MockAgentResponseStore) : AgentDispatchPort {
    override fun execute(task: AgentTask): AgentResult {
        val response = responses.pollOrDefault()
        return AgentResult(
            runId = task.runId,
            status = response.status,
            summary = response.summary,
            artifacts = response.artifacts,
        )
    }
}

@RestController
@Profile("mock-agent-bridge")
@RequestMapping("/internal/mock-agent-bridge/responses")
class MockAgentBridgeController(private val responses: MockAgentResponseStore) {
    @GetMapping
    fun list(): List<MockAgentResponse> = responses.peekAll()

    @PostMapping
    fun enqueue(@RequestBody response: MockAgentResponse) = responses.enqueue(response)

    @DeleteMapping
    fun clear() = responses.clear()
}
