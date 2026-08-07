package nl.vdzon.productfactory.agentworker

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import nl.vdzon.productfactory.contracts.AgentTask
import nl.vdzon.productfactory.contracts.AgentWorkerTaskFrame
import java.nio.file.Files
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
        assertTrue(command.containsAll(listOf("exec", "--json", "workspace-write", "--model", "gpt-5.6-terra")))
        assertTrue(command.last().contains("Onderzoek openbare archieven"))
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
