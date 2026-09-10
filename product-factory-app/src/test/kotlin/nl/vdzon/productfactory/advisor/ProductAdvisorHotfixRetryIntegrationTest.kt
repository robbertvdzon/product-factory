package nl.vdzon.productfactory.advisor

import nl.vdzon.productfactory.ai.FakeRuntime
import nl.vdzon.productfactory.api.advisor.ApproveProductRequestCommand
import nl.vdzon.productfactory.api.advisor.CreateConversationCommand
import nl.vdzon.productfactory.api.advisor.ProductRequestId
import nl.vdzon.productfactory.api.advisor.ProductRequestStatus
import nl.vdzon.productfactory.api.advisor.ProductRequestType
import nl.vdzon.productfactory.api.product.CreateProductCommand
import nl.vdzon.productfactory.api.product.ProductCommandService
import nl.vdzon.productfactory.api.product.UpdateProductAssignmentCommand
import nl.vdzon.productfactory.api.shared.ActorReference
import nl.vdzon.productfactory.api.shared.ActorType
import nl.vdzon.productfactory.api.shared.ProductId
import nl.vdzon.productfactory.auth.UserIdentityRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

@SpringBootTest(properties = [
    "PF_AUTH_REQUIRED=false",
    "PF_SOFTWARE_FACTORY_MODE=MOCKED",
    "PF_SOFTWARE_FACTORY_HOTFIX_ENABLED=true",
])
@ActiveProfiles("test")
@Import(ProductAdvisorHotfixRetryIntegrationTest.Fakes::class)
class ProductAdvisorHotfixRetryIntegrationTest(
    @Autowired private val advisor: ProductAdvisorApplicationService,
    @Autowired private val products: ProductCommandService,
    @Autowired private val users: UserIdentityRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private var requestId = ProductRequestId("uninitialized")

    @BeforeEach
    fun setup() {
        advisor.deleteAllOwnedData()
        val productId = ProductId("hotfix-${UUID.randomUUID().toString().take(8)}")
        val owner = users.resolveOrCreate("owner-${productId.value}@example.test", true)
        products.createProduct(CreateProductCommand(productId, "Hotfix product", actor = SYSTEM, idempotencyKey = "create-${productId.value}"))
        products.updateProductAssignment(UpdateProductAssignmentCommand(
            productId, "Gebruikers", "Betrouwbare hotfix", listOf("Geen wijzigingen buiten de hotfix"), "https://github.com/example/product.git", 0,
            SYSTEM, "assignment-${productId.value}", "openai", "gpt-5.6-sol",
        ))
        val conversationId = advisor.createConversation(CreateConversationCommand(
            productId, "Herstel dit probleem", owner.id, "conversation-${productId.value}",
        ))
        requestId = ProductRequestId(UUID.randomUUID().toString())
        val now = Instant.now()
        jdbc.update(
            """INSERT INTO pf_product_request(request_id,product_id,conversation_id,requested_by,request_type,status,current_version,created_at,updated_at,version)
                VALUES (?,?,?,?,?,'PROPOSED',1,?,?,1)""".trimIndent(),
            requestId.value, productId.value, conversationId.value, owner.id.value, ProductRequestType.HOTFIX.name, now, now,
        )
        jdbc.update(
            """INSERT INTO pf_product_request_version(request_id,version,request_type,title,summary,problem,user_impact,current_behavior,
                desired_behavior,evidence_json,git_commit_sha,acceptance_criteria_json,scope_json,boundaries_json,excluded_hotfix_categories_json,created_at)
                VALUES (?,1,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
            requestId.value, ProductRequestType.HOTFIX.name, "Kleine correctie", "Begrensde hotfix.", "Gedrag wijkt af.",
            "De gebruiker loopt vast.", "Het werkt niet.", "Het werkt weer.", "[]", "a".repeat(40),
            "[\"Het gedrag werkt.\"]", "[\"Alleen deze route\"]", "[]", "[]", now,
        )
        advisor.approveRequest(ApproveProductRequestCommand(requestId, 1, 1, owner.id, "approve-${requestId.value}"))
    }

    @Test
    fun `hotfix stopt na twee mislukte verzendpogingen`() {
        advisor.routeApprovedRequests()
        advisor.routeApprovedRequests()

        val afterTwoAttempts = advisor.getRequest(requestId)
        val versionAfterTwoAttempts = afterTwoAttempts.version
        assertThat(afterTwoAttempts.status).isEqualTo(ProductRequestStatus.ROUTING_FAILED)
        assertThat(afterTwoAttempts.safeErrorCode).isEqualTo("HOTFIX_TOKEN_MISSING")
        assertThat(routeAttempts()).isEqualTo(2)

        advisor.routeApprovedRequests()

        assertThat(routeAttempts()).isEqualTo(2)
        assertThat(advisor.getRequest(requestId).version).isEqualTo(versionAfterTwoAttempts)
    }

    private fun routeAttempts(): Int = jdbc.queryForObject(
        "SELECT attempt_count FROM pf_product_request_route WHERE request_id=? AND request_version=1",
        Int::class.java,
        requestId.value,
    )!!

    @TestConfiguration
    class Fakes {
        @Bean
        @Primary
        fun fakeRuntime(): FakeRuntime = FakeRuntime()
    }

    companion object { private val SYSTEM = ActorReference(ActorType.SYSTEM, "hotfix-retry-test") }
}
