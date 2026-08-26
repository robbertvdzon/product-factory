package nl.vdzon.productfactory.dispatcher

import nl.vdzon.productfactory.api.dispatcher.*
import nl.vdzon.productfactory.api.shared.*
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class DispatcherController(
    private val dispatcher: SoftwareFactoryDispatcherService,
    private val queries: SoftwareFactoryDispatcherQueryService,
) {
    @PostMapping("/products/{productId}/dispatcher/sessions/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun run(@PathVariable productId: String) = dispatcher.runDispatchSession(ProductId(productId))

    @GetMapping("/products/{productId}/dispatcher/status")
    fun status(@PathVariable productId: String) = queries.getDispatchStatus(ProductId(productId))

    @GetMapping("/products/{productId}/dispatcher/attempts")
    fun attempts(
        @PathVariable productId: String,
        @RequestParam(required = false) status: Set<DeliveryAttemptStatus>?,
    ) = queries.findDeliveryAttempts(DeliveryAttemptFilter(ProductId(productId), statuses = status.orEmpty()))

    @GetMapping("/products/{productId}/dispatcher/sessions")
    fun sessions(
        @PathVariable productId: String,
        @RequestParam(required = false) status: Set<ProcessSessionStatus>?,
    ) = queries.findDispatchSessions(ProcessSessionFilter(ProductId(productId), status.orEmpty()))

    @GetMapping("/dispatcher/sessions/{sessionId}")
    fun session(@PathVariable sessionId: String) = queries.getDispatchSession(ProcessSessionId(sessionId))
}

data class MockCompletionRequest(val commitSha: String)
data class MockCancellationRequest(val reason: String)

@Profile("acceptance")
@RestController
@RequestMapping("/api/test-control/software-factory")
class MockSoftwareFactoryController(private val mock: MockSoftwareFactoryControl) {
    @PostMapping("/stories/{storyKey}/complete") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun complete(@PathVariable storyKey: String, @RequestBody request: MockCompletionRequest) = mock.complete(storyKey, request.commitSha)

    @PostMapping("/stories/{storyKey}/cancel") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(@PathVariable storyKey: String, @RequestBody request: MockCancellationRequest) = mock.cancel(storyKey, request.reason)

    @PostMapping("/fail-next") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun failNext() = mock.failNextCall()

    @PostMapping("/lose-next-create-response") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun loseNext() = mock.loseNextCreateResponse()

    @PostMapping("/break-next-contract") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun breakNext() = mock.breakNextContract()

    @PostMapping("/reset") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reset() = mock.reset()
}
