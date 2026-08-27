package nl.vdzon.productfactory.flow

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import nl.vdzon.productfactory.ai.AiExecutionApplicationService
import nl.vdzon.productfactory.ai.FakeRuntime
import nl.vdzon.productfactory.ai.RuntimeArtifactView
import nl.vdzon.productfactory.api.design.*
import nl.vdzon.productfactory.api.dispatcher.*
import nl.vdzon.productfactory.api.foundation.DeploymentRevisionResolver
import nl.vdzon.productfactory.api.foundation.PublicGitRevisionResolver
import nl.vdzon.productfactory.api.planning.*
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.quality.*
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.design.mvp.ProductDesignMvpService
import nl.vdzon.productfactory.dispatcher.MockSoftwareFactory
import nl.vdzon.productfactory.dispatcher.SoftwareFactoryDispatcherMvpService
import nl.vdzon.productfactory.planning.mvp.ProductPlanningMvpService
import nl.vdzon.productfactory.quality.mvp.QualityMvpService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

@SpringBootTest(properties = ["PF_AUTH_REQUIRED=false", "PF_SOFTWARE_FACTORY_MODE=MOCKED"])
@ActiveProfiles("test")
@Import(MvpHappyFlowIntegrationTest.Fakes::class)
class MvpHappyFlowIntegrationTest @Autowired constructor(
    private val productCommands: ProductCommandService,
    private val design: ProductDesignService,
    private val designQueries: ProductDesignQueryService,
    private val designImpl: ProductDesignMvpService,
    private val planning: ProductPlanningService,
    private val planningQueries: ProductPlanningQueryService,
    private val planningImpl: ProductPlanningMvpService,
    private val dispatcher: SoftwareFactoryDispatcherService,
    private val dispatcherQueries: SoftwareFactoryDispatcherQueryService,
    private val dispatcherImpl: SoftwareFactoryDispatcherMvpService,
    private val quality: QualityService,
    private val qualityQueries: QualityQueryService,
    private val qualityImpl: QualityMvpService,
    private val ai: AiExecutionApplicationService,
    private val runtime: FakeRuntime,
    private val mockFactory: MockSoftwareFactory,
    private val mapper: ObjectMapper,
) {
    private var productId = ProductId("not-initialized")

    @BeforeEach
    fun prepare() {
        qualityImpl.deleteAllOwnedData()
        dispatcherImpl.deleteAllOwnedData()
        planningImpl.deleteAllOwnedData()
        designImpl.deleteAllOwnedData()
        ai.deleteAllOwnedExecutionData()
        runtime.reset()
        mockFactory.reset()
        productId = ProductId("mvp-${UUID.randomUUID().toString().take(8)}")
        productCommands.createProduct(CreateProductCommand(productId, "MVP happy flow", actor = STAKEHOLDER, idempotencyKey = "happy-product-${productId.value}"))
        productCommands.updateProductAssignment(UpdateProductAssignmentCommand(
            productId, "HKH-gebruikers", "Maak afspraken aantoonbaar eenvoudiger", listOf("Geen echte persoonsgegevens"),
            "https://github.com/robbertvdzon/hkh-autopilot.git", 0, STAKEHOLDER, "happy-assignment",
        ))
        productCommands.configureTestableProduct(ConfigureTestableProductCommand(
            productId, TestEnvironmentConfiguration("acceptance", "https://acceptance.example.test", listOf("/"), "/api/version", "commit"),
            null, 0, STAKEHOLDER, "happy-environment",
        ))
        productCommands.setProductDispatching(SetProductDispatchingCommand(productId, true, 1, STAKEHOLDER, "happy-dispatching"))
    }

    @Test
    fun `productinput wordt complete epic story levering controles en completed epic`() {
        design.runProcessSession(productId)
        completeRuntime(epicResult())
        design.runProcessSession(productId)
        runtime.reset()
        val epic = designQueries.findEpics(EpicFilter(productId)).single()

        planning.runProcessSession(productId)
        completeRuntime(selection(epic))
        planning.runProcessSession(productId)
        runtime.reset()
        ai.dispatchPending()
        completeDispatched(plan(epic))
        planning.runProcessSession(productId)
        runtime.reset()
        val story = planningQueries.getBacklog(productId).single()

        dispatcher.runDispatchSession(productId)
        val storyKey = dispatcherQueries.findDeliveryAttempts(DeliveryAttemptFilter(productId)).single().externalStoryId!!
        mockFactory.complete(storyKey, SHA)
        dispatcher.runDispatchSession(productId)
        assertThat(planningQueries.getStory(story.id).status).isEqualTo(StoryStatus.DONE)

        val storyWork = qualityQueries.findQualityWorkItems(productId).single()
        completeQuality(storyWork)
        planning.flushPendingEffects()
        runtime.reset()
        val epicWork = qualityQueries.findQualityWorkItems(productId).single { it.type == QualityWorkItemType.VERIFY_EPIC }
        completeQuality(epicWork)

        assertThat(designQueries.getEpic(epic.id).status).isEqualTo(EpicStatus.COMPLETED)
        assertThat(qualityQueries.findVerifications(VerificationFilter(productId))).hasSize(2)
        assertThat(qualityQueries.getCurrentQuality(productId)?.productRevision).isEqualTo(SHA)
        assertThat(dispatcherQueries.findDeliveryAttempts(DeliveryAttemptFilter(productId)).single().status).isEqualTo(DeliveryAttemptStatus.COMPLETED)
    }

    private fun completeQuality(work: QualityWorkItemDetails) {
        quality.runProcessSession(productId)
        ai.dispatchPending()
        completeDispatched(qualityResult(work.id))
        quality.runProcessSession(productId)
    }

    private fun completeRuntime(result: ObjectNode) {
        ai.dispatchPending()
        completeDispatched(result)
    }

    private fun completeDispatched(result: ObjectNode) {
        val job = runtime.onlyJob()
        runtime.results[job.id] = result
        if (result.path("epic").isObject) {
            runtime.resultArtifacts[job.id] = result.path("epic").path("uxArtifactChanges")
                .mapNotNull { it.path("outputArtifactName").takeIf(JsonNode::isTextual)?.asText() }
                .mapIndexed { index, name ->
                    RuntimeArtifactView("ux-$index", job.id, name, "image/png", 128, (index + 1).toString().take(1).repeat(64), java.time.Instant.now())
                }
        }
        runtime.jobs[job.id] = job.copy(status = "SUCCEEDED", phase = "COMPLETED", progressPercent = 100)
        ai.reconcileActive()
    }

    private fun epicResult() = mapper.createObjectNode().apply {
        put("outcome", "CREATE_EPIC")
        set<ObjectNode>("epic", mapper.createObjectNode().apply {
            put("title", "Afsprakenoverzicht begrijpen")
            put("summary", "De gebruiker begrijpt direct welke afspraak aandacht nodig heeft.")
            put("problem", "De gebruiker ziet niet zelfstandig welke afspraak als eerste aandacht nodig heeft.")
            put("solution", "Toon een rustig overzicht met één duidelijke eerstvolgende actie en aantoonbare lege en fouttoestand.")
            putArray("directionReferences").addObject().put("type", "PRODUCT_ASSIGNMENT").put("id", productId.value).put("version", 1)
            put("visibleBehaviorChange", true)
            put("uxDesign", "Een rustige overzichtskaart met duidelijke status, actie en toegankelijke focusvolgorde.")
            putArray("uxArtifactChanges").apply {
                listOf(
                    "ux-main-desktop.png" to "start", "ux-main-mobile.png" to "start",
                    "ux-empty-desktop.png" to "empty", "ux-empty-mobile.png" to "empty",
                ).forEach { (name, screenKey) ->
                    addObject().apply {
                        put("operation", "ADD"); putNull("existingArtifactName"); put("outputArtifactName", name)
                        put("screenKey", screenKey); put("reason", "Dit bestand maakt de volledige UX-route aantoonbaar.")
                    }
                }
            }
            putArray("uxScreens").apply {
                addObject().apply {
                    put("screenKey", "start"); put("state", "INITIAL"); put("purpose", "Toon het afsprakenoverzicht als ingang van de route.")
                    putArray("artifacts").apply {
                        addObject().put("viewport", "DESKTOP").put("artifactName", "ux-main-desktop.png")
                        addObject().put("viewport", "MOBILE").put("artifactName", "ux-main-mobile.png")
                    }
                }
                addObject().apply {
                    put("screenKey", "empty"); put("state", "EMPTY"); put("purpose", "Toon begrijpelijk dat er geen afspraak beschikbaar is.")
                    putArray("artifacts").apply {
                        addObject().put("viewport", "DESKTOP").put("artifactName", "ux-empty-desktop.png")
                        addObject().put("viewport", "MOBILE").put("artifactName", "ux-empty-mobile.png")
                    }
                }
            }
            putArray("acceptanceCriteria").add(CRITERION)
            put("slicabilityRationale", "Eén zelfstandige story levert de complete kleine gebruikersroute zonder verborgen vervolgwerk.")
            putArray("researchSources")
            putObject("readiness").apply {
                put("readyForPlanning", true)
                put("requiresExternalData", false)
                putArray("unmetConditions")
                putArray("openQuestions")
            }
        })
        putArray("processedSignalIds")
        putArray("memoryChanges")
    }

    private fun selection(epic: EpicDetails) = mapper.createObjectNode().apply {
        put("outcome", "PLAN")
        putArray("epicSelections").addObject().put("epicId", epic.id.value).put("expectedVersion", epic.version)
    }

    private fun plan(epic: EpicDetails) = mapper.createObjectNode().apply {
        put("outcome", "PUBLISH_PLAN")
        putArray("stories").addObject().apply {
            put("draftKey", "appointments-overview")
            put("type", "PRODUCT_STORY")
            put("epicId", epic.id.value)
            put("epicVersion", epic.version)
            putNull("bugId")
            putNull("bugVersion")
            put("title", "Toon begrijpelijk afsprakenoverzicht")
            put("summary", "De gebruiker ziet de eerstvolgende afspraak en benodigde actie.")
            put("content", "Bouw de complete overzichtsroute met afspraakstatus, eerstvolgende actie, toegankelijke bediening en zichtbare laad-, lege en fouttoestand.")
            putArray("acceptanceCriteria").add(CRITERION)
            put("uxDesign", "Rustige kaart, duidelijke koppen en logische toetsenbordfocus.")
            putArray("uxArtifactNames").add("ux-main-desktop.png")
            putArray("dependencies")
            putArray("coveredAcceptanceCriteria").add(CRITERION)
            put("priorityReason", "Dit is de kleinste complete gebruikerswaarde.")
        }
        putArray("todoOrder").add("appointments-overview")
        putArray("refinementRequests")
        putNull("stakeholderQuestion")
        putArray("memoryChanges")
    }

    private fun qualityResult(work: QualityWorkItemId) = mapper.createObjectNode().apply {
        put("outcome", "PUBLISH_RESULTS")
        putArray("results").addObject().apply {
            put("workItemId", work.value)
            put("outcome", "PASSED")
            putArray("checks").add("De complete hoofdroute is tegen de exacte deploymentrevision uitgevoerd.")
            set<ObjectNode>("evidence", mapper.createObjectNode().apply {
                put("description", "De zichtbare route, lege toestand en fouttoestand zijn aantoonbaar geobserveerd.")
                putArray("artifacts")
            })
            putArray("missingCoverage")
            putArray("bugs")
            put("explanation", "De bedoelde gebruikerswaarde is volledig en reproduceerbaar aangetoond.")
            put("signalOutcome", "Geen afwijking gevonden")
        }
        putArray("memoryChanges")
    }

    @TestConfiguration
    class Fakes {
        @Bean @Primary fun runtime() = FakeRuntime()
        @Bean @Primary fun git(): PublicGitRevisionResolver = object : PublicGitRevisionResolver { override fun resolveHead(publicGitUrl: String) = SHA }
        @Bean @Primary fun revision(): DeploymentRevisionResolver = object : DeploymentRevisionResolver {
            override fun resolve(baseUrl: String, revisionEndpoint: String, revisionJsonPath: String) = SHA
        }
    }

    companion object {
        private const val SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val CRITERION = "De gebruiker ziet de eerstvolgende afspraak, status en benodigde actie inclusief lege en fouttoestand."
        private val STAKEHOLDER = ActorReference(ActorType.STAKEHOLDER, "stakeholder@example.com")
    }
}
