package nl.vdzon.productfactory.dispatcher

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class SoftwareFactoryConfigurationGuard(private val environment: Environment) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val runtime = environment.getProperty("PF_ENVIRONMENT", "local").uppercase()
        val mode = environment.getProperty("PF_SOFTWARE_FACTORY_MODE", "DISABLED").uppercase()
        val url = environment.getProperty("PF_SOFTWARE_FACTORY_URL").orEmpty()
        val token = environment.getProperty("PF_SOFTWARE_FACTORY_TOKEN").orEmpty()
        check(mode in setOf("DISABLED", "MOCKED", "REAL")) { "PF_SOFTWARE_FACTORY_MODE moet DISABLED, MOCKED of REAL zijn." }
        if (runtime == "PRODUCTION") {
            check(mode == "REAL") { "Productie vereist PF_SOFTWARE_FACTORY_MODE=REAL." }
            check(url == RealSoftwareFactoryAdapter.PRODUCTION_URL || url == RealSoftwareFactoryAdapter.PRODUCTION_INTERNAL_URL) {
                "Productie vereist exact de veilige Software Factory-v2-URL (publiek of het gepinde interne clusteradres)."
            }
            check(token.isNotBlank()) { "Productie vereist PF_SOFTWARE_FACTORY_TOKEN." }
        }
        if (runtime == "ACCEPTANCE") {
            check(mode == "MOCKED") { "Acceptatie vereist PF_SOFTWARE_FACTORY_MODE=MOCKED." }
            check(token.isBlank()) { "Acceptatie mag geen Software Factory-productietoken bevatten." }
        }
    }
}
