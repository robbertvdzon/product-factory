package nl.vdzon.productfactory.dashboard

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import nl.vdzon.productfactory.contracts.AgentResult
import nl.vdzon.productfactory.contracts.AgentTask
import nl.vdzon.productfactory.contracts.AgentTaskState
import nl.vdzon.productfactory.contracts.AgentWorkerHello
import nl.vdzon.productfactory.contracts.AgentWorkerResultFrame
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentWorkerHubTest {
    private val mapper = jacksonObjectMapper().findAndRegisterModules()

    @Test
    fun `authenticated worker accepts task and reports result`() {
        val session = openSession()
        val hub = AgentWorkerHub("bridge-secret")
        hub.handleMessage(
            session,
            TextMessage(mapper.writeValueAsString(AgentWorkerHello(token = "bridge-secret", workerId = "macbook", workerVersion = "abc123"))),
        )

        assertTrue(hub.status().connected)
        val task = AgentTask("run-1", "hkh-autopilot", "research", "Onderzoek historische bronnen")
        assertEquals(AgentTaskState.RUNNING, hub.submit(task).state)

        val messages = ArgumentCaptor.forClass(org.springframework.web.socket.WebSocketMessage::class.java)
        verify(session).sendMessage(messages.capture())
        assertEquals("task", mapper.readTree((messages.value as TextMessage).payload).path("type").asText())

        val result = AgentResult("run-1", "COMPLETED", "Onderzoek afgerond")
        hub.handleMessage(session, TextMessage(mapper.writeValueAsString(AgentWorkerResultFrame(result = result))))
        assertEquals(AgentTaskState.COMPLETED, hub.taskStatus("run-1").state)
        assertEquals("Onderzoek afgerond", hub.taskStatus("run-1").result?.summary)
    }

    @Test
    fun `submitting the same running task is idempotent`() {
        val session = openSession()
        val hub = AgentWorkerHub("bridge-secret")
        hub.handleMessage(
            session,
            TextMessage(mapper.writeValueAsString(AgentWorkerHello(token = "bridge-secret", workerId = "macbook", workerVersion = "abc123"))),
        )
        val task = AgentTask("run-idempotent", "hkh-autopilot", "test-session", "Test de omgeving")

        assertEquals(AgentTaskState.RUNNING, hub.submit(task).state)
        assertEquals(AgentTaskState.RUNNING, hub.submit(task).state)

        val messages = ArgumentCaptor.forClass(org.springframework.web.socket.WebSocketMessage::class.java)
        verify(session).sendMessage(messages.capture())
        assertEquals(1, messages.allValues.size)
    }

    @Test
    fun `disconnected task is offered again after worker reconnects`() {
        val firstSession = openSession()
        val hub = AgentWorkerHub("bridge-secret")
        val hello = AgentWorkerHello(token = "bridge-secret", workerId = "macbook", workerVersion = "abc123")
        hub.handleMessage(firstSession, TextMessage(mapper.writeValueAsString(hello)))
        val task = AgentTask("run-resume", "hkh-autopilot", "test-session", "Test de omgeving")
        hub.submit(task)
        hub.afterConnectionClosed(firstSession, CloseStatus.SERVER_ERROR)

        val secondSession = openSession()
        hub.handleMessage(secondSession, TextMessage(mapper.writeValueAsString(hello)))
        assertEquals(AgentTaskState.RUNNING, hub.submit(task).state)

        val messages = ArgumentCaptor.forClass(org.springframework.web.socket.WebSocketMessage::class.java)
        verify(secondSession).sendMessage(messages.capture())
        assertEquals("task", mapper.readTree((messages.value as TextMessage).payload).path("type").asText())
    }

    @Test
    fun `invalid hello is rejected without exposing a connected worker`() {
        val session = openSession()
        val hub = AgentWorkerHub("bridge-secret")
        hub.handleMessage(
            session,
            TextMessage(mapper.writeValueAsString(AgentWorkerHello(token = "wrong", workerId = "unknown", workerVersion = "none"))),
        )

        assertFalse(hub.status().connected)
        verify(session).close(CloseStatus.POLICY_VIOLATION)
    }

    private fun openSession(): WebSocketSession = mock(WebSocketSession::class.java).also {
        `when`(it.isOpen).thenReturn(true)
    }
}
