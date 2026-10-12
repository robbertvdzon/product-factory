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
            productId, "Stakeholders", "Lever waarde in kleine zelfstandige stappen",
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
    fun `bugfixstory levert net als een gewone story pas verificatie op zodra de hele epic klaar is`() {
        publishPlan()
        val bugfixId = StoryId(UUID.randomUUID().toString())
        insertTodoBugfixStory(bugfixId)

        val firstReservation = planning.reserveNextStoryForDispatch(ReserveNextStoryForDispatchCommand(productId, PROCESS, "reserve-first"))!!
        planning.markStoryAsDispatched(MarkStoryAsDispatchedCommand(firstReservation.reservationId, "SF-1", firstReservation.story.version, PROCESS, "dispatched-first"))
        val dispatchedFirst = planningQueries.getStory(firstReservation.story.id)
        planning.markStoryAsDeveloped(MarkStoryAsDevelopedCommand(dispatchedFirst.id, "SF-1", "a".repeat(40), dispatchedFirst.version, PROCESS, "developed-first"))

        val secondReservation = planning.reserveNextStoryForDispatch(ReserveNextStoryForDispatchCommand(productId, PROCESS, "reserve-second"))!!
        planning.markStoryAsDispatched(MarkStoryAsDispatchedCommand(secondReservation.reservationId, "SF-2", secondReservation.story.version, PROCESS, "dispatched-second"))
        val dispatchedSecond = planningQueries.getStory(secondReservation.story.id)
        planning.markStoryAsDeveloped(MarkStoryAsDevelopedCommand(dispatchedSecond.id, "SF-2", "b".repeat(40), dispatchedSecond.version, PROCESS, "developed-second"))

        // Beide gewone stories zijn klaar, maar de bugfixstory nog niet: nog geen epicverificatie.
        assertThat(pendingVerifyEpicEffects()).isEmpty()
        assertThat(jdbc.query("SELECT effect_type FROM pf_planning_quality_effect", { rs, _ -> rs.getString(1) })).doesNotContain("RETEST_BUGFIX")

        val bugfixReservation = planning.reserveNextStoryForDispatch(ReserveNextStoryForDispatchCommand(productId, PROCESS, "reserve-bugfix"))!!
        assertThat(bugfixReservation.story.id).isEqualTo(bugfixId)
        planning.markStoryAsDispatched(MarkStoryAsDispatchedCommand(bugfixReservation.reservationId, "SF-3", bugfixReservation.story.version, PROCESS, "dispatched-bugfix"))
        val dispatchedBugfix = planningQueries.getStory(bugfixId)
        planning.markStoryAsDeveloped(MarkStoryAsDevelopedCommand(dispatchedBugfix.id, "SF-3", "c".repeat(40), dispatchedBugfix.version, PROCESS, "developed-bugfix"))

        // Nu alles klaar is (inclusief de bugfixstory), volgt in één keer de epicverificatie —
        // geen losse retest van de bugfixstory zelf.
        assertThat(pendingVerifyEpicEffects()).containsExactly(epic.id.value)
        assertThat(jdbc.query("SELECT effect_type FROM pf_planning_quality_effect", { rs, _ -> rs.getString(1) })).doesNotContain("RETEST_BUGFIX")
        assertThat(designQueries.getEpic(epic.id).status).isEqualTo(EpicStatus.VERIFYING)
    }

    private fun insertTodoBugfixStory(id: StoryId) {
        val now = java.time.Instant.now()
        val sequence = (jdbc.queryForObject("SELECT COALESCE(MAX(sequence_number),0)+1 FROM pf_story WHERE product_id=?", Long::class.java, productId.value) ?: 1L)
        jdbc.update(
            """INSERT INTO pf_story(id,product_id,epic_id,epic_version,type,status,current_version,sequence_number,
                priority_reason,bug_link_confirmed,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
            id.value, productId.value, epic.id.value, epic.version, "BUGFIX", "TODO", 1L, sequence,
            "Gericht bugbewijs.", true, now, now,
        )
        jdbc.update(
            """INSERT INTO pf_story_version(story_id,version,title,summary,content,acceptance_criteria_json,ux_design,
                dependencies_json,source_references_json,created_at,ux_artifacts_json)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
            id.value, 1L, "Bewijsroute herstellen", "De Stakeholder gebruikt de herstelde bewijsroute.",
            "Herstel de gemelde regressie en behoud de bestaande acceptatiecriteria.",
            "[\"De bewijsroute werkt weer zoals bedoeld.\"]", null, "[]", "[]", now, "[]",
        )
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
    fun `afgekeurd UX resultaat herstelt automatisch met concrete feedback en dezelfde bevroren context`() {
        planning.runProcessSession(productId)
        completeOnlyJob(selection())
        planningOrchestrator.resumeReady()
        val sessionId = planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single().id
        runtime.reset()
        ai.dispatchPending()
        val initialRequest = runtime.requests.single()
        val frozenContext = initialRequest.prompt.substringAfter("Compacte bevroren planningscontext:\n")
        assertThat(mapper.readTree(frozenContext).path("selectedEpics").single().path("uxDesign").asText()).isEqualTo(epic.uxDesign)
        assertThat(initialRequest.prompt).contains("stories[].uxDesign", "stories[].uxArtifactNames", "selectedEpics[].uxArtifacts")
        completeDispatchedJob(plan().also { result -> result.path("stories").forEach { (it as ObjectNode).putNull("uxDesign") } })

        planningOrchestrator.resumeReady()
        assertThat(planningQueries.getProcessSession(sessionId).blockedReason).isEqualTo("Storyset mist het bevroren UX-ontwerp.")
        assertThat(planningQueries.findStories(StoryFilter(productId))).isEmpty()
        assertThat(designQueries.getEpic(epic.id).status).isEqualTo(EpicStatus.IN_PLANNING)
        planningOrchestrator.resumeReady()
        planningOrchestrator.resumeReady()

        runtime.reset()
        ai.dispatchPending()
        val retry = runtime.requests.single()
        assertThat(retry.prompt).contains("\"validationError\":\"Storyset mist het bevroren UX-ontwerp.\"")
        assertThat(retry.prompt.substringAfter("Compacte bevroren planningscontext:\n")).isEqualTo(frozenContext)
        assertThat(retry.repositorySnapshot).isEqualTo(initialRequest.repositorySnapshot)
        completeDispatchedJob(plan().also { result -> (result.path("stories")[0] as ObjectNode).putArray("uxArtifactNames") })
        planningOrchestrator.resumeReady()
        assertThat(planningQueries.getProcessSession(sessionId).blockedReason).isEqualTo("Visuele story mist een expliciete koppeling met een UX-model.")
        assertThat(planningQueries.findStories(StoryFilter(productId))).isEmpty()
        planningOrchestrator.resumeReady()

        runtime.reset()
        ai.dispatchPending()
        val finalRetry = runtime.requests.single()
        assertThat(finalRetry.prompt).contains("\"validationError\":\"Visuele story mist een expliciete koppeling met een UX-model.\"")
        assertThat(finalRetry.prompt.substringAfter("Compacte bevroren planningscontext:\n")).isEqualTo(frozenContext)
        completeDispatchedJob(plan())
        planningOrchestrator.resumeReady()
        planningOrchestrator.resumeReady()

        val completed = planningQueries.getProcessSession(sessionId)
        assertThat(completed.status).isEqualTo(ProcessSessionStatus.SUCCEEDED)
        assertThat(completed.aiTaskIds).hasSize(4)
        assertThat(completed.errorCode).isNull()
        assertThat(planningQueries.findProcessSessions(ProcessSessionFilter(productId))).hasSize(1)
        assertThat(planningQueries.getBacklog(productId)).hasSize(2)
        assertThat(planningQueries.getBacklog(productId).first().uxArtifacts.map { it.name }).containsExactly("ux-main-desktop.png")
        assertThat(designQueries.getEpic(epic.id).status).isEqualTo(EpicStatus.ACTIVE)
    }

    @Test
    fun `inhoudelijk ongeldige AI resultaten stoppen na drie automatische pogingen zonder gedeeltelijke publicatie`() {
        planning.runProcessSession(productId)
        completeOnlyJob(selection())
        planningOrchestrator.resumeReady()
        val sessionId = planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single().id

        repeat(3) {
            runtime.reset()
            ai.dispatchPending()
            completeDispatchedJob(plan().also { result -> result.path("stories").forEach { (it as ObjectNode).putNull("uxDesign") } })
            planningOrchestrator.resumeReady()
            assertThat(planningQueries.getProcessSession(sessionId).status).isEqualTo(ProcessSessionStatus.BLOCKED)
            planningOrchestrator.resumeReady()
        }
        repeat(3) { planningOrchestrator.resumeReady() }

        val blocked = planningQueries.getProcessSession(sessionId)
        assertThat(blocked.status).isEqualTo(ProcessSessionStatus.BLOCKED)
        assertThat(blocked.errorCode).isEqualTo("PLANNING_RESULT_INVALID")
        assertThat(blocked.blockedReason).isEqualTo("Storyset mist het bevroren UX-ontwerp.")
        assertThat(blocked.aiTaskIds).hasSize(4)
        assertThat(planningQueries.findStories(StoryFilter(productId))).isEmpty()
        assertThat(designQueries.getEpic(epic.id).status).isEqualTo(EpicStatus.IN_PLANNING)
    }

    @Test
    fun `annulering voorkomt automatisch promptupgradeherstel en publicatie van een later geslaagd resultaat`() {
        planning.runProcessSession(productId)
        completeOnlyJob(selection())
        planningOrchestrator.resumeReady()
        runtime.reset()
        ai.dispatchPending()
        completeDispatchedJob(plan())
        val before = planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single()
        jdbc.update("UPDATE pf_planning_process_session SET status='BLOCKED',error_code='PLANNING_RESULT_INVALID' WHERE id=?", before.id.value)
        jdbc.update("UPDATE pf_ai_task SET status='CANCELLED',prompt_template_version=1 WHERE id=?", before.aiTaskIds.last().value)
        planningOrchestrator.resumeReady()
        assertThat(planningQueries.getProcessSession(before.id).aiTaskIds).containsExactlyElementsOf(before.aiTaskIds)

        jdbc.update("UPDATE pf_ai_task SET status='SUCCEEDED',cancel_requested=TRUE WHERE id=?", before.aiTaskIds.last().value)
        planningOrchestrator.resumeReady()
        assertThat(planningQueries.getProcessSession(before.id).aiTaskIds).containsExactlyElementsOf(before.aiTaskIds)

        jdbc.update("UPDATE pf_planning_process_session SET status='WAITING_FOR_AI' WHERE id=?", before.id.value)
        planningOrchestrator.resumeReady()
        planningOrchestrator.resumeReady()
        val after = planningQueries.getProcessSession(before.id)
        assertThat(after.status).isEqualTo(ProcessSessionStatus.BLOCKED)
        assertThat(after.blockedReason).contains("geannuleerd")
        assertThat(after.aiTaskIds).containsExactlyElementsOf(before.aiTaskIds)
        assertThat(planningQueries.findStories(StoryFilter(productId))).isEmpty()
    }

    @Test
    fun `automatisch herstel respecteert productpauze maar verwerkt een reeds gereed resultaat`() {
        planning.runProcessSession(productId)
        completeOnlyJob(selection())
        planningOrchestrator.resumeReady()
        runtime.reset()
        ai.dispatchPending()
        completeDispatchedJob(plan().also { result -> result.path("stories").forEach { (it as ObjectNode).putNull("uxDesign") } })
        planningOrchestrator.resumeReady()
        val blocked = planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single()

        jdbc.update("UPDATE pf_product SET dispatching_enabled=FALSE WHERE product_id=?", productId.value)
        planningOrchestrator.resumeReady()
        assertThat(planningQueries.getProcessSession(blocked.id).aiTaskIds).containsExactlyElementsOf(blocked.aiTaskIds)
        jdbc.update("UPDATE pf_product SET dispatching_enabled=TRUE,status='INACTIVE' WHERE product_id=?", productId.value)
        planningOrchestrator.resumeReady()
        assertThat(planningQueries.getProcessSession(blocked.id).aiTaskIds).containsExactlyElementsOf(blocked.aiTaskIds)

        jdbc.update("UPDATE pf_product SET status='ACTIVE' WHERE product_id=?", productId.value)
        planningOrchestrator.resumeReady()
        runtime.reset()
        ai.dispatchPending()
        completeDispatchedJob(plan())
        jdbc.update("UPDATE pf_product SET dispatching_enabled=FALSE WHERE product_id=?", productId.value)
        planningOrchestrator.resumeReady()
        assertThat(planningQueries.getProcessSession(blocked.id).status).isEqualTo(ProcessSessionStatus.SUCCEEDED)
        assertThat(planningQueries.findStories(StoryFilter(productId))).hasSize(2)
    }

    @Test
    fun `oude prompt start geen automatische herhaallus voor een publicatieconflict`() {
        planning.runProcessSession(productId)
        completeOnlyJob(selection())
        planningOrchestrator.resumeReady()
        runtime.reset()
        ai.dispatchPending()
        completeDispatchedJob(plan())
        val blocked = planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single()
        jdbc.update("UPDATE pf_ai_task SET prompt_template_version=1 WHERE id=?", blocked.aiTaskIds.last().value)
        jdbc.update("UPDATE pf_planning_process_session SET status='BLOCKED',error_code='PLANNING_PUBLICATION_CONFLICT' WHERE id=?", blocked.id.value)
        repeat(3) { planningOrchestrator.resumeReady() }

        val after = planningQueries.getProcessSession(blocked.id)
        assertThat(after.status).isEqualTo(ProcessSessionStatus.BLOCKED)
        assertThat(after.aiTaskIds).containsExactlyElementsOf(blocked.aiTaskIds)
        assertThat(planningQueries.findStories(StoryFilter(productId))).isEmpty()
    }

    @Test
    fun `verouderde blokkadestatus start geen tweede AI poging naast een lopende taak`() {
        val workItem = planning.requestManualReplan(RequestManualReplanCommand(
            productId, "Controleer het bestaande plan opnieuw.", emptyList(), STAKEHOLDER, "stale-blocked-work",
        ))
        planning.runProcessSession(productId)
        completeOnlyJob(selection())
        planningOrchestrator.resumeReady()
        val before = planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single()
        jdbc.update("UPDATE pf_planning_process_session SET status='BLOCKED',error_code='PLANNING_RESULT_INVALID' WHERE id=?", before.id.value)
        jdbc.update("UPDATE pf_planning_work_item SET status='BLOCKED',error_code='PLANNING_RESULT_INVALID' WHERE id=?", workItem.value)

        planning.runProcessSession(productId)
        assertThat(planningQueries.findPlanningWorkItems(productId).single().status).isEqualTo(WorkItemStatus.IN_PROGRESS)
        runtime.reset()
        ai.dispatchPending()
        planning.runProcessSession(productId)

        assertThat(planningQueries.getProcessSession(before.id).aiTaskIds).containsExactlyElementsOf(before.aiTaskIds)
        assertThat(runtime.requests).hasSize(1)
        assertThat(jdbc.queryForObject("SELECT call_claimed_until FROM pf_planning_process_session WHERE id=?", java.sql.Timestamp::class.java, before.id.value)).isNull()
        completeDispatchedJob(plan())
        planningOrchestrator.resumeReady()
        assertThat(planningQueries.getProcessSession(before.id).status).isEqualTo(ProcessSessionStatus.SUCCEEDED)
        assertThat(planningQueries.findStories(StoryFilter(productId))).hasSize(2)
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
    fun `gericht fixturegat bewaart vier opgeleverde stories als context en publiceert alleen het restant zonder UX`() {
        val completed = publishCompletedEpicWithFourStories()
        val gap = requestFixtureGap()
        runtime.reset()
        planning.runProcessSession(productId)
        ai.dispatchPending()
        val selectionContext = mapper.readTree(runtime.requests.single().prompt.substringAfter("\\n"))
        assertThat(selectionContext.path("completedStories")).hasSize(4)
        assertThat(selectionContext.path("epicPlanningScopes").single().path("mode").asText()).isEqualTo("TARGETED")
        completed.forEach { story ->
            val frozen = selectionContext.path("completedStories").single { it.path("id").asText() == story.id.value }
            assertThat(frozen.path("title").asText()).isEqualTo(story.title)
            assertThat(frozen.path("content").asText()).isEqualTo(story.content)
            assertThat(frozen.path("acceptanceCriteria").map(JsonNode::asText)).containsExactlyElementsOf(story.acceptanceCriteria)
            assertThat(frozen.path("deliveredCommitSha").asText()).isEqualTo(story.deliveredCommitSha)
        }
        completeDispatchedJob(selection())
        planning.runProcessSession(productId)
        runtime.reset()
        ai.dispatchPending()
        val planningContext = mapper.readTree(runtime.requests.single().prompt.substringAfter("Compacte bevroren planningscontext:\n"))
        assertThat(planningContext.path("completedStories")).isEqualTo(selectionContext.path("completedStories"))
        assertThat(planningContext.path("epicPlanningScopes").single().path("mode").asText()).isEqualTo("TARGETED")
        val activeSession = planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single { it.status == ProcessSessionStatus.WAITING_FOR_AI }
        val inputs = mapper.readTree(jdbc.queryForObject("SELECT inputs_json FROM pf_planning_process_session WHERE id=?", String::class.java, activeSession.id.value))
        completed.forEach { story ->
            assertThat(inputs.any { it.path("type").asText() == "STORY" && it.path("id").asText() == story.id.value && it.path("version").asLong() == story.version }).isTrue()
        }
        val fixturePlan = fixtureGapPlan(gap)
        (fixturePlan.path("gapResolutions")[0] as ObjectNode).put("reason", "Alleen de ontbrekende fixture is nodig. ".repeat(60).take(2000))
        completeDispatchedJob(fixturePlan)
        planning.runProcessSession(productId)

        val stories = planningQueries.findStories(StoryFilter(productId))
        assertThat(stories.filter { it.status == StoryStatus.DONE }).containsExactlyInAnyOrderElementsOf(completed)
        assertThat(stories.filter { it.status == StoryStatus.TODO }.map { it.title }).containsExactly("Fixture voor lege vergaderagenda")
        assertThat(stories.single { it.status == StoryStatus.TODO }.uxDesign).isNull()
        assertThat(planningQueries.findPlanningWorkItems(productId).single().status).isEqualTo(WorkItemStatus.DONE)
        assertThat(planningQueries.findPlanningWorkItems(productId).single().resultSummary).contains("Gericht dekkingsgat", "Nieuwe stories")
        assertThat(planningQueries.findPlanningWorkItems(productId).single().resultSummary!!.length).isLessThanOrEqualTo(2000)
    }

    @Test
    fun `reeds gedekt gapwerk eindigt zonder nieuwe stories met expliciete bronnen en nieuwe verificatie`() {
        val completed = publishCompletedEpicWithFourStories()
        val gap = requestFixtureGap()
        startGapPlanning()
        completeDispatchedJob(coveredGapPlan(gap, completed.first().id))
        planning.runProcessSession(productId)

        assertThat(planningQueries.findStories(StoryFilter(productId))).containsExactlyInAnyOrderElementsOf(completed)
        val work = planningQueries.findPlanningWorkItems(productId).single()
        assertThat(work.status).isEqualTo(WorkItemStatus.DONE)
        assertThat(work.resultSummary).contains(completed.first().id.value, "Bestaande dekking")
        assertThat(planningQueries.findProcessSessions(ProcessSessionFilter(productId))).allSatisfy { assertThat(it.status).isEqualTo(ProcessSessionStatus.SUCCEEDED) }
        assertThat(designQueries.getEpic(epic.id).status).isEqualTo(EpicStatus.VERIFYING)
        assertThat(pendingVerifyEpicEffects()).containsExactly(epic.id.value)
    }

    @Test
    fun `gericht gapwerk weigert extra volledige epicstories buiten de opgegeven gapresolutie`() {
        val completed = publishCompletedEpicWithFourStories()
        val gap = requestFixtureGap()
        startGapPlanning()
        val result = fixtureGapPlan(gap)
        (result.path("stories") as com.fasterxml.jackson.databind.node.ArrayNode).add(plan().path("stories")[0])
        (result.path("todoOrder") as com.fasterxml.jackson.databind.node.ArrayNode).add("overview")
        completeDispatchedJob(result)
        planning.runProcessSession(productId)

        assertThat(planningQueries.findStories(StoryFilter(productId))).containsExactlyInAnyOrderElementsOf(completed)
        assertThat(planningQueries.findPlanningWorkItems(productId).single().status).isEqualTo(WorkItemStatus.BLOCKED)
        assertThat(planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single { it.status == ProcessSessionStatus.BLOCKED }.blockedReason)
            .contains("buiten het expliciet afgehandelde dekkingsgat")
    }

    @Test
    fun `gapwerk mag niet met een leeg plan zonder bestaande dekking verdwijnen`() {
        val completed = publishCompletedEpicWithFourStories()
        val gap = requestFixtureGap()
        startGapPlanning()
        val result = coveredGapPlan(gap, completed.first().id)
        (result.path("gapResolutions")[0] as ObjectNode).putArray("coveredByStoryIds")
        completeDispatchedJob(result)
        planning.runProcessSession(productId)

        assertThat(planningQueries.findStories(StoryFilter(productId))).containsExactlyInAnyOrderElementsOf(completed)
        assertThat(planningQueries.findPlanningWorkItems(productId).single().status).isEqualTo(WorkItemStatus.BLOCKED)
        assertThat(planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single { it.status == ProcessSessionStatus.BLOCKED }.blockedReason)
            .contains("zonder nieuwe story of bestaande dekking")
    }

    @Test
    fun `no change gapresolutie weigert dekking die na de momentopname is geannuleerd`() {
        val completed = publishCompletedEpicWithFourStories()
        val gap = requestFixtureGap()
        startGapPlanning()
        completeDispatchedJob(coveredGapPlan(gap, completed.first().id))
        jdbc.update("UPDATE pf_story SET status='CANCELLED' WHERE id=?", completed.first().id.value)
        planning.runProcessSession(productId)

        assertThat(planningQueries.getBacklog(productId)).isEmpty()
        assertThat(planningQueries.findPlanningWorkItems(productId).single().status).isEqualTo(WorkItemStatus.BLOCKED)
        assertThat(planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single { it.status == ProcessSessionStatus.BLOCKED }.errorCode)
            .isEqualTo("PLANNING_VERSION_CONFLICT")
        assertThat(pendingVerifyEpicEffects()).isEmpty()
    }

    @Test
    fun `oude gerichte context zonder DONE bronmateriaal blokkeert zonder automatische herplanning`() {
        val completed = publishCompletedEpicWithFourStories()
        val gap = requestFixtureGap()
        startGapPlanning()
        val before = planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single { it.status == ProcessSessionStatus.WAITING_FOR_AI }
        val snapshot = mapper.readTree(jdbc.queryForObject("SELECT snapshot_json FROM pf_planning_process_session WHERE id=?", String::class.java, before.id.value)) as ObjectNode
        snapshot.remove("completedStories"); snapshot.remove("epicPlanningScopes")
        jdbc.update("UPDATE pf_planning_process_session SET snapshot_json=? WHERE id=?", mapper.writeValueAsString(snapshot), before.id.value)
        jdbc.update("UPDATE pf_ai_task SET prompt_template_version=6 WHERE id=?", before.aiTaskIds.last().value)
        completeDispatchedJob(fixtureGapPlan(gap))
        planning.runProcessSession(productId)
        repeat(3) { planningOrchestrator.resumeReady() }

        val blocked = planningQueries.getProcessSession(before.id)
        assertThat(blocked.status).isEqualTo(ProcessSessionStatus.BLOCKED)
        assertThat(blocked.errorCode).isEqualTo("PLANNING_VERSION_CONFLICT")
        assertThat(blocked.blockedReason).contains("nieuwe planningssessie")
        assertThat(blocked.aiTaskIds).containsExactlyElementsOf(before.aiTaskIds)
        assertThat(planningQueries.findStories(StoryFilter(productId))).containsExactlyInAnyOrderElementsOf(completed)
    }

    @Test
    fun `draftkey mag bestaande TODO story niet aliasen of haar volgnummer wijzigen`() {
        publishPlan()
        val existing = planningQueries.getBacklog(productId)
        epic = designQueries.getEpic(epic.id)
        planning.requestEpicReprioritization(RequestEpicReprioritizationCommand(
            productId, epic.id, "Behoud de bestaande stories en bepaal hun volgorde.", 50, STAKEHOLDER, "alias-existing-story",
        ))
        startGapPlanning()
        val result = plan()
        (result.path("stories")[0] as ObjectNode).put("draftKey", existing.first().id.value)
        completeDispatchedJob(result)
        planning.runProcessSession(productId)

        assertThat(planningQueries.getBacklog(productId)).containsExactlyElementsOf(existing)
        assertThat(planningQueries.findProcessSessions(ProcessSessionFilter(productId)).single { it.status == ProcessSessionStatus.BLOCKED }.blockedReason)
            .contains("bestaande story-id")
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

    private fun publishPlan(result: ObjectNode = plan()) {
        planning.runProcessSession(productId)
        completeOnlyJob(selection())
        planning.runProcessSession(productId)
        runtime.reset()
        ai.dispatchPending()
        completeDispatchedJob(result)
        planning.runProcessSession(productId)
    }

    private fun publishCompletedEpicWithFourStories(): List<StoryDetails> {
        val result = plan()
        val stories = result.path("stories") as com.fasterxml.jackson.databind.node.ArrayNode
        listOf("workflow" to "Vergaderacties uitvoeren", "privacy" to "Vergadergegevens afschermen").forEach { (key, title) ->
            stories.add((stories[0] as ObjectNode).deepCopy().apply {
                put("draftKey", key); put("title", title); putNull("uxDesign"); putArray("uxArtifactNames")
            })
            (result.path("todoOrder") as com.fasterxml.jackson.databind.node.ArrayNode).add(key)
        }
        publishPlan(result)
        jdbc.update("UPDATE pf_story SET status='DONE',delivered_commit_sha=? WHERE product_id=?", "d".repeat(40), productId.value)
        epic = designQueries.getEpic(epic.id)
        return planningQueries.findStories(StoryFilter(productId))
    }

    private fun requestFixtureGap() = planning.requestEpicGapPlanning(RequestEpicGapPlanningCommand(
        productId, epic.id, epic.version, VerificationId(UUID.randomUUID().toString()),
        listOf("Een vaste acceptatiefixture voor de lege vergaderagenda ontbreekt; het productgedrag is al opgeleverd."),
        PROCESS, "fixture-gap-${productId.value}",
    ))

    private fun startGapPlanning() {
        runtime.reset()
        planning.runProcessSession(productId)
        completeOnlyJob(selection())
        planning.runProcessSession(productId)
        runtime.reset()
        ai.dispatchPending()
    }

    private fun fixtureGapPlan(gap: PlanningWorkItemId) = plan().apply {
        val fixture = (path("stories")[0] as ObjectNode).deepCopy().apply {
            put("draftKey", "fixture"); put("title", "Fixture voor lege vergaderagenda")
            put("summary", "Acceptatie kan het reeds opgeleverde lege-agendagedrag herhaalbaar controleren.")
            put("content", "Voeg uitsluitend een vaste lege vergaderagenda aan de acceptatie-fixtures toe. Behoud de bestaande schermen, autorisatie en gebruikersflow; deze zijn al opgeleverd.")
            putArray("acceptanceCriteria").add("De acceptatietest kan de lege vergaderagenda uit een vaste fixture laden.")
            putNull("uxDesign"); putArray("uxArtifactNames"); putArray("coveredAcceptanceCriteriaIndexes")
        }
        putArray("stories").add(fixture)
        putArray("todoOrder").add("fixture")
        putArray("gapResolutions").addObject().apply {
            put("workItemId", gap.value); putArray("newStoryKeys").add("fixture"); putArray("coveredByStoryIds")
            put("reason", "Alle productfunctionaliteit is opgeleverd; alleen de vaste acceptatiefixture ontbreekt nog.")
        }
    }

    private fun coveredGapPlan(gap: PlanningWorkItemId, story: StoryId) = plan().apply {
        putArray("stories"); putArray("todoOrder")
        putArray("gapResolutions").addObject().apply {
            put("workItemId", gap.value); putArray("newStoryKeys"); putArray("coveredByStoryIds").add(story.value)
            put("reason", "De bestaande opgeleverde story bevat deze fixture al; de bevroren oplevercommit dekt het gemelde gat.")
        }
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
        putArray("gapResolutions")
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
