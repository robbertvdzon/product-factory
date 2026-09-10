package nl.vdzon.productfactory.design

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import nl.vdzon.productfactory.ai.AiExecutionApplicationService
import nl.vdzon.productfactory.ai.FakeRuntime
import nl.vdzon.productfactory.ai.RuntimeArtifactView
import nl.vdzon.productfactory.api.design.*
import nl.vdzon.productfactory.api.advisor.*
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.api.foundation.PublicGitRevisionResolver
import nl.vdzon.productfactory.design.mvp.ProductDesignMvpService
import nl.vdzon.productfactory.design.mvp.ProductDesignAiOrchestrator
import nl.vdzon.productfactory.advisor.ProductAdvisorApplicationService
import nl.vdzon.productfactory.auth.UserIdentityRepository
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
@Import(ProductDesignMvpIntegrationTest.Fakes::class)
class ProductDesignMvpIntegrationTest @Autowired constructor(
    private val products: ProductCommandService,
    private val productQueries: ProductQueryService,
    private val design: ProductDesignService,
    private val queries: ProductDesignQueryService,
    private val ai: AiExecutionApplicationService,
    private val designImplementation: ProductDesignMvpService,
    private val orchestrator: ProductDesignAiOrchestrator,
    private val runtime: FakeRuntime,
    private val mapper: ObjectMapper,
    private val advisor: ProductAdvisorApplicationService,
    private val users: UserIdentityRepository,
    private val jdbc: JdbcTemplate,
) {
    private var productId = ProductId("not-initialized")

    @BeforeEach
    fun prepare() {
        advisor.deleteAllOwnedData()
        designImplementation.deleteAllOwnedData()
        ai.deleteAllOwnedExecutionData()
        runtime.reset()
        productId = product("design-${UUID.randomUUID().toString().take(8)}")
    }

    @Test
    fun `gericht ProductRequest wordt door Productontwerp een complete epic met twee approvals`() {
        val owner = users.resolveOrCreate("owner-${productId.value}@example.test", true)
        val conversation = advisor.createConversation(CreateConversationCommand(
            productId, "Nieuwe productmogelijkheid", owner.id, "directed-conversation-${productId.value}",
        ))
        val requestId = ProductRequestId(UUID.randomUUID().toString())
        val now = java.time.Instant.now()
        jdbc.update(
            """INSERT INTO pf_product_request(request_id,product_id,conversation_id,requested_by,request_type,status,current_version,created_at,updated_at,version)
                VALUES (?,?,?,?,?,'PROPOSED',1,?,?,1)""".trimIndent(),
            requestId.value, productId.value, conversation.value, owner.id.value, ProductRequestType.EPIC_CANDIDATE.name, now, now,
        )
        jdbc.update(
            """INSERT INTO pf_product_request_version(request_id,version,request_type,title,summary,problem,user_impact,current_behavior,
                desired_behavior,evidence_json,git_commit_sha,acceptance_criteria_json,scope_json,boundaries_json,excluded_hotfix_categories_json,created_at)
                VALUES (?,1,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
            requestId.value, ProductRequestType.EPIC_CANDIDATE.name, "Duidelijke verbetering", "Een volledige nieuwe mogelijkheid.",
            "Gebruikers missen een controleerbare route.", "Het doel wordt nu niet bereikt.", "De route ontbreekt.",
            "De route werkt aantoonbaar.", "[]", "a".repeat(40), "[\"De route is aantoonbaar compleet.\"]", "[\"Nieuwe route\"]", "[\"Geen neveneffecten\"]", "[]", now,
        )
        advisor.approveRequest(ApproveProductRequestCommand(requestId, 1, 1, owner.id, "directed-approve-${productId.value}"))

        advisor.routeApprovedRequests()
        assertThat(advisor.getRequest(requestId).status).isEqualTo(ProductRequestStatus.ROUTING)
        completeOnlyJob(validEpic())
        assertThat(runtime.distinctIdempotencyKeys()).hasSize(1)
        advisor.routeApprovedRequests()

        val routed = advisor.getRequest(requestId)
        assertThat(routed.status).isEqualTo(ProductRequestStatus.ROUTED)
        val epic = queries.getEpic(EpicId(routed.linkedEpicId!!))
        assertThat(epic.status).isEqualTo(EpicStatus.AWAITING_PRODUCT_OWNER_APPROVAL)
        assertThat(epic.sourceProductRequestId).isEqualTo(requestId.value)
        assertThat(epic.acceptanceCriteria).hasSize(2)

        advisor.approveEpic(epic.id.value, ApprovalRole.PRODUCT_OWNER, owner.id, epic.version, "directed-po-${productId.value}")
        assertThat(queries.getEpic(epic.id).status).isEqualTo(EpicStatus.AWAITING_FACTORY_OWNER_APPROVAL)
        advisor.approveEpic(epic.id.value, ApprovalRole.FACTORY_OWNER, owner.id, epic.version, "directed-fo-${productId.value}")
        assertThat(queries.getEpic(epic.id).status).isEqualTo(EpicStatus.AVAILABLE)
    }

    @Test
    fun `antwoord op gerichte ontwerpvraag hervat exact hetzelfde workitem`() {
        val owner = users.resolveOrCreate("question-owner-${productId.value}@example.test", true)
        val conversation = advisor.createConversation(CreateConversationCommand(
            productId, "Gerichte ontwerpvraag", owner.id, "question-conversation-${productId.value}",
        ))
        val requestId = insertDirectedRequest(conversation, owner.id)
        advisor.approveRequest(ApproveProductRequestCommand(requestId, 1, 1, owner.id, "question-approve-${productId.value}"))
        advisor.routeApprovedRequests()

        val first = validEpic().also { result ->
            (result.path("epic").path("readiness") as ObjectNode).apply {
                put("readyForPlanning", false)
                putArray("openQuestions").add("Welke uitleg moet op de lege toestand staan?")
            }
            result.putObject("stakeholderQuestion")
                .put("question", "Welke uitleg moet op de lege toestand staan?")
                .put("context", "Dit antwoord is nodig om de gerichte epic af te ronden.")
        }
        completeOnlyJob(first)
        advisor.routeApprovedRequests()

        val question = productQueries.findStakeholderQuestions(StakeholderQuestionFilter(productId, "PRODUCT_DESIGNER_MVP")).single()
        assertThat(question.requestedRespondentUserId).isEqualTo(owner.id)
        assertThat(question.productRequestId).isEqualTo(requestId)
        assertThat(jdbc.queryForObject("SELECT status FROM pf_design_work_item WHERE request_id=?", String::class.java, requestId.value))
            .isEqualTo("WAITING_FOR_USER")

        products.answerStakeholderQuestionDirectly(AnswerStakeholderQuestionDirectlyCommand(
            question.id, "Leg uit dat er nog geen gecontroleerde resultaten zijn.", question.version,
            ActorReference(ActorType.STAKEHOLDER, owner.id.value), "question-answer-${productId.value}",
        ))
        advisor.routeApprovedRequests()
        val current = queries.findEpics(EpicFilter(productId)).single { it.sourceProductRequestId == requestId.value }
        val revised = validEpic().apply {
            put("outcome", "REVISE_EPIC")
            put("epicId", current.id.value)
            put("expectedVersion", current.version)
            keepExistingUx(path("epic") as ObjectNode, current)
        }
        completeOnlyJob(revised)
        advisor.routeApprovedRequests()

        val routed = advisor.getRequest(requestId)
        assertThat(routed.status).isEqualTo(ProductRequestStatus.ROUTED)
        assertThat(routed.linkedEpicId).isEqualTo(current.id.value)
        assertThat(jdbc.queryForObject("SELECT process_session_id FROM pf_design_work_item WHERE request_id=?", String::class.java, requestId.value))
            .isEqualTo(question.processSessionId.value)
        assertThat(runtime.distinctIdempotencyKeys()).hasSize(2)
    }

    @Test
    fun `wachtende sessie vraagt exact een taak en publiceert complete epic atomair`() {
        design.runProcessSession(productId)
        design.runProcessSession(productId)
        assertThat(queries.findProcessSessions(ProcessSessionFilter(productId)).single().status).isEqualTo(ProcessSessionStatus.WAITING_FOR_AI)

        completeOnlyJob(validEpic())
        design.runProcessSession(productId)

        val epic = queries.findEpics(EpicFilter(productId)).single()
        assertThat(epic.status).isEqualTo(EpicStatus.AVAILABLE)
        assertThat(epic.title).isEqualTo("Rustige voortgang")
        assertThat(epic.acceptanceCriteria).hasSize(2)
        assertThat(epic.uxArtifacts).hasSize(4)
        assertThat(epic.uxScreens.map { it.screenKey }).containsExactly("start", "empty")
        assertThat(epic.readiness.readyForPlanning).isTrue()
        val session = queries.findProcessSessions(ProcessSessionFilter(productId)).first()
        assertThat(session.status).isEqualTo(ProcessSessionStatus.SUCCEEDED)
        assertThat(session.publications).containsExactly(SourceReference("EPIC", epic.id.value, epic.version))
        assertThat(runtime.distinctIdempotencyKeys()).hasSize(1)
    }

    @Test
    fun `ontwerpgeheugen gebruikt dezelfde titelgrens als centraal agentgeheugen`() {
        val memoryTitle = "Onderzochte broncontext ".repeat(9).trim()
        assertThat(memoryTitle.length).isBetween(201, 300)
        val result = validEpic().also {
            it.withArray("memoryChanges").addObject().apply {
                put("type", "ADD")
                putNull("itemId")
                putNull("expectedVersionId")
                put("title", memoryTitle)
                put("content", "Deze broncontext voorkomt dat een volgende ontwerpronde hetzelfde onderzoek opnieuw uitvoert.")
                put("reason", "Bewaar reproduceerbaar onderzoek voor een volgende ontwerpronde.")
            }
        }

        design.runProcessSession(productId)
        completeOnlyJob(result)
        design.runProcessSession(productId)

        assertThat(queries.findProcessSessions(ProcessSessionFilter(productId)).single().status)
            .isEqualTo(ProcessSessionStatus.SUCCEEDED)
        assertThat(jdbc.queryForObject("SELECT title FROM pf_agent_memory_version", String::class.java)).isEqualTo(memoryTitle)
    }

    @Test
    fun `oude blokkade zonder lokaal resultaat hervat dezelfde taak zodra projectie gereed is`() {
        design.runProcessSession(productId)
        ai.dispatchPending()
        val session = queries.findProcessSessions(ProcessSessionFilter(productId)).single()
        val taskId = session.aiTaskIds.single()
        val job = runtime.onlyJob()
        val result = validEpic()
        runtime.results[job.id] = result
        runtime.resultArtifacts[job.id] = result.path("epic").path("uxArtifactChanges")
            .mapNotNull { it.path("outputArtifactName").takeIf(JsonNode::isTextual)?.asText() }
            .mapIndexed { index, name ->
                RuntimeArtifactView("ux-$index", job.id, name, "image/png", 128, (index + 1).toString().take(1).repeat(64), java.time.Instant.now())
            }
        runtime.jobs[job.id] = job.copy(status = "SUCCEEDED", phase = "COMPLETED", progressPercent = 100)
        jdbc.update("UPDATE pf_ai_task SET status='SUCCEEDED' WHERE id=?", taskId.value)
        jdbc.update(
            "UPDATE pf_design_process_session SET status='BLOCKED',error_code='AI_RESULT_MISSING',blocked_reason='Oude projectiefout' WHERE id=?",
            session.id.value,
        )

        ai.reconcileActive()
        orchestrator.resumeReady()

        val completed = queries.getProcessSession(session.id)
        assertThat(completed.status).isEqualTo(ProcessSessionStatus.SUCCEEDED)
        assertThat(queries.findEpics(EpicFilter(productId))).hasSize(1)
        assertThat(completed.aiTaskIds).containsExactly(taskId)
        assertThat(runtime.distinctIdempotencyKeys()).hasSize(1)
    }

    @Test
    fun `product kan iedere rijpe epic eerst door stakeholder laten goedkeuren of terugsturen`() {
        val product = productQueries.getProduct(productId)
        products.setEpicApprovalMode(SetEpicApprovalModeCommand(
            productId, EpicApprovalMode.MANUAL, product.version, STAKEHOLDER, "manual-approval-${productId.value}",
        ))

        design.runProcessSession(productId)
        completeOnlyJob(validEpic())
        design.runProcessSession(productId)

        var epic = queries.findEpics(EpicFilter(productId)).single()
        assertThat(epic.status).isEqualTo(EpicStatus.AWAITING_APPROVAL)

        design.approveEpic(ApproveEpicCommand(epic.id, epic.version, STAKEHOLDER, "approve-${epic.id.value}"))
        epic = queries.getEpic(epic.id)
        assertThat(epic.status).isEqualTo(EpicStatus.AVAILABLE)

        design.requestEpicRefinement(RequestEpicRefinementCommand(
            epic.id, "Het scherm waarin de gebruiker de eerste vraag stelt ontbreekt.", epic.version,
            STAKEHOLDER, "refine-${epic.id.value}",
        ))
        epic = queries.getEpic(epic.id)
        assertThat(epic.status).isEqualTo(EpicStatus.NEEDS_REFINEMENT)
        assertThat(epic.refinementReason).isEqualTo("Het scherm waarin de gebruiker de eerste vraag stelt ontbreekt.")
    }

    @Test
    fun `ongeldige output publiceert niets en bewuste retry houdt dezelfde bevroren input`() {
        design.runProcessSession(productId)
        completeOnlyJob(validEpic().also { (it.path("epic") as ObjectNode).remove("uxDesign") })
        design.runProcessSession(productId)

        val blocked = queries.findProcessSessions(ProcessSessionFilter(productId)).single()
        assertThat(blocked.status).isEqualTo(ProcessSessionStatus.BLOCKED)
        assertThat(queries.findEpics(EpicFilter(productId))).isEmpty()

        design.runProcessSession(productId)
        assertThat(queries.findProcessSessions(ProcessSessionFilter(productId)).single().aiTaskIds).hasSize(2)
    }

    @Test
    fun `epiclevenscyclus bewaart iedere versie en controleert actor versie en idempotentie`() {
        design.runProcessSession(productId)
        completeOnlyJob(validEpic())
        design.runProcessSession(productId)
        var epic = queries.findEpics(EpicFilter(productId)).single()

        val claim = ClaimEpicForPlanningCommand(epic.id, epic.version, PROCESS, "claim-${epic.id.value}")
        design.claimEpicForPlanning(claim)
        design.claimEpicForPlanning(claim)
        epic = queries.getEpic(epic.id)
        assertThat(epic.status).isEqualTo(EpicStatus.IN_PLANNING)

        design.markEpicActive(MarkEpicActiveCommand(epic.id, 1, epic.version, PROCESS, "active-${epic.id.value}"))
        epic = queries.getEpic(epic.id)
        design.markEpicReadyForVerification(MarkEpicReadyForVerificationCommand(epic.id, epic.version, PROCESS, "verify-${epic.id.value}"))
        epic = queries.getEpic(epic.id)
        design.recordEpicVerification(RecordEpicVerificationCommand(
            epic.id, VerificationId("verification-1"), EpicVerificationOutcome.NEEDS_WORK, "Herstel is aantoonbaar nodig.", epic.version, PROCESS, "needs-work-${epic.id.value}",
        ))

        val activeAgain = queries.getEpic(epic.id)
        assertThat(activeAgain.status).isEqualTo(EpicStatus.ACTIVE)
        assertThat(queries.getEpicHistory(epic.id)).hasSize(5)
        assertThatThrownBy {
            design.markEpicReadyForVerification(MarkEpicReadyForVerificationCommand(epic.id, 1, STAKEHOLDER, "stale-${epic.id.value}"))
        }.isInstanceOf(VersionConflict::class.java)
    }

    @Test
    fun `ongewijzigde input eindigt als succesvolle no-op zonder tweede taak`() {
        design.runProcessSession(productId)
        completeOnlyJob(mapper.createObjectNode().put("outcome", "NO_EPIC").put("reason", "Er is nog geen aantoonbare nieuwe gebruikersverbetering."))
        design.runProcessSession(productId)
        design.runProcessSession(productId)

        val sessions = queries.findProcessSessions(ProcessSessionFilter(productId))
        assertThat(sessions).hasSize(2)
        assertThat(sessions.first().resultSummary).contains("no-op")
        assertThat(runtime.distinctIdempotencyKeys()).hasSize(1)
    }

    @Test
    fun `een NO_EPIC-reden ruim boven de mensmaat maar binnen de AI-marge eindigt succesvol`() {
        // Reproduceert een echte productiesessie: de agent schrijft voor NO_EPIC een uitgebreide
        // onderbouwing (waargenomen 1200-1350 tekens), ruim boven de oude harde grens van 1000 —
        // niets in de prompt begrensde dat veld, dus elke retry botste opnieuw en de sessie bleef
        // permanent BLOCKED. 1300 tekens moet nu gewoon slagen.
        val longReason = "Dit signaal is al volledig verwerkt in de actieve epic. ".repeat(23).trim()
        assertThat(longReason.length).isGreaterThan(1000)

        design.runProcessSession(productId)
        completeOnlyJob(mapper.createObjectNode().put("outcome", "NO_EPIC").put("reason", longReason))
        design.runProcessSession(productId)

        val session = queries.findProcessSessions(ProcessSessionFilter(productId)).first()
        assertThat(session.status).isEqualTo(ProcessSessionStatus.SUCCEEDED)
        assertThat(session.resultSummary).contains(longReason.take(100))
    }

    @Test
    fun `een absurd lange NO_EPIC-reden blijft geblokkeerd`() {
        val absurdlyLongReason = "x".repeat(1_801)

        design.runProcessSession(productId)
        completeOnlyJob(mapper.createObjectNode().put("outcome", "NO_EPIC").put("reason", absurdlyLongReason))
        design.runProcessSession(productId)

        val session = queries.findProcessSessions(ProcessSessionFilter(productId)).first()
        assertThat(session.status).isEqualTo(ProcessSessionStatus.BLOCKED)
        assertThat(session.errorCode).isEqualTo("DESIGN_INPUT_INVALID")
    }

    @Test
    fun `verschillende producten hebben onafhankelijke wachtende sessies`() {
        val other = product("other-${UUID.randomUUID().toString().take(8)}")
        design.runProcessSession(productId)
        design.runProcessSession(other)

        assertThat(queries.findProcessSessions(ProcessSessionFilter()).filter { it.status == ProcessSessionStatus.WAITING_FOR_AI }.map { it.productId })
            .contains(productId, other)
    }

    @Test
    fun `responseschema voldoet recursief aan strikte objectregels`() {
        design.runProcessSession(productId)
        ai.dispatchPending()

        val schema = runtime.requests.single().responseSchema!!
        assertStrictObjectSchemas(schema)
        assertThat(schema.at("/properties/epic/properties/directionReferences/items/properties/type/enum").map(JsonNode::asText))
            .containsExactly("PRODUCT_ASSIGNMENT", "DECISION")
        assertThat(schema.at("/properties/epic/properties/researchSources/items/properties/status/enum").map(JsonNode::asText))
            .containsExactly("CANDIDATE", "VALIDATED", "BLOCKED")
        assertThat(schema.at("/properties/memoryChanges/items/properties/title/maxLength").asInt()).isEqualTo(300)
        assertThat(schema.at("/properties/memoryChanges/items/properties/content/maxLength").asInt()).isEqualTo(4000)
        assertThat(schema.at("/properties/memoryChanges/items/properties/reason/maxLength").asInt()).isEqualTo(1000)
    }

    @Test
    fun `onrijpe data-afhankelijke epic blijft buiten planning en wordt begrensd verder ontworpen`() {
        design.runProcessSession(productId)
        completeOnlyJob(validEpic().also { result ->
            val epic = result.path("epic") as ObjectNode
            epic.put("solution", "Gebruik externe archieven en datasets als bronnen voor een betrouwbaar zoekresultaat.")
            epic.path("readiness").let { it as ObjectNode }.apply {
                put("readyForPlanning", false)
                put("requiresExternalData", true)
                putArray("unmetConditions").add("Concrete externe bronnen moeten nog worden gevalideerd.")
            }
        })
        orchestrator.resumeReady()

        val firstIterationSession = queries.findProcessSessions(ProcessSessionFilter(productId)).single()
        assertThat(firstIterationSession.status)
            .withFailMessage("Ontwerpiteratie blokkeerde: %s / %s", firstIterationSession.errorCode, firstIterationSession.blockedReason)
            .isEqualTo(ProcessSessionStatus.WAITING_FOR_AI)
        var epic = queries.findEpics(EpicFilter(productId)).single()
        assertThat(epic.status).isEqualTo(EpicStatus.NEEDS_REFINEMENT)
        assertThat(queries.findProcessSessions(ProcessSessionFilter(productId)).single().status).isEqualTo(ProcessSessionStatus.WAITING_FOR_AI)
        assertThat(queries.findProcessSessions(ProcessSessionFilter(productId)).single().aiTaskIds).hasSize(2)

        val refined = validEpic().apply {
            put("outcome", "REVISE_EPIC")
            put("epicId", epic.id.value)
            put("expectedVersion", epic.version)
            val draft = path("epic") as ObjectNode
            draft.put("solution", "Gebruik twee gevalideerde externe archieven en datasets als bronnen voor een betrouwbaar zoekresultaat.")
            draft.putArray("researchSources").apply {
                add(validatedSource("https://example.org/archive", "Gemeentearchief"))
                add(validatedSource("https://example.org/museum", "Museumcollectie"))
            }
            (draft.path("readiness") as ObjectNode).apply {
                put("readyForPlanning", true)
                put("requiresExternalData", true)
                putArray("unmetConditions")
                putArray("openQuestions")
            }
            keepExistingUx(draft, epic)
        }
        completeOnlyJob(refined)
        orchestrator.resumeReady()

        epic = queries.getEpic(epic.id)
        assertThat(epic.status).isEqualTo(EpicStatus.AVAILABLE)
        assertThat(epic.researchSources).hasSize(2)
        assertThat(epic.readiness.readyForPlanning).isTrue()
        assertThat(queries.findProcessSessions(ProcessSessionFilter(productId)).single().status).isEqualTo(ProcessSessionStatus.SUCCEEDED)
    }

    @Test
    fun `epicrevisie behoudt ieder bestaand UX artifact expliciet en voegt nieuwe schermvarianten toe`() {
        design.runProcessSession(productId)
        completeOnlyJob(validEpic())
        design.runProcessSession(productId)
        val current = queries.findEpics(EpicFilter(productId)).single()

        design.requestEpicRefinement(RequestEpicRefinementCommand(
            current.id, "Voeg een zelfstandig detailsscherm toe zonder bestaande schermen te verliezen.", current.version,
            STAKEHOLDER, "ux-refine-${current.id.value}",
        ))
        val refinementTarget = queries.getEpic(current.id)
        design.runProcessSession(productId)
        val revised = validEpic().apply {
            put("outcome", "REVISE_EPIC")
            put("epicId", refinementTarget.id.value)
            put("expectedVersion", refinementTarget.version)
            val draft = path("epic") as ObjectNode
            draft.putArray("uxArtifactChanges").apply {
                refinementTarget.uxScreens.forEach { screen ->
                    screen.artifacts.forEach { (_, name) ->
                        addObject().apply {
                            put("operation", "KEEP")
                            put("existingArtifactName", name)
                            putNull("outputArtifactName")
                            put("screenKey", screen.screenKey)
                            put("reason", "Dit scherm blijft ongewijzigd onderdeel van de hoofdroute.")
                        }
                    }
                }
                addObject().apply {
                    put("operation", "ADD"); putNull("existingArtifactName"); put("outputArtifactName", "ux-detail-desktop.png")
                    put("screenKey", "detail"); put("reason", "Het nieuwe detailsscherm maakt de aanvullende route compleet.")
                }
                addObject().apply {
                    put("operation", "ADD"); putNull("existingArtifactName"); put("outputArtifactName", "ux-detail-mobile.png")
                    put("screenKey", "detail"); put("reason", "De mobiele variant maakt het nieuwe detailsscherm responsive compleet.")
                }
            }
            draft.putArray("uxScreens").apply {
                refinementTarget.uxScreens.forEach { screen ->
                    addObject().apply {
                        put("screenKey", screen.screenKey); put("state", screen.state.name); put("purpose", screen.purpose)
                        putArray("artifacts").apply {
                            screen.artifacts.forEach { (viewport, name) -> addObject().put("viewport", viewport.name).put("artifactName", name) }
                        }
                    }
                }
                addObject().apply {
                    put("screenKey", "detail"); put("state", "DETAIL"); put("purpose", "Toon het gekozen bewijs met een duidelijke terugroute.")
                    putArray("artifacts").apply {
                        addObject().put("viewport", "DESKTOP").put("artifactName", "ux-detail-desktop.png")
                        addObject().put("viewport", "MOBILE").put("artifactName", "ux-detail-mobile.png")
                    }
                }
            }
        }
        completeOnlyJob(revised)
        design.runProcessSession(productId)

        val result = queries.getEpic(current.id)
        assertThat(result.uxArtifacts.map { it.name }).containsExactly(
            "ux-main-desktop.png", "ux-main-mobile.png", "ux-empty-desktop.png", "ux-empty-mobile.png",
            "ux-detail-desktop.png", "ux-detail-mobile.png",
        )
        assertThat(result.uxScreens.map { it.screenKey }).containsExactly("start", "empty", "detail")
    }

    @Test
    fun `epicrevisie kan bestaande UX artifacts niet stilzwijgend laten verdwijnen`() {
        design.runProcessSession(productId)
        completeOnlyJob(validEpic())
        design.runProcessSession(productId)
        val original = queries.findEpics(EpicFilter(productId)).single()
        design.requestEpicRefinement(RequestEpicRefinementCommand(
            original.id, "Controleer dat alle bestaande UX-schermen behouden blijven.", original.version,
            STAKEHOLDER, "silent-loss-${original.id.value}",
        ))
        val target = queries.getEpic(original.id)
        design.runProcessSession(productId)
        val invalidRevision = validEpic().apply {
            put("outcome", "REVISE_EPIC")
            put("epicId", target.id.value)
            put("expectedVersion", target.version)
        }
        completeOnlyJob(invalidRevision)
        design.runProcessSession(productId)

        assertThat(queries.findProcessSessions(ProcessSessionFilter(productId)).first().status).isEqualTo(ProcessSessionStatus.BLOCKED)
        assertThat(queries.getEpic(original.id).version).isEqualTo(target.version)
        assertThat(queries.getEpic(original.id).uxArtifacts.map { it.name }).containsExactlyElementsOf(target.uxArtifacts.map { it.name })
    }

    private fun validatedSource(uri: String, name: String) = mapper.createObjectNode().apply {
        put("name", name)
        put("provider", name)
        put("uri", uri)
        put("accessMethod", "Publieke HTTPS-collectie met gedocumenteerde zoekroute")
        put("license", "Publiek raadpleegbaar; rechten per object vermeld")
        put("coverage", "Historische records, objectbeschrijvingen en datering voor de relevante regio")
        put("status", "VALIDATED")
        put("validationEvidence", "De collectiepagina en zoekfunctie zijn geopend en geven concrete resultaten met bronmetadata.")
    }

    private fun assertStrictObjectSchemas(schema: JsonNode) {
        val types = schema.path("type").let { type ->
            if (type.isArray) type.map(JsonNode::asText).toSet() else setOf(type.asText())
        }
        if ("object" in types) {
            assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse()
            assertThat(schema.path("required").map(JsonNode::asText).toSet())
                .isEqualTo(schema.path("properties").fieldNames().asSequence().toSet())
            schema.path("properties").forEach(::assertStrictObjectSchemas)
        }
        schema.path("items").takeUnless(JsonNode::isMissingNode)?.let(::assertStrictObjectSchemas)
    }

    private fun completeOnlyJob(result: ObjectNode) {
        ai.dispatchPending()
        val job = runtime.jobs.values.single { it.status != "SUCCEEDED" }
        runtime.results[job.id] = result
        runtime.resultArtifacts[job.id] = result.path("epic").path("uxArtifactChanges")
            .mapNotNull { it.path("outputArtifactName").takeIf(JsonNode::isTextual)?.asText() }
            .mapIndexed { index, name ->
                RuntimeArtifactView("ux-$index", job.id, name, "image/png", 128, (index + 1).toString().take(1).repeat(64), java.time.Instant.now())
            }
        runtime.jobs[job.id] = job.copy(status = "SUCCEEDED", phase = "COMPLETED", progressPercent = 100)
        ai.reconcileActive()
    }

    private fun validEpic() = mapper.createObjectNode().apply {
        put("outcome", "CREATE_EPIC")
        set<ObjectNode>("epic", mapper.createObjectNode().apply {
            put("title", "Rustige voortgang")
            put("summary", "Stakeholders zien voortgang zonder technische ruis.")
            put("problem", "Stakeholders kunnen nu niet snel zien welk productwerk aantoonbaar voortgang boekt.")
            put("solution", "Toon een rustig overzicht met de actuele gebruikersverbetering, haar grens en bewijs, zodat de Stakeholder begrijpt waarom dit het probleem oplost.")
            putArray("directionReferences").addObject().put("type", "PRODUCT_ASSIGNMENT").put("id", productId.value).put("version", 1)
            put("visibleBehaviorChange", true)
            put("uxDesign", "Eén scanbaar voortgangsblok met titel, samenvatting, status en bewijslink.")
            putArray("uxArtifactChanges").apply {
                addUxArtifact("ux-main-desktop.png", "start")
                addUxArtifact("ux-main-mobile.png", "start")
                addUxArtifact("ux-empty-desktop.png", "empty")
                addUxArtifact("ux-empty-mobile.png", "empty")
            }
            putArray("uxScreens").apply {
                addUxScreen("start", "INITIAL", "Toon de actuele verbetering als ingang van de hoofdroute.", "ux-main-desktop.png", "ux-main-mobile.png")
                addUxScreen("empty", "EMPTY", "Leg begrijpelijk uit dat er nog geen bewijs beschikbaar is.", "ux-empty-desktop.png", "ux-empty-mobile.png")
            }
            putArray("acceptanceCriteria").add("De Stakeholder ziet de actuele verbetering en status op het productoverzicht.").add("De bewijslink opent de opgeslagen bron zonder technische databasekennis.")
            put("slicabilityRationale", "De verbetering heeft één gebruikersdoel en kan langs overzicht, detail en bewijs in zelfstandige slices worden geleverd.")
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

    private fun insertDirectedRequest(conversationId: ProductConversationId, ownerId: UserId): ProductRequestId {
        val requestId = ProductRequestId(UUID.randomUUID().toString())
        val now = java.time.Instant.now()
        jdbc.update(
            """INSERT INTO pf_product_request(request_id,product_id,conversation_id,requested_by,request_type,status,current_version,created_at,updated_at,version)
                VALUES (?,?,?,?,?,'PROPOSED',1,?,?,1)""".trimIndent(),
            requestId.value, productId.value, conversationId.value, ownerId.value, ProductRequestType.EPIC_CANDIDATE.name, now, now,
        )
        jdbc.update(
            """INSERT INTO pf_product_request_version(request_id,version,request_type,title,summary,problem,user_impact,current_behavior,
                desired_behavior,evidence_json,git_commit_sha,acceptance_criteria_json,scope_json,boundaries_json,excluded_hotfix_categories_json,created_at)
                VALUES (?,1,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
            requestId.value, ProductRequestType.EPIC_CANDIDATE.name, "Gerichte verbetering", "Een volledige nieuwe mogelijkheid.",
            "Gebruikers missen een controleerbare route.", "Het doel wordt nu niet bereikt.", "De route ontbreekt.",
            "De route werkt aantoonbaar.", "[]", "a".repeat(40), "[\"De route is aantoonbaar compleet.\"]", "[\"Nieuwe route\"]", "[\"Geen neveneffecten\"]", "[]", now,
        )
        return requestId
    }

    private fun com.fasterxml.jackson.databind.node.ArrayNode.addUxArtifact(name: String, screenKey: String) {
        addObject().apply {
            put("operation", "ADD"); putNull("existingArtifactName"); put("outputArtifactName", name)
            put("screenKey", screenKey); put("reason", "Dit bestand maakt het UX-scherm aantoonbaar en compleet.")
        }
    }

    private fun com.fasterxml.jackson.databind.node.ArrayNode.addUxScreen(
        screenKey: String,
        state: String,
        purpose: String,
        desktop: String,
        mobile: String,
    ) {
        addObject().apply {
            put("screenKey", screenKey); put("state", state); put("purpose", purpose)
            putArray("artifacts").apply {
                addObject().put("viewport", "DESKTOP").put("artifactName", desktop)
                addObject().put("viewport", "MOBILE").put("artifactName", mobile)
            }
        }
    }

    private fun keepExistingUx(draft: ObjectNode, epic: EpicDetails) {
        draft.putArray("uxArtifactChanges").apply {
            epic.uxScreens.forEach { screen ->
                screen.artifacts.values.forEach { name ->
                    addObject().apply {
                        put("operation", "KEEP"); put("existingArtifactName", name); putNull("outputArtifactName")
                        put("screenKey", screen.screenKey); put("reason", "Dit bestaande scherm blijft volledig geldig.")
                    }
                }
            }
        }
        draft.set<JsonNode>("uxScreens", mapper.valueToTree(epic.uxScreens.map { screen ->
            mapOf(
                "screenKey" to screen.screenKey,
                "state" to screen.state.name,
                "purpose" to screen.purpose,
                "artifacts" to screen.artifacts.map { (viewport, name) -> mapOf("viewport" to viewport.name, "artifactName" to name) },
            )
        }))
    }

    private fun product(id: String): ProductId {
        val product = ProductId(id)
        products.createProduct(CreateProductCommand(product, id, actor = STAKEHOLDER, idempotencyKey = "create-$id"))
        products.updateProductAssignment(UpdateProductAssignmentCommand(
            product, "Stakeholders", "Maak productvoortgang aantoonbaar", listOf("Geen credentials"),
            "https://github.com/robbertvdzon/hkh-autopilot.git", 0, STAKEHOLDER, "assignment-$id",
        ))
        assertThat(productQueries.getProductAssignment(product).version).isEqualTo(1)
        return product
    }

    @TestConfiguration
    class Fakes {
        @Bean @Primary fun fakeRuntime(): FakeRuntime = FakeRuntime()
        @Bean @Primary fun fakeGit(): PublicGitRevisionResolver = object : PublicGitRevisionResolver {
            override fun resolveHead(publicGitUrl: String) = "a".repeat(40)
        }
    }

    companion object {
        private val STAKEHOLDER = ActorReference(ActorType.STAKEHOLDER, "stakeholder@example.com")
        private val PROCESS = ActorReference(ActorType.PROCESS, "planner-mvp")
    }
}
