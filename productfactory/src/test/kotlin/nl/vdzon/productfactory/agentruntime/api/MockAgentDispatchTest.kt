package nl.vdzon.productfactory.agentruntime.api

import nl.vdzon.productfactory.contracts.AgentArtifact
import nl.vdzon.productfactory.contracts.AgentTask
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockAgentDispatchTest {
    private val task = AgentTask(runId = "run-1", productSlug = "hkh-autopilot", taskType = "research", prompt = "test")

    @Test
    fun `returns a benign default when no response is queued`() {
        val bridge = MockAgentDispatch(MockAgentResponseStore())
        val result = bridge.execute(task)
        assertEquals("run-1", result.runId)
        assertEquals("COMPLETED", result.status)
    }

    @Test
    fun `returns queued responses in order and then falls back to the default`() {
        val store = MockAgentResponseStore()
        store.enqueue(MockAgentResponse(status = "FAILED", summary = "eerste"))
        store.enqueue(MockAgentResponse(status = "COMPLETED", summary = "tweede", artifacts = listOf(AgentArtifact("out.md", "text/markdown", "inhoud"))))
        val bridge = MockAgentDispatch(store)

        val first = bridge.execute(task)
        assertEquals("FAILED", first.status)
        assertEquals("eerste", first.summary)

        val second = bridge.execute(task)
        assertEquals("tweede", second.summary)
        assertEquals(1, second.artifacts.size)

        val third = bridge.execute(task)
        assertEquals("COMPLETED", third.status)
    }

    @Test
    fun `controller enqueues lists and clears queued responses`() {
        val store = MockAgentResponseStore()
        val controller = MockAgentBridgeController(store)

        controller.enqueue(MockAgentResponse(summary = "een"))
        controller.enqueue(MockAgentResponse(summary = "twee"))
        assertEquals(listOf("een", "twee"), controller.list().map { it.summary })

        controller.clear()
        assertTrue(controller.list().isEmpty())
    }
}
