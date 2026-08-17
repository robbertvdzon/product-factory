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
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentContractTest {
    @Test fun `worker task pool executes agent tasks with the configured bound`() {
        val pool = boundedAgentTaskPool(3)
        val entered = CountDownLatch(3)
        val release = CountDownLatch(1)
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        try {
            val tasks = (1..6).map {
                pool.submit {
                    val current = active.incrementAndGet()
                    maximum.accumulateAndGet(current, ::maxOf)
                    entered.countDown()
                    release.await(2, TimeUnit.SECONDS)
                    active.decrementAndGet()
                }
            }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertEquals(3, maximum.get())
            release.countDown()
            tasks.forEach { it.get(2, TimeUnit.SECONDS) }
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun `worker transports a generated image from its guarded temporary path`() {
        val task = AgentTask(
            "meeting-42",
            "hkh-autopilot",
            "meeting-chat",
            "Maak een mockup",
            responseSchema = """{"generatedImages":{"type":"array"}}""",
        )
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)
        val image = Files.createTempFile("pf-generated-meeting-42-", ".png")
        Files.write(image, bytes)
        val summary = jacksonObjectMapper().writeValueAsString(
            mapOf(
                "reply" to "Hier is de mockup.",
                "generatedImages" to listOf(
                    mapOf(
                        "filename" to "mockup.png",
                        "mediaType" to "image/png",
                        "temporaryPath" to image.toString(),
                        "altText" to "Mockup van het zoekscherm",
                    ),
                ),
            ),
        )

        val transported = jacksonObjectMapper().readTree(materializeGeneratedImages(task, summary))

        val transportedImage = transported.path("generatedImages").first()
        assertEquals(Base64.getEncoder().encodeToString(bytes), transportedImage.path("base64Content").asText())
        assertFalse(transportedImage.has("temporaryPath"))
        assertFalse(Files.exists(image))
    }

    @Test fun `meeting prompt gives the agent a guarded image handoff path`() {
        val prompt = agentPrompt(
            AgentTask(
                "meeting/unsafe id",
                "hkh-autopilot",
                "meeting-chat",
                "Maak een mockup",
                responseSchema = """{"generatedImages":{"type":"array"}}""",
            ),
        )

        assertTrue(prompt.contains("generatedImages[].temporaryPath"))
        assertTrue(prompt.contains("pf-generated-meeting-unsafe-id-"))
        assertTrue(prompt.contains("Encodeer het beeld niet zelf als base64"))
    }

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
        assertTrue(command.last().contains("Wijzig geen bronbestanden"))
        assertTrue(command.last().contains("Maak geen bestanden"))
    }

    @Test fun `codex command grants the researcher unrestricted browser process access`() {
        val workspace = Files.createTempDirectory("pf-agent-workspace-researcher")
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
            AgentTask("run-2b", "hkh-autopilot", "shadow-researcher", "Onderzoek de acceptatieomgeving"),
            workspace.resolve("last-message"),
        )

        assertTrue(command.containsAll(listOf("--sandbox", "danger-full-access")))
        assertTrue(command.contains("--ignore-user-config"))
        assertFalse(command.contains("workspace-write"))
        assertFalse(command.contains("read-only"))
        assertTrue(command.last().contains("tijdelijk Playwright-testbestand onder de systeem-tempmap"))
    }

    @Test fun `codex command grants the delivery verifier unrestricted browser process access`() {
        val workspace = Files.createTempDirectory("pf-agent-workspace-verifier")
        val executor = CodexAgentTaskExecutor(
            AgentWorkerSettings(
                "wss://factory.example/agent-worker", "secret", "macbook", "test",
                workspace, "/opt/homebrew/bin/codex", "gpt-5.6-terra",
            ),
        ) { _, _, _ -> error("niet uitvoeren") }

        val command = executor.command(
            AgentTask("run-2c", "hkh-autopilot", "delivery-verification", "Controleer de oplevering"),
            workspace.resolve("last-message"),
        )

        assertTrue(command.containsAll(listOf("--sandbox", "danger-full-access")))
        assertFalse(command.contains("workspace-write"))
        assertFalse(command.contains("read-only"))
    }

    @Test fun `codex command grants the meeting agent browser access while retaining read only instructions`() {
        val workspace = Files.createTempDirectory("pf-agent-workspace-meeting")
        val executor = CodexAgentTaskExecutor(
            AgentWorkerSettings(
                "wss://factory.example/agent-worker", "secret", "macbook", "test",
                workspace, "/opt/homebrew/bin/codex", "gpt-5.6-terra",
            ),
        ) { _, _, _ -> error("niet uitvoeren") }

        val command = executor.command(
            AgentTask("run-meeting", "hkh-autopilot", "meeting-chat", "Bekijk de productieomgeving"),
            workspace.resolve("last-message"),
        )

        assertTrue(command.containsAll(listOf("--sandbox", "danger-full-access")))
        assertTrue(command.last().contains("tijdelijk Playwright-testbestand onder de systeem-tempmap"))
        assertTrue(command.last().contains("Voer geen Git-, GitHub-, OpenShift-, database- of clusterwijzigingen uit"))
    }

    @Test fun `scheduled test session receives browser access`() {
        val task = AgentTask("test-1", "hkh-autopilot", "test-session", "Test alles")
        assertTrue(requiresBrowserAccess(task))

        val workspace = Files.createTempDirectory("pf-agent-workspace-test-session")
        val executor = CodexAgentTaskExecutor(
            AgentWorkerSettings(
                "wss://factory.example/agent-worker", "secret", "macbook", "test",
                workspace, "codex", "gpt-5.6-terra",
            ),
        ) { _, _, _ -> error("niet uitvoeren") }
        val command = executor.command(task, workspace.resolve("last-message"))

        assertTrue(command.contains("--ignore-user-config"))
        assertTrue(command.last().contains("npx --no-install playwright"))
        assertTrue(command.last().contains("Rapporteer nooit een lege-pagina-bug op alleen DOM- of semanticsevidence"))
        assertTrue(command.last().contains("Een ontbrekende Browser-plugin is nooit een geldige BLOCKED-uitkomst"))
        assertTrue(command.last().contains("curl gelden niet als browsertest"))
    }

    @Test fun `browser preflight launches chromium and requires screenshot evidence`() {
        val workspace = Files.createTempDirectory("pf-browser-preflight-test")
        val commands = mutableListOf<List<String>>()
        val task = AgentTask("test-browser", "hkh-autopilot", "test-session", "Test alles")

        val failure = browserPreflightFailure(task, workspace) { command, _, _ ->
            commands += command
            Path.of(command.last()).writeBytes(byteArrayOf(1, 2, 3))
            AgentCommandResult(0, false, "ok")
        }

        assertEquals(null, failure)
        assertEquals(
            listOf("npx", "--no-install", "playwright", "screenshot", "--browser=chromium", "about:blank"),
            commands.single().dropLast(1),
        )
    }

    @Test fun `browser preflight reports playwright failure before an agent starts`() {
        val task = AgentTask("test-browser-fail", "hkh-autopilot", "test-session", "Test alles")

        val failure = browserPreflightFailure(task, Files.createTempDirectory("pf-browser-preflight-fail")) { _, _, _ ->
            AgentCommandResult(1, false, "Executable does not exist")
        }

        assertTrue(failure.orEmpty().contains("Executable does not exist"))
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
        assertTrue(command.last().contains("Wijzig geen bronbestanden"))
        assertTrue(command.last().contains("Maak geen bestanden"))
    }

    @Test fun `claude command grants the researcher role Bash and Read for a headless browser and screenshots`() {
        val workspace = Files.createTempDirectory("pf-claude-workspace-researcher")
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
        val command = executor.command(AgentTask("run-3b", "hkh-autopilot", "shadow-researcher", "Onderzoek de acceptatieomgeving"))

        assertTrue(command.containsAll(listOf("--tools", "WebSearch,WebFetch,Bash,Read")))
    }

    @Test fun `claude command grants the delivery verifier Bash and Read for a headless browser and screenshots`() {
        val workspace = Files.createTempDirectory("pf-claude-workspace-verifier")
        val executor = ClaudeAgentTaskExecutor(
            AgentWorkerSettings(
                "wss://factory.example/agent-worker", "secret", "macbook", "test",
                workspace, "codex", "gpt-5.6-terra", "/opt/homebrew/bin/claude",
            ),
        ) { _, _, _ -> error("niet uitvoeren") }

        val command = executor.command(
            AgentTask("run-3c", "hkh-autopilot", "delivery-verification", "Controleer de oplevering"),
        )

        assertTrue(command.containsAll(listOf("--tools", "WebSearch,WebFetch,Bash,Read")))
    }

    @Test fun `claude command grants the meeting agent Bash and Read for browser research`() {
        val workspace = Files.createTempDirectory("pf-claude-workspace-meeting")
        val executor = ClaudeAgentTaskExecutor(
            AgentWorkerSettings(
                "wss://factory.example/agent-worker", "secret", "macbook", "test",
                workspace, "codex", "gpt-5.6-terra", "/opt/homebrew/bin/claude",
            ),
        ) { _, _, _ -> error("niet uitvoeren") }

        val command = executor.command(AgentTask("run-meeting", "hkh-autopilot", "meeting-chat", "Test acceptatie"))

        assertTrue(command.containsAll(listOf("--tools", "WebSearch,WebFetch,Bash,Read")))
    }

    @Test fun `claude command keeps non researcher roles read only`() {
        val executor = ClaudeAgentTaskExecutor(
            AgentWorkerSettings(
                "wss://factory.example/agent-worker", "secret", "macbook", "test",
                Files.createTempDirectory("pf-claude-workspace-non-researcher"), "codex", "gpt-5.6-terra",
            ),
        ) { _, _, _ -> error("niet uitvoeren") }

        val command = executor.command(AgentTask("run-3d", "hkh-autopilot", "shadow-critic", "Beoordeel"))

        assertTrue(command.containsAll(listOf("--tools", "WebSearch,WebFetch")))
        assertFalse(command.contains("WebSearch,WebFetch,Bash,Read"))
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

    @Test fun `claude result parsing prefers the last valid json object when the model thinks out loud first`() {
        val executor = ClaudeAgentTaskExecutor(
            AgentWorkerSettings(
                "wss://factory.example/agent-worker", "secret", "macbook", "test",
                Files.createTempDirectory("pf-claude-parse-multi-json"), "codex", "gpt-5.6-terra",
            ),
        ) { _, _, _ -> error("niet uitvoeren") }
        val task = AgentTask("run-7b", "hkh-autopilot", "research", "Onderzoek", responseSchema = """{"type":"object"}""")
        val rawResult = "Kladversie: {\"greeting\": \"concept\"}\n\nHet definitieve antwoord: {\"greeting\": \"definitief\"}"
        val envelope = jacksonObjectMapper().writeValueAsString(
            mapOf("type" to "result", "subtype" to "success", "is_error" to false, "result" to rawResult),
        )

        val result = executor.parseResult(task, AgentCommandResult(0, false, envelope))

        assertEquals("COMPLETED", result.status)
        assertEquals("""{"greeting": "definitief"}""", result.summary)
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

    @Test fun `claude result parsing falls back to raw output and subtype when result text is blank on error`() {
        val executor = ClaudeAgentTaskExecutor(
            AgentWorkerSettings(
                "wss://factory.example/agent-worker", "secret", "macbook", "test",
                Files.createTempDirectory("pf-claude-parse-blank-error"), "codex", "gpt-5.6-terra",
            ),
        ) { _, _, _ -> error("niet uitvoeren") }
        val task = AgentTask("run-9", "hkh-autopilot", "research", "Onderzoek")
        val envelope = """{"type":"result","subtype":"error_max_turns","is_error":true,"result":""}"""

        val result = executor.parseResult(task, AgentCommandResult(1, false, envelope))

        assertEquals("FAILED", result.status)
        assertTrue(result.summary.contains("error_max_turns"))
        assertTrue(result.summary.contains(envelope))
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
