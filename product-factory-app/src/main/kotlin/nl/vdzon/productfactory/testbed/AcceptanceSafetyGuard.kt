package nl.vdzon.productfactory.testbed

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
@Profile("acceptance")
@Order(0)
class AcceptanceSafetyGuard(
    private val environment: Environment,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        requireValue("PF_ENVIRONMENT", "acceptance")
        requireValue("PF_AUTH_REQUIRED", "false")
        requireValue("PF_SCHEDULES_ENABLED", "false")
        requireValue("PF_AI_PROVIDER", "MOCKED")
        requireValue("PF_SOFTWARE_FACTORY_MODE", "MOCKED")
        requireValue("PF_EXTERNAL_MUTATIONS_ALLOWED", "false")
        check(environment.getProperty("PF_SOFTWARE_FACTORY_TOKEN").isNullOrBlank()) {
            "Acceptatie weigert een Software Factory-schrijftoken."
        }
        check(environment.getProperty("PF_AGENT_WORKER_TOKEN").isNullOrBlank()) {
            "Acceptatie weigert een externe AI-workercredential."
        }
    }

    private fun requireValue(key: String, expected: String) {
        val actual = environment.getProperty(key, DEFAULTS.getValue(key))
        check(actual == expected) { "Onveilige acceptatieconfiguratie voor $key." }
    }

    companion object {
        private val DEFAULTS = mapOf(
            "PF_ENVIRONMENT" to "",
            "PF_AUTH_REQUIRED" to "",
            "PF_SCHEDULES_ENABLED" to "false",
            "PF_AI_PROVIDER" to "MOCKED",
            "PF_SOFTWARE_FACTORY_MODE" to "MOCKED",
            "PF_EXTERNAL_MUTATIONS_ALLOWED" to "false",
        )
    }
}

interface ExternalMutationGate {
    fun requireAllowed(target: String)
}

@Component
@Profile("acceptance")
class BlockedAcceptanceMutationGate : ExternalMutationGate {
    override fun requireAllowed(target: String): Nothing =
        error("Externe mutatie naar $target is in acceptatie geblokkeerd.")
}
