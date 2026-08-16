package nl.vdzon.productfactory.dashboard

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class MeetingApiTest {
    @Test
    fun `message proxy stays compatible with clients that only accept 200`() {
        val runtime = mock(ProductFactoryRuntimeClient::class.java)
        `when`(runtime.sendMeetingMessage("hkh-autopilot", "meeting-5", "Maak een screenshot", emptyList()))
            .thenReturn(mapOf("id" to 42, "role" to "OWNER"))
        val mvc = MockMvcBuilders.standaloneSetup(MeetingApi(runtime)).build()

        mvc.perform(
            post("/api/products/hkh-autopilot/meetings/meeting-5/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"content":"Maak een screenshot","imageAssetIds":[]}"""),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(42))

        verify(runtime).sendMeetingMessage("hkh-autopilot", "meeting-5", "Maak een screenshot", emptyList())
    }
}
