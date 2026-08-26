package nl.vdzon.productfactory.planning

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import nl.vdzon.productfactory.ai.AiExecutionApplicationService
import nl.vdzon.productfactory.ai.FakeRuntime
import nl.vdzon.productfactory.api.design.*
import nl.vdzon.productfactory.api.foundation.PublicGitRevisionResolver
import nl.vdzon.productfactory.api.planning.*
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.design.mvp.ProductDesignMvpService
import nl.vdzon.productfactory.planning.mvp.ProductPlanningMvpService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

@SpringBootTest(properties = ["PF_AUTH_REQUIRED=false"])
@ActiveProfiles("test")
@Import(ProductPlanningMvpIntegrationTest.Fakes::class)
class ProductPlanningMvpIntegrationTest @Autowired constructor(
    private val productCommands: ProductCommandService,
    private val design: ProductDesignService,
    private val designQueries: ProductDesignQueryService,
    private val designImpl: ProductDesignMvpService,
    private val planning: ProductPlanningService,
    private val planningQueries: ProductPlanningQueryService,
    private val planningImpl: ProductPlanningMvpService,
    private val ai: AiExecutionApplicationService,
    private val runtime: FakeRuntime,
    private val mapper: ObjectMapper,
) {
    private var productId = ProductId("not-initialized")
    private lateinit var epic: EpicDetails

    @BeforeEach
    fun prepare() {
        planningImpl.deleteAllOwnedData()
        designImpl.deleteAllOwnedData()
        ai.deleteAllOwnedExecutionData()
        runtime.reset()
        productId = ProductId("planning-${UUID.randomUUID().toString().take(8)}")
        productCommands.createProduct(CreateProductCommand(productId, "Planning test", actor = STAKEHOLDER, idempotencyKey = "create-${productId.value}"))
        productCommands.updateProductAssignment(UpdateProductAssignmentCommand(
            productId, "Stakeholders", "Lever waarde in kleine zelfstandige stappen", listOf("Geen credentials"),
            "https://github.com/robbertvdzon/hkh-autopilot.git", 0, STAKEHOLDER, "assignment-${productId.value}",
        ))
        epic = publishEpic()
        runtime.reset()
    }

    @Test
    fun `selectie en planning hervatten zonder duplicaat en publiceren volledige geordende storyset`() {
        planning.runProcessSession(productId)
        planning.runProcessSession(productId)
        assertThat(planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single().aiTaskIds).hasSize(1)

        completeOnlyJob(selection())
        planning.runProcessSession(productId)
        assertThat(designQueries.getEpic(epic.id).status).isEqualTo(EpicStatus.IN_PLANNING)

        runtime.reset()
        ai.dispatchPending()
        completeDispatchedJob(plan())
        planning.runProcessSession(productId)

        val backlog = planningQueries.getBacklog(productId)
        assertThat(backlog.map { it.title }).containsExactly("Overzicht tonen", "Bewijs openen")
        assertThat(backlog.map { it.sequenceNumber }).containsExactly(1, 2)
        assertThat(backlog[1].dependencies).containsExactly(backlog[0].id)
        assertThat(designQueries.getEpic(epic.id).status).isEqualTo(EpicStatus.ACTIVE)
        assertThat(planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single().status).isEqualTo(ProcessSessionStatus.SUCCEEDED)
    }

    @Test
    fun `dispatchreservering is idempotent en bewaart oplevercommit voor kwaliteitsgrens`() {
        publishPlan()
        val reserve = ReserveNextStoryForDispatchCommand(productId, PROCESS, "reserve-1")
        val first = planning.reserveNextStoryForDispatch(reserve)!!
        assertThat(planning.reserveNextStoryForDispatch(reserve)?.reservationId).isEqualTo(first.reservationId)

        val dispatched = MarkStoryAsDispatchedCommand(first.reservationId, "SF-1", first.story.version, PROCESS, "dispatched-1")
        planning.markStoryAsDispatched(dispatched)
        planning.markStoryAsDispatched(dispatched)
        var story = planningQueries.getStory(first.story.id)
        assertThat(story.status).isEqualTo(StoryStatus.IN_PROGRESS)

        planning.markStoryAsDeveloped(MarkStoryAsDevelopedCommand(story.id, "SF-1", "b".repeat(40), story.version, PROCESS, "developed-1"))
        story = planningQueries.getStory(story.id)
        assertThat(story.status).isEqualTo(StoryStatus.DONE)
        assertThat(story.deliveredCommitSha).isEqualTo("b".repeat(40))
        assertThat(planning.reserveNextStoryForDispatch(ReserveNextStoryForDispatchCommand(productId, PROCESS, "reserve-2"))?.story?.title).isEqualTo("Bewijs openen")
    }

    @Test
    fun `epicannulering en lokale reservering worden race safe herbevestigd`() {
        publishPlan()
        val reservation = planning.reserveNextStoryForDispatch(ReserveNextStoryForDispatchCommand(productId, PROCESS, "reserve-cancel"))!!
        val active = designQueries.getEpic(epic.id)
        design.cancelEpic(CancelEpicCommand(active.id, "Stakeholder stopt de epic.", active.version, STAKEHOLDER, "cancel-epic"))

        val validation = planning.revalidateDispatchReservation(RevalidateDispatchReservationCommand(
            reservation.reservationId, reservation.story.version, false, PROCESS, "revalidate-cancel",
        ))
        assertThat(validation.valid).isFalse()
        assertThat(planningQueries.getStory(reservation.story.id).status).isEqualTo(StoryStatus.CANCELLED)
        assertThat(planningQueries.findStories(StoryFilter(productId, epicId = epic.id))).allSatisfy {
            assertThat(it.status).isEqualTo(StoryStatus.CANCELLED)
        }
        assertThat(designQueries.getEpic(epic.id).status).isEqualTo(EpicStatus.CANCELLED)
    }

    @Test
    fun `ongeldige onvolledige dekking publiceert atomair niets en houdt epicclaim`() {
        planning.runProcessSession(productId)
        completeOnlyJob(selection())
        planning.runProcessSession(productId)
        runtime.reset()
        ai.dispatchPending()
        val invalid = plan().also {
            val stories = it.path("stories")
            (stories[1] as ObjectNode).putArray("coveredAcceptanceCriteria")
        }
        completeDispatchedJob(invalid)
        planning.runProcessSession(productId)

        assertThat(planningQueries.findStories(StoryFilter(productId))).isEmpty()
        assertThat(designQueries.getEpic(epic.id).status).isEqualTo(EpicStatus.IN_PLANNING)
        assertThat(planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single().status).isEqualTo(ProcessSessionStatus.BLOCKED)
    }

    @Test
    fun `gerichte herprioritering van actieve epic herschikt alleen todo stories`() {
        publishPlan()
        val before = planningQueries.getBacklog(productId)
        val active = designQueries.getEpic(epic.id)
        planning.requestEpicReprioritization(RequestEpicReprioritizationCommand(
            productId, active.id, "Het bewijsdetail is nu het urgente Stakeholderdoel.", 95, STAKEHOLDER, "reprioritize-active",
        ))

        runtime.reset()
        planning.runProcessSession(productId)
        completeOnlyJob(mapper.createObjectNode().apply {
            put("outcome", "PLAN")
            putArray("epicSelections").addObject().put("epicId", active.id.value).put("expectedVersion", active.version)
        })
        planning.runProcessSession(productId)
        runtime.reset()
        ai.dispatchPending()
        completeDispatchedJob(mapper.createObjectNode().apply {
            put("outcome", "PUBLISH_PLAN")
            putArray("stories")
            putArray("todoOrder").add(before[1].id.value).add(before[0].id.value)
            putArray("memoryChanges")
        })
        planning.runProcessSession(productId)

        assertThat(planningQueries.getBacklog(productId).map { it.id }).containsExactly(before[1].id, before[0].id)
        assertThat(planningQueries.findStories(StoryFilter(productId))).hasSize(2)
        assertThat(designQueries.getEpic(active.id).status).isEqualTo(EpicStatus.ACTIVE)
        assertThat(planningQueries.findPlanningWorkItems(productId).single().status).isEqualTo(WorkItemStatus.DONE)
    }

    @Test
    fun `terminale plannertaak houdt epicclaim en hervat dezelfde sessie voor nieuw werk`() {
        planning.runProcessSession(productId)
        completeOnlyJob(selection())
        planning.runProcessSession(productId)
        val sessionId = planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single().id
        assertThat(designQueries.getEpic(epic.id).status).isEqualTo(EpicStatus.IN_PLANNING)

        runtime.reset()
        ai.dispatchPending()
        val failed = runtime.onlyJob()
        runtime.jobs[failed.id] = failed.copy(status = "FAILED", phase = "FAILED", progressPercent = 100)
        ai.reconcileActive()
        planning.runProcessSession(productId)

        var session = planningQueries.getProcessSession(sessionId)
        assertThat(session.status).isEqualTo(ProcessSessionStatus.BLOCKED)
        assertThat(session.errorCode).isEqualTo("PLANNING_RESULT_INVALID")
        assertThat(designQueries.getEpic(epic.id).status).isEqualTo(EpicStatus.IN_PLANNING)

        runtime.reset()
        planning.runProcessSession(productId)
        ai.dispatchPending()
        session = planningQueries.getProcessSession(sessionId)
        assertThat(session.status).isEqualTo(ProcessSessionStatus.WAITING_FOR_AI)
        assertThat(session.aiTaskIds).hasSize(3)
        assertThat(planningQueries.findProcessSessions(ProcessSessionFilter(productId))).hasSize(1)
    }

    @Test
    fun `geannuleerde dependency blijft onvervuld en maakt gericht herplanningswerk`() {
        publishPlan()
        val reservation = planning.reserveNextStoryForDispatch(ReserveNextStoryForDispatchCommand(productId, PROCESS, "reserve-dependency"))!!
        planning.markStoryAsDispatched(MarkStoryAsDispatchedCommand(
            reservation.reservationId, "SF-dependency", reservation.story.version, PROCESS, "dispatch-dependency",
        ))
        val inProgress = planningQueries.getStory(reservation.story.id)

        planning.markStoryAsCancelled(MarkStoryAsCancelledCommand(
            inProgress.id, "SF-dependency", "Externe levering is geannuleerd.", inProgress.version, PROCESS, "cancel-dependency",
        ))

        val stories = planningQueries.findStories(StoryFilter(productId))
        val cancelled = stories.single { it.id == inProgress.id }
        val dependent = stories.single { it.id != inProgress.id }
        assertThat(cancelled.status).isEqualTo(StoryStatus.CANCELLED)
        assertThat(dependent.status).isEqualTo(StoryStatus.TODO)
        assertThat(dependent.dependencies).contains(cancelled.id)
        assertThat(planning.reserveNextStoryForDispatch(ReserveNextStoryForDispatchCommand(productId, PROCESS, "reserve-blocked"))).isNull()
        assertThat(planningQueries.findPlanningWorkItems(productId).single().type)
            .isEqualTo(PlanningWorkItemType.REPLAN_CANCELLED_DEPENDENCY)
    }

    private fun publishPlan() {
        planning.runProcessSession(productId)
        completeOnlyJob(selection())
        planning.runProcessSession(productId)
        runtime.reset()
        ai.dispatchPending()
        completeDispatchedJob(plan())
        planning.runProcessSession(productId)
    }

    private fun publishEpic(): EpicDetails {
        design.runProcessSession(productId)
        completeOnlyJob(mapper.createObjectNode().apply {
            put("outcome", "CREATE_EPIC")
            set<ObjectNode>("epic", mapper.createObjectNode().apply {
                put("title", "Aantoonbare voortgang")
                put("summary", "Stakeholders volgen waarde en bewijs in één rustig overzicht.")
                put("problem", "Stakeholders kunnen voortgang en bewijs niet vanuit één begrijpelijke plek volgen.")
                put("solution", "Bied een overzicht en bewijsdetail met duidelijke grenzen, zodat voortgang zonder technische kennis aantoonbaar wordt.")
                putArray("directionReferences").addObject().put("type", "PRODUCT_ASSIGNMENT").put("id", productId.value).put("version", 1)
                put("visibleBehaviorChange", true)
                put("uxDesign", "Een overzichtskaart opent een rustig bewijsdetail en behoudt een duidelijke terugroute.")
                putArray("acceptanceCriteria").add(CRITERION_OVERVIEW).add(CRITERION_EVIDENCE)
                put("slicabilityRationale", "Overzicht en bewijsdetail leveren elk zelfstandig gebruikerswaarde en vormen samen het complete pad.")
            })
            putArray("processedSignalIds")
            putArray("memoryChanges")
        })
        design.runProcessSession(productId)
        return designQueries.findEpics(EpicFilter(productId)).single()
    }

    private fun selection() = mapper.createObjectNode().apply {
        put("outcome", "PLAN")
        putArray("epicSelections").addObject().put("epicId", epic.id.value).put("expectedVersion", epic.version)
    }

    private fun plan() = mapper.createObjectNode().apply {
        put("outcome", "PUBLISH_PLAN")
        putArray("stories").apply {
            addObject().apply {
                put("draftKey", "overview"); put("type", "PRODUCT_STORY"); put("epicId", epic.id.value); put("epicVersion", epic.version)
                put("title", "Overzicht tonen"); put("summary", "De Stakeholder ziet de actuele verbetering en status.")
                put("content", "Bouw het scanbare productoverzicht met de actuele gebruikersverbetering, duidelijke status, lege en fouttoestand en een toegankelijke link naar bewijs.")
                putArray("acceptanceCriteria").add("Het overzicht toont titel, samenvatting en status in hoofd- en lege toestand.")
                put("uxDesign", "Toon één rustige overzichtskaart met toegankelijke status en bewijsactie.")
                putArray("dependencies"); putArray("coveredAcceptanceCriteria").add(CRITERION_OVERVIEW)
                put("priorityReason", "Het overzicht is de ingang voor de hele gebruikersflow.")
            }
            addObject().apply {
                put("draftKey", "evidence"); put("type", "PRODUCT_STORY"); put("epicId", epic.id.value); put("epicVersion", epic.version)
                put("title", "Bewijs openen"); put("summary", "De Stakeholder opent het opgeslagen bewijs vanuit het overzicht.")
                put("content", "Bouw het bewijsdetail met bron, laad- en fouttoestand, veilige terugroute en voldoende zelfstandige context voor uitvoering zonder epicquery.")
                putArray("acceptanceCriteria").add("De bewijsactie opent de juiste opgeslagen bron en toont een veilige fouttoestand.")
                putArray("dependencies").add("overview"); putArray("coveredAcceptanceCriteria").add(CRITERION_EVIDENCE)
                put("priorityReason", "Het bewijsdetail volgt nadat de overzichtsingang beschikbaar is.")
            }
        }
        putArray("todoOrder").add("overview").add("evidence")
        putArray("memoryChanges")
    }

    private fun completeOnlyJob(result: ObjectNode) {
        ai.dispatchPending()
        completeDispatchedJob(result)
    }

    private fun completeDispatchedJob(result: ObjectNode) {
        val job = runtime.onlyJob()
        runtime.results[job.id] = result
        runtime.jobs[job.id] = job.copy(status = "SUCCEEDED", phase = "COMPLETED", progressPercent = 100)
        ai.reconcileActive()
    }

    @TestConfiguration
    class Fakes {
        @Bean @Primary fun fakeRuntime() = FakeRuntime()
        @Bean @Primary fun fakeGit(): PublicGitRevisionResolver = object : PublicGitRevisionResolver {
            override fun resolveHead(publicGitUrl: String) = "a".repeat(40)
        }
    }

    companion object {
        private const val CRITERION_OVERVIEW = "De Stakeholder ziet de actuele verbetering en status op het productoverzicht."
        private const val CRITERION_EVIDENCE = "De bewijslink opent de opgeslagen bron zonder technische databasekennis."
        private val STAKEHOLDER = ActorReference(ActorType.STAKEHOLDER, "stakeholder@example.com")
        private val PROCESS = ActorReference(ActorType.PROCESS, "dispatcher-test")
    }
}
