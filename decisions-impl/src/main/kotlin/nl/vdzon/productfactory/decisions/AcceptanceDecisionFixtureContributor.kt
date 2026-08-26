package nl.vdzon.productfactory.decisions

import nl.vdzon.productfactory.api.decisions.*
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContext
import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContributor
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("acceptance")
class AcceptanceDecisionFixtureContributor(
    private val service: DecisionApplicationService,
) : AcceptanceFixtureContributor {
    override val key: String = "decisions"
    override val order: Int = 300

    override fun reset(context: AcceptanceFixtureContext) {
        service.deleteAllOwnedData()
        val productId = ProductId("synthetic-history")
        val active = service.createDecision(CreateDecisionCommand(
            productId, "Publieke bronnen blijven herleidbaar.", DecisionOrigin.STAKEHOLDER, SYSTEM,
            key(context, "active"),
        ))
        service.reviseDecision(ReviseDecisionCommand(
            active, "Publieke bronnen blijven altijd zichtbaar en herleidbaar.", 1, SYSTEM,
            key(context, "active-revision"),
        ))
        val withdrawn = service.createDecision(CreateDecisionCommand(
            productId, "Gebruik uitsluitend één synthetische bron.", DecisionOrigin.FACTORY, SYSTEM,
            key(context, "withdrawn"),
        ))
        service.withdrawDecision(WithdrawDecisionCommand(
            withdrawn, "Meerdere synthetische bronnen zijn nodig voor het scenario.", 1, SYSTEM,
            key(context, "withdraw"),
        ))
        val replaced = service.createDecision(CreateDecisionCommand(
            productId, "Toon alleen tekstuele bronverwijzingen.", DecisionOrigin.STAKEHOLDER, SYSTEM,
            key(context, "replaced"),
        ))
        service.supersedeDecisions(SupersedeDecisionsCommand(
            productId, setOf(replaced), "Toon tekstuele en visuele bronverwijzingen.", DecisionOrigin.STAKEHOLDER,
            mapOf(replaced to 1L), SYSTEM, key(context, "supersede"),
        ))
    }

    private fun key(context: AcceptanceFixtureContext, suffix: String) = "fixture:${context.datasetVersion}:${context.scenarioKey}:decision:$suffix"

    companion object {
        private val SYSTEM = ActorReference(ActorType.SYSTEM, "acceptance-fixture")
    }
}
