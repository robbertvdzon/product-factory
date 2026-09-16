package nl.vdzon.productfactory.ai

import nl.vdzon.productfactory.api.ai.*
import nl.vdzon.productfactory.api.shared.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ExecutionCredentialPolicyTest {
    private val request = RequestAiTaskCommand(
        AiJobKey("PRODUCT_ADVISOR.CONVERSE"), ProductId("pvdd"), "product-advisor", null, "PRODUCT_ADVISOR",
        AiExecutionSelection("anthropic", "claude-opus-5", AiExecutionMode.SUBSCRIPTION), 1,
        1, "Observe", "{}", executionTimeout = java.time.Duration.ofMinutes(5), idempotencyKey = "test",
    )
    @Test fun `production read grant cannot reach other products jobs or roles even when misconfigured`() {
        val key = "PVDD__PRODUCTION_READ_ONLY_TOKEN"
        assertTrue(executionCredentialAllowed(key, request))
        for (other in listOf(
            request.copy(productId = ProductId("hkh")), request.copy(agentRole = "TESTER_MVP"),
            request.copy(jobKey = AiJobKey("QUALITY.VERIFY_EPIC")), request.copy(requesterCapability = "quality"),
        )) assertFalse(executionCredentialAllowed(key, other))
        for (forbidden in listOf("PVDD__PRODUCTION_TOKEN", "PVDD__PRODUCTION_AGENT_TOKEN", "PVDD__ACCEPTANCE_DATABASE_PRODUCTION_PASSWORD")) {
            assertFalse(executionCredentialAllowed(forbidden, request))
        }
        assertTrue(executionCredentialAllowed("PVDD__ACCEPTANCE_AGENT_TOKEN", request.copy(agentRole = "TESTER_MVP")))
    }
}
