package nl.vdzon.productfactory.testbed

import nl.vdzon.productfactory.api.testbed.ActivateTestScenarioCommand
import nl.vdzon.productfactory.api.testbed.AdvanceTestScenarioCommand
import nl.vdzon.productfactory.api.testbed.InjectTestFaultCommand
import nl.vdzon.productfactory.api.testbed.ResetAcceptanceEnvironmentCommand
import nl.vdzon.productfactory.api.testbed.TestControlService
import nl.vdzon.productfactory.api.testbed.TestScenarioDetails
import nl.vdzon.productfactory.api.testbed.TestScenarioSummary
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class TestControlError(val code: String, val message: String)

@RestController
@Profile("acceptance")
@RequestMapping("/api/test-control")
class AcceptanceTestControlController(
    private val testControlService: TestControlService,
) {
    @GetMapping("/scenarios")
    fun scenarios(): List<TestScenarioSummary> = testControlService.getAvailableScenarios()

    @GetMapping("/scenario")
    fun activeScenario(): TestScenarioDetails = testControlService.getActiveScenario()

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reset(@RequestBody command: ResetAcceptanceEnvironmentCommand) =
        testControlService.resetAcceptanceEnvironment(command)

    @PostMapping("/scenario")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun activate(@RequestBody command: ActivateTestScenarioCommand) =
        testControlService.activateScenario(command)

    @PostMapping("/advance")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun advance(@RequestBody command: AdvanceTestScenarioCommand) =
        testControlService.advanceScenario(command)

    @PostMapping("/fault")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun injectFault(@RequestBody command: InjectTestFaultCommand) =
        testControlService.injectTestFault(command)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun invalidCommand(exception: IllegalArgumentException) =
        TestControlError("INVALID_TEST_CONTROL_COMMAND", exception.message ?: "Ongeldige Testbed-opdracht.")

    @ExceptionHandler(IllegalStateException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun conflictingCommand(exception: IllegalStateException) =
        TestControlError("TEST_CONTROL_CONFLICT", exception.message ?: "Testbed-toestand is gewijzigd.")
}
