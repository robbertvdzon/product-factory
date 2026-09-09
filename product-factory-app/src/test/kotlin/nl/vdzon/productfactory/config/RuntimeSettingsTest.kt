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
        "PF_FACTORY_OWNER_EMAILS" to "stakeholder@example.invalid",
        "PF_SESSION_SIGNING_SECRET" to "x".repeat(32),
        "PF_PUBLIC_FRONTEND_URL" to "https://product-factory.example.invalid",
        "PF_PUBLIC_BACKEND_URL" to "https://product-factory-api.example.invalid",
        "PF_AGENT_RUNTIME_URL" to agentRuntimeUrl,
        "PF_AGENT_RUNTIME_TOKEN" to "consumer-token",
        "PF_AGENT_RUNTIME_API_VERSION" to "v1",
        "PF_AI_VENDOR_ID" to "openai",
        "PF_AI_MODEL" to "gpt-5.6-sol",
        "PF_AI_EXECUTION_MODE" to "SUBSCRIPTION",
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

    @Test
    fun `productie weigert mockuitvoering en test-controlcredentials`() {
        val mock = completeProductionValues(RuntimeSettings.AGENT_RUNTIME_PRODUCTION_INTERNAL_URL) + mapOf(
            "PF_AI_VENDOR_ID" to "mock", "PF_AI_MODEL" to "mock", "PF_AI_EXECUTION_MODE" to "MOCK",
        )
        assertThatThrownBy { RuntimeSettings.validate(mock) }.hasMessageContaining("openai/gpt-5.6-sol/SUBSCRIPTION")

        val testControl = completeProductionValues(RuntimeSettings.AGENT_RUNTIME_PRODUCTION_INTERNAL_URL) +
            ("PF_AGENT_RUNTIME_TEST_CONTROL_TOKEN" to "forbidden")
        assertThatThrownBy { RuntimeSettings.validate(testControl) }.hasMessageContaining("test-controlcredential")
    }

    @Test
    fun `productie vereist factory owners uit de loginallowlist`() {
        val invalid = completeProductionValues(RuntimeSettings.AGENT_RUNTIME_PRODUCTION_INTERNAL_URL) +
            ("PF_FACTORY_OWNER_EMAILS" to "outsider@example.invalid")

        assertThatThrownBy { RuntimeSettings.validate(invalid) }
            .hasMessageContaining("subset van PF_STAKEHOLDER_EMAILS")
    }

    @Test
    fun `ingeschakelde hotfixroute vereist dashboardconfiguratie`() {
        val invalid = completeProductionValues(RuntimeSettings.AGENT_RUNTIME_PRODUCTION_INTERNAL_URL) +
            ("PF_SOFTWARE_FACTORY_HOTFIX_ENABLED" to "true")

        assertThatThrownBy { RuntimeSettings.validate(invalid) }
            .hasMessageContaining("dashboard-URL en dashboardtoken")
    }
}
