package nl.vdzon.productfactory.design

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import nl.vdzon.productfactory.ai.AiExecutionApplicationService
import nl.vdzon.productfactory.ai.FakeRuntime
import nl.vdzon.productfactory.api.design.*
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.api.foundation.PublicGitRevisionResolver
import nl.vdzon.productfactory.design.mvp.ProductDesignMvpService
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
        val session = queries.findProcessSessions(ProcessSessionFilter(productId)).first()
        assertThat(session.status).isEqualTo(ProcessSessionStatus.SUCCEEDED)
        assertThat(session.publications).containsExactly(SourceReference("EPIC", epic.id.value, epic.version))
        assertThat(runtime.distinctIdempotencyKeys()).hasSize(1)
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

        assertStrictObjectSchemas(runtime.requests.single().responseSchema!!)
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
        val job = runtime.onlyJob()
        runtime.results[job.id] = result
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
            putArray("acceptanceCriteria").add("De Stakeholder ziet de actuele verbetering en status op het productoverzicht.").add("De bewijslink opent de opgeslagen bron zonder technische databasekennis.")
            put("slicabilityRationale", "De verbetering heeft één gebruikersdoel en kan langs overzicht, detail en bewijs in zelfstandige slices worden geleverd.")
        })
        putArray("processedSignalIds")
        putArray("memoryChanges")
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
