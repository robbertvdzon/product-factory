package nl.vdzon.productfactory.planning

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import nl.vdzon.productfactory.ai.AiExecutionApplicationService
import nl.vdzon.productfactory.ai.FakeRuntime
import nl.vdzon.productfactory.ai.RuntimeArtifactView
import nl.vdzon.productfactory.api.design.*
import nl.vdzon.productfactory.api.foundation.PublicGitRevisionResolver
import nl.vdzon.productfactory.api.planning.*
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.design.mvp.ProductDesignMvpService
import nl.vdzon.productfactory.planning.mvp.ProductPlanningAiOrchestrator
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
import org.springframework.jdbc.core.JdbcTemplate
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
    private val planningOrchestrator: ProductPlanningAiOrchestrator,
    private val ai: AiExecutionApplicationService,
    private val jdbc: JdbcTemplate,
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
        val compactContext = runtime.requests.single().prompt.substringAfter("Compacte bevroren planningscontext:\n")
        val contextJson = mapper.readTree(compactContext)
        assertThat(contextJson.has("frozenContext")).isFalse()
        assertThat(contextJson.has("selection")).isFalse()
        assertThat(contextJson.path("selectedEpics")).hasSize(1)
        assertThat(contextJson.path("existingTodoStories")).isEmpty()
        assertThat(contextJson.path("inProgressStoryReferences")).isEmpty()
        completeDispatchedJob(plan())
        planning.runProcessSession(productId)

        val backlog = planningQueries.getBacklog(productId)
        assertThat(backlog.map { it.title }).containsExactly("Overzicht tonen", "Bewijs openen")
        assertThat(backlog.map { it.sequenceNumber }).containsExactly(1, 2)
        assertThat(backlog[1].dependencies).containsExactly(backlog[0].id)
        assertThat(backlog[0].uxArtifacts.map { it.name }).containsExactly("ux-main-desktop.png")
        assertThat(backlog[1].uxArtifacts).isEmpty()
        assertThat(designQueries.getEpic(epic.id).status).isEqualTo(EpicStatus.ACTIVE)
        assertThat(planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single().status).isEqualTo(ProcessSessionStatus.SUCCEEDED)
    }

    @Test
    fun `nieuwe planning gebruikt geen volgnummers van geannuleerde stories opnieuw`() {
        val legacyId = UUID.randomUUID().toString()
        val now = java.time.Instant.now()
        jdbc.update(
            """INSERT INTO pf_story(id,product_id,epic_id,epic_version,type,status,current_version,sequence_number,
                priority_reason,bug_link_confirmed,created_at,updated_at,cancellation_reason)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
            legacyId, productId.value, epic.id.value, epic.version, "PRODUCT_STORY", "CANCELLED", 1L, 1L,
            "Historische positie blijft gereserveerd.", true, now, now, "Stakeholder heeft deze story geannuleerd.",
        )
        jdbc.update(
            """INSERT INTO pf_story_version(story_id,version,title,summary,content,acceptance_criteria_json,ux_design,
                dependencies_json,source_references_json,created_at,ux_artifacts_json)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
            legacyId, 1L, "Geannuleerde story", "Deze story blijft alleen in de historie zichtbaar.",
            "Deze historische story is geannuleerd en mag haar eerder gebruikte volgnummer niet vrijgeven voor een nieuwe database-insert.",
            "[\"Deze story blijft geannuleerd.\"]", null, "[]", "[]", now, "[]",
        )

        publishPlan()

        val allStories = planningQueries.findStories(StoryFilter(productId))
        assertThat(allStories.single { it.id.value == legacyId }.sequenceNumber).isEqualTo(1)
        assertThat(planningQueries.getBacklog(productId).map { it.sequenceNumber }).containsExactly(2, 3)
    }

    @Test
    fun `geblokkeerde publicatie hergebruikt een reeds geslaagd AI plan`() {
        planning.runProcessSession(productId)
        completeOnlyJob(selection())
        planning.runProcessSession(productId)
        runtime.reset()
        ai.dispatchPending()
        completeDispatchedJob(plan())
        val before = planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single()
        jdbc.update(
            """UPDATE pf_planning_process_session
                SET status='BLOCKED',error_code='PLANNING_PUBLICATION_CONFLICT',blocked_reason=?
                WHERE id=?""".trimIndent(),
            "Het plan is gemaakt, maar kon nog niet worden opgeslagen.", before.id.value,
        )

        planning.runProcessSession(productId)

        val after = planningQueries.getProcessSession(before.id)
        assertThat(after.status).isEqualTo(ProcessSessionStatus.SUCCEEDED)
        assertThat(after.errorCode).isNull()
        assertThat(after.blockedReason).isNull()
        assertThat(after.aiTaskIds).containsExactlyElementsOf(before.aiTaskIds)
        assertThat(planningQueries.getBacklog(productId).map { it.title }).containsExactly("Overzicht tonen", "Bewijs openen")
    }

    @Test
    fun `planner responseschemas geven enum en const altijd een expliciet type`() {
        planning.runProcessSession(productId)
        ai.dispatchPending()
        assertTypedLiterals(runtime.requests.single().responseSchema!!)

        completeDispatchedJob(selection())
        planning.runProcessSession(productId)
        runtime.reset()
        ai.dispatchPending()

        val planSchema = runtime.requests.single().responseSchema!!
        assertTypedLiterals(planSchema)
        assertThat(planSchema.at("/properties/outcome/type").asText()).isEqualTo("string")
        assertThat(planSchema.at("/properties/stories/items/properties/type/type").asText()).isEqualTo("string")
        assertThat(planSchema.at("/properties/memoryChanges/items/properties/type/type").asText()).isEqualTo("string")
        assertThat(planSchema.at("/properties/refinementRequests/items/properties/reason/minLength").asInt()).isEqualTo(10)
        assertThat(planSchema.at("/properties/refinementRequests/items/properties/reason/maxLength").asInt()).isEqualTo(10_000)
    }

    @Test
    fun `planner stuurt epic zonder voldoende ontwerp terug met vrije reden en zonder stories`() {
        val refinementReason = "Het initiële vraagscherm en een concrete harvesting- en indexeringsroute ontbreken. " +
            "De ontbrekende ontwerp- en broninformatie moet concreet en uitvoerbaar worden vastgelegd. ".repeat(13).trim()
        assertThat(refinementReason.length).isBetween(1_001, 10_000)
        planning.runProcessSession(productId)
        completeOnlyJob(selection())
        planning.runProcessSession(productId)
        runtime.reset()
        ai.dispatchPending()
        completeDispatchedJob(mapper.createObjectNode().apply {
            put("outcome", "REQUEST_EPIC_REFINEMENT")
            putArray("stories")
            putArray("todoOrder")
            putArray("refinementRequests").addObject().apply {
                put("epicId", epic.id.value)
                put("reason", refinementReason)
            }
            putNull("stakeholderQuestion")
            putArray("memoryChanges")
        })

        planning.runProcessSession(productId)

        val refined = designQueries.getEpic(epic.id)
        assertThat(refined.status).isEqualTo(EpicStatus.NEEDS_REFINEMENT)
        assertThat(refined.refinementReason).isEqualTo(refinementReason)
        assertThat(planningQueries.findStories(StoryFilter(productId))).isEmpty()
        assertThat(planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single().status)
            .isEqualTo(ProcessSessionStatus.SUCCEEDED)
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
    fun `epic wordt pas ter verificatie aangeboden zodra alle epicstories zijn opgeleverd, niet per story`() {
        publishPlan()
        val first = planningQueries.getBacklog(productId).first()

        val firstReservation = planning.reserveNextStoryForDispatch(ReserveNextStoryForDispatchCommand(productId, PROCESS, "reserve-first"))!!
        planning.markStoryAsDispatched(MarkStoryAsDispatchedCommand(firstReservation.reservationId, "SF-1", firstReservation.story.version, PROCESS, "dispatched-first"))
        val dispatchedFirst = planningQueries.getStory(first.id)
        planning.markStoryAsDeveloped(MarkStoryAsDevelopedCommand(dispatchedFirst.id, "SF-1", "a".repeat(40), dispatchedFirst.version, PROCESS, "developed-first"))

        assertThat(pendingVerifyEpicEffects()).isEmpty()
        assertThat(designQueries.getEpic(epic.id).status).isEqualTo(EpicStatus.ACTIVE)

        val secondReservation = planning.reserveNextStoryForDispatch(ReserveNextStoryForDispatchCommand(productId, PROCESS, "reserve-second"))!!
        planning.markStoryAsDispatched(MarkStoryAsDispatchedCommand(secondReservation.reservationId, "SF-2", secondReservation.story.version, PROCESS, "dispatched-second"))
        val dispatchedSecond = planningQueries.getStory(secondReservation.story.id)
        planning.markStoryAsDeveloped(MarkStoryAsDevelopedCommand(dispatchedSecond.id, "SF-2", "b".repeat(40), dispatchedSecond.version, PROCESS, "developed-second"))

        assertThat(pendingVerifyEpicEffects()).containsExactly(epic.id.value)
        assertThat(designQueries.getEpic(epic.id).status).isEqualTo(EpicStatus.VERIFYING)
    }

    private fun pendingVerifyEpicEffects(): List<String> = jdbc.query(
        "SELECT payload_json FROM pf_planning_quality_effect WHERE effect_type='VERIFY_EPIC'",
        { rs, _ -> mapper.readTree(rs.getString(1)).get("epicId").asText() },
    )

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
            (stories[1] as ObjectNode).putArray("coveredAcceptanceCriteriaIndexes")
        }
        completeDispatchedJob(invalid)
        planning.runProcessSession(productId)

        assertThat(planningQueries.findStories(StoryFilter(productId))).isEmpty()
        assertThat(designQueries.getEpic(epic.id).status).isEqualTo(EpicStatus.IN_PLANNING)
        assertThat(planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single().status).isEqualTo(ProcessSessionStatus.BLOCKED)

        // Retry na een inhoudelijk afgekeurd antwoord moet een verse AI-poging doen i.p.v.
        // hetzelfde afgekeurde antwoord opnieuw te verwerken (dat zou deterministisch weer
        // exact dezelfde blokkade opleveren, zonder ooit een geldig plan te kunnen krijgen).
        planning.runProcessSession(productId)
        runtime.reset()
        ai.dispatchPending()
        completeDispatchedJob(plan())
        planning.runProcessSession(productId)

        assertThat(planningQueries.findStories(StoryFilter(productId))).hasSize(2)
        assertThat(designQueries.getEpic(epic.id).status).isEqualTo(EpicStatus.ACTIVE)
        assertThat(planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single().status).isEqualTo(ProcessSessionStatus.SUCCEEDED)
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
    fun `terminale plannertaak herstelt legacy blokkade en probeert begrensd automatisch opnieuw`() {
        planning.runProcessSession(productId)
        completeOnlyJob(selection())
        planningOrchestrator.resumeReady()
        val sessionId = planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single().id
        assertThat(designQueries.getEpic(epic.id).status).isEqualTo(EpicStatus.IN_PLANNING)

        runtime.reset()
        ai.dispatchPending()
        val failed = runtime.onlyJob()
        runtime.jobs[failed.id] = failed.copy(status = "FAILED", phase = "FAILED", progressPercent = 100)
        ai.reconcileActive()
        jdbc.update(
            "UPDATE pf_planning_process_session SET status='BLOCKED',error_code='PLANNING_RESULT_INVALID' WHERE id=?",
            sessionId.value,
        )
        planningOrchestrator.resumeReady()

        var session = planningQueries.getProcessSession(sessionId)
        assertThat(session.status).isEqualTo(ProcessSessionStatus.WAITING_FOR_AI)
        assertThat(session.aiTaskIds).hasSize(3)
        assertThat(designQueries.getEpic(epic.id).status).isEqualTo(EpicStatus.IN_PLANNING)

        runtime.reset()
        ai.dispatchPending()
        val secondFailure = runtime.onlyJob()
        runtime.jobs[secondFailure.id] = secondFailure.copy(status = "FAILED", phase = "FAILED", progressPercent = 100)
        ai.reconcileActive()
        planningOrchestrator.resumeReady()

        session = planningQueries.getProcessSession(sessionId)
        assertThat(session.status).isEqualTo(ProcessSessionStatus.WAITING_FOR_AI)
        assertThat(session.aiTaskIds).hasSize(4)

        runtime.reset()
        ai.dispatchPending()
        val finalFailure = runtime.onlyJob()
        runtime.jobs[finalFailure.id] = finalFailure.copy(status = "FAILED", phase = "FAILED", progressPercent = 100)
        ai.reconcileActive()
        planningOrchestrator.resumeReady()
        planningOrchestrator.resumeReady()

        session = planningQueries.getProcessSession(sessionId)
        assertThat(session.status).isEqualTo(ProcessSessionStatus.BLOCKED)
        assertThat(session.errorCode).isEqualTo("PLANNING_RESULT_INVALID")
        assertThat(session.aiTaskIds).hasSize(4)

        jdbc.update(
            "UPDATE pf_ai_task SET status='SUCCEEDED',prompt_template_version=1 WHERE id=?",
            session.aiTaskIds.last().value,
        )
        planningOrchestrator.resumeReady()
        session = planningQueries.getProcessSession(sessionId)
        assertThat(session.status).isEqualTo(ProcessSessionStatus.WAITING_FOR_AI)
        assertThat(session.aiTaskIds).hasSize(5)

        assertThat(planningQueries.findProcessSessions(ProcessSessionFilter(productId))).hasSize(1)
    }

    @Test
    fun `geannuleerde dependency blijft onvervuld en maakt gericht herplanningswerk`() {
        val cancellationReason = "Externe levering is geannuleerd. " +
            "De volledige reden blijft beschikbaar voor vervolgplanning en controle. ".repeat(16).trim()
        assertThat(cancellationReason.length).isBetween(1_001, 10_000)
        publishPlan()
        val reservation = planning.reserveNextStoryForDispatch(ReserveNextStoryForDispatchCommand(productId, PROCESS, "reserve-dependency"))!!
        planning.markStoryAsDispatched(MarkStoryAsDispatchedCommand(
            reservation.reservationId, "SF-dependency", reservation.story.version, PROCESS, "dispatch-dependency",
        ))
        val inProgress = planningQueries.getStory(reservation.story.id)

        planning.markStoryAsCancelled(MarkStoryAsCancelledCommand(
            inProgress.id, "SF-dependency", cancellationReason, inProgress.version, PROCESS, "cancel-dependency",
        ))

        val stories = planningQueries.findStories(StoryFilter(productId))
        val cancelled = stories.single { it.id == inProgress.id }
        val dependent = stories.single { it.id != inProgress.id }
        assertThat(cancelled.status).isEqualTo(StoryStatus.CANCELLED)
        assertThat(cancelled.cancellationReason).isEqualTo(cancellationReason)
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
                putArray("uxArtifactChanges").apply {
                    listOf(
                        "ux-main-desktop.png" to "start",
                        "ux-main-mobile.png" to "start",
                        "ux-empty-desktop.png" to "empty",
                        "ux-empty-mobile.png" to "empty",
                    ).forEach { (name, screenKey) ->
                        addObject().apply {
                            put("operation", "ADD"); putNull("existingArtifactName"); put("outputArtifactName", name)
                            put("screenKey", screenKey); put("reason", "Dit bestand maakt de volledige UX-route controleerbaar.")
                        }
                    }
                }
                putArray("uxScreens").apply {
                    addObject().apply {
                        put("screenKey", "start"); put("state", "INITIAL"); put("purpose", "Toon de ingang naar de actuele productvoortgang.")
                        putArray("artifacts").apply {
                            addObject().put("viewport", "DESKTOP").put("artifactName", "ux-main-desktop.png")
                            addObject().put("viewport", "MOBILE").put("artifactName", "ux-main-mobile.png")
                        }
                    }
                    addObject().apply {
                        put("screenKey", "empty"); put("state", "EMPTY"); put("purpose", "Toon de toestand zonder beschikbaar bewijs.")
                        putArray("artifacts").apply {
                            addObject().put("viewport", "DESKTOP").put("artifactName", "ux-empty-desktop.png")
                            addObject().put("viewport", "MOBILE").put("artifactName", "ux-empty-mobile.png")
                        }
                    }
                }
                putArray("acceptanceCriteria").add(CRITERION_OVERVIEW).add(CRITERION_EVIDENCE)
                put("slicabilityRationale", "Overzicht en bewijsdetail leveren elk zelfstandig gebruikerswaarde en vormen samen het complete pad.")
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
                putNull("bugId"); putNull("bugVersion")
                put("title", "Overzicht tonen"); put("summary", "De Stakeholder ziet de actuele verbetering en status.")
                put("content", "Bouw het scanbare productoverzicht met de actuele gebruikersverbetering, duidelijke status, lege en fouttoestand en een toegankelijke link naar bewijs.")
                putArray("acceptanceCriteria").add("Het overzicht toont titel, samenvatting en status in hoofd- en lege toestand.")
                put("uxDesign", "Toon één rustige overzichtskaart met toegankelijke status en bewijsactie.")
                putArray("uxArtifactNames").add("ux-main-desktop.png")
                putArray("dependencies"); putArray("coveredAcceptanceCriteriaIndexes").add(0)
                put("priorityReason", "Het overzicht is de ingang voor de hele gebruikersflow.")
            }
            addObject().apply {
                put("draftKey", "evidence"); put("type", "PRODUCT_STORY"); put("epicId", epic.id.value); put("epicVersion", epic.version)
                putNull("bugId"); putNull("bugVersion")
                put("title", "Bewijs openen"); put("summary", "De Stakeholder opent het opgeslagen bewijs vanuit het overzicht.")
                put("content", "Bouw het bewijsdetail met bron, laad- en fouttoestand, veilige terugroute en voldoende zelfstandige context voor uitvoering zonder epicquery.")
                putArray("acceptanceCriteria").add("De bewijsactie opent de juiste opgeslagen bron en toont een veilige fouttoestand.")
                putNull("uxDesign"); putArray("uxArtifactNames")
                putArray("dependencies").add("overview"); putArray("coveredAcceptanceCriteriaIndexes").add(1)
                put("priorityReason", "Het bewijsdetail volgt nadat de overzichtsingang beschikbaar is.")
            }
        }
        putArray("todoOrder").add("overview").add("evidence")
        putArray("refinementRequests")
        putNull("stakeholderQuestion")
        putArray("memoryChanges")
    }

    private fun completeOnlyJob(result: ObjectNode) {
        ai.dispatchPending()
        completeDispatchedJob(result)
    }

    private fun assertTypedLiterals(schema: JsonNode) {
        if (schema.has("enum") || schema.has("const")) {
            assertThat(schema.has("type")).isTrue()
        }
        schema.path("properties").forEach(::assertTypedLiterals)
        schema.path("items").takeUnless(JsonNode::isMissingNode)?.let(::assertTypedLiterals)
    }

    private fun completeDispatchedJob(result: ObjectNode) {
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
