package nl.vdzon.productfactory.operations

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import nl.vdzon.productfactory.foundation.BuildIdentityService
import org.springframework.stereotype.Component

@Component
class BuildIdentityMetrics(
    meterRegistry: MeterRegistry,
    buildIdentityService: BuildIdentityService,
) {
    init {
        val identity = buildIdentityService.identity
        Gauge.builder("product_factory_build_info") { 1 }
            .description("Immutable Product Factory-buildidentiteit")
            .tag("application_version", identity.applicationVersion)
            .tag("git_revision", identity.gitRevision)
            .tag("environment", identity.environment)
            .register(meterRegistry)
    }
}
