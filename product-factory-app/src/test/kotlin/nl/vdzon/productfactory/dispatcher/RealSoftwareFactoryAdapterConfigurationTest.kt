package nl.vdzon.productfactory.dispatcher

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Dekt [RealSoftwareFactoryAdapter.validateConfiguration] voor het gepinde interne
 * clusteradres ([RealSoftwareFactoryAdapter.PRODUCTION_INTERNAL_URL]): dat adres moet in
 * productie dezelfde configuratieboom doorlopen als het publieke [RealSoftwareFactoryAdapter.PRODUCTION_URL],
 * zonder de bestaande "alleen https, of expliciet local" garantie voor willekeurige URLs te verzwakken.
 */
class RealSoftwareFactoryAdapterConfigurationTest {
    @Test
    fun `productie accepteert het gepinde interne clusteradres als configuratie geldig`() {
        val adapter = RealSoftwareFactoryAdapter(
            jacksonObjectMapper(), RealSoftwareFactoryAdapter.PRODUCTION_INTERNAL_URL, "test-token", "production",
        )
        // Het interne hostname resolvet hier niet (geen cluster-DNS lokaal): de call moet dus
        // stuklopen op transport, niet op INVALID_CONFIGURATION — dat bewijst dat de guard 'm toelaat.
        assertThatThrownBy { adapter.status() }
            .isInstanceOf(RetryableFactoryFailure::class.java)
    }

    @Test
    fun `productie weigert een willekeurig intern http-adres buiten de pinning`() {
        val adapter = RealSoftwareFactoryAdapter(
            jacksonObjectMapper(), "http://some-other-service.some-namespace.svc.cluster.local/api/integrations/v2", "test-token", "production",
        )
        assertThatThrownBy { adapter.status() }
            .isInstanceOf(ConfigurationFactoryFailure::class.java)
    }

    @Test
    fun `productie weigert het interne clusteradres in de foute omgeving niet als https ontbreekt`() {
        val adapter = RealSoftwareFactoryAdapter(
            jacksonObjectMapper(), RealSoftwareFactoryAdapter.PRODUCTION_URL.replace("https", "http"), "test-token", "production",
        )
        assertThatThrownBy { adapter.status() }
            .isInstanceOf(ConfigurationFactoryFailure::class.java)
    }
}
