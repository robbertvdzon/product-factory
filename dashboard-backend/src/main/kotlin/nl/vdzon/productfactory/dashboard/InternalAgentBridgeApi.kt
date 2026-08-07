package nl.vdzon.productfactory.dashboard

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import nl.vdzon.productfactory.contracts.AgentTask
import nl.vdzon.productfactory.contracts.AgentTaskStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class InternalAgentAuthenticationFilter(
    @Value("\${PF_AGENT_WORKER_TOKEN:}") private val expectedToken: String,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest) = !request.requestURI.startsWith("/internal/agent-worker/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val candidate = request.getHeader(TOKEN_HEADER).orEmpty()
        if (expectedToken.isBlank() || !MessageDigest.isEqual(
                candidate.toByteArray(StandardCharsets.UTF_8),
                expectedToken.toByteArray(StandardCharsets.UTF_8),
            )
        ) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Agentbridgetoken is ongeldig")
            return
        }
        chain.doFilter(request, response)
    }

    companion object {
        const val TOKEN_HEADER = "X-Product-Factory-Agent-Token"
    }
}

@RestController
@RequestMapping("/internal/agent-worker")
class InternalAgentBridgeApi(private val hub: AgentWorkerHub) {
    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun submit(@RequestBody task: AgentTask): AgentTaskStatus = hub.submit(task)

    @GetMapping("/tasks/{runId}")
    fun status(@PathVariable runId: String): AgentTaskStatus = hub.taskStatus(runId)
}
