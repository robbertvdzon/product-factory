package nl.vdzon.productfactory.dashboard

import nl.vdzon.productfactory.contracts.AgentTask
import nl.vdzon.productfactory.contracts.AgentTaskStatus
import nl.vdzon.productfactory.contracts.AgentWorkerStatus
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/agent-worker")
class AgentWorkerApi(private val hub: AgentWorkerHub, private val runtime: ProductFactoryRuntimeClient) {
    @GetMapping("/status")
    fun status(): AgentWorkerStatus = hub.status()

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun submit(@RequestBody task: AgentTask): AgentTaskStatus = translateErrors {
        runtime.requireRunnableProduct(task.productSlug)
        hub.submit(task).also { runtime.registerAgentRun(task) }
    }

    @GetMapping("/tasks/{runId}")
    fun task(@PathVariable runId: String, @RequestParam productSlug: String): AgentTaskStatus = translateErrors {
        runtime.requireAgentRun(productSlug, runId)
        hub.taskStatus(runId)
    }

    private fun <T> translateErrors(action: () -> T): T = try {
        action()
    } catch (exception: AgentWorkerOfflineException) {
        throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.message)
    } catch (exception: AgentTaskNotFoundException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, exception.message)
    } catch (exception: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, exception.message)
    } catch (exception: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.CONFLICT, exception.message)
    }
}
