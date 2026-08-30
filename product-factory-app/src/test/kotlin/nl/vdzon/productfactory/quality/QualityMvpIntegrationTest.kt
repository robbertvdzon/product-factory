package nl.vdzon.productfactory.quality

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import nl.vdzon.productfactory.ai.AiExecutionApplicationService
import nl.vdzon.productfactory.ai.FakeRuntime
import nl.vdzon.productfactory.api.design.*
import nl.vdzon.productfactory.api.foundation.DeploymentRevisionResolver
import nl.vdzon.productfactory.api.foundation.PublicGitRevisionResolver
import nl.vdzon.productfactory.api.planning.*
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.quality.*
import nl.vdzon.productfactory.api.shared.*
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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Clock
import java.util.UUID

@SpringBootTest(properties = ["PF_AUTH_REQUIRED=false"])
@ActiveProfiles("test")
@Import(QualityMvpIntegrationTest.Fakes::class)
class QualityMvpIntegrationTest @Autowired constructor(
    private val productCommands: ProductCommandService,
    private val productQueries: ProductQueryService,
    private val quality: QualityService,
    private val qualityQueries: QualityQueryService,
    private val qualityImpl: QualityMvpService,
    private val planningQueries: ProductPlanningQueryService,
    private val planningImpl: ProductPlanningMvpService,
    private val designQueries: ProductDesignQueryService,
    private val ai: AiExecutionApplicationService,
    private val runtime: FakeRuntime,
    private val revisions: FakeDeploymentRevisionResolver,
    private val mapper: ObjectMapper,
    private val jdbc: JdbcTemplate,
    private val clock: Clock,
) {
    private var productId = ProductId("not-initialized")
    private var epicId = EpicId("not-initialized")
    private var storyId = StoryId("not-initialized")

    @BeforeEach
    fun prepare() {
        qualityImpl.deleteAllOwnedData()
        planningImpl.deleteAllOwnedData()
        ai.deleteAllOwnedExecutionData()
        runtime.reset()
        revisions.revision = SHA
        productId = ProductId("quality-${UUID.randomUUID().toString().take(8)}")
        epicId = EpicId(UUID.randomUUID().toString())
        storyId = StoryId(UUID.randomUUID().toString())
        productCommands.createProduct(CreateProductCommand(productId, "Quality test", actor = STAKEHOLDER, idempotencyKey = "create-${productId.value}"))
        productCommands.updateProductAssignment(UpdateProductAssignmentCommand(
            productId, "Stakeholders", "Bewijs werkende gebruikerswaarde", listOf("Geen echte persoonsgegevens"),
            "https://github.com/robbertvdzon/hkh-autopilot.git", 0, STAKEHOLDER, "assignment-${productId.value}",
        ))
        productCommands.configureTestableProduct(ConfigureTestableProductCommand(
            productId,
            TestEnvironmentConfiguration("acceptance", "https://acceptance.example.test", listOf("/"), "/api/version", "$.commit"),
            null, 0, STAKEHOLDER, "testable-${productId.value}",
        ))
        insertEpic(EpicStatus.ACTIVE)
        insertStory(storyId, StoryType.PRODUCT_STORY, null, null)
    }

    @Test
    fun `story wordt alleen tegen exacte live revision getest en levert snapshot`() {
        val work = quality.requestStoryVerification(RequestStoryVerificationCommand(productId, storyId, 1, "acceptance", 50, "verify-story"))
        completeSession(result(work, "PASSED"))

        val verification = qualityQueries.findVerifications(VerificationFilter(productId)).single()
        assertThat(verification.outcome).isEqualTo(VerificationOutcome.PASSED)
        assertThat(verification.requiredCommitSha).isEqualTo(SHA)
        assertThat(verification.testedRevision).isEqualTo(SHA)
        assertThat(planningQueries.getStory(storyId).verificationPassed).isTrue()
        assertThat(designQueries.getEpic(epicId).status).isEqualTo(EpicStatus.VERIFYING)
        assertThat(qualityQueries.getCurrentQuality(productId)?.productRevision).isEqualTo(SHA)
        assertThat(qualityQueries.findQualityWorkItems(productId).single().status).isEqualTo(WorkItemStatus.DONE)
    }

    @Test
    fun `tester gebruikt UX modellen als richting en niet als pixelperfect contract`() {
        quality.requestStoryVerification(RequestStoryVerificationCommand(productId, storyId, 1, "acceptance", 50, "ux-policy"))
        quality.runProcessSession(productId)
        ai.dispatchPending()

        assertThat(runtime.requests.single().prompt)
            .contains("UX-screenshots zijn richtinggevend en geen golden masters")
            .contains("informatiehiërarchie, vereiste toestanden, gebruikersflow, toegankelijkheid en responsive gedrag")
            .contains("keur niet af op pixelverschillen, exacte kleuren, afstanden of typografie")
        ai.deleteAllOwnedExecutionData()
        runtime.reset()
    }

    @Test
    fun `achterlopende deployment blokkeert niet meer maar de tester test gewoon wat er draait`() {
        revisions.revision = "b".repeat(40)
        val work = quality.requestStoryVerification(RequestStoryVerificationCommand(productId, storyId, 1, "acceptance", 50, "deployment-lagging"))
        completeSession(result(work, "NEEDS_WORK"))

        val item = qualityQueries.findQualityWorkItems(productId).single()
        assertThat(item.status).isEqualTo(WorkItemStatus.DONE)
        assertThat(item.attemptCount).isEqualTo(1)
        val verification = qualityQueries.findVerifications(VerificationFilter(productId)).single()
        assertThat(verification.outcome).isEqualTo(VerificationOutcome.NEEDS_WORK)
        assertThat(verification.testedRevision).isEqualTo("b".repeat(40))
        assertThat(verification.requiredCommitSha).isEqualTo(SHA)
    }

    @Test
    fun `epic needs work publiceert dekkingsbewijs en gericht planwerk`() {
        jdbc.update("UPDATE pf_epic SET status='VERIFYING' WHERE id=?", epicId.value)
        jdbc.update("UPDATE pf_epic_version SET status='VERIFYING' WHERE epic_id=? AND version=1", epicId.value)
        val epic = designQueries.getEpic(epicId)
        val work = quality.requestEpicVerification(RequestEpicVerificationCommand(productId, epic.id, epic.version, "acceptance", 80, "verify-epic"))
        completeSession(result(work, "NEEDS_WORK").apply {
            (path("results")[0] as ObjectNode).apply {
                put("explanation", "De kernroute mist aantoonbaar het tweede afgesproken resultaat.")
                putArray("missingCoverage").add(CRITERION)
                putArray("bugs").addObject().apply {
                    put("title", "Epicroute verliest bewijs")
                    put("summary", "De complete epicroute toont het opgeslagen bewijs niet betrouwbaar.")
                    put("actualBehaviour", "De route eindigt reproduceerbaar zonder het afgesproken bewijsdetail.")
                    put("expectedBehaviour", "De complete route toont het opgeslagen bewijsdetail en een veilige terugroute.")
                    putArray("reproductionSteps").add("Open de complete epicroute.").add("Activeer de bewijsactie.")
                    put("impact", "Het centrale gebruikersdoel van de epic is niet aantoonbaar bereikt.")
                    put("severity", "P1")
                    set<ObjectNode>("evidence", proof("Epiccontrole toont de afwijking tegen de exacte revision."))
                }
            }
        })

        assertThat(qualityQueries.findVerifications(VerificationFilter(productId)).single().missingCoverage).containsExactly(CRITERION)
        assertThat(qualityQueries.findBugs(BugFilter(productId))).hasSize(1)
        assertThat(designQueries.getEpic(epicId).status).isEqualTo(EpicStatus.ACTIVE)
        assertThat(planningQueries.findPlanningWorkItems(productId).map { it.type })
            .containsExactlyInAnyOrder(PlanningWorkItemType.PLAN_EPIC_GAP, PlanningWorkItemType.PLAN_BUGFIX)
    }

    @Test
    fun `afgekeurde bugfix houdt dezelfde bug open en maakt een volgende gewone poging`() {
        val first = quality.requestStoryVerification(RequestStoryVerificationCommand(productId, storyId, 1, "acceptance", 80, "find-bug"))
        completeSession(result(first, "FAILED").apply {
            (path("results")[0] as ObjectNode).putArray("bugs").addObject().apply {
                    put("title", "Bewijsroute opent niet")
                    put("summary", "De bewijsactie eindigt in een fout en blokkeert de Stakeholder.")
                    put("actualBehaviour", "Na activeren verschijnt reproduceerbaar een foutmelding zonder bewijsdetail.")
                    put("expectedBehaviour", "De actie opent het opgeslagen bewijsdetail met een veilige terugroute.")
                    putArray("reproductionSteps").add("Open het overzicht vanaf een schone sessie.").add("Activeer de bewijsactie.")
                    put("impact", "De Stakeholder kan opgeleverde waarde niet zelfstandig controleren.")
                    put("severity", "P1")
                    set<ObjectNode>("evidence", proof("Browserobservatie en screenshot tonen dezelfde fout in twee schone sessies."))
            }
        })
        val bug = qualityQueries.findBugs(BugFilter(productId)).single()
        val bugfix = StoryId(UUID.randomUUID().toString())
        insertStory(bugfix, StoryType.BUGFIX, bug.id, bug.version)
        quality.linkBugfixStory(bug.id, bugfix)

        runtime.reset()
        val retest = quality.requestBugfixRetest(RequestBugfixRetestCommand(productId, bug.id, bugfix, 1, "acceptance", "retest-bug"))
        completeSession(result(retest, "FAILED"))

        val current = qualityQueries.getBug(bug.id)
        assertThat(current.status).isEqualTo(BugStatus.OPEN)
        assertThat(current.version).isEqualTo(2)
        assertThat(planningQueries.getStory(bugfix).status).isEqualTo(StoryStatus.DONE)
        assertThat(planningQueries.findPlanningWorkItems(productId).map { it.source.version }).containsExactlyInAnyOrder(1, 2)
    }

    @Test
    fun `signaalonderzoek bewaart verificatie bij de oorspronkelijke melding`() {
        val signal = productCommands.submitUserSignal(SubmitUserSignalCommand(
            productId, UserSignalCategory.QUALITY_CONCERN, UserSignalUrgency.HIGH, "stakeholder-test",
            "De bewijsroute lijkt soms niet te laden.", actor = STAKEHOLDER, idempotencyKey = "quality-signal",
        ))
        val source = productQueries.getUserSignal(signal)
        assertThat(source.status).isEqualTo(UserSignalStatus.OPEN)
        val work = qualityQueries.findQualityWorkItems(productId).single().id
        completeSession(result(work, "PASSED").apply {
            (path("results")[0] as ObjectNode).put("signalOutcome", "Geen probleem gevonden")
        })

        val updated = productQueries.getUserSignal(signal)
        assertThat(updated.status).isEqualTo(UserSignalStatus.PROCESSED)
        assertThat(updated.outcome).isEqualTo("Geen probleem gevonden")
        assertThat(updated.verificationId).isNotNull()
    }

    @Test
    fun `testerblokkade publiceert historie maar geen kunstmatig snapshot`() {
        val work = quality.requestStoryVerification(RequestStoryVerificationCommand(productId, storyId, 1, "acceptance", 50, "tester-blocked"))
        completeSession(result(work, "BLOCKED").apply {
            (path("results")[0] as ObjectNode).put("blockedReason", "Testaccount is tijdelijk niet beschikbaar.")
        })

        assertThat(qualityQueries.findVerifications(VerificationFilter(productId)).single().outcome).isEqualTo(VerificationOutcome.BLOCKED)
        assertThat(qualityQueries.findQualityWorkItems(productId).single().retryable).isTrue()
        assertThat(qualityQueries.getCurrentQuality(productId)).isNull()

        quality.retryQualityWorkItem(work)
        val retry = qualityQueries.findQualityWorkItems(productId).single()
        assertThat(retry.id).isEqualTo(work)
        assertThat(retry.status).isEqualTo(WorkItemStatus.PENDING)
        assertThat(retry.attemptCount).isEqualTo(1)
    }

    @Test
    fun `geblokkeerde epiccontrole blijft verifying en hetzelfde workitem wordt retrybaar`() {
        jdbc.update("UPDATE pf_epic SET status='VERIFYING' WHERE id=?", epicId.value)
        jdbc.update("UPDATE pf_epic_version SET status='VERIFYING' WHERE epic_id=? AND version=1", epicId.value)
        val epic = designQueries.getEpic(epicId)
        val work = quality.requestEpicVerification(RequestEpicVerificationCommand(productId, epic.id, epic.version, "acceptance", 80, "blocked-epic"))
        completeSession(result(work, "BLOCKED").apply {
            (path("results")[0] as ObjectNode).put("blockedReason", "De testomgeving levert tijdelijk geen bronantwoord.")
        })

        assertThat(designQueries.getEpic(epicId).status).isEqualTo(EpicStatus.VERIFYING)
        assertThat(qualityQueries.findQualityWorkItems(productId).single().retryable).isTrue()
        quality.retryQualityWorkItem(work)
        val retry = qualityQueries.findQualityWorkItems(productId).single()
        assertThat(retry.id).isEqualTo(work)
        assertThat(retry.status).isEqualTo(WorkItemStatus.PENDING)
    }

    @Test
    fun `positief bewijs kan epic als niet succesvol afsluiten`() {
        jdbc.update("UPDATE pf_epic SET status='VERIFYING' WHERE id=?", epicId.value)
        jdbc.update("UPDATE pf_epic_version SET status='VERIFYING' WHERE epic_id=? AND version=1", epicId.value)
        val epic = designQueries.getEpic(epicId)
        val work = quality.requestEpicVerification(RequestEpicVerificationCommand(productId, epic.id, epic.version, "acceptance", 80, "not-successful"))
        completeSession(result(work, "NOT_SUCCESSFUL").apply {
            (path("results")[0] as ObjectNode).put(
                "explanation",
                "Alle afgesproken functies werken, maar herleidbare vooraf afgesproken gebruiksdata weerlegt het succescriterium.",
            )
        })

        assertThat(designQueries.getEpic(epicId).status).isEqualTo(EpicStatus.NOT_SUCCESSFUL)
        assertThat(qualityQueries.findVerifications(VerificationFilter(productId)).single().outcome).isEqualTo(VerificationOutcome.NOT_SUCCESSFUL)
    }

    private fun completeSession(result: ObjectNode) {
        quality.runProcessSession(productId)
        ai.dispatchPending()
        val job = runtime.onlyJob()
        runtime.results[job.id] = result
        runtime.jobs[job.id] = job.copy(status = "SUCCEEDED", phase = "COMPLETED", progressPercent = 100)
        ai.reconcileActive()
        quality.runProcessSession(productId)
    }

    private fun result(work: QualityWorkItemId, outcome: String) = mapper.createObjectNode().apply {
        put("outcome", "PUBLISH_RESULTS")
        putArray("results").addObject().apply {
            put("workItemId", work.value)
            put("outcome", outcome)
            putArray("checks").add("Hoofdroute vanaf een bekende schone uitgangssituatie uitgevoerd.")
            set<ObjectNode>("evidence", proof("Werkelijk gedrag is in de geconfigureerde acceptatieomgeving geobserveerd en herhaald."))
            putArray("missingCoverage")
            putArray("bugs")
            put("explanation", "De waarneming is herleidbaar tot het exacte bevroren doel.")
            put("signalOutcome", "Geen probleem gevonden")
        }
        putArray("memoryChanges")
    }

    private fun proof(description: String) = mapper.createObjectNode().apply {
        put("description", description)
        putArray("artifacts")
    }

    private fun insertEpic(status: EpicStatus) {
        val now = clock.instant()
        jdbc.update("INSERT INTO pf_epic(id,product_id,current_version,status,created_at,updated_at) VALUES (?,?,?,?,?,?)", epicId.value, productId.value, 1L, status.name, now, now)
        jdbc.update(
            """INSERT INTO pf_epic_version(epic_id,version,title,summary,problem,solution,direction_references_json,ux_design,
               acceptance_criteria_json,slicability_rationale,source_references_json,status,actor_type,actor_id,created_at)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            epicId.value, 1L, "Aantoonbaar bewijs", "Stakeholders controleren waarde via één bewijsroute.",
            "Waarde is niet zelfstandig controleerbaar.", "Bied een overzicht met een werkend bewijsdetail.", "[]",
            "Rustig overzicht en bewijsdetail.", mapper.writeValueAsString(listOf(CRITERION)), "De route is in twee zelfstandige stories te bouwen.",
            "[]", status.name, "PROCESS", "quality-test", now,
        )
    }

    private fun insertStory(id: StoryId, type: StoryType, bugId: BugId?, bugVersion: Long?) {
        val now = clock.instant()
        val sequence = (jdbc.queryForObject("SELECT COALESCE(MAX(sequence_number),0)+1 FROM pf_story WHERE product_id=?", Long::class.java, productId.value) ?: 1L)
        jdbc.update(
            """INSERT INTO pf_story(id,product_id,epic_id,epic_version,bug_id,bug_version,type,status,current_version,sequence_number,
               priority_reason,external_story_id,delivered_commit_sha,bug_link_confirmed,created_at,updated_at)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            id.value, productId.value, epicId.value, 1L, bugId?.value, bugVersion, type.name, "DONE", 1L, sequence,
            "Gericht kwaliteitsbewijs", "SF-${id.value.take(8)}", SHA, type != StoryType.BUGFIX, now, now,
        )
        jdbc.update(
            """INSERT INTO pf_story_version(story_id,version,title,summary,content,acceptance_criteria_json,ux_design,dependencies_json,
               source_references_json,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)""",
            id.value, 1L, if (type == StoryType.BUGFIX) "Bewijsroute herstellen" else "Bewijsroute tonen",
            "De Stakeholder gebruikt de aantoonbare bewijsroute.",
            "Bouw en lever de volledige gebruikersroute met fouttoestand, veilige terugroute en zelfstandig toetsbaar gedrag.",
            mapper.writeValueAsString(listOf(CRITERION)), "Rustig bewijsdetail", "[]", "[]", now,
        )
    }

    @TestConfiguration
    class Fakes {
        @Bean @Primary fun fakeRuntime() = FakeRuntime()
        @Bean @Primary fun fakeGit(): PublicGitRevisionResolver = object : PublicGitRevisionResolver {
            override fun resolveHead(publicGitUrl: String) = SHA
        }
        @Bean @Primary fun fakeDeploymentRevision() = FakeDeploymentRevisionResolver()
    }

    class FakeDeploymentRevisionResolver : DeploymentRevisionResolver {
        var revision: String = SHA
        override fun resolve(baseUrl: String, revisionEndpoint: String, revisionJsonPath: String) = revision
    }

    companion object {
        private const val SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val CRITERION = "De Stakeholder opent aantoonbaar het opgeslagen bewijs vanuit het overzicht."
        private val STAKEHOLDER = ActorReference(ActorType.STAKEHOLDER, "stakeholder@example.com")
    }
}
