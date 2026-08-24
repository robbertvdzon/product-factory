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
        private val productionRequired = setOf(
            "PF_DB_URL",
            "PF_DB_USERNAME",
            "PF_DB_PASSWORD",
            "PF_GOOGLE_CLIENT_ID",
            "PF_STAKEHOLDER_EMAILS",
            "PF_SESSION_SIGNING_SECRET",
            "PF_PUBLIC_FRONTEND_URL",
            "PF_PUBLIC_BACKEND_URL",
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
            }
            if (environment == RuntimeEnvironment.ACCEPTANCE) {
                check(!authRequired) { "Acceptatie moet authenticatie expliciet uitgeschakeld houden." }
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
    ).mapNotNull { key -> getProperty(key)?.let { key to it } }.toMap()
}
