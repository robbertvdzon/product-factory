package nl.vdzon.productfactory.foundation

import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContext
import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContributor
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Instant

@Component
@Profile("acceptance")
class AcceptanceFoundationFixtureContributor(
    private val repository: EnvironmentMetadataRepository,
) : AcceptanceFixtureContributor {
    override val key: String = "technical-foundation"
    override val order: Int = 100

    override fun reset(context: AcceptanceFixtureContext) {
        repository.deleteAll()
        seed("dataset.kind", "synthetic-temporary")
        seed("dataset.version", context.datasetVersion)
        seed("scenario.key", context.scenarioKey)
    }

    private fun seed(key: String, expectedValue: String) {
        repository.insertIfAbsent(key, expectedValue, Instant.EPOCH)
        check(repository.find(key) == expectedValue) {
            "Gereserveerde acceptatiemetadata botst voor sleutel $key."
        }
    }
}
