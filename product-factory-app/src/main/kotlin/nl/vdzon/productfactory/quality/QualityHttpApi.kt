package nl.vdzon.productfactory.quality

import nl.vdzon.productfactory.api.quality.*
import nl.vdzon.productfactory.api.shared.*
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class QualityController(
    private val quality: QualityService,
    private val queries: QualityQueryService,
) {
    @PostMapping("/products/{productId}/quality/sessions/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun run(@PathVariable productId: String) = quality.runProcessSession(ProductId(productId))

    @GetMapping("/products/{productId}/quality/work-items")
    fun workItems(@PathVariable productId: String, @RequestParam(required = false) status: WorkItemStatus?) =
        queries.findQualityWorkItems(ProductId(productId), status)

    @GetMapping("/quality/work-items/retryable")
    fun retryable() = queries.findRetryableQualityWorkItems()

    @PostMapping("/quality/work-items/{workItemId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun retry(@PathVariable workItemId: String) {
        val id = QualityWorkItemId(workItemId)
        val item = queries.findRetryableQualityWorkItems().singleOrNull { it.id == id }
            ?: throw AggregateNotFound("Retrybaar kwaliteitswerk bestaat niet.")
        quality.retryQualityWorkItem(id)
        try {
            quality.runProcessSession(item.productId)
        } catch (_: ProcessAlreadyRunning) {
            // De bestaande sessie of de volgende vaste batch verwerkt hetzelfde PENDING item.
        }
    }

    @GetMapping("/products/{productId}/bugs")
    fun bugs(@PathVariable productId: String, @RequestParam(required = false) status: Set<BugStatus>?) =
        queries.findBugs(BugFilter(ProductId(productId), statuses = status.orEmpty()))

    @GetMapping("/bugs/{bugId}") fun bug(@PathVariable bugId: String) = queries.getBug(BugId(bugId))

    @GetMapping("/products/{productId}/verifications")
    fun verifications(@PathVariable productId: String) = queries.findVerifications(VerificationFilter(ProductId(productId)))

    @GetMapping("/products/{productId}/quality/current")
    fun current(@PathVariable productId: String) = queries.getCurrentQuality(ProductId(productId))

    @GetMapping("/products/{productId}/quality/history")
    fun history(@PathVariable productId: String) = queries.getQualityHistory(ProductId(productId), TimeRange())

    @GetMapping("/products/{productId}/quality/sessions")
    fun sessions(@PathVariable productId: String, @RequestParam(required = false) status: Set<ProcessSessionStatus>?) =
        queries.findProcessSessions(ProcessSessionFilter(ProductId(productId), status.orEmpty()))

    @GetMapping("/quality/sessions/{sessionId}")
    fun session(@PathVariable sessionId: String) = queries.getProcessSession(ProcessSessionId(sessionId))
}
