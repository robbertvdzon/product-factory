package nl.vdzon.productfactory.foundation

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FoundationControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `publieke funderingsroute is herkenbaar`() {
        mockMvc.get("/api/foundation")
            .andExpect {
                status { isOk() }
                jsonPath("$.application") { value("Product Factory") }
                jsonPath("$.state") { value("READY") }
            }
    }
}
