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

    @Test fun `claude command uses print json output setting isolation and configured model`() {
        val workspace = Files.createTempDirectory("pf-claude-workspace")
        val settings = AgentWorkerSettings(
            url = "wss://factory.example/agent-worker",
            token = "secret",
            workerId = "macbook",
            version = "test",
            workspacePath = workspace,
            codexExecutable = "codex",
            defaultModel = "gpt-5.6-terra",
            claudeExecutable = "/opt/homebrew/bin/claude",
        )
        val executor = ClaudeAgentTaskExecutor(settings) { _, _, _ -> error("niet uitvoeren") }
        val command = executor.command(AgentTask("run-3", "hkh-autopilot", "research", "Onderzoek openbare archieven", model = "claude-opus-5"))

        assertEquals("/opt/homebrew/bin/claude", command.first())
        assertTrue(
            command.containsAll(
                listOf(
                    "--print", "--output-format", "json", "--setting-sources", "", "--tools", "WebSearch,WebFetch",
                    "--permission-mode", "bypassPermissions", "--model", "claude-opus-5",
                ),
            ),
        )
        assertTrue(command.last().contains("Onderzoek openbare archieven"))
        assertTrue(command.last().contains("Wijzig geen bestanden"))
    }

    @Test fun `claude command passes the response schema inline instead of via a file`() {
        val executor = ClaudeAgentTaskExecutor(
            AgentWorkerSettings(
                "wss://factory.example/agent-worker", "secret", "macbook", "test",
                Files.createTempDirectory("pf-claude-schema"), "codex", "gpt-5.6-terra",
            ),
        ) { _, _, _ -> error("niet uitvoeren") }
        val schema = """{"type":"object","required":["greeting"],"properties":{"greeting":{"type":"string"}}}"""

        val command = executor.command(AgentTask("run-4", "hkh-autopilot", "research", "Onderzoek", responseSchema = schema))

        val option = command.indexOf("--json-schema")
        assertTrue(option > 0)
        assertEquals(schema, command[option + 1])
    }

    @Test fun `claude command omits the model flag when the task has no explicit model`() {
        val executor = ClaudeAgentTaskExecutor(
            AgentWorkerSettings(
                "wss://factory.example/agent-worker", "secret", "macbook", "test",
                Files.createTempDirectory("pf-claude-default-model"), "codex", "gpt-5.6-terra",
            ),
        ) { _, _, _ -> error("niet uitvoeren") }

        val command = executor.command(AgentTask("run-5", "hkh-autopilot", "research", "Onderzoek"))

        assertFalse(command.contains("--model"))
    }

    @Test fun `claude result parsing accepts a clean structured result`() {
        val executor = ClaudeAgentTaskExecutor(
            AgentWorkerSettings(
                "wss://factory.example/agent-worker", "secret", "macbook", "test",
                Files.createTempDirectory("pf-claude-parse-ok"), "codex", "gpt-5.6-terra",
            ),
        ) { _, _, _ -> error("niet uitvoeren") }
        val task = AgentTask("run-6", "hkh-autopilot", "research", "Onderzoek", responseSchema = """{"type":"object"}""")
        val envelope = """{"type":"result","subtype":"success","is_error":false,"result":"{\"greeting\":\"hoi\"}"}"""

        val result = executor.parseResult(task, AgentCommandResult(0, false, envelope))

        assertEquals("COMPLETED", result.status)
        assertEquals("""{"greeting":"hoi"}""", result.summary)
    }

    @Test fun `claude result parsing prefers the structured_output field over the free-text result`() {
        val executor = ClaudeAgentTaskExecutor(
            AgentWorkerSettings(
                "wss://factory.example/agent-worker", "secret", "macbook", "test",
                Files.createTempDirectory("pf-claude-parse-structured"), "codex", "gpt-5.6-terra",
            ),
        ) { _, _, _ -> error("niet uitvoeren") }
        val task = AgentTask("run-6b", "hkh-autopilot", "research", "Onderzoek", responseSchema = """{"type":"object"}""")
        val envelope = jacksonObjectMapper().writeValueAsString(
            mapOf(
                "type" to "result",
                "subtype" to "success",
                "is_error" to false,
                "result" to "Onderzoek afgerond en als gestructureerde JSON opgeleverd.",
                "structured_output" to mapOf("greeting" to "hoi"),
            ),
        )

        val result = executor.parseResult(task, AgentCommandResult(0, false, envelope))

        assertEquals("COMPLETED", result.status)
        assertEquals("""{"greeting":"hoi"}""", result.summary)
    }

    @Test fun `claude result parsing extracts the embedded json object when the model added prose around it`() {
        val executor = ClaudeAgentTaskExecutor(
            AgentWorkerSettings(
                "wss://factory.example/agent-worker", "secret", "macbook", "test",
                Files.createTempDirectory("pf-claude-parse-prose"), "codex", "gpt-5.6-terra",
            ),
        ) { _, _, _ -> error("niet uitvoeren") }
        val task = AgentTask("run-7", "hkh-autopilot", "research", "Onderzoek", responseSchema = """{"type":"object"}""")
        val rawResult = "Hier is mijn antwoord:\n\n```json\n{\"greeting\": \"hoi\", \"nested\": {\"a\": 1}}\n```\n\nBedankt!"
        val envelope = jacksonObjectMapper().writeValueAsString(
            mapOf("type" to "result", "subtype" to "success", "is_error" to false, "result" to rawResult),
        )

        val result = executor.parseResult(task, AgentCommandResult(0, false, envelope))

        assertEquals("COMPLETED", result.status)
        assertEquals("""{"greeting": "hoi", "nested": {"a": 1}}""", result.summary)
    }

    @Test fun `claude result parsing fails when is_error is set`() {
        val executor = ClaudeAgentTaskExecutor(
            AgentWorkerSettings(
                "wss://factory.example/agent-worker", "secret", "macbook", "test",
                Files.createTempDirectory("pf-claude-parse-error"), "codex", "gpt-5.6-terra",
            ),
        ) { _, _, _ -> error("niet uitvoeren") }
        val task = AgentTask("run-8", "hkh-autopilot", "research", "Onderzoek")
        val envelope = """{"type":"result","subtype":"error","is_error":true,"result":"OAuth access token has been revoked."}"""

        val result = executor.parseResult(task, AgentCommandResult(1, false, envelope))

        assertEquals("FAILED", result.status)
        assertTrue(result.summary.contains("OAuth access token has been revoked"))
    }

    @Test fun `routing executor dispatches on task provider and defaults to codex`() {
        val calls = mutableListOf<String>()
        val router = RoutingAgentTaskExecutor(
            mapOf(
                "codex" to AgentTaskExecutor { task -> calls.add("codex:${task.runId}"); AgentResult(task.runId, "COMPLETED", "ok") },
                "claude" to AgentTaskExecutor { task -> calls.add("claude:${task.runId}"); AgentResult(task.runId, "COMPLETED", "ok") },
            ),
        )

        router.execute(AgentTask("r1", "hkh-autopilot", "research", "x", provider = "claude"))
        router.execute(AgentTask("r2", "hkh-autopilot", "research", "x", provider = null))
        router.execute(AgentTask("r3", "hkh-autopilot", "research", "x", provider = "CLAUDE"))

        assertEquals(listOf("claude:r1", "codex:r2", "claude:r3"), calls)
    }

    @Test fun `routing executor fails clearly for an unknown provider`() {
        val router = RoutingAgentTaskExecutor(mapOf("codex" to AgentTaskExecutor { AgentResult(it.runId, "COMPLETED", "ok") }))

        val result = router.execute(AgentTask("r4", "hkh-autopilot", "research", "x", provider = "bard"))

        assertEquals("FAILED", result.status)
        assertTrue(result.summary.contains("bard"))
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
