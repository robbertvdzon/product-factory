package nl.vdzon.productfactory.foundation

import nl.vdzon.productfactory.api.testbed.ActivateTestScenarioCommand
import nl.vdzon.productfactory.api.testbed.ResetAcceptanceEnvironmentCommand
import nl.vdzon.productfactory.api.testbed.TestControlService
import nl.vdzon.productfactory.testbed.ExternalMutationGate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest(
    properties = [
        "PF_ENVIRONMENT=acceptance",
        "PF_AUTH_REQUIRED=false",
        "PF_SOFTWARE_FACTORY_TOKEN=",
        "PF_AGENT_WORKER_TOKEN=",
        "PF_AGENT_RUNTIME_URL=https://agent-runtime-acceptance.vdzonsoftware.nl",
        "PF_AGENT_RUNTIME_TOKEN=test-consumer-token",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("acceptance")
class AcceptanceFoundationSeederTest(
    @Autowired private val repository: EnvironmentMetadataRepository,
    @Autowired private val testControlService: TestControlService,
    @Autowired private val externalMutationGate: ExternalMutationGate,
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `acceptatie seedt vaste synthetische metadata`() {
        assertThat(repository.find("dataset.kind")).isEqualTo("synthetic-temporary")
        assertThat(repository.find("dataset.version")).isEqualTo("dispatcher-mvp-v1")
    }

    @Test
    fun `reset en scenariokeuze zijn herhaalbaar en transactioneel begrensd`() {
        val command = ResetAcceptanceEnvironmentCommand("outbound-mutations-blocked", "browser-owner")

        testControlService.resetAcceptanceEnvironment(command)
        testControlService.resetAcceptanceEnvironment(command)

        assertThat(repository.find("scenario.key")).isEqualTo("outbound-mutations-blocked")
        assertThat(testControlService.getActiveScenario().scenario.key).isEqualTo("outbound-mutations-blocked")
        assertThat(testControlService.getActiveScenario().lock?.browserSessionId).isEqualTo("browser-owner")
    }

    @Test
    fun `scenariolock voorkomt gelijktijdige besturing`() {
        testControlService.activateScenario(ActivateTestScenarioCommand("foundation-clean", "browser-owner"))

        assertThatThrownBy {
            testControlService.activateScenario(ActivateTestScenarioCommand("foundation-clean", "browser-other"))
        }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `echte externe mutaties zijn altijd geblokkeerd`() {
        assertThatThrownBy { externalMutationGate.requireAllowed("software-factory") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("geblokkeerd")
    }

    @Test
    fun `Test Control API is in acceptatie beschikbaar en valideert invoer`() {
        mockMvc.get("/api/test-control/scenarios")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].key") { value("foundation-clean") }
                jsonPath("$[1].key") { value("outbound-mutations-blocked") }
            }

        mockMvc.post("/api/test-control/reset") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"scenarioKey":"bestaat-niet","browserSessionId":"browser-api"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_TEST_CONTROL_COMMAND") }
        }
    }
}
