package nl.vdzon.productfactory.advisor

import nl.vdzon.productfactory.api.advisor.CreateConversationCommand
import nl.vdzon.productfactory.api.shared.ProductId
import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContext
import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContributor
import nl.vdzon.productfactory.auth.UserIdentityRepository
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
@Profile("acceptance")
class AcceptanceProductAdvisorResetContributor(
    private val advisor: ProductAdvisorApplicationService,
    private val jdbc: JdbcTemplate,
) : AcceptanceFixtureContributor {
    override val key = "product-advisor-reset"
    override val order = 85

    override fun reset(context: AcceptanceFixtureContext) {
        advisor.deleteAllOwnedData()
        jdbc.update("DELETE FROM pf_product_membership_history")
        jdbc.update("DELETE FROM pf_product_membership")
        jdbc.update("DELETE FROM pf_user_command")
    }
}

@Component
@Profile("acceptance")
class AcceptanceProductAdvisorIdentityResetContributor(
    private val jdbc: JdbcTemplate,
) : AcceptanceFixtureContributor {
    override val key = "product-advisor-identity-reset"
    override val order = 210

    override fun reset(context: AcceptanceFixtureContext) {
        jdbc.update("DELETE FROM pf_user_global_role")
        jdbc.update("DELETE FROM pf_user_account")
    }
}

@Component
@Profile("acceptance")
class AcceptanceProductAdvisorScenarioContributor(
    private val advisor: ProductAdvisorApplicationService,
    private val users: UserIdentityRepository,
) : AcceptanceFixtureContributor {
    override val key = "product-advisor-scenario"
    override val order = 260

    override fun reset(context: AcceptanceFixtureContext) {
        if (!context.scenarioKey.startsWith("advisor-")) return
        val productId = ProductId("synthetic-history")
        val factoryOwner = users.resolveOrCreate("factory-owner@acceptance.invalid", true)
        val productOwner = users.resolveOrCreate("product-owner@acceptance.invalid", false)
        users.grantProductOwner(
            productOwner.id, productId, factoryOwner.id, 0,
            "fixture:${context.datasetVersion}:${context.scenarioKey}:membership",
        )
        advisor.createConversation(CreateConversationCommand(
            productId,
            title(context.scenarioKey),
            productOwner.id,
            "fixture:${context.datasetVersion}:${context.scenarioKey}:conversation",
        ))
    }

    private fun title(scenario: String) = when (scenario) {
        "advisor-information" -> "Hoe werkt de huidige productroute?"
        "advisor-follow-up" -> "Onderzoek met een vervolgvraag"
        "advisor-hotfix" -> "Kleine correctie onderzoeken"
        "advisor-bugfix" -> "Grotere fout onderzoeken"
        "advisor-epic" -> "Nieuwe productmogelijkheid onderzoeken"
        "advisor-directed-question" -> "Ontwerp met gerichte vraag"
        "advisor-double-approval" -> "Epic in twee stappen beoordelen"
        "advisor-refinement" -> "Epic terugsturen en herzien"
        else -> "Autorisatiescheiding controleren"
    }
}
