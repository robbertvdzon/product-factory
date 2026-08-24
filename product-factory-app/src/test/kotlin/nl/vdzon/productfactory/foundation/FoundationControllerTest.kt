package nl.vdzon.productfactory.foundation

import nl.vdzon.productfactory.api.testbed.TestControlService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FoundationControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val testControlService: ObjectProvider<TestControlService>,
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

    @Test
    fun `publieke versieinformatie is begrensd en niet cachebaar`() {
        mockMvc.get("/api/version")
            .andExpect {
                status { isOk() }
                header { string("Cache-Control", "no-store") }
                jsonPath("$.applicationVersion") { value("Onbekend") }
                jsonPath("$.apiVersion") { value("1") }
                jsonPath("$.gitRevision") { value("Onbekend") }
            }
    }

    @Test
    fun `productieachtige profielen registreren geen Testbed`() {
        assertThat(testControlService.ifAvailable).isNull()
        mockMvc.get("/api/test-control/scenarios")
            .andExpect { status { isNotFound() } }
    }
}
