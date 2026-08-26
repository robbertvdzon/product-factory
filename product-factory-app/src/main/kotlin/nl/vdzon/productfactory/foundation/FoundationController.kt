package nl.vdzon.productfactory.foundation

import nl.vdzon.productfactory.api.foundation.FoundationState
import nl.vdzon.productfactory.api.foundation.FoundationStatus
import nl.vdzon.productfactory.api.foundation.ImplementationManifest
import nl.vdzon.productfactory.api.shared.ImplementationIdentity
import nl.vdzon.productfactory.api.product.ProductQueryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/foundation")
class FoundationController(
    private val buildIdentityService: BuildIdentityService,
    private val productQueries: ProductQueryService,
) {
    @GetMapping
    fun getFoundation(): FoundationStatus = FoundationStatus(
        application = "Product Factory",
        state = FoundationState.READY,
        message = "Productbasis, Agent Runtime-uitvoering, Productontwerp, Productplanning, Kwaliteitsbewaking en Software Factory-dispatching zijn actief.",
    )

    @GetMapping("/implementations")
    fun implementations(): ImplementationManifest {
        val build = buildIdentityService.identity
        val revision = build.gitRevision
        val version = build.applicationVersion
        return ImplementationManifest(
            manifestVersion = "2",
            implementations = linkedMapOf(
                "product" to ImplementationIdentity("product-impl", "jdbc", version, revision),
                "decisions" to ImplementationIdentity("decisions-impl", "jdbc", version, revision),
                "agent-memory" to ImplementationIdentity("agent-memory-impl", "append-only-jdbc", version, revision),
                "ai-execution" to ImplementationIdentity("ai-execution-impl", "agent-runtime-outbox-v1", version, revision),
                "product-design" to ImplementationIdentity("product-design-impl-mvp", "single-agent", version, revision),
                "product-planning" to ImplementationIdentity("product-planning-impl-mvp", "single-planner", version, revision),
                "quality" to ImplementationIdentity("quality-impl-mvp", "single-tester", version, revision),
                "software-factory-dispatcher" to ImplementationIdentity("software-factory-dispatcher-impl", "v2-idempotent", version, revision),
            ),
        )
    }

    @GetMapping("/schedules")
    fun schedules() = productQueries.findProducts().associate { product ->
        product.id.value to productQueries.getProcessSchedules(product.id)
    }
}
