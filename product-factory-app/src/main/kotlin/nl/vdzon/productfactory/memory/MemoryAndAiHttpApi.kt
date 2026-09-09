package nl.vdzon.productfactory.memory

import nl.vdzon.productfactory.api.ai.*
import nl.vdzon.productfactory.api.memory.*
import nl.vdzon.productfactory.api.product.ProductQueryService
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.auth.ResolvedSession
import org.springframework.http.HttpStatus
import org.springframework.http.HttpHeaders
import org.springframework.http.ContentDisposition
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
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
    val vendorId: String,
    val model: String,
    val mode: AiExecutionMode,
    val enabled: Boolean,
    val expectedVersion: Long,
    val idempotencyKey: String,
)

data class MemoryIdResponse(val id: String)
data class CancelAiTaskRequest(val reason: String)
data class RefreshEnvironmentCatalogRequest(val projectPrefix: String)
data class RefreshExecutionCatalogRequest(val taskType: String = "STRUCTURED_GENERATION")
data class SetProductEnvironmentKeyRequest(val active: Boolean, val expectedVersion: Long, val idempotencyKey: String)
data class SetAgentEnvironmentGrantRequest(val granted: Boolean, val idempotencyKey: String)

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
        AiJobKey(jobKey), AiExecutionSelection(request.vendorId, request.model, request.mode), request.enabled, request.expectedVersion,
        authentication.memoryStakeholder(), request.idempotencyKey,
    ))
}

@RestController
@RequestMapping("/api/ai/tasks")
class AiTaskController(
    private val commands: AiExecutionService,
    private val queries: AiExecutionQueryService,
) {
    @GetMapping
    fun all(
        @RequestParam(required = false) productId: String?,
        @RequestParam(required = false) status: Set<AiTaskStatus>?,
        @RequestParam(required = false) jobKey: String?,
    ) = queries.findAiTasks(AiTaskFilter(productId?.let(::ProductId), status.orEmpty(), jobKey?.let(::AiJobKey)))

    @GetMapping("/{taskId}")
    fun one(@PathVariable taskId: String) = queries.getAiTask(AiTaskId(taskId))

    @GetMapping("/{taskId}/result")
    fun result(@PathVariable taskId: String) = queries.getAiTaskResult(AiTaskId(taskId))

    @GetMapping("/{taskId}/events")
    fun events(@PathVariable taskId: String) = queries.getAiTaskEvents(AiTaskId(taskId))

    @GetMapping("/{taskId}/usage")
    fun usage(@PathVariable taskId: String) = queries.getAiTaskUsage(AiTaskId(taskId))

    @PostMapping("/{taskId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun cancel(@PathVariable taskId: String, @RequestBody request: CancelAiTaskRequest) = commands.cancelAiTask(AiTaskId(taskId), request.reason)

    @GetMapping("/{taskId}/artifacts/{artifactId}")
    fun artifact(
        @PathVariable taskId: String,
        @PathVariable artifactId: String,
        @RequestHeader(HttpHeaders.RANGE, required = false) rangeHeader: String?,
    ): ResponseEntity<StreamingResponseBody> {
        val requested = parseRange(rangeHeader)
        val content = queries.openAiTaskArtifact(AiTaskId(taskId), artifactId, requested?.first ?: 0)
        val end = requested?.last?.coerceAtMost(content.sizeBytes - 1) ?: content.sizeBytes - 1
        if (end < content.offset) {
            content.inputStream.close()
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build()
        }
        val responseLength = end - content.offset + 1
        val body = StreamingResponseBody { output ->
            content.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                var remaining = responseLength
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
        }
        val status = if (requested == null) HttpStatus.OK else HttpStatus.PARTIAL_CONTENT
        return ResponseEntity.status(status)
            .contentType(runCatching { MediaType.parseMediaType(content.mediaType) }.getOrDefault(MediaType.APPLICATION_OCTET_STREAM))
            .contentLength(responseLength)
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(content.filename).build().toString())
            .apply {
                content.sha256?.let { header(HttpHeaders.ETAG, "\"sha256-$it\"") }
                if (requested != null) header(HttpHeaders.CONTENT_RANGE, "bytes ${content.offset}-$end/${content.sizeBytes}")
            }
            .body(body)
    }

    private fun parseRange(value: String?): LongRange? {
        if (value == null) return null
        val match = Regex("bytes=(\\d+)-(\\d*)").matchEntire(value)
            ?: throw InvalidCommand("Alleen één byte-range wordt ondersteund.")
        val start = match.groupValues[1].toLongOrNull() ?: throw InvalidCommand("Ongeldige byte-range.")
        val end = match.groupValues[2].takeIf(String::isNotBlank)?.toLongOrNull() ?: Long.MAX_VALUE
        if (start < 0 || end < start) throw InvalidCommand("Ongeldige byte-range.")
        return start..end
    }
}

@RestController
class AgentEnvironmentAccessController(
    private val commands: AiExecutionService,
    private val queries: AiExecutionQueryService,
) {
    @GetMapping("/api/ai/environment-catalog")
    fun catalog(@RequestParam projectPrefix: String) = queries.getEnvironmentCatalog(projectPrefix)

    @PostMapping("/api/ai/environment-catalog/refresh")
    fun refresh(@RequestBody request: RefreshEnvironmentCatalogRequest) =
        commands.refreshEnvironmentCatalog(RefreshEnvironmentCatalogCommand(request.projectPrefix))

    @GetMapping("/api/ai/execution-catalog")
    fun executionCatalog(@RequestParam(defaultValue = "STRUCTURED_GENERATION") taskType: String) = queries.getExecutionCatalog(taskType)

    @PostMapping("/api/ai/execution-catalog/refresh")
    fun refreshExecutionCatalog(@RequestBody request: RefreshExecutionCatalogRequest) =
        commands.refreshExecutionCatalog(RefreshExecutionCatalogCommand(request.taskType))

    @GetMapping("/api/products/{productId}/agent-environment-keys")
    fun productKeys(@PathVariable productId: String) = queries.getProductEnvironmentKeys(ProductId(productId))

    @PutMapping("/api/products/{productId}/agent-environment-keys/{name}")
    fun setProductKey(
        @PathVariable productId: String,
        @PathVariable name: String,
        @RequestBody request: SetProductEnvironmentKeyRequest,
        authentication: Authentication?,
    ) = commands.setProductEnvironmentKey(SetProductEnvironmentKeyCommand(
        ProductId(productId), name, request.active, request.expectedVersion, authentication.memoryStakeholder(), request.idempotencyKey,
    ))

    @PutMapping("/api/products/{productId}/agent-environment-keys/{name}/roles/{role}")
    fun setGrant(
        @PathVariable productId: String,
        @PathVariable name: String,
        @PathVariable role: String,
        @RequestBody request: SetAgentEnvironmentGrantRequest,
        authentication: Authentication?,
    ) = commands.setAgentEnvironmentGrant(SetAgentEnvironmentGrantCommand(
        ProductId(productId), name, role, request.granted, authentication.memoryStakeholder(), request.idempotencyKey,
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
        "aiTasks" to mapOf("available" to true, "tasks" to ai.findAiTasks(AiTaskFilter())),
    )
}

@RestController
@RequestMapping("/api/operations/step-4")
class AgentRuntimeOperationsController(private val ai: AiExecutionQueryService) {
    @GetMapping
    fun overview() = mapOf(
        "tasks" to ai.findAiTasks(AiTaskFilter()),
        "runtimeMonitor" to "Agent Runtime toont technische workers en attempts; Product Factory toont alleen domeincorrelatie.",
    )
}
