package nl.vdzon.productfactory.agentruntime

import nl.vdzon.productfactory.agentruntime.api.AgentRunRegistry
import nl.vdzon.productfactory.contracts.AgentRunView
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class RegisterAgentRunRequest(val runId: String, val productSlug: String, val taskType: String)

@RestController
@RequestMapping("/api/agent-runs")
class AgentRunController(private val registry: AgentRunRegistry) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody request: RegisterAgentRunRequest): AgentRunView =
        registry.register(request.runId, request.productSlug, request.taskType)

    @GetMapping
    fun list(@RequestParam productSlug: String): List<AgentRunView> = registry.list(productSlug)

    @GetMapping("/{runId}")
    fun get(@PathVariable runId: String, @RequestParam productSlug: String): AgentRunView = registry.requireRun(productSlug, runId)
}
