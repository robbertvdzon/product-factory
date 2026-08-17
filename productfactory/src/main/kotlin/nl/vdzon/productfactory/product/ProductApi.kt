package nl.vdzon.productfactory.product

import nl.vdzon.productfactory.contracts.MemoryVersionView
import nl.vdzon.productfactory.contracts.ProductRecordView
import nl.vdzon.productfactory.contracts.ProductView
import nl.vdzon.productfactory.contracts.WeeklyScheduleView
import nl.vdzon.productfactory.product.api.AiCatalog
import nl.vdzon.productfactory.product.api.ProductCatalog
import nl.vdzon.productfactory.product.api.ProductConfiguration
import nl.vdzon.productfactory.product.api.UpdateProductSettingsRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class CreateProductRequest(
    val slug: String,
    val name: String,
    val mission: String,
    val description: String = "",
    val guardrails: String = "",
    val softwareFactoryProjectKey: String = slug,
    val targetRepositoryName: String = slug,
    val allowedWritePaths: List<String> = DEFAULT_WRITE_PATHS,
    val workspaceOwnership: String = "product-factory",
    val liveUrl: String? = null,
    val previewUrlPattern: String? = null,
    val acceptanceUrl: String? = null,
    val adminUrl: String? = null,
    val status: String = "draft",
    val developmentMode: String = "manual",
    val iterationTimes: List<String> = listOf("03:00"),
    val roadmapSchedule: List<WeeklyScheduleView> = emptyList(),
    val testSchedule: List<WeeklyScheduleView> = listOf(
        WeeklyScheduleView("TUESDAY", "10:00"),
        WeeklyScheduleView("FRIDAY", "10:00"),
    ),
    val timezone: String = "Europe/Amsterdam",
    val maxStoriesPerCycle: Int = 3,
    val wipLimit: Int = 1,
    val aiProvider: String = "codex",
    val aiModel: String = "default",
    val dailyBudgetCents: Int = 0,
    val monthlyBudgetCents: Int = 0,
    val escalationPolicy: String = "",
    val privacyRules: String = "",
    val accessibilityRules: String = "",
    val qualityRules: String = "",
    val roadmapProcessVersion: String = "legacy-v1",
) {
    fun configuration() = ProductConfiguration(
        slug = slug,
        name = name,
        mission = mission,
        description = description,
        guardrails = guardrails,
        softwareFactoryProjectKey = softwareFactoryProjectKey,
        targetRepositoryName = targetRepositoryName,
        allowedWritePaths = allowedWritePaths,
        workspaceOwnership = workspaceOwnership,
        liveUrl = liveUrl,
        previewUrlPattern = previewUrlPattern,
        acceptanceUrl = acceptanceUrl,
        adminUrl = adminUrl,
        status = status,
        developmentMode = developmentMode,
        iterationTimes = iterationTimes,
        roadmapSchedule = roadmapSchedule,
        testSchedule = testSchedule,
        timezone = timezone,
        maxStoriesPerCycle = maxStoriesPerCycle,
        wipLimit = wipLimit,
        aiProvider = aiProvider,
        aiModel = aiModel,
        dailyBudgetCents = dailyBudgetCents,
        monthlyBudgetCents = monthlyBudgetCents,
        escalationPolicy = escalationPolicy,
        privacyRules = privacyRules,
        accessibilityRules = accessibilityRules,
        qualityRules = qualityRules,
        roadmapProcessVersion = roadmapProcessVersion,
    )

    companion object {
        val DEFAULT_WRITE_PATHS = listOf("research", "product-memory", "decisions", "ux", "roadmap", "stories")
    }
}

data class ProductRecordRequest(val title: String, val content: String, val sourceUrl: String? = null)

@RestController
@RequestMapping("/api/products")
class ProductController(private val catalog: ProductCatalog) {
    @GetMapping
    fun list(): List<ProductView> = catalog.list()

    @GetMapping("/{slug}")
    fun get(@PathVariable slug: String): ProductView = catalog.requireProduct(slug)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateProductRequest): ProductView = catalog.create(request.configuration())

    @PostMapping("/{slug}/pause")
    fun pause(@PathVariable slug: String): ProductView = catalog.changeStatus(slug, "paused")

    @PostMapping("/{slug}/resume")
    fun resume(@PathVariable slug: String): ProductView = catalog.changeStatus(slug, "active")

    @PutMapping("/{slug}/settings")
    fun updateSettings(@PathVariable slug: String, @RequestBody request: UpdateProductSettingsRequest): ProductView =
        catalog.updateSettings(slug, request)

    @GetMapping("/{slug}/research")
    fun research(@PathVariable slug: String): List<ProductRecordView> = catalog.listRecords(slug, "research")

    @PostMapping("/{slug}/research")
    @ResponseStatus(HttpStatus.CREATED)
    fun addResearch(@PathVariable slug: String, @RequestBody request: ProductRecordRequest): ProductRecordView =
        catalog.addRecord(slug, "research", request.title, request.content, request.sourceUrl)

    @GetMapping("/{slug}/memory")
    fun memory(
        @PathVariable slug: String,
        @RequestParam(required = false) asOf: String?,
    ): List<ProductRecordView> = asOf?.let { catalog.memoryAt(slug, it) } ?: catalog.listRecords(slug, "memory")

    @GetMapping("/{slug}/memory/history")
    fun memoryHistory(@PathVariable slug: String): List<MemoryVersionView> = catalog.memoryHistory(slug)

    @PostMapping("/{slug}/memory")
    @ResponseStatus(HttpStatus.CREATED)
    fun addMemory(@PathVariable slug: String, @RequestBody request: ProductRecordRequest): ProductRecordView =
        catalog.addRecord(slug, "memory", request.title, request.content)

    @GetMapping("/{slug}/decisions")
    fun decisions(@PathVariable slug: String): List<ProductRecordView> = catalog.listRecords(slug, "decision")

    @PostMapping("/{slug}/decisions")
    @ResponseStatus(HttpStatus.CREATED)
    fun addDecision(@PathVariable slug: String, @RequestBody request: ProductRecordRequest): ProductRecordView =
        catalog.addRecord(slug, "decision", request.title, request.content)
}

@RestController
@RequestMapping("/api/ai-catalog")
class AiCatalogController {
    @GetMapping fun get(): Map<String, List<String>> = AiCatalog.MODELS_BY_PROVIDER
}
