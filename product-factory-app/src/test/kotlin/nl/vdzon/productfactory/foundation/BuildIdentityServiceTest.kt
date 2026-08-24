package nl.vdzon.productfactory.foundation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BuildIdentityServiceTest {
    @Test
    fun `geldige buildmetadata vormt een concrete identiteit`() {
        val identity = BuildIdentityService(
            "0.1.0",
            "0123456789abcdef0123456789abcdef01234567",
            "2026-08-24T18:00:00Z",
            "production",
        ).identity

        assertThat(identity.applicationVersion).isEqualTo("0.1.0")
        assertThat(identity.gitRevision).isEqualTo("0123456789abcdef0123456789abcdef01234567")
        assertThat(identity.buildTime).isEqualTo("2026-08-24T18:00:00Z")
        assertThat(identity.environment).isEqualTo("production")
        assertThat(identity.backendBuildIdentity).isEqualTo("0.1.0+0123456789ab")
        assertThat(identity.apiVersion).isEqualTo("1")
    }

    @Test
    fun `ongeldige of ontbrekende metadata wordt zichtbaar onbekend`() {
        val identity = BuildIdentityService("snapshot", "main", "gisteren", "staging").identity

        assertThat(identity.applicationVersion).isEqualTo("Onbekend")
        assertThat(identity.gitRevision).isEqualTo("Onbekend")
        assertThat(identity.buildTime).isEqualTo("Onbekend")
        assertThat(identity.environment).isEqualTo("Onbekend")
        assertThat(identity.backendBuildIdentity).isEqualTo("Onbekend")
    }
}
