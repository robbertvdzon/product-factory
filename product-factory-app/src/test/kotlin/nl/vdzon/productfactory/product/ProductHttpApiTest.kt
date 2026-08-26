package nl.vdzon.productfactory.product

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(properties = ["PF_AUTH_REQUIRED=false"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductHttpApiTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
) {
    @Test
    fun `publieke adapter maakt product met serverbepaalde actor en geeft het terug`() {
        mockMvc.post("/api/products") {
            contentType = MediaType.APPLICATION_JSON
            content = """{
              "requestedId":"http-product",
              "name":"HTTP product",
              "idempotencyKey":"http-create-1",
              "actor":{"type":"SYSTEM","id":"spoofed"}
            }"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { value("http-product") }
        }

        mockMvc.get("/api/products/http-product").andExpect {
            status { isOk() }
            jsonPath("$.name") { value("HTTP product") }
            jsonPath("$.version") { value(1) }
        }
        val actor = jdbc.queryForMap("SELECT actor_type,actor_id FROM pf_product_command WHERE idempotency_key='http-create-1'")
        assertThat(actor["actor_type"]).isEqualTo("STAKEHOLDER")
        assertThat(actor["actor_id"]).isEqualTo("local-stakeholder")
    }

    @Test
    fun `interne agentvraag is niet als vrije externe mutatie gepubliceerd`() {
        mockMvc.post("/api/products/http-product/questions") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"agentRole":"PRODUCT_DESIGNER","question":"Vrije vraag"}"""
        }.andExpect { status { isMethodNotAllowed() } }
    }

    @Test
    fun `operationele projectie toont gekozen implementaties`() {
        mockMvc.get("/api/foundation/implementations").andExpect {
            status { isOk() }
            jsonPath("$.manifestVersion") { value("2") }
            jsonPath("$.implementations.product.artifact") { value("product-impl") }
            jsonPath("$.implementations.decisions.artifact") { value("decisions-impl") }
        }
    }
}
