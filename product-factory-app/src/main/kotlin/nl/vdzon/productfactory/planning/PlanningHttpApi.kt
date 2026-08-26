package nl.vdzon.productfactory.planning

import nl.vdzon.productfactory.api.planning.*
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.auth.ResolvedSession
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

data class ReprioritizeEpicRequest(val reason: String, val priority: Int, val idempotencyKey: String)
data class ManualReplanRequest(val reason: String, val linkedObjects: List<SourceReference> = emptyList(), val idempotencyKey: String)
data class ReserveStoryRequest(val idempotencyKey: String)
data class RevalidateStoryRequest(val expectedStoryVersion: Long, val externalStoryExists: Boolean, val idempotencyKey: String)
data class DispatchedStoryRequest(val externalStoryId: String, val expectedStoryVersion: Long, val idempotencyKey: String)
data class DevelopedStoryRequest(val externalStoryId: String, val deliveredCommitSha: String, val expectedVersion: Long, val idempotencyKey: String)
data class CancelledStoryRequest(val externalStoryId: String, val reason: String, val expectedVersion: Long, val idempotencyKey: String)

@RestController
@RequestMapping("/api")
class PlanningController(
    private val commands: ProductPlanningService,
    private val queries: ProductPlanningQueryService,
) {
    @PostMapping("/products/{productId}/planning/sessions/run") @ResponseStatus(HttpStatus.ACCEPTED)
    fun run(@PathVariable productId: String) = commands.runProcessSession(ProductId(productId))

    @GetMapping("/products/{productId}/stories")
    fun stories(@PathVariable productId: String, @RequestParam(required = false) status: Set<StoryStatus>?) =
        queries.findStories(StoryFilter(ProductId(productId), statuses = status.orEmpty()))

    @GetMapping("/products/{productId}/backlog") fun backlog(@PathVariable productId: String) = queries.getBacklog(ProductId(productId))
    @GetMapping("/stories/{storyId}") fun story(@PathVariable storyId: String) = queries.getStory(StoryId(storyId))
    @GetMapping("/products/{productId}/planning/work-items") fun workItems(@PathVariable productId: String, @RequestParam(required = false) status: WorkItemStatus?) = queries.findPlanningWorkItems(ProductId(productId), status)
    @GetMapping("/products/{productId}/planning/sessions") fun sessions(@PathVariable productId: String, @RequestParam(required = false) status: Set<ProcessSessionStatus>?) = queries.findProcessSessions(ProcessSessionFilter(ProductId(productId), status.orEmpty()))
    @GetMapping("/planning/sessions/{sessionId}") fun session(@PathVariable sessionId: String) = queries.getProcessSession(ProcessSessionId(sessionId))

    @PostMapping("/products/{productId}/planning/replan") @ResponseStatus(HttpStatus.CREATED)
    fun replan(@PathVariable productId: String, @RequestBody request: ManualReplanRequest, authentication: Authentication?) =
        commands.requestManualReplan(RequestManualReplanCommand(ProductId(productId), request.reason, request.linkedObjects, authentication.stakeholder(), request.idempotencyKey))

    @PostMapping("/products/{productId}/planning/epics/{epicId}/reprioritize") @ResponseStatus(HttpStatus.CREATED)
    fun reprioritize(@PathVariable productId: String, @PathVariable epicId: String, @RequestBody request: ReprioritizeEpicRequest, authentication: Authentication?) =
        commands.requestEpicReprioritization(RequestEpicReprioritizationCommand(ProductId(productId), EpicId(epicId), request.reason, request.priority, authentication.stakeholder(), request.idempotencyKey))

    @PostMapping("/products/{productId}/planning/reservations")
    fun reserve(@PathVariable productId: String, @RequestBody request: ReserveStoryRequest) =
        commands.reserveNextStoryForDispatch(ReserveNextStoryForDispatchCommand(ProductId(productId), SYSTEM, request.idempotencyKey))

    @PostMapping("/planning/reservations/{reservationId}/revalidate")
    fun revalidate(@PathVariable reservationId: String, @RequestBody request: RevalidateStoryRequest) = commands.revalidateDispatchReservation(
        RevalidateDispatchReservationCommand(reservationId, request.expectedStoryVersion, request.externalStoryExists, SYSTEM, request.idempotencyKey),
    )

    @PostMapping("/planning/reservations/{reservationId}/dispatched") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun dispatched(@PathVariable reservationId: String, @RequestBody request: DispatchedStoryRequest) = commands.markStoryAsDispatched(
        MarkStoryAsDispatchedCommand(reservationId, request.externalStoryId, request.expectedStoryVersion, SYSTEM, request.idempotencyKey),
    )

    @PostMapping("/stories/{storyId}/developed") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun developed(@PathVariable storyId: String, @RequestBody request: DevelopedStoryRequest) = commands.markStoryAsDeveloped(
        MarkStoryAsDevelopedCommand(StoryId(storyId), request.externalStoryId, request.deliveredCommitSha, request.expectedVersion, SYSTEM, request.idempotencyKey),
    )

    @PostMapping("/stories/{storyId}/cancelled") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelled(@PathVariable storyId: String, @RequestBody request: CancelledStoryRequest) = commands.markStoryAsCancelled(
        MarkStoryAsCancelledCommand(StoryId(storyId), request.externalStoryId, request.reason, request.expectedVersion, SYSTEM, request.idempotencyKey),
    )

    companion object { private val SYSTEM = ActorReference(ActorType.SYSTEM, "software-factory-dispatcher") }
}

private fun Authentication?.stakeholder(): ActorReference {
    val session = this?.principal as? ResolvedSession
    return ActorReference(ActorType.STAKEHOLDER, session?.stakeholderEmail ?: "local-stakeholder")
}
