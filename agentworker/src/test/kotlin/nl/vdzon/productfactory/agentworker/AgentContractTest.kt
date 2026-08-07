package nl.vdzon.productfactory.agentworker

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import nl.vdzon.productfactory.contracts.AgentResult
import nl.vdzon.productfactory.contracts.AgentTask
import nl.vdzon.productfactory.contracts.AgentWorkerResultFrame
import nl.vdzon.productfactory.contracts.AgentWorkerTaskFrame
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentContractTest {
    @Test fun `agent task contract round trips`() {
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val task = AgentTask("run-1", "hkh-autopilot", "research", "Onderzoek bronnen")
        assertEquals(task, mapper.readValue<AgentTask>(mapper.writeValueAsString(task)))
        val frame = AgentWorkerTaskFrame(task = task)
        assertEquals(frame, mapper.readValue<AgentWorkerTaskFrame>(mapper.writeValueAsString(frame)))
    }

    @Test fun `agent result frame serializes its completion timestamp`() {
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val frame = AgentWorkerResultFrame(
            result = AgentResult("run-result", "COMPLETED", "klaar", completedAt = Instant.parse("2026-08-07T13:17:00Z")),
        )

        val decoded = mapper.readValue<AgentWorkerResultFrame>(mapper.writeValueAsString(frame))

        assertEquals(frame, decoded)
    }

    @Test fun `codex command uses workspace sandbox subscription cli and configured model`() {
        val workspace = Files.createTempDirectory("pf-agent-workspace")
        val settings = AgentWorkerSettings(
            url = "wss://factory.example/agent-worker",
            token = "secret",
            workerId = "macbook",
            version = "test",
            workspacePath = workspace,
            codexExecutable = "/opt/homebrew/bin/codex",
            defaultModel = "gpt-5.6-terra",
        )
        val executor = CodexAgentTaskExecutor(settings) { _, _, _ -> error("niet uitvoeren") }
        val command = executor.command(
            AgentTask("run-2", "hkh-autopilot", "research", "Onderzoek openbare archieven"),
            workspace.resolve("last-message"),
        )

        assertEquals("/opt/homebrew/bin/codex", command.first())
        assertTrue(
            command.containsAll(
                listOf(
                    "--search", "exec", "--ignore-user-config", "--ignore-rules", "shell_environment_policy.inherit=none",
                    "--json", "read-only", "--ephemeral", "--model", "gpt-5.6-terra",
                ),
            ),
        )
        assertTrue(command.last().contains("Onderzoek openbare archieven"))
        assertTrue(command.last().contains("Wijzig geen bestanden"))
    }

    @Test fun `codex command passes a structured output schema`() {
        val workspace = Files.createTempDirectory("pf-agent-schema")
        val executor = CodexAgentTaskExecutor(
            AgentWorkerSettings(
                "wss://factory.example/agent-worker", "secret", "macbook", "test", workspace, "codex", "gpt-5.6-terra",
            ),
        ) { _, _, _ -> error("niet uitvoeren") }
        val schema = workspace.resolve("response-schema.json")
        Files.writeString(schema, "{}")

        val command = executor.command(
            AgentTask("run-schema", "hkh-autopilot", "research", "Onderzoek"),
            workspace.resolve("last-message"),
            schema,
        )

        val option = command.indexOf("--output-schema")
        assertTrue(option > 0)
        assertEquals(schema.toString(), command[option + 1])
    }

    @Test fun `agent process environment contains no application or infrastructure credentials`() {
        val safe = safeAgentEnvironment(
            mapOf(
                "PATH" to "/usr/bin",
                "HOME" to "/Users/test",
                "CODEX_HOME" to "/Users/test/.codex",
                "GH_TOKEN" to "github-secret",
                "GITHUB_TOKEN" to "github-secret-2",
                "KUBECONFIG" to "/secret/kubeconfig",
                "PF_DB_PASSWORD" to "database-secret",
                "PF_WORKSPACE_GITHUB_TOKEN" to "workspace-secret",
                "OPENAI_API_KEY" to "api-secret",
            ),
        )
        assertEquals(setOf("PATH", "HOME", "CODEX_HOME"), safe.keys)
        assertFalse(safe.values.any { it.contains("secret") })
    }

    @Test fun `agent command receives end of input instead of waiting forever`() {
        val result = ProcessAgentCommandRunner().run(
            listOf("/bin/sh", "-c", "if read input; then exit 9; else printf closed; fi"),
            Files.createTempDirectory("pf-agent-stdin"),
            2,
        )

        assertFalse(result.timedOut)
        assertEquals(0, result.exitCode)
        assertEquals("closed", result.output.trim())
    }

    @Test fun `relative workspace path is resolved from repository configuration root`() {
        val repository = Path.of("/Users/example/git/product-factory")

        assertEquals(
            Path.of("/Users/example/git/product-factory-workspace"),
            resolveWorkspacePath(repository, "../product-factory-workspace"),
        )
        assertEquals(
            Path.of("/opt/product-factory-workspace"),
            resolveWorkspacePath(repository, "/opt/product-factory-workspace"),
        )
    }

    @Test fun `worker process remains alive until shutdown`() {
        val settings = AgentWorkerSettings(
            url = "ws://127.0.0.1:1/agent-worker",
            token = "secret",
            workerId = "lifecycle-test",
            version = "test",
            workspacePath = Files.createTempDirectory("pf-agent-lifecycle"),
            codexExecutable = "codex",
            defaultModel = "gpt-5.6-terra",
        )
        val client = AgentWorkerClient(settings, AgentTaskExecutor { error("geen taak verwacht") })
        val workerThread = Thread(client::runUntilShutdown).apply { start() }

        try {
            Thread.sleep(100)
            assertTrue(workerThread.isAlive)
        } finally {
            client.stop()
            workerThread.join(2_000)
        }
        assertFalse(workerThread.isAlive)
    }
}
