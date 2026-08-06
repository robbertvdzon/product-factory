package nl.vdzon.productfactory.agentworker

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import nl.vdzon.productfactory.contracts.AgentTask
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentContractTest {
    @Test fun `agent task contract round trips`() {
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val task = AgentTask("run-1", "hkh-autopilot", "research", "Onderzoek bronnen")
        assertEquals(task, mapper.readValue<AgentTask>(mapper.writeValueAsString(task)))
    }
}
