package nl.vdzon.productfactory.testbed

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.mock.env.MockEnvironment

class AcceptanceSafetyGuardTest {
    @Test
    fun `veilige acceptatieconfiguratie start`() {
        assertThatCode { guard().run(DefaultApplicationArguments()) }.doesNotThrowAnyException()
    }

    @Test
    fun `acceptatie weigert externe mutaties`() {
        val environment = safeEnvironment().withProperty("PF_EXTERNAL_MUTATIONS_ALLOWED", "true")

        assertThatThrownBy { AcceptanceSafetyGuard(environment).run(DefaultApplicationArguments()) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("PF_EXTERNAL_MUTATIONS_ALLOWED")
    }

    @Test
    fun `acceptatie weigert productiecredentials`() {
        val environment = safeEnvironment().withProperty("PF_SOFTWARE_FACTORY_TOKEN", "niet-leeg")

        assertThatThrownBy { AcceptanceSafetyGuard(environment).run(DefaultApplicationArguments()) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("schrijftoken")
    }

    private fun guard() = AcceptanceSafetyGuard(safeEnvironment())

    private fun safeEnvironment() = MockEnvironment()
        .withProperty("PF_ENVIRONMENT", "acceptance")
        .withProperty("PF_AUTH_REQUIRED", "false")
        .withProperty("PF_SCHEDULES_ENABLED", "false")
        .withProperty("PF_AI_PROVIDER", "MOCKED")
        .withProperty("PF_SOFTWARE_FACTORY_MODE", "MOCKED")
        .withProperty("PF_EXTERNAL_MUTATIONS_ALLOWED", "false")
}
