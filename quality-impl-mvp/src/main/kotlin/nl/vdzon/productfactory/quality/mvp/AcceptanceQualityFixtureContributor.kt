package nl.vdzon.productfactory.quality.mvp

import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContext
import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContributor
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("acceptance")
class AcceptanceQualityFixtureContributor(private val quality: QualityMvpService) : AcceptanceFixtureContributor {
    override val key = "quality-mvp"
    override val order = 70
    override fun reset(context: AcceptanceFixtureContext) = quality.deleteAllOwnedData()
}
