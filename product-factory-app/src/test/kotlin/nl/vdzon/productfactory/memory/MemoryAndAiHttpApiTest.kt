package nl.vdzon.productfactory.memory

import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.api.product.CreateProductCommand
import nl.vdzon.productfactory.api.product.ProductCommandService
import nl.vdzon.productfactory.api.shared.ActorReference
import nl.vdzon.productfactory.api.shared.ActorType
import nl.vdzon.productfactory.api.shared.ProductId
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest(properties = ["PF_AUTH_REQUIRED=false"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MemoryAndAiHttpApiTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val mapper: ObjectMapper,
    @Autowired private val products: ProductCommandService,
) {
    @Test
    fun `REST bepaalt actor server-side en bewaakt verwachte versies`() {
        val productId = ProductId("memory-http-${UUID.randomUUID().toString().take(8)}")
        products.createProduct(CreateProductCommand(productId, "REST geheugen", actor = STAKEHOLDER, idempotencyKey = "create-${productId.value}"))

        mockMvc.post("/api/products/${productId.value}/agent-memory/roles/PRODUCT_DESIGNER_MVP/items") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(mapOf(
                "title" to "Richting",
                "content" to "Begin met één doelgroep",
                "reason" to "Stakeholderkeuze",
                "idempotencyKey" to "http-memory-${productId.value}",
                "actor" to mapOf("type" to "SYSTEM", "id" to "spoofed-client"),
            ))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { isNotEmpty() }
        }

        mockMvc.get("/api/products/${productId.value}/agent-memory/roles/PRODUCT_DESIGNER_MVP/items")
            .andExpect {
                status { isOk() }
                jsonPath("$", hasSize<Any>(1))
                jsonPath("$[0].actor.type") { value("STAKEHOLDER") }
                jsonPath("$[0].actor.id") { value("local-stakeholder") }
                jsonPath("$[0].activeVersionId") { isNotEmpty() }
            }

        mockMvc.put("/api/ai/job-configurations/MEETING.CONVERSE") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(mapOf(
                "provider" to "CLAUDE",
                "model" to "claude-sonnet-4-5",
                "enabled" to true,
                "expectedVersion" to 0,
                "idempotencyKey" to "http-ai-${productId.value}",
                "actor" to mapOf("type" to "SYSTEM", "id" to "spoofed-client"),
            ))
        }.andExpect {
            status { isOk() }
            jsonPath("$.version") { value(1) }
            jsonPath("$.updatedBy.type") { value("STAKEHOLDER") }
            jsonPath("$.updatedBy.id") { value("local-stakeholder") }
        }
    }

    companion object {
        private val STAKEHOLDER = ActorReference(ActorType.STAKEHOLDER, "stakeholder@example.com")
    }
}
