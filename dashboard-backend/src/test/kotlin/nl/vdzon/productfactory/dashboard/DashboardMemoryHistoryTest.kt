package nl.vdzon.productfactory.dashboard

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class DashboardMemoryHistoryTest {
    @Test
    fun `historical memory routes forward the explicit date and timeline request`() {
        val runtime = mock(ProductFactoryRuntimeClient::class.java)
        `when`(runtime.memory("hkh-autopilot", "2026-04-01"))
            .thenReturn(listOf(mapOf("title" to "Database", "content" to "Gebruik PostgreSQL.")))
        `when`(runtime.memoryHistory("hkh-autopilot"))
            .thenReturn(listOf(mapOf("id" to 7, "status" to "SUPERSEDED", "versionNumber" to 1)))
        val mvc = MockMvcBuilders.standaloneSetup(DashboardApi(runtime)).build()

        mvc.perform(
            get("/api/products/hkh-autopilot/memory").queryParam("asOf", "2026-04-01"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].content").value("Gebruik PostgreSQL."))
        mvc.perform(get("/api/products/hkh-autopilot/memory/history"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].status").value("SUPERSEDED"))

        verify(runtime).memory("hkh-autopilot", "2026-04-01")
        verify(runtime).memoryHistory("hkh-autopilot")
    }
}
