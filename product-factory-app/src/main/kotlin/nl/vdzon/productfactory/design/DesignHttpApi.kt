package nl.vdzon.productfactory.design

import nl.vdzon.productfactory.api.design.*
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.auth.ResolvedSession
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

data class DesignVersionedRequest(val expectedVersion: Long, val idempotencyKey: String)
data class MarkEpicActiveRequest(val plannedEpicVersion: Long, val expectedVersion: Long, val idempotencyKey: String)
data class EpicVerificationRequest(val verificationId: String, val outcome: EpicVerificationOutcome, val explanation: String, val expectedVersion: Long, val idempotencyKey: String)
data class EpicReasonRequest(val reason: String, val expectedVersion: Long, val idempotencyKey: String)

@RestController
@RequestMapping("/api")
class DesignController(
    private val commands: ProductDesignService,
    private val queries: ProductDesignQueryService,
) {
    @PostMapping("/products/{productId}/design/sessions/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun run(@PathVariable productId: String) = commands.runProcessSession(ProductId(productId))

    @GetMapping("/products/{productId}/epics")
    fun epics(@PathVariable productId: String, @RequestParam(required = false) status: Set<EpicStatus>?) =
        queries.findEpics(EpicFilter(ProductId(productId), status.orEmpty()))

    @GetMapping("/epics/{epicId}")
    fun epic(@PathVariable epicId: String) = queries.getEpic(EpicId(epicId))

    @GetMapping("/epics/{epicId}/history")
    fun history(@PathVariable epicId: String) = queries.getEpicHistory(EpicId(epicId))

    @GetMapping("/products/{productId}/design/sessions")
    fun sessions(@PathVariable productId: String, @RequestParam(required = false) status: Set<ProcessSessionStatus>?) =
        queries.findProcessSessions(ProcessSessionFilter(ProductId(productId), status.orEmpty()))

    @GetMapping("/design/sessions/{sessionId}")
    fun session(@PathVariable sessionId: String) = queries.getProcessSession(ProcessSessionId(sessionId))

    @PostMapping("/epics/{epicId}/claim") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun claim(@PathVariable epicId: String, @RequestBody request: DesignVersionedRequest, authentication: Authentication?) =
        commands.claimEpicForPlanning(ClaimEpicForPlanningCommand(EpicId(epicId), request.expectedVersion, authentication.actor(), request.idempotencyKey))

    @PostMapping("/epics/{epicId}/active") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun active(@PathVariable epicId: String, @RequestBody request: MarkEpicActiveRequest, authentication: Authentication?) =
        commands.markEpicActive(MarkEpicActiveCommand(EpicId(epicId), request.plannedEpicVersion, request.expectedVersion, authentication.actor(), request.idempotencyKey))

    @PostMapping("/epics/{epicId}/ready-for-verification") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun ready(@PathVariable epicId: String, @RequestBody request: DesignVersionedRequest, authentication: Authentication?) =
        commands.markEpicReadyForVerification(MarkEpicReadyForVerificationCommand(EpicId(epicId), request.expectedVersion, authentication.actor(), request.idempotencyKey))

    @PostMapping("/epics/{epicId}/verification") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun verification(@PathVariable epicId: String, @RequestBody request: EpicVerificationRequest, authentication: Authentication?) =
        commands.recordEpicVerification(RecordEpicVerificationCommand(
            EpicId(epicId), VerificationId(request.verificationId), request.outcome, request.explanation,
            request.expectedVersion, authentication.actor(), request.idempotencyKey,
        ))

    @PostMapping("/epics/{epicId}/withdraw") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdraw(@PathVariable epicId: String, @RequestBody request: EpicReasonRequest, authentication: Authentication?) =
        commands.withdrawEpic(WithdrawEpicCommand(EpicId(epicId), request.reason, request.expectedVersion, authentication.actor(), request.idempotencyKey))

    @PostMapping("/epics/{epicId}/cancel") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(@PathVariable epicId: String, @RequestBody request: EpicReasonRequest, authentication: Authentication?) =
        commands.cancelEpic(CancelEpicCommand(EpicId(epicId), request.reason, request.expectedVersion, authentication.actor(), request.idempotencyKey))
}

private fun Authentication?.actor(): ActorReference {
    val session = this?.principal as? ResolvedSession
    return ActorReference(ActorType.STAKEHOLDER, session?.stakeholderEmail ?: "local-stakeholder")
}
