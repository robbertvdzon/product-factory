package nl.vdzon.productfactory.dashboard

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class DashboardApiManualStartTest {
    @Test
    fun `manual start forwards only focus and origin to runtime`() {
        val runtime = mock(ProductFactoryRuntimeClient::class.java)
        `when`(runtime.startCycle("active-product", "Exacte vraag", "OWNER_INPUT"))
            .thenReturn(mapOf("id" to "iteration-1"))
        val mvc = MockMvcBuilders.standaloneSetup(DashboardApi(runtime)).build()

        mvc.post("/api/products/active-product/cycles") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"focus":"Exacte vraag","manualStartOrigin":"OWNER_INPUT"}"""
        }.andExpect { status { isAccepted() } }

        verify(runtime).startCycle("active-product", "Exacte vraag", "OWNER_INPUT")
    }
}
