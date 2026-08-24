package nl.vdzon.productfactory.foundation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(properties = ["PF_ENVIRONMENT=acceptance", "PF_AUTH_REQUIRED=false"])
@ActiveProfiles("acceptance")
class AcceptanceFoundationSeederTest(
    @Autowired private val repository: EnvironmentMetadataRepository,
) {
    @Test
    fun `acceptatie seedt vaste synthetische metadata`() {
        assertThat(repository.find("dataset.kind")).isEqualTo("synthetic-temporary")
        assertThat(repository.find("dataset.version")).isEqualTo("foundation-v1")
    }
}
