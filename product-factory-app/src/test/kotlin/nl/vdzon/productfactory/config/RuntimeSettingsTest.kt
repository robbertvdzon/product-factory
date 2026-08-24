package nl.vdzon.productfactory.config

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RuntimeSettingsTest {
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
        val complete = mapOf(
            "PF_ENVIRONMENT" to "production",
            "PF_AUTH_REQUIRED" to "false",
            "PF_DB_URL" to "jdbc:postgresql://database/productfactory",
            "PF_DB_USERNAME" to "productfactory",
            "PF_DB_PASSWORD" to "secret",
            "PF_GOOGLE_CLIENT_ID" to "client",
            "PF_STAKEHOLDER_EMAILS" to "stakeholder@example.invalid",
            "PF_SESSION_SIGNING_SECRET" to "x".repeat(32),
            "PF_PUBLIC_FRONTEND_URL" to "https://product-factory.example.invalid",
            "PF_PUBLIC_BACKEND_URL" to "https://product-factory-api.example.invalid",
        )

        assertThatThrownBy { RuntimeSettings.validate(complete) }
            .hasMessage("Productie mag niet starten met uitgeschakelde authenticatie.")
    }
}
