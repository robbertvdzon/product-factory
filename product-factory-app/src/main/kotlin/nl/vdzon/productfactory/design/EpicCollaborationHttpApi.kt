package nl.vdzon.productfactory.design

import nl.vdzon.productfactory.api.advisor.*
import nl.vdzon.productfactory.api.design.*
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.auth.ProductAuthorizationService
import org.springframework.web.bind.annotation.*
import org.springframework.http.*
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import nl.vdzon.productfactory.api.ai.AiExecutionQueryService
import org.springframework.security.core.Authentication

data class EpicReviewRequest(val expectedVersion: Long, val decision: ReviewDecision, val reason: String, val role: ProductMembershipRole, val idempotencyKey: String)
data class GovernancePolicyRequest(val policy: ProductGovernancePolicy, val idempotencyKey: String)
data class EpicDiscussionRequest(val title: String, val role: ProductMembershipRole, val idempotencyKey: String)
data class EpicFeedbackRequest(val expectedVersion: Long, val role: ProductMembershipRole, val text: String, val screenKey: String?=null, val viewport: String?=null, val artifactName: String?=null, val idempotencyKey: String)

@RestController
@RequestMapping("/api")
class EpicCollaborationController(private val policies: ProductGovernanceService, private val governance: EpicGovernanceService,
    private val design: ProductDesignService, private val queries: ProductDesignQueryService,
    private val advisor: ProductAdvisorService, private val advisorQueries: ProductAdvisorQueryService,
    private val authorization: ProductAuthorizationService, private val ai: AiExecutionQueryService, private val jdbc: org.springframework.jdbc.core.JdbcTemplate) {
    @GetMapping("/products/{productId}/governance")
    fun policy(@PathVariable productId: String, authentication: Authentication?): ProductGovernancePolicy {
        authorization.requireProduct(ProductId(productId),authentication)
        return policies.getPolicy(ProductId(productId))
    }
    @PutMapping("/products/{productId}/governance")
    fun policy(@PathVariable productId: String,@RequestBody request: GovernancePolicyRequest,authentication: Authentication?): ProductGovernancePolicy {
        require(request.policy.productId.value==productId)
        authorization.requireProduct(ProductId(productId),authentication)
        return policies.updatePolicy(UpdateGovernancePolicyCommand(request.policy,authorization.currentUserId(authentication),request.idempotencyKey))
    }
    @GetMapping("/epics/{epicId}/reviews")
    fun reviews(@PathVariable epicId: String,authentication: Authentication?): EpicReviewState {
        authorization.requireProduct(queries.getEpic(EpicId(epicId)).productId,authentication)
        return governance.reviewState(EpicId(epicId))
    }
    @PostMapping("/epics/{epicId}/reviews")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun review(@PathVariable epicId: String,@RequestBody request: EpicReviewRequest,authentication: Authentication?) {
        val epic=queries.getEpic(EpicId(epicId))
        authorization.requireRole(epic.productId,request.role,authentication)
        governance.review(ReviewEpicCommand(epic.id,request.expectedVersion,request.role,authorization.currentUserId(authentication),request.decision,request.reason,request.idempotencyKey))
    }
    @GetMapping("/epics/{epicId}/discussions")
    fun discussions(@PathVariable epicId: String,authentication: Authentication?, @RequestParam(defaultValue="true") includeMessages: Boolean = true): List<ProductConversationDetails> {
        val epic=queries.getEpic(EpicId(epicId));authorization.requireProduct(epic.productId,authentication)
        return advisorQueries.findConversations(epic.productId, includeMessages).filter {
            it.epicId==epicId || it.request?.linkedEpicId==epicId
        }.sortedWith(compareBy<ProductConversationDetails> { it.status == ConversationStatus.CLOSED }.thenBy { it.createdAt })
    }
    @GetMapping("/epics/{epicId}/messages")
    fun messages(@PathVariable epicId: String, authentication: Authentication?, @RequestParam(required=false) before: String?, @RequestParam(required=false) after: String?, @RequestParam(defaultValue="30") limit: Int): ConversationMessagePage {
        val conversations = discussions(epicId, authentication, false)
        return advisorQueries.messagePage(conversations.map { it.id }, before, after, limit)
    }
    @PostMapping("/epics/{epicId}/discussions")
    @org.springframework.transaction.annotation.Transactional
    fun discussion(@PathVariable epicId: String,@RequestBody request: EpicDiscussionRequest,authentication: Authentication?): Map<String,String> {
        val epic=queries.getEpic(EpicId(epicId));authorization.requireRole(epic.productId,request.role,authentication)
        jdbc.queryForObject("SELECT current_version FROM pf_epic WHERE id=? FOR UPDATE",Long::class.java,epicId)
        discussions(epicId,authentication).firstOrNull { it.status != ConversationStatus.CLOSED }?.let { return mapOf("id" to it.id.value) }
        val id=advisor.createConversation(CreateConversationCommand(epic.productId,request.title,authorization.currentUserId(authentication),request.idempotencyKey,epicId,request.role,ConversationPurpose.EPIC))
        return mapOf("id" to id.value)
    }
    @GetMapping("/epics/{epicId}/ux-artifacts")
    fun uxArtifact(@PathVariable epicId: String, @RequestParam name: String, authentication: Authentication?): ResponseEntity<StreamingResponseBody> {
        val epic = queries.getEpic(EpicId(epicId))
        authorization.requireProduct(epic.productId, authentication)
        val artifact = epic.uxArtifacts.singleOrNull { it.name == name && it.mediaType in setOf("image/png", "image/jpeg", "image/webp") }
            ?: throw AggregateNotFound("Dit beeld hoort niet bij de actuele epic.")
        val match = Regex("/api/ai/tasks/([A-Za-z0-9-]{1,80})/artifacts/([A-Za-z0-9-]{1,80})").matchEntire(artifact.uri)
            ?: throw InvalidCommand("Het ontwerp heeft geen bewaard artifact.")
        val content = ai.openAiTaskArtifact(AiTaskId(match.groupValues[1]), match.groupValues[2])
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(artifact.mediaType))
            .contentLength(content.sizeBytes).header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header("X-Content-Type-Options", "nosniff")
            .body(StreamingResponseBody { output -> content.inputStream.use { it.copyTo(output) } })
    }
    @PostMapping("/epics/{epicId}/feedback")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun feedback(@PathVariable epicId: String,@RequestBody request: EpicFeedbackRequest,authentication: Authentication?) {
        val current=queries.getEpic(EpicId(epicId));authorization.requireRole(current.productId,request.role,authentication)
        // Validate the referenced snapshot; the design command owns replay and concurrency checks.
        val epic=queries.getEpicHistory(current.id).singleOrNull { it.version==request.expectedVersion }
            ?: throw VersionConflict("Deze epicversie bestaat niet.")
        require(request.text.trim().length in 1..8000)
        if (request.screenKey!=null && epic.uxScreens.none { it.screenKey==request.screenKey }) throw InvalidCommand("Het UX-scherm hoort niet bij deze versie.")
        if (request.artifactName!=null && epic.uxArtifacts.none { it.name==request.artifactName }) throw InvalidCommand("Het artifact hoort niet bij deze versie.")
        val reason="${request.role} feedback op inhoudsversie ${epic.contentVersion}, scherm ${request.screenKey ?: "uitwerking"}, viewport ${request.viewport ?: "n.v.t."}, artifact ${request.artifactName ?: "n.v.t."}: ${request.text.trim()}"
        design.requestEpicRefinement(RequestEpicRefinementCommand(epic.id,reason,epic.version,ActorReference(ActorType.STAKEHOLDER,authorization.currentUserId(authentication).value),request.idempotencyKey))
        design.runProcessSession(epic.productId)
    }
}
