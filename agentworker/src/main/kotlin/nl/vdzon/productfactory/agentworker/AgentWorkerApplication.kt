package nl.vdzon.productfactory.agentworker

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import nl.vdzon.productfactory.common.config.EnvironmentFiles
import nl.vdzon.productfactory.contracts.AgentResult
import nl.vdzon.productfactory.contracts.AgentTask
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class AgentWorkerApplication {
    @Bean fun executeTask() = CommandLineRunner {
        val taskJson = System.getenv("PF_AGENT_TASK_JSON") ?: return@CommandLineRunner
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val task: AgentTask = mapper.readValue(taskJson)
        val result = AgentResult(task.runId, "COMPLETED", "Agentworker-contract is uitgevoerd voor ${task.taskType}")
        println(mapper.writeValueAsString(result))
    }
}

fun main(args: Array<String>) {
    SpringApplication(AgentWorkerApplication::class.java).apply {
        setDefaultProperties(EnvironmentFiles.load(EnvironmentFiles.locate()))
        run(*args)
    }
}
