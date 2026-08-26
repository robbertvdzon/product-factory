package nl.vdzon.productfactory.design.mvp

import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContext
import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContributor
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("acceptance")
class AcceptanceDesignFixtureContributor(
    private val design: ProductDesignMvpService,
) : AcceptanceFixtureContributor {
    override val key = "product-design-mvp"
    override val order = 90

    override fun reset(context: AcceptanceFixtureContext) = design.deleteAllOwnedData()
}
