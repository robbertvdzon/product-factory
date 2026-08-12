package nl.vdzon.productfactory.dashboard

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class DashboardApiResumeTest {
    @Test
    fun `resume route forwards the iteration to the runtime`() {
        val runtime = mock(ProductFactoryRuntimeClient::class.java)
        `when`(runtime.resumeShadowIteration("hkh-autopilot", "iteration-54"))
            .thenReturn(mapOf("id" to "iteration-55", "status" to "PLANNED"))
        val mvc = MockMvcBuilders.standaloneSetup(DashboardApi(runtime)).build()

        mvc.perform(
            post("/api/shadow-iterations/iteration-54/resume")
                .queryParam("productSlug", "hkh-autopilot"),
        ).andExpect(status().isAccepted)

        verify(runtime).resumeShadowIteration("hkh-autopilot", "iteration-54")
    }
}
