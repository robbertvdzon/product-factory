package nl.vdzon.productfactory.foundation

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
@Profile("acceptance")
class AcceptanceFoundationSeeder(
    private val repository: EnvironmentMetadataRepository,
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        seed("dataset.kind", "synthetic-temporary")
        seed("dataset.version", "foundation-v1")
    }

    private fun seed(key: String, expectedValue: String) {
        repository.insertIfAbsent(key, expectedValue, Instant.EPOCH)
        check(repository.find(key) == expectedValue) {
            "Gereserveerde acceptatiemetadata botst voor sleutel $key."
        }
    }
}
