package nl.vdzon.productfactory.config

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RuntimeSettingsTest {
    private fun completeProductionValues(agentRuntimeUrl: String) = mapOf(
        "PF_ENVIRONMENT" to "production",
        "PF_AUTH_REQUIRED" to "true",
        "PF_DB_URL" to "jdbc:postgresql://database/productfactory",
        "PF_DB_USERNAME" to "productfactory",
        "PF_DB_PASSWORD" to "secret",
        "PF_GOOGLE_CLIENT_ID" to "client",
        "PF_STAKEHOLDER_EMAILS" to "stakeholder@example.invalid",
        "PF_SESSION_SIGNING_SECRET" to "x".repeat(32),
        "PF_PUBLIC_FRONTEND_URL" to "https://product-factory.example.invalid",
        "PF_PUBLIC_BACKEND_URL" to "https://product-factory-api.example.invalid",
        "PF_AGENT_RUNTIME_URL" to agentRuntimeUrl,
        "PF_AGENT_RUNTIME_TOKEN" to "consumer-token",
        "PF_AI_PROVIDER" to "CODEX",
    )

    @Test
    fun `productie weigert ontbrekende verplichte waarden`() {
        assertThatThrownBy {
            RuntimeSettings.validate(mapOf("PF_ENVIRONMENT" to "production", "PF_AUTH_REQUIRED" to "true"))
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("PF_DB_PASSWORD")
            .hasMessageNotContaining("change-me")
    }

    @Test
    fun `productie weigert uitgeschakelde authenticatie`() {
        val complete = completeProductionValues("https://agent-runtime.example.invalid") + ("PF_AUTH_REQUIRED" to "false")

        assertThatThrownBy { RuntimeSettings.validate(complete) }
            .hasMessage("Productie mag niet starten met uitgeschakelde authenticatie.")
    }

    @Test
    fun `productie accepteert het gepinde interne Agent-Runtime-clusteradres`() {
        assertThatCode {
            RuntimeSettings.validate(completeProductionValues(RuntimeSettings.AGENT_RUNTIME_PRODUCTION_INTERNAL_URL))
        }.doesNotThrowAnyException()
    }

    @Test
    fun `productie weigert een willekeurige http-Agent-Runtime-URL`() {
        assertThatThrownBy {
            RuntimeSettings.validate(completeProductionValues("http://agent-runtime.example.invalid"))
        }.hasMessage("De productie-Agent-Runtime-URL moet HTTPS gebruiken (of het gepinde interne clusteradres zijn).")
    }
}
