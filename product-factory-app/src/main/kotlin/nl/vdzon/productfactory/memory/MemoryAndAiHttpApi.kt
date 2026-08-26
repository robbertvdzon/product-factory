package nl.vdzon.productfactory.memory

import nl.vdzon.productfactory.api.ai.*
import nl.vdzon.productfactory.api.memory.*
import nl.vdzon.productfactory.api.product.ProductQueryService
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.auth.ResolvedSession
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private fun Authentication?.memoryStakeholder(): ActorReference {
    val session = this?.principal as? ResolvedSession
    return ActorReference(ActorType.STAKEHOLDER, session?.stakeholderEmail ?: "local-stakeholder")
}

data class AddMemoryRequest(
    val title: String,
    val content: String,
    val reason: String,
    val idempotencyKey: String,
)

data class ReplaceMemoryRequest(
    val expectedVersionId: String,
    val title: String,
    val content: String,
    val reason: String,
    val idempotencyKey: String,
)

data class RetractMemoryRequest(
    val expectedVersionId: String,
    val reason: String,
    val idempotencyKey: String,
)

data class UpdateAiSettingsRequest(
    val provider: AiProvider,
    val model: String,
    val enabled: Boolean,
    val expectedVersion: Long,
    val idempotencyKey: String,
)

data class MemoryIdResponse(val id: String)

@RestController
@RequestMapping("/api/products/{productId}/agent-memory")
class AgentMemoryController(
    private val commands: AgentMemoryService,
    private val queries: AgentMemoryQueryService,
    private val products: ProductQueryService,
) {
    @GetMapping("/roles")
    fun roles(@PathVariable productId: String) = queries.getAgentRoleCatalog(ProductId(productId))

    @GetMapping("/roles/{role}/budget")
    fun budget(@PathVariable productId: String, @PathVariable role: String) =
        queries.getMemoryBudget(ProductId(productId), AgentRoleKey(role))

    @GetMapping("/roles/{role}/items")
    fun items(
        @PathVariable productId: String,
        @PathVariable role: String,
        @RequestParam(required = false) validAt: Instant?,
        @RequestParam(required = false) date: LocalDate?,
    ): List<AgentMemoryItemDetails> {
        if (validAt != null && date != null) throw InvalidCommand("Kies een exact tijdstip of een productdatum, niet beide.")
        val product = ProductId(productId)
        val at = validAt ?: date?.let { productDayEnd(product, it) } ?: Instant.now()
        return queries.getMemoryAt(product, AgentRoleKey(role), at)
    }

    @GetMapping("/roles/{role}/items/{itemId}/history")
    fun history(@PathVariable productId: String, @PathVariable role: String, @PathVariable itemId: String) =
        queries.getMemoryHistory(ProductId(productId), AgentRoleKey(role), MemoryItemId(itemId))

    @PostMapping("/roles/{role}/items")
    @ResponseStatus(HttpStatus.CREATED)
    fun add(
        @PathVariable productId: String,
        @PathVariable role: String,
        @RequestBody request: AddMemoryRequest,
        authentication: Authentication?,
    ) = MemoryIdResponse(commands.addAgentMemory(AddAgentMemoryCommand(
        stakeholderContext(productId, role, authentication), request.title, request.content, request.reason, request.idempotencyKey,
    )).value)

    @PostMapping("/roles/{role}/items/{itemId}/replace")
    @ResponseStatus(HttpStatus.CREATED)
    fun replace(
        @PathVariable productId: String,
        @PathVariable role: String,
        @PathVariable itemId: String,
        @RequestBody request: ReplaceMemoryRequest,
        authentication: Authentication?,
    ) = MemoryIdResponse(commands.replaceAgentMemory(ReplaceAgentMemoryCommand(
        stakeholderContext(productId, role, authentication), MemoryItemId(itemId), MemoryVersionId(request.expectedVersionId),
        request.title, request.content, request.reason, request.idempotencyKey,
    )).value)

    @PostMapping("/roles/{role}/items/{itemId}/retract")
    @ResponseStatus(HttpStatus.CREATED)
    fun retract(
        @PathVariable productId: String,
        @PathVariable role: String,
        @PathVariable itemId: String,
        @RequestBody request: RetractMemoryRequest,
        authentication: Authentication?,
    ) = MemoryIdResponse(commands.retractAgentMemory(RetractAgentMemoryCommand(
        stakeholderContext(productId, role, authentication), MemoryItemId(itemId), MemoryVersionId(request.expectedVersionId),
        request.reason, request.idempotencyKey,
    )).value)

    private fun stakeholderContext(productId: String, role: String, authentication: Authentication?) = MemoryWriteContext(
        ProductId(productId), AgentRoleKey(role), authentication.memoryStakeholder(),
    )

    private fun productDayEnd(productId: ProductId, date: LocalDate): Instant {
        val timezone = products.getProcessSchedules(productId).firstOrNull()?.timezone ?: "Europe/Amsterdam"
        val zone = runCatching { ZoneId.of(timezone) }.getOrElse { ZoneId.of("Europe/Amsterdam") }
        return date.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1)
    }
}

@RestController
@RequestMapping("/api/ai/job-configurations")
class AiSettingsController(
    private val commands: AiExecutionService,
    private val queries: AiExecutionQueryService,
) {
    @GetMapping
    fun all() = queries.getAiJobConfigurations()

    @GetMapping("/{jobKey}")
    fun one(@PathVariable jobKey: String) = queries.getAiJobConfiguration(AiJobKey(jobKey))

    @PutMapping("/{jobKey}")
    fun update(
        @PathVariable jobKey: String,
        @RequestBody request: UpdateAiSettingsRequest,
        authentication: Authentication?,
    ) = commands.updateAiJobConfiguration(UpdateAiJobConfigurationCommand(
        AiJobKey(jobKey), request.provider, request.model, request.enabled, request.expectedVersion,
        authentication.memoryStakeholder(), request.idempotencyKey,
    ))
}

@RestController
@RequestMapping("/api/operations/step-3")
class MemoryAndAiOperationsController(
    private val memory: AgentMemoryQueryService,
    private val ai: AiExecutionQueryService,
    private val products: ProductQueryService,
) {
    @GetMapping
    fun overview() = mapOf(
        "rolesByProduct" to products.findProducts().associate { it.id.value to memory.getAgentRoleCatalog(it.id) },
        "aiJobConfigurations" to ai.getAiJobConfigurations(),
        "aiTasks" to mapOf("available" to false, "message" to "Beschikbaar vanaf stap 4"),
    )
}
