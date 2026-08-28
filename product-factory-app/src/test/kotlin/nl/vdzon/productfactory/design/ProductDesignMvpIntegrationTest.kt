package nl.vdzon.productfactory.design

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import nl.vdzon.productfactory.ai.AiExecutionApplicationService
import nl.vdzon.productfactory.ai.FakeRuntime
import nl.vdzon.productfactory.ai.RuntimeArtifactView
import nl.vdzon.productfactory.api.design.*
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.api.foundation.PublicGitRevisionResolver
import nl.vdzon.productfactory.design.mvp.ProductDesignMvpService
import nl.vdzon.productfactory.design.mvp.ProductDesignAiOrchestrator
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
) {
    private var productId = ProductId("not-initialized")

    @BeforeEach
    fun prepare() {
        designImplementation.deleteAllOwnedData()
        ai.deleteAllOwnedExecutionData()
        runtime.reset()
        productId = product("design-${UUID.randomUUID().toString().take(8)}")
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
