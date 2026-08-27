package nl.vdzon.productfactory.config

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

enum class RuntimeEnvironment { LOCAL, ACCEPTANCE, PRODUCTION }

data class RuntimeSettings(
    val environment: RuntimeEnvironment,
    val authRequired: Boolean,
) {
    companion object {
        /**
         * In-cluster alternatief voor de publieke `https://agent-runtime.vdzonsoftware.nl`:
         * rechtstreeks via de ClusterIP-service in namespace `agent-runtime`, zonder de externe
         * route. Bewust een exact gepinde string (zelfde patroon als
         * [nl.vdzon.productfactory.dispatcher.RealSoftwareFactoryAdapter.PRODUCTION_INTERNAL_URL]),
         * geen hostname-patroon.
         */
        const val AGENT_RUNTIME_PRODUCTION_INTERNAL_URL = "http://agent-runtime-server.agent-runtime.svc.cluster.local"

        private val productionRequired = setOf(
            "PF_DB_URL",
            "PF_DB_USERNAME",
            "PF_DB_PASSWORD",
            "PF_GOOGLE_CLIENT_ID",
            "PF_STAKEHOLDER_EMAILS",
            "PF_SESSION_SIGNING_SECRET",
            "PF_PUBLIC_FRONTEND_URL",
            "PF_PUBLIC_BACKEND_URL",
            "PF_AGENT_RUNTIME_URL",
            "PF_AGENT_RUNTIME_TOKEN",
        )

        fun validate(values: Map<String, String>): RuntimeSettings {
            val environment = runCatching {
                RuntimeEnvironment.valueOf(values["PF_ENVIRONMENT"].orEmpty().ifBlank { "local" }.uppercase())
            }.getOrElse { throw IllegalStateException("PF_ENVIRONMENT moet local, acceptance of production zijn.") }
            val authRequired = values["PF_AUTH_REQUIRED"].orEmpty().ifBlank { "false" }.toBooleanStrictOrNull()
                ?: throw IllegalStateException("PF_AUTH_REQUIRED moet true of false zijn.")

            if (environment == RuntimeEnvironment.PRODUCTION) {
                val missing = productionRequired.filter { values[it].isNullOrBlank() }.sorted()
                check(missing.isEmpty()) { "Verplichte productieconfiguratie ontbreekt: ${missing.joinToString()}" }
                check(authRequired) { "Productie mag niet starten met uitgeschakelde authenticatie." }
                check(values.getValue("PF_SESSION_SIGNING_SECRET").length >= 32) {
                    "PF_SESSION_SIGNING_SECRET moet minimaal 32 tekens bevatten."
                }
                check(values.getValue("PF_PUBLIC_FRONTEND_URL").startsWith("https://")) {
                    "De publieke productie-frontend-URL moet HTTPS gebruiken."
                }
                check(values.getValue("PF_PUBLIC_BACKEND_URL").startsWith("https://")) {
                    "De publieke productie-backend-URL moet HTTPS gebruiken."
                }
                check(
                    values.getValue("PF_AGENT_RUNTIME_URL").let { it.startsWith("https://") || it == AGENT_RUNTIME_PRODUCTION_INTERNAL_URL },
                ) {
                    "De productie-Agent-Runtime-URL moet HTTPS gebruiken (of het gepinde interne clusteradres zijn)."
                }
                check(values["PF_AI_PROVIDER"] != "MOCKED") { "Productie weigert de MOCKED AI-provider." }
            }
            if (environment == RuntimeEnvironment.ACCEPTANCE) {
                check(!authRequired) { "Acceptatie moet authenticatie expliciet uitgeschakeld houden." }
                check(values["PF_AGENT_RUNTIME_URL"] == "https://agent-runtime-acceptance.vdzonsoftware.nl") {
                    "Acceptatie mag alleen de Agent Runtime-acceptatieomgeving gebruiken."
                }
                check(!values["PF_AGENT_RUNTIME_TOKEN"].isNullOrBlank()) { "Acceptatie vereist een gescopete Runtime-consumentcredential." }
            }
            return RuntimeSettings(environment, authRequired)
        }
    }
}

@Component
class RuntimeConfigurationGuard(
    private val springEnvironment: Environment,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val values = springEnvironment.propertySources()
        RuntimeSettings.validate(values)
    }

    private fun Environment.propertySources(): Map<String, String> = sequenceOf(
        "PF_ENVIRONMENT",
        "PF_AUTH_REQUIRED",
        "PF_DB_URL",
        "PF_DB_USERNAME",
        "PF_DB_PASSWORD",
        "PF_GOOGLE_CLIENT_ID",
        "PF_STAKEHOLDER_EMAILS",
        "PF_SESSION_SIGNING_SECRET",
        "PF_PUBLIC_FRONTEND_URL",
        "PF_PUBLIC_BACKEND_URL",
        "PF_AGENT_RUNTIME_URL",
        "PF_AGENT_RUNTIME_TOKEN",
        "PF_AGENT_RUNTIME_TEST_CONTROL_TOKEN",
        "PF_AI_PROVIDER",
    ).mapNotNull { key -> getProperty(key)?.let { key to it } }.toMap()
}
