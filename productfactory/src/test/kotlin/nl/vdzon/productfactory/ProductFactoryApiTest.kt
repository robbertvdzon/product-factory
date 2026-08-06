package nl.vdzon.productfactory

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class ProductFactoryApiTest(@Autowired private val mvc: MockMvc) {
    @Test fun `product and internal story candidate can be recorded`() {
        mvc.post("/api/products") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"slug":"hkh-autopilot","name":"HKH Autopilot","mission":"Geschiedenis toegankelijk maken","guardrails":"Gebruik bronnen"}"""
        }.andExpect { status { isCreated() }; jsonPath("$.slug") { value("hkh-autopilot") } }

        mvc.post("/api/story-candidates") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"productSlug":"hkh-autopilot","title":"Eerste hypothese","description":"Maak een kleine, toetsbare productstap"}"""
        }.andExpect { status { isCreated() }; jsonPath("$.status") { value("INTERNAL") } }

        mvc.get("/api/story-candidates?productSlug=hkh-autopilot").andExpect {
            status { isOk() }
            jsonPath("$[0].title") { value("Eerste hypothese") }
        }
    }
}
