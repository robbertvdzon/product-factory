package nl.vdzon.productfactory.dispatcher

import nl.vdzon.productfactory.api.product.ProductCommandService
import nl.vdzon.productfactory.api.product.ProductQueryService
import nl.vdzon.productfactory.api.product.SetProductDispatchingCommand
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContext
import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContributor
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.UUID

@Component
@Profile("acceptance")
class AcceptanceDispatcherResetContributor(
    private val dispatcher: SoftwareFactoryDispatcherMvpService,
    private val mock: MockSoftwareFactory,
) : AcceptanceFixtureContributor {
    override val key = "software-factory-dispatcher-reset"
    override val order = 70
    override fun reset(context: AcceptanceFixtureContext) {
        dispatcher.deleteAllOwnedData()
        mock.reset()
    }
}

@Component
@Profile("acceptance")
class AcceptanceDispatcherScenarioContributor(
    private val jdbc: JdbcTemplate,
    private val clock: Clock,
    private val products: ProductQueryService,
    private val productCommands: ProductCommandService,
) : AcceptanceFixtureContributor {
    override val key = "software-factory-dispatcher-scenario"
    override val order = 220

    override fun reset(context: AcceptanceFixtureContext) {
        if (!context.scenarioKey.startsWith("software-factory-")) return
        val productId = ProductId("synthetic-history")
        val product = products.getProduct(productId)
        productCommands.setProductDispatching(SetProductDispatchingCommand(
            productId, true, product.version, SYSTEM, "fixture:${context.datasetVersion}:${context.scenarioKey}:dispatching",
        ))
        val storyId = UUID.nameUUIDFromBytes("${context.scenarioKey}:story".toByteArray()).toString()
        val now = clock.instant()
        jdbc.update(
            """INSERT INTO pf_story(id,product_id,epic_id,epic_version,type,status,current_version,sequence_number,priority_reason,
                bug_link_confirmed,created_at,updated_at) VALUES (?,?,?,?,?,'TODO',1,1,?,FALSE,?,?)""".trimIndent(),
            storyId, productId.value, UUID.nameUUIDFromBytes("${context.scenarioKey}:epic".toByteArray()).toString(), 1L,
            "PRODUCT_STORY", "Vaste dispatcheracceptatiestory", now, now,
        )
        jdbc.update(
            """INSERT INTO pf_story_version(story_id,version,title,summary,content,acceptance_criteria_json,ux_design,dependencies_json,
                source_references_json,created_at) VALUES (?,1,?,?,?,?,?,?,?,?)""".trimIndent(),
            storyId, "Toon dispatcherbewijs", "De tester ziet dat één story veilig naar de mockfabriek gaat.",
            "Bouw een zelfstandig zichtbaar bewijs met laad-, succes- en fouttoestand zonder echte externe mutatie.",
            "[\"De mockfabriek ontvangt exact één complete story.\"]", "Rustige bewijskaart met duidelijke status.", "[]", "[]", now,
        )
    }

    companion object { private val SYSTEM = ActorReference(ActorType.SYSTEM, "acceptance-dispatcher-fixture") }
}
