package nl.vdzon.productfactory.product

import nl.vdzon.productfactory.api.decisions.*
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.auth.ResolvedSession
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant

data class ApiError(val code: String, val message: String)
data class IdResponse(val id: String)

@RestControllerAdvice
class ProductApiErrorHandler {
    @ExceptionHandler(AggregateNotFound::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(error: AggregateNotFound) = ApiError("NOT_FOUND", error.message ?: "Niet gevonden.")

    @ExceptionHandler(VersionConflict::class, IdempotencyConflict::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun conflict(error: RuntimeException) = ApiError("CONFLICT", error.message ?: "Conflict.")

    @ExceptionHandler(InvalidCommand::class, IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun invalid(error: RuntimeException) = ApiError("INVALID_COMMAND", error.message ?: "Ongeldige opdracht.")

    @ExceptionHandler(CapabilityNotAvailable::class)
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    fun unavailable(error: CapabilityNotAvailable) = ApiError("CAPABILITY_NOT_AVAILABLE", error.message ?: "Capability nog niet beschikbaar.")
}

private fun Authentication?.stakeholderActor(): ActorReference {
    val session = this?.principal as? ResolvedSession
    return ActorReference(ActorType.STAKEHOLDER, session?.stakeholderEmail ?: "local-stakeholder")
}

data class CreateProductRequest(val requestedId: String? = null, val name: String, val status: ProductStatus = ProductStatus.ACTIVE, val idempotencyKey: String)
data class AssignmentRequest(val audience: String, val goal: String, val hardBoundaries: List<String>, val publicGitUrl: String, val expectedVersion: Long, val idempotencyKey: String)
data class TestConfigurationRequest(val acceptance: TestEnvironmentConfiguration, val production: TestEnvironmentConfiguration? = null, val expectedVersion: Long, val idempotencyKey: String)
data class ProductStatusRequest(val status: ProductStatus, val expectedVersion: Long, val idempotencyKey: String)
data class DispatchingRequest(val enabled: Boolean, val expectedVersion: Long, val idempotencyKey: String)
data class ScheduleRequest(val enabled: Boolean, val timezone: String, val pattern: SchedulePattern, val expectedVersion: Long, val idempotencyKey: String)
data class SignalRequest(val category: UserSignalCategory, val urgency: UserSignalUrgency, val source: String, val text: String, val attachments: List<ArtifactReference> = emptyList(), val idempotencyKey: String)
data class VersionedRequest(val expectedVersion: Long, val idempotencyKey: String)
data class InvestigationRequest(val verificationId: String, val outcome: String, val expectedVersion: Long, val idempotencyKey: String)
data class EpicLinkRequest(val epicId: String, val epicVersion: Long, val expectedVersion: Long, val idempotencyKey: String)
data class MeetingRequest(val reason: String, val agenda: List<String> = emptyList(), val linkedObjects: List<SourceReference> = emptyList(), val requested: Boolean = false, val idempotencyKey: String)
data class MeetingMessageRequest(val text: String, val expectedVersion: Long, val idempotencyKey: String)
data class CloseMeetingRequest(val minutes: String, val outcomes: List<MeetingOutcomeDetails> = emptyList(), val expectedVersion: Long, val idempotencyKey: String)
data class AnswerQuestionRequest(val meetingId: String, val messageId: String, val answer: String, val expectedVersion: Long, val idempotencyKey: String)
data class WithdrawQuestionRequest(val reason: String, val expectedVersion: Long, val idempotencyKey: String)

@RestController
@RequestMapping("/api/products")
class ProductController(private val commands: ProductCommandService, private val queries: ProductQueryService) {
    @GetMapping fun products() = queries.findProducts()
    @GetMapping("/{productId}") fun product(@PathVariable productId: String) = queries.getProduct(ProductId(productId))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateProductRequest, authentication: Authentication?) = IdResponse(
        commands.createProduct(CreateProductCommand(request.requestedId?.let(::ProductId), request.name, request.status, authentication.stakeholderActor(), request.idempotencyKey)).value,
    )

    @GetMapping("/{productId}/assignment") fun assignment(@PathVariable productId: String) = queries.getProductAssignment(ProductId(productId))
    @PutMapping("/{productId}/assignment") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun assignment(@PathVariable productId: String, @RequestBody request: AssignmentRequest, authentication: Authentication?) = commands.updateProductAssignment(
        UpdateProductAssignmentCommand(ProductId(productId), request.audience, request.goal, request.hardBoundaries, request.publicGitUrl, request.expectedVersion, authentication.stakeholderActor(), request.idempotencyKey),
    )

    @GetMapping("/{productId}/test-configuration") fun testConfiguration(@PathVariable productId: String) = queries.getTestableProduct(ProductId(productId))
    @PutMapping("/{productId}/test-configuration") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun testConfiguration(@PathVariable productId: String, @RequestBody request: TestConfigurationRequest, authentication: Authentication?) = commands.configureTestableProduct(
        ConfigureTestableProductCommand(ProductId(productId), request.acceptance, request.production, request.expectedVersion, authentication.stakeholderActor(), request.idempotencyKey),
    )

    @PatchMapping("/{productId}/status") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun status(@PathVariable productId: String, @RequestBody request: ProductStatusRequest, authentication: Authentication?) = commands.setProductStatus(
        SetProductStatusCommand(ProductId(productId), request.status, request.expectedVersion, authentication.stakeholderActor(), request.idempotencyKey),
    )

    @PatchMapping("/{productId}/dispatching") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun dispatching(@PathVariable productId: String, @RequestBody request: DispatchingRequest, authentication: Authentication?) = commands.setProductDispatching(
        SetProductDispatchingCommand(ProductId(productId), request.enabled, request.expectedVersion, authentication.stakeholderActor(), request.idempotencyKey),
    )

    @GetMapping("/{productId}/schedules") fun schedules(@PathVariable productId: String) = queries.getProcessSchedules(ProductId(productId))
    @PutMapping("/{productId}/schedules/{process}") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun schedule(@PathVariable productId: String, @PathVariable process: ScheduledProcess, @RequestBody request: ScheduleRequest, authentication: Authentication?) = commands.updateProcessSchedule(
        UpdateProcessScheduleCommand(ProductId(productId), process, request.enabled, request.timezone, request.pattern, request.expectedVersion, authentication.stakeholderActor(), request.idempotencyKey),
    )

    @GetMapping("/{productId}/signals")
    fun signals(@PathVariable productId: String, @RequestParam(required = false) status: Set<UserSignalStatus>?) = queries.findUserSignals(UserSignalFilter(ProductId(productId), statuses = status.orEmpty()))

    @PostMapping("/{productId}/signals") @ResponseStatus(HttpStatus.CREATED)
    fun signal(@PathVariable productId: String, @RequestBody request: SignalRequest, authentication: Authentication?) = IdResponse(
        commands.submitUserSignal(SubmitUserSignalCommand(ProductId(productId), request.category, request.urgency, request.source, request.text, request.attachments, authentication.stakeholderActor(), request.idempotencyKey)).value,
    )

    @PostMapping("/signals/{signalId}/review") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun review(@PathVariable signalId: String, @RequestBody request: VersionedRequest, authentication: Authentication?) = commands.markUserSignalInReview(MarkUserSignalInReviewCommand(UserSignalId(signalId), request.expectedVersion, authentication.stakeholderActor(), request.idempotencyKey))

    @PostMapping("/signals/{signalId}/investigation") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun investigation(@PathVariable signalId: String, @RequestBody request: InvestigationRequest, authentication: Authentication?) = commands.recordSignalInvestigation(RecordSignalInvestigationCommand(UserSignalId(signalId), VerificationId(request.verificationId), request.outcome, request.expectedVersion, authentication.stakeholderActor(), request.idempotencyKey))

    @PostMapping("/signals/{signalId}/epic") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun epic(@PathVariable signalId: String, @RequestBody request: EpicLinkRequest, authentication: Authentication?) = commands.linkSignalToEpic(LinkSignalToEpicCommand(UserSignalId(signalId), EpicId(request.epicId), request.epicVersion, request.expectedVersion, authentication.stakeholderActor(), request.idempotencyKey))

    @GetMapping("/{productId}/questions") fun questions(@PathVariable productId: String) = queries.findStakeholderQuestions(StakeholderQuestionFilter(ProductId(productId)))
    @PostMapping("/questions/{questionId}/answer") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun answer(@PathVariable questionId: String, @RequestBody request: AnswerQuestionRequest, authentication: Authentication?) = commands.recordStakeholderAnswer(RecordStakeholderAnswerCommand(StakeholderQuestionId(questionId), MeetingId(request.meetingId), request.messageId, request.answer, request.expectedVersion, authentication.stakeholderActor(), request.idempotencyKey))
    @PostMapping("/questions/{questionId}/withdraw") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdraw(@PathVariable questionId: String, @RequestBody request: WithdrawQuestionRequest, authentication: Authentication?) = commands.withdrawStakeholderQuestion(WithdrawStakeholderQuestionCommand(StakeholderQuestionId(questionId), request.reason, request.expectedVersion, authentication.stakeholderActor(), request.idempotencyKey))

    @GetMapping("/{productId}/meetings") fun meetings(@PathVariable productId: String, @RequestParam(required = false) status: MeetingStatus?) = queries.findMeetings(ProductId(productId), status)
    @PostMapping("/{productId}/meetings") @ResponseStatus(HttpStatus.CREATED)
    fun meeting(@PathVariable productId: String, @RequestBody request: MeetingRequest, authentication: Authentication?) = IdResponse(commands.startMeeting(StartMeetingCommand(ProductId(productId), request.reason, request.agenda, request.linkedObjects, request.requested, authentication.stakeholderActor(), request.idempotencyKey)).value)
    @GetMapping("/meetings/{meetingId}") fun meeting(@PathVariable meetingId: String) = queries.getMeeting(MeetingId(meetingId))
    @PostMapping("/meetings/{meetingId}/messages") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun message(@PathVariable meetingId: String, @RequestBody request: MeetingMessageRequest, authentication: Authentication?) = commands.recordMeetingMessage(RecordMeetingMessageCommand(MeetingId(meetingId), MeetingSenderRole.STAKEHOLDER, request.text, null, request.expectedVersion, authentication.stakeholderActor(), request.idempotencyKey))
    @PostMapping("/meetings/{meetingId}/close") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun close(@PathVariable meetingId: String, @RequestBody request: CloseMeetingRequest, authentication: Authentication?) = commands.closeMeeting(CloseMeetingCommand(MeetingId(meetingId), request.minutes, request.outcomes, request.expectedVersion, authentication.stakeholderActor(), request.idempotencyKey))
}

data class CreateDecisionRequest(val decision: String, val origin: DecisionOrigin = DecisionOrigin.STAKEHOLDER, val idempotencyKey: String)
data class ReviseDecisionRequest(val decision: String, val expectedVersion: Long, val idempotencyKey: String)
data class WithdrawDecisionRequest(val reason: String, val expectedVersion: Long, val idempotencyKey: String)
data class SupersedeDecisionRequest(val supersededIds: Set<String>, val replacementDecision: String, val origin: DecisionOrigin = DecisionOrigin.STAKEHOLDER, val expectedVersions: Map<String, Long>, val idempotencyKey: String)

@RestController
@RequestMapping("/api/products/{productId}/decisions")
class DecisionController(private val commands: DecisionService, private val queries: DecisionQueryService) {
    @GetMapping fun current(@PathVariable productId: String, @RequestParam(required = false) validAt: Instant?) = queries.getDecisions(ProductId(productId), validAt ?: Instant.now())
    @GetMapping("/archive") fun archive(@PathVariable productId: String) = queries.getDecisionArchive(ProductId(productId))
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable productId: String, @RequestBody request: CreateDecisionRequest, authentication: Authentication?) = IdResponse(commands.createDecision(CreateDecisionCommand(ProductId(productId), request.decision, request.origin, authentication.stakeholderActor(), request.idempotencyKey)).value)
    @PostMapping("/{decisionId}/revise") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revise(@PathVariable decisionId: String, @RequestBody request: ReviseDecisionRequest, authentication: Authentication?) = commands.reviseDecision(ReviseDecisionCommand(DecisionId(decisionId), request.decision, request.expectedVersion, authentication.stakeholderActor(), request.idempotencyKey))
    @PostMapping("/{decisionId}/withdraw") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdraw(@PathVariable decisionId: String, @RequestBody request: WithdrawDecisionRequest, authentication: Authentication?) = commands.withdrawDecision(WithdrawDecisionCommand(DecisionId(decisionId), request.reason, request.expectedVersion, authentication.stakeholderActor(), request.idempotencyKey))
    @PostMapping("/supersede") @ResponseStatus(HttpStatus.CREATED)
    fun supersede(@PathVariable productId: String, @RequestBody request: SupersedeDecisionRequest, authentication: Authentication?) = IdResponse(commands.supersedeDecisions(SupersedeDecisionsCommand(ProductId(productId), request.supersededIds.map(::DecisionId).toSet(), request.replacementDecision, request.origin, request.expectedVersions.mapKeys { DecisionId(it.key) }, authentication.stakeholderActor(), request.idempotencyKey)).value)
}
