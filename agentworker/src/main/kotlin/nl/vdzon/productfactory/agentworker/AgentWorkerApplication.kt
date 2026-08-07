package nl.vdzon.productfactory.agentworker

import nl.vdzon.productfactory.common.config.EnvironmentFiles
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import java.nio.file.Path

@SpringBootApplication
class AgentWorkerApplication {
    @Bean
    fun settings(
        @Value("\${PF_AGENT_WORKER_URL:}") url: String,
        @Value("\${PF_AGENT_WORKER_TOKEN:}") token: String,
        @Value("\${PF_AGENT_WORKER_ID:\${user.name}-mac}") workerId: String,
        @Value("\${PF_BUILD_SHA:development}") version: String,
        @Value("\${PF_AGENT_WORKSPACE_PATH:../product-factory-workspace}") workspacePath: String,
        @Value("\${PF_CODEX_EXECUTABLE:codex}") codexExecutable: String,
        @Value("\${PF_AGENT_MODEL:gpt-5.6-terra}") defaultModel: String,
    ) = AgentWorkerSettings(
        url = url,
        token = token,
        workerId = workerId,
        version = version,
        workspacePath = resolveWorkspacePath(EnvironmentFiles.locate(), workspacePath),
        codexExecutable = codexExecutable,
        defaultModel = defaultModel,
    )

    @Bean fun taskExecutor(settings: AgentWorkerSettings): AgentTaskExecutor = CodexAgentTaskExecutor(settings)
    @Bean fun workerClient(settings: AgentWorkerSettings, taskExecutor: AgentTaskExecutor) = AgentWorkerClient(settings, taskExecutor)
    @Bean fun startWorker(client: AgentWorkerClient) = CommandLineRunner { client.runUntilShutdown() }
}

internal fun resolveWorkspacePath(configDirectory: Path, configuredPath: String): Path {
    val path = Path.of(configuredPath)
    return (if (path.isAbsolute) path else configDirectory.resolve(path)).toAbsolutePath().normalize()
}

fun main(args: Array<String>) {
    SpringApplication(AgentWorkerApplication::class.java).apply {
        setDefaultProperties(EnvironmentFiles.load(EnvironmentFiles.locate()))
        run(*args)
    }
}
