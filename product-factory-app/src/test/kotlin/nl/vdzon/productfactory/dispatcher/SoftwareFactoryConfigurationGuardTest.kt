package nl.vdzon.productfactory.dispatcher

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.mock.env.MockEnvironment

class SoftwareFactoryConfigurationGuardTest {
    @Test
    fun `productie accepteert de publieke v2-URL`() {
        assertThatCode { guard(RealSoftwareFactoryAdapter.PRODUCTION_URL).run(DefaultApplicationArguments()) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `productie accepteert het gepinde interne clusteradres`() {
        assertThatCode { guard(RealSoftwareFactoryAdapter.PRODUCTION_INTERNAL_URL).run(DefaultApplicationArguments()) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `productie weigert een andere URL dan de twee gepinde adressen`() {
        assertThatThrownBy { guard("https://dashboard.vdzonsoftware.nl/api/integrations/v1").run(DefaultApplicationArguments()) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("veilige Software Factory-v2-URL")
    }

    private fun guard(url: String) = SoftwareFactoryConfigurationGuard(
        MockEnvironment()
            .withProperty("PF_ENVIRONMENT", "production")
            .withProperty("PF_SOFTWARE_FACTORY_MODE", "REAL")
            .withProperty("PF_SOFTWARE_FACTORY_URL", url)
            .withProperty("PF_SOFTWARE_FACTORY_TOKEN", "niet-leeg"),
    )
}
