package nl.vdzon.productfactory.agentworker

import nl.vdzon.productfactory.contracts.AgentResult
import nl.vdzon.productfactory.contracts.AgentTask
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit

data class AgentWorkerSettings(
    val url: String,
    val token: String,
    val workerId: String,
    val version: String,
    val workspacePath: Path,
    val codexExecutable: String,
    val defaultModel: String,
)

data class AgentCommandResult(val exitCode: Int, val timedOut: Boolean, val output: String)

fun interface AgentCommandRunner {
    fun run(command: List<String>, cwd: Path, timeoutSeconds: Long): AgentCommandResult
}

class ProcessAgentCommandRunner : AgentCommandRunner {
    override fun run(command: List<String>, cwd: Path, timeoutSeconds: Long): AgentCommandResult {
        val process = ProcessBuilder(command)
            .directory(cwd.toFile())
            .redirectErrorStream(true)
            .apply {
                environment().remove("OPENAI_API_KEY")
                environment().remove("CODEX_API_KEY")
            }
            .start()
        val output = StringBuilder()
        val reader = Thread({
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (output.length < MAX_CAPTURED_OUTPUT_CHARS) output.appendLine(line)
                }
            }
        }, "product-factory-agent-output").apply {
            isDaemon = true
            start()
        }
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly().waitFor(10, TimeUnit.SECONDS)
        reader.join(10_000)
        return AgentCommandResult(if (finished) process.exitValue() else -1, !finished, output.toString())
    }

    companion object {
        const val MAX_CAPTURED_OUTPUT_CHARS = 1_000_000
    }
}

fun interface AgentTaskExecutor {
    fun execute(task: AgentTask): AgentResult
}

class CodexAgentTaskExecutor(
    private val settings: AgentWorkerSettings,
    private val runner: AgentCommandRunner = ProcessAgentCommandRunner(),
) : AgentTaskExecutor {
    override fun execute(task: AgentTask): AgentResult {
        if (!Files.isDirectory(settings.workspacePath)) {
            return failed(task, "Workspace bestaat niet: ${settings.workspacePath}")
        }
        if (!hasCodexSubscriptionCredentials()) {
            return failed(task, "Geen Codex-abonnementslogin gevonden; voer `codex login` uit op deze Mac.")
        }

        val lastMessage = Files.createTempFile(settings.workspacePath, ".agent-${safeRunId(task.runId)}-", ".last-message")
        return try {
            val commandResult = runner.run(command(task, lastMessage), settings.workspacePath, task.timeoutSeconds)
            val summary = runCatching { Files.readString(lastMessage).trim() }.getOrDefault("")
                .ifBlank { commandResult.output.takeLast(FALLBACK_SUMMARY_CHARS).trim() }
            when {
                commandResult.timedOut -> failed(task, "Codex-taak stopte na de time-out van ${task.timeoutSeconds} seconden.")
                commandResult.exitCode != 0 -> failed(task, summary.ifBlank { "Codex stopte met exitcode ${commandResult.exitCode}." })
                summary.isBlank() -> failed(task, "Codex heeft geen eindresultaat teruggegeven.")
                else -> AgentResult(task.runId, "COMPLETED", summary, completedAt = Instant.now())
            }
        } catch (exception: Exception) {
            failed(task, "Codex-taak kon niet worden uitgevoerd: ${exception.message ?: exception.javaClass.simpleName}")
        } finally {
            Files.deleteIfExists(lastMessage)
        }
    }

    internal fun command(task: AgentTask, lastMessage: Path): List<String> = buildList {
        add(settings.codexExecutable)
        add("exec")
        add("--json")
        add("--sandbox")
        add("workspace-write")
        add("--skip-git-repo-check")
        add("--output-last-message")
        add(lastMessage.toString())
        (task.model ?: settings.defaultModel).takeIf { it.isNotBlank() }?.let { model ->
            require(model.matches(Regex("[A-Za-z0-9._-]+"))) { "Ongeldig model" }
            add("--model")
            add(model)
        }
        add(
            """
            Je bent een autonome Product Factory-agent voor product '${task.productSlug}'.
            Taaktype: ${task.taskType}.
            Werk uitsluitend binnen de huidige product-factory-workspace en behandel bestaande bestanden als gedeelde projectkennis.

            ${task.prompt.trim()}
            """.trimIndent(),
        )
    }

    private fun hasCodexSubscriptionCredentials(): Boolean {
        val home = System.getenv("CODEX_HOME")?.takeIf(String::isNotBlank)?.let(Path::of)
            ?: Path.of(System.getProperty("user.home"), ".codex")
        return Files.exists(home.resolve("auth.json"))
    }

    private fun failed(task: AgentTask, summary: String) = AgentResult(task.runId, "FAILED", summary, completedAt = Instant.now())
    private fun safeRunId(runId: String) = runId.replace(Regex("[^A-Za-z0-9._-]"), "-").take(80)

    companion object {
        const val FALLBACK_SUMMARY_CHARS = 8_000
    }
}
