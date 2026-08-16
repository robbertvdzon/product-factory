package nl.vdzon.productfactory.dashboard

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class DashboardApiOverviewRoutesTest {
    @Test
    fun `overview aggregation routes are registered and scoped through known products`() {
        val runtime = mock(ProductFactoryRuntimeClient::class.java)
        `when`(runtime.products()).thenReturn(listOf(mapOf("slug" to "active-product")))
        `when`(runtime.bugs("active-product")).thenReturn(listOf(mapOf("id" to 11)))
        `when`(runtime.testSessions("active-product")).thenReturn(listOf(mapOf("id" to "test-12")))
        `when`(runtime.roadmapVision("active-product")).thenReturn(mapOf("id" to 13))
        val mvc = MockMvcBuilders.standaloneSetup(DashboardApi(runtime)).build()

        mvc.perform(get("/api/bugs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(11))
        mvc.perform(get("/api/test-sessions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value("test-12"))
        mvc.perform(get("/api/roadmap/visions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(13))

        verify(runtime, times(3)).products()
        verify(runtime).bugs("active-product")
        verify(runtime).testSessions("active-product")
        verify(runtime).roadmapVision("active-product")
    }
}
