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
        message = "De product- en stakeholderbasis is actief; AI-processen volgen in latere stappen.",
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
            ),
        )
    }

    @GetMapping("/schedules")
    fun schedules() = productQueries.findProducts().associate { product ->
        product.id.value to productQueries.getProcessSchedules(product.id)
    }
}
