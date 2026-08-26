package nl.vdzon.productfactory.planning.mvp

import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContext
import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContributor
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("acceptance")
class AcceptancePlanningFixtureContributor(
    private val planning: ProductPlanningMvpService,
) : AcceptanceFixtureContributor {
    override val key = "product-planning-mvp"
    override val order = 80
    override fun reset(context: AcceptanceFixtureContext) = planning.deleteAllOwnedData()
}
