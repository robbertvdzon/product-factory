package nl.vdzon.productfactory.quality

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import nl.vdzon.productfactory.ai.AiExecutionApplicationService
import nl.vdzon.productfactory.ai.AiArtifactStore
import nl.vdzon.productfactory.ai.RuntimeArtifactView
import nl.vdzon.productfactory.ai.FakeRuntime
import nl.vdzon.productfactory.api.design.*
import nl.vdzon.productfactory.api.foundation.DeploymentRevisionResolver
import nl.vdzon.productfactory.api.foundation.PublicGitRevisionResolver
import nl.vdzon.productfactory.api.planning.*
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.quality.*
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.product.AutomaticProductProcessing
import nl.vdzon.productfactory.planning.mvp.ProductPlanningMvpService
import nl.vdzon.productfactory.quality.mvp.QualityMvpService
import nl.vdzon.productfactory.quality.mvp.TransientTestEnvironmentFailure
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.security.MessageDigest
import java.util.HexFormat

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
    private val artifactStore: AiArtifactStore,
    private val runtime: FakeRuntime,
    private val revisions: FakeDeploymentRevisionResolver,
    private val gitRevisions: FakeGitRevisionResolver,
    private val automatic: AutomaticProductProcessing,
    private val mapper: ObjectMapper,
    private val jdbc: JdbcTemplate,
    private val clock: MutableQualityClock,
) {
    private var productId = ProductId("not-initialized")
    private var epicId = EpicId("not-initialized")
    private var storyId = StoryId("not-initialized")
    private val repairStorageKeys = mutableListOf<String>()

    @AfterEach
    fun deleteRepairEvidence() {
        repairStorageKeys.forEach(artifactStore::delete)
        repairStorageKeys.clear()
    }

    @BeforeEach
    fun prepare() {
        qualityImpl.deleteAllOwnedData()
        planningImpl.deleteAllOwnedData()
        ai.deleteAllOwnedExecutionData()
        runtime.reset()
        revisions.revision = SHA
        revisions.failure = null
        revisions.afterResolve = null
        revisions.resolveCalls = 0
        gitRevisions.reset()
        clock.now = Instant.parse("2026-10-13T08:00:00Z")
        productId = ProductId("quality-${UUID.randomUUID().toString().take(8)}")
        epicId = EpicId(UUID.randomUUID().toString())
        storyId = StoryId(UUID.randomUUID().toString())
        productCommands.createProduct(CreateProductCommand(productId, "Quality test", actor = STAKEHOLDER, idempotencyKey = "create-${productId.value}"))
        productCommands.updateProductAssignment(UpdateProductAssignmentCommand(
            productId, "Stakeholders", "Bewijs werkende gebruikerswaarde",
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
    fun `tester schema begrenst de uitleg volgens het epic publicatiecontract`() {
        quality.requestStoryVerification(RequestStoryVerificationCommand(productId, storyId, 1, "acceptance", 50, "explanation-schema"))
        quality.runProcessSession(productId)
        ai.dispatchPending()
        val properties = runtime.requests.single().responseSchema!!.path("properties").path("results").path("items").path("properties")
        assertThat(properties.path("explanation").path("minLength").asInt()).isEqualTo(10)
        assertThat(properties.path("explanation").path("maxLength").asInt()).isEqualTo(2000)
        assertThat(properties.path("signalOutcome").path("maxLength").asInt()).isEqualTo(200)
        assertThat(properties.path("testBlockers").path("items").path("properties").path("type").path("enum").map { it.asText() })
            .containsExactlyInAnyOrder("INSUFFICIENT_TEST_EVIDENCE", "TEST_ACCESS_UNAVAILABLE", "TEST_CAPABILITY_UNAVAILABLE", "TEST_ENVIRONMENT_UNAVAILABLE", "TRANSIENT_TECHNICAL_FAILURE")
        assertThat(properties.path("implementationGaps").path("items").path("required").map { it.asText() })
            .contains("criterion", "observedBehaviour", "coverageExplanation", "reviewedStoryIds", "evidence")
        assertThat(jdbc.queryForObject("SELECT prompt_template_version FROM pf_ai_task WHERE product_id=?", Long::class.java, productId.value)).isEqualTo(6L)
        ai.deleteAllOwnedExecutionData()
        runtime.reset()
    }

    @Test
    fun `productieverzoek wordt uitsluitend tegen acceptatie uitgevoerd`() {
        val work = quality.requestStoryVerification(RequestStoryVerificationCommand(productId, storyId, 1, "production", 50, "production-request"))
        completeSession(result(work, "PASSED"))
        assertThat(qualityQueries.findVerifications(VerificationFilter(productId)).single().environment).isEqualTo("acceptance")
    }

    @Test
    fun `tester krijgt uitvoerbare Node browserinstructies en uitsluitend de acceptatieomgeving`() {
        val configuration = productQueries.getTestableProduct(productId)
        productCommands.configureTestableProduct(ConfigureTestableProductCommand(
            productId, configuration.acceptance,
            TestEnvironmentConfiguration(
                "production", "https://production.example.test", listOf("/"), "/api/version", "$.commit",
                accessBoundaries = listOf("Alleen begeleide handmatige toegang."),
            ),
            configuration.version, STAKEHOLDER, "browser-instructions-${productId.value}",
        ))
        quality.requestStoryVerification(RequestStoryVerificationCommand(productId, storyId, 1, "production", 50, "browser-instructions"))
        quality.runProcessSession(productId)
        ai.dispatchPending()

        assertThat(runtime.requests.single().prompt)
            .contains("NODE_PATH=\"${'$'}(npm root -g)\"")
            .contains("PLAYWRIGHT_BROWSERS_PATH=\"${'$'}{PLAYWRIGHT_BROWSERS_PATH:-/ms-playwright}\"")
            .contains("const { chromium } = require('playwright');")
            .contains("chromium.launch({ headless: true, args: ['--no-sandbox'] })")
            .contains("await browser.close();")
            .contains("Een geslaagde startcontrole of API-call bewijst geen werkend scherm")
            .contains("browserContext.request.post met maxRedirects: 0")
            .contains("gebruik geen globale extraHTTPHeaders met de credential")
            .contains("path: '/job/output/artifacts/evidence-01', type: 'png'")
            .contains("TEST_CAPABILITY_UNAVAILABLE")
            .contains("lijst hun exacte namen en tel ze")
            .contains("overgeslagen nummers blijven overgeslagen")
            .contains("https://acceptance.example.test")
            .doesNotContain("https://production.example.test")
        val artifacts = mapper.readTree(jdbc.queryForObject(
            """SELECT s.output_artifacts_json FROM pf_ai_task_specification s
               JOIN pf_ai_task t ON t.id=s.task_id WHERE t.product_id=?""",
            String::class.java, productId.value,
        ))
        assertThat(artifacts.map { it.path("name").asText() })
            .containsExactlyElementsOf((1..50).map { "evidence-${it.toString().padStart(2, '0')}" })
        artifacts.forEach {
            assertThat(it.path("mimeTypes").map { type -> type.asText() }).containsExactly("image/png")
            assertThat(it.path("maxBytes").asLong()).isEqualTo(5L * 1024 * 1024)
        }
        ai.deleteAllOwnedExecutionData()
        runtime.reset()
    }

    @Test
    fun `deployment tijdens de test blokkeert goedkeuring`() {
        val work = quality.requestStoryVerification(RequestStoryVerificationCommand(productId, storyId, 1, "acceptance", 50, "changed-deploy"))
        quality.runProcessSession(productId)
        ai.dispatchPending()
        val job = runtime.onlyJob()
        runtime.results[job.id] = result(work, "PASSED")
        runtime.jobs[job.id] = job.copy(status = "SUCCEEDED", phase = "COMPLETED", progressPercent = 100)
        revisions.revision = "b".repeat(40)
        ai.reconcileActive()
        quality.runProcessSession(productId)
        assertThat(qualityQueries.findVerifications(VerificationFilter(productId))).isEmpty()
        assertThat(jdbc.queryForObject("SELECT error_code FROM pf_quality_work_item WHERE id=?", String::class.java, work.value)).isEqualTo("TEST_ENVIRONMENT_CHANGED")
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
    fun `tester mag een bewezen nieuwere deployment testen maar afwijzing zonder defect blijft geblokkeerd`() {
        revisions.revision = "b".repeat(40)
        gitRevisions.ancestors[revisions.revision] = setOf(SHA)
        val work = quality.requestStoryVerification(RequestStoryVerificationCommand(productId, storyId, 1, "acceptance", 50, "deployment-lagging"))
        completeSession(result(work, "NEEDS_WORK"))

        val item = qualityQueries.findQualityWorkItems(productId).single()
        assertThat(item.status).isEqualTo(WorkItemStatus.BLOCKED)
        assertThat(item.attemptCount).isEqualTo(1)
        val verification = qualityQueries.findVerifications(VerificationFilter(productId)).single()
        assertThat(verification.outcome).isEqualTo(VerificationOutcome.BLOCKED)
        assertThat(verification.testedRevision).isEqualTo("b".repeat(40))
        assertThat(verification.requiredCommitSha).isEqualTo(SHA)
    }

    @Test
    fun `achterlopende deployment wacht zonder testpoging en start automatisch zodra een latere revision alles bevat`() {
        val oldRevision = "b".repeat(40)
        val laterRevision = "c".repeat(40)
        gitRevisions.ancestors[oldRevision] = emptySet()
        gitRevisions.ancestors[laterRevision] = setOf(SHA)
        revisions.revision = oldRevision
        val work = quality.requestStoryVerification(RequestStoryVerificationCommand(productId, storyId, 1, "acceptance", 50, "deployment-wait"))

        automatic.check(productId.value, ScheduledProcess.QUALITY_ASSURANCE)
        ai.dispatchPending()
        val waiting = qualityQueries.findQualityWorkItems(productId).single()
        assertThat(waiting.id).isEqualTo(work)
        assertThat(waiting.status).isEqualTo(WorkItemStatus.PENDING)
        assertThat(waiting.errorCode).isEqualTo("WAITING_FOR_DEPLOYMENT")
        assertThat(waiting.blockedReason).contains(oldRevision.take(12), SHA.take(12))
        assertThat(waiting.attemptCount).isZero()
        assertThat(waiting.lastAttemptAt).isNull()
        assertThat(waiting.attempts).isEmpty()
        assertThat(waiting.retryAfter).isNull()
        assertThat(waiting.retryable).isFalse()
        assertThat(runtime.requests).isEmpty()
        assertThat(qualityQueries.findVerifications(VerificationFilter(productId))).isEmpty()

        // Both the scheduler and a direct process call respect the deployment polling interval.
        clock.advance(Duration.ofSeconds(59))
        repeat(3) {
            automatic.check(productId.value, ScheduledProcess.QUALITY_ASSURANCE)
            quality.runProcessSession(productId)
        }
        assertThat(gitRevisions.checks).hasSize(1)
        assertThat(qualityQueries.findQualityWorkItems(productId).single().version).isEqualTo(waiting.version)
        clock.advance(Duration.ofSeconds(1))
        automatic.check(productId.value, ScheduledProcess.QUALITY_ASSURANCE)
        val checkedAgain = qualityQueries.findQualityWorkItems(productId).single()
        assertThat(checkedAgain.version).isEqualTo(waiting.version + 1)
        assertThat(checkedAgain.attempts).isEmpty()
        assertThat(gitRevisions.checks).hasSize(2)

        revisions.revision = laterRevision
        clock.advance(Duration.ofSeconds(60))
        automatic.check(productId.value, ScheduledProcess.QUALITY_ASSURANCE)
        ai.dispatchPending()
        val started = qualityQueries.findQualityWorkItems(productId).single()
        assertThat(started.status).isEqualTo(WorkItemStatus.IN_PROGRESS)
        assertThat(started.attemptCount).isEqualTo(1)
        assertThat(started.attempts).hasSize(1)
        assertThat(started.errorCode).isNull()
        assertThat(started.blockedReason).isNull()
        assertThat(runtime.requests).hasSize(1)
        automatic.check(productId.value, ScheduledProcess.QUALITY_ASSURANCE)
        ai.dispatchPending()
        assertThat(runtime.requests).hasSize(1)
    }

    @Test
    fun `epic wacht op alle opgeleverde commits en negeert geannuleerde stories`() {
        val other = StoryId(UUID.randomUUID().toString())
        val cancelled = StoryId(UUID.randomUUID().toString())
        val otherSha = "b".repeat(40)
        val cancelledSha = "c".repeat(40)
        val deployed = "d".repeat(40)
        insertStory(other, StoryType.PRODUCT_STORY, null, null)
        insertStory(cancelled, StoryType.PRODUCT_STORY, null, null)
        jdbc.update("UPDATE pf_story SET delivered_commit_sha=? WHERE id=?", otherSha, other.value)
        jdbc.update("UPDATE pf_story SET status='CANCELLED',delivered_commit_sha=? WHERE id=?", cancelledSha, cancelled.value)
        epicVerification("all-deliveries")
        revisions.revision = deployed
        gitRevisions.ancestors[deployed] = setOf(SHA)

        quality.runProcessSession(productId)
        ai.dispatchPending()
        assertThat(gitRevisions.checks.single()).isEqualTo(deployed to setOf(SHA, otherSha))
        assertThat(qualityQueries.findQualityWorkItems(productId).single().attemptCount).isZero()
        assertThat(runtime.requests).isEmpty()
        gitRevisions.ancestors[deployed] = setOf(SHA, otherSha)
        clock.advance(Duration.ofSeconds(60))
        quality.runProcessSession(productId)
        ai.dispatchPending()
        assertThat(runtime.requests).hasSize(1)
        assertThat(qualityQueries.findQualityWorkItems(productId).single().attemptCount).isEqualTo(1)
        assertThat(runtime.requests.single().prompt).doesNotContain(cancelled.value)
    }

    @Test
    fun `epiccontext bevat exact de stories waarvan de commits voor de gate zijn bevroren`() {
        val deliveredDuringCheck = StoryId(UUID.randomUUID().toString())
        val laterSha = "b".repeat(40)
        epicVerification("frozen-delivery-snapshot")
        gitRevisions.onCheck = {
            insertStory(deliveredDuringCheck, StoryType.PRODUCT_STORY, null, null)
            jdbc.update("UPDATE pf_story SET delivered_commit_sha=? WHERE id=?", laterSha, deliveredDuringCheck.value)
        }
        quality.runProcessSession(productId)
        ai.dispatchPending()

        assertThat(gitRevisions.checks.single()).isEqualTo(SHA to setOf(SHA))
        assertThat(planningQueries.findStories(StoryFilter(productId, epicId = epicId, statuses = setOf(StoryStatus.DONE)))).hasSize(2)
        assertThat(runtime.requests.single().prompt).contains(storyId.value).doesNotContain(deliveredDuringCheck.value, laterSha)
        val context = mapper.readTree(jdbc.queryForObject(
            "SELECT frozen_context_json FROM pf_quality_process_session WHERE product_id=? AND status='WAITING_FOR_AI'",
            String::class.java, productId.value,
        ))
        assertThat(context.path("workItems")[0].path("detail").path("stories").map { it.path("id").asText() })
            .containsExactly(storyId.value)
    }

    @Test
    fun `batch gebruikt dezelfde deploymentrevision voor iedere gate en de AI context`() {
        val other = StoryId(UUID.randomUUID().toString())
        val otherSha = "b".repeat(40)
        val checkedRevision = "c".repeat(40)
        val changedRevision = "d".repeat(40)
        insertStory(other, StoryType.PRODUCT_STORY, null, null)
        jdbc.update("UPDATE pf_story SET delivered_commit_sha=? WHERE id=?", otherSha, other.value)
        gitRevisions.ancestors[checkedRevision] = setOf(SHA, otherSha)
        gitRevisions.ancestors[changedRevision] = setOf(otherSha)
        revisions.revision = checkedRevision
        revisions.afterResolve = { revisions.revision = changedRevision }
        quality.requestStoryVerification(RequestStoryVerificationCommand(productId, storyId, 1, "acceptance", 80, "batch-first"))
        quality.requestStoryVerification(RequestStoryVerificationCommand(productId, other, 1, "acceptance", 70, "batch-second"))
        quality.runProcessSession(productId)
        ai.dispatchPending()

        assertThat(revisions.resolveCalls).isEqualTo(1)
        assertThat(gitRevisions.checks).containsExactly(checkedRevision to setOf(SHA), checkedRevision to setOf(otherSha))
        assertThat(runtime.requests).hasSize(1)
        val context = mapper.readTree(jdbc.queryForObject(
            "SELECT frozen_context_json FROM pf_quality_process_session WHERE product_id=? AND status='WAITING_FOR_AI'",
            String::class.java, productId.value,
        ))
        assertThat(context.path("deployedRevisions").path("acceptance").asText()).isEqualTo(checkedRevision)
        assertThat(context.path("workItems").size()).isEqualTo(2)
        assertThat(runtime.requests.single().prompt).doesNotContain(changedRevision)
    }

    @Test
    fun `bugfixhertest wacht op zijn eigen oplevercommit`() {
        val bug = BugId(UUID.randomUUID().toString())
        val fix = StoryId(UUID.randomUUID().toString())
        val fixSha = "b".repeat(40)
        insertBug(bug)
        insertStory(fix, StoryType.BUGFIX, bug, 1)
        jdbc.update("UPDATE pf_story SET delivered_commit_sha=? WHERE id=?", fixSha, fix.value)
        quality.requestBugfixRetest(RequestBugfixRetestCommand(productId, bug, fix, 1, "acceptance", "retest-waits-for-fix"))
        quality.runProcessSession(productId)
        ai.dispatchPending()

        assertThat(gitRevisions.checks.single()).isEqualTo(SHA to setOf(fixSha))
        assertThat(qualityQueries.findQualityWorkItems(productId).single().errorCode).isEqualTo("WAITING_FOR_DEPLOYMENT")
        assertThat(qualityQueries.findQualityWorkItems(productId).single().attemptCount).isZero()
        assertThat(runtime.requests).isEmpty()
    }

    @Test
    fun `onbekende ancestry blokkeert handmatig zonder AI of testpoging`() {
        revisions.revision = "b".repeat(40)
        epicVerification("unknown-ancestry")
        quality.runProcessSession(productId)
        clock.advance(Duration.ofDays(7))
        automatic.check(productId.value, ScheduledProcess.QUALITY_ASSURANCE)
        ai.dispatchPending()

        val item = qualityQueries.findQualityWorkItems(productId).single()
        assertThat(item.status).isEqualTo(WorkItemStatus.BLOCKED)
        assertThat(item.errorCode).isEqualTo("DEPLOYMENT_REVISION_UNVERIFIED")
        assertThat(item.retryAfter).isNull()
        assertThat(item.retryable).isTrue()
        assertThat(item.attentionNeeded).isTrue()
        assertThat(item.attemptCount).isZero()
        assertThat(item.attempts).isEmpty()
        assertThat(runtime.requests).isEmpty()
    }

    @Test
    fun `ontbrekende oplevercommit wordt geen veronderstelde geslaagde deploymentcontrole`() {
        jdbc.update("UPDATE pf_story SET delivered_commit_sha=NULL WHERE id=?", storyId.value)
        epicVerification("missing-delivery-commit")
        quality.runProcessSession(productId)
        ai.dispatchPending()
        val item = qualityQueries.findQualityWorkItems(productId).single()
        assertThat(item.status).isEqualTo(WorkItemStatus.BLOCKED)
        assertThat(item.errorCode).isEqualTo("DEPLOYMENT_REVISION_UNVERIFIED")
        assertThat(item.blockedReason).contains("oplevercommit")
        assertThat(item.retryAfter).isNull()
        assertThat(item.attemptCount).isZero()
        assertThat(gitRevisions.checks).isEmpty()
        assertThat(runtime.requests).isEmpty()
    }

    @Test
    fun `deploymentwachten en startblokkade behouden het bewijs van een eerdere testpoging`() {
        val work = epicVerification("preserved-attempt-history")
        completeSession(result(work, "BLOCKED").apply {
            (path("results")[0] as ObjectNode).put("blockedReason", "De beloofde testvoorziening moet eerst worden hersteld.")
        })
        val historicalAttempt = qualityQueries.findQualityWorkItems(productId).single().attempts.single()
        quality.retryQualityWorkItem(work)
        revisions.revision = "b".repeat(40)
        gitRevisions.ancestors[revisions.revision] = emptySet()
        quality.runProcessSession(productId)
        assertThat(qualityQueries.findQualityWorkItems(productId).single().attempts).containsExactly(historicalAttempt)

        revisions.failure = InvalidCommand("Revisionendpoint is niet geconfigureerd.")
        clock.advance(Duration.ofSeconds(60))
        quality.runProcessSession(productId)
        val item = qualityQueries.findQualityWorkItems(productId).single()
        assertThat(item.status).isEqualTo(WorkItemStatus.BLOCKED)
        assertThat(item.attemptCount).isEqualTo(1)
        assertThat(item.attempts).containsExactly(historicalAttempt)
        assertThat(item.retryAfter).isNull()
        assertThat(runtime.requests).hasSize(1)
    }

    @Test
    fun `epic needs work publiceert bewezen nieuw criterium en afzonderlijke reproduceerbare bug`() {
        jdbc.update("UPDATE pf_epic_version SET acceptance_criteria_json=? WHERE epic_id=? AND version=1", mapper.writeValueAsString(listOf(CRITERION, MISSING_CRITERION)), epicId.value)
        jdbc.update("UPDATE pf_epic SET status='VERIFYING' WHERE id=?", epicId.value)
        jdbc.update("UPDATE pf_epic_version SET status='VERIFYING' WHERE epic_id=? AND version=1", epicId.value)
        val epic = designQueries.getEpic(epicId)
        val work = quality.requestEpicVerification(RequestEpicVerificationCommand(productId, epic.id, epic.version, "acceptance", 80, "verify-epic"))
        completeSession(result(work, "NEEDS_WORK").apply {
            (path("results")[0] as ObjectNode).apply {
                put("explanation", "De kernroute mist aantoonbaar het tweede afgesproken resultaat.")
                putArray("missingCoverage").add(MISSING_CRITERION)
                putArray("implementationGaps").add(implementationGap(MISSING_CRITERION))
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

        assertThat(qualityQueries.findVerifications(VerificationFilter(productId)).single().missingCoverage).containsExactly(MISSING_CRITERION)
        assertThat(qualityQueries.findBugs(BugFilter(productId))).hasSize(1)
        assertThat(designQueries.getEpic(epicId).status).isEqualTo(EpicStatus.ACTIVE)
        assertThat(planningQueries.findPlanningWorkItems(productId).map { it.type })
            .containsExactlyInAnyOrder(PlanningWorkItemType.PLAN_EPIC_GAP, PlanningWorkItemType.PLAN_BUGFIX)
    }

    @Test
    fun `epic passed met resterende dekkingsgaten completeert de epic zonder onplanbaar gatverzoek`() {
        jdbc.update("UPDATE pf_epic SET status='VERIFYING' WHERE id=?", epicId.value)
        jdbc.update("UPDATE pf_epic_version SET status='VERIFYING' WHERE epic_id=? AND version=1", epicId.value)
        val epic = designQueries.getEpic(epicId)
        val work = quality.requestEpicVerification(RequestEpicVerificationCommand(productId, epic.id, epic.version, "acceptance", 80, "verify-epic-passed-gap"))
        completeSession(result(work, "PASSED").apply {
            (path("results")[0] as ObjectNode).putArray("missingCoverage").add("Randgeval met een lege bronlijst is niet apart live geforceerd.")
        })

        // recordEpicVerification verhoogt de epicversie altijd met 1, ook bij PASSED — dat mag de
        // sessie niet meer laten falen op een versiemismatch (regressie op de eerdere bugfix).
        // Een PASSED epic wordt COMPLETED en is nooit meer planbaar, dus een restgat daar mag geen
        // PLAN_EPIC_GAP-item opleveren: dat zou voor altijd vastlopen (regressie op de bugfix hierna).
        assertThat(designQueries.getEpic(epicId).status).isEqualTo(EpicStatus.COMPLETED)
        assertThat(qualityQueries.findQualityWorkItems(productId).single().status).isEqualTo(WorkItemStatus.DONE)
        assertThat(planningQueries.findPlanningWorkItems(productId)).isEmpty()
    }

    @Test
    fun `een mockvergadering en ontbrekende browser blokkeren verificatie zonder nieuwe stories of bugs`() {
        val work = epicVerification("missing-test-evidence")
        completeSession(result(work, "NEEDS_WORK").apply {
            (path("results")[0] as ObjectNode).apply {
                putArray("missingCoverage").add("Meerdere vergaderingen binnen tien dagen zijn niet live getest.")
                putArray("testBlockers").apply {
                    addObject().put("type", "INSUFFICIENT_TEST_EVIDENCE").put("reason", "De acceptatiemock bevat slechts één vergadering; het scenario met drie vergaderingen kan niet worden uitgevoerd.")
                    addObject().put("type", "TEST_CAPABILITY_UNAVAILABLE").put("reason", "Er is geen browser beschikbaar om de agenda en de uitkomsten per vergadering visueel te controleren.")
                }
                // Zelfs een onjuist meegestuurde bug of gap mag de testblokkade niet omzetten in ontwikkeling.
                putArray("implementationGaps").add(implementationGap(CRITERION))
                putArray("bugs").addObject().put("title", "Ongeteste schermen")
            }
        })

        assertBlockedWithoutDevelopment(work)
        assertThat(qualityQueries.findVerifications(VerificationFilter(productId)).single().blockedReason)
            .contains("slechts één vergadering", "geen browser")
        assertThat(qualityQueries.getCurrentQuality(productId)).isNull()
        quality.retryQualityWorkItem(work)
        assertThat(qualityQueries.findQualityWorkItems(productId).single().status).isEqualTo(WorkItemStatus.PENDING)
    }

    @Test
    fun `structurele of ongetypeerde blokkade start ook na een week geen nieuwe test`() {
        val work = epicVerification("structural-no-retry")
        completeSession(result(work, "BLOCKED").apply {
            (path("results")[0] as ObjectNode).put("blockedReason", "De vereiste acceptatietestvoorziening ontbreekt; herstel is nodig.")
        })
        clock.advance(Duration.ofDays(7))
        repeat(3) { quality.runProcessSession(productId); ai.dispatchPending() }

        assertBlockedWithoutDevelopment(work)
        val item = qualityQueries.findQualityWorkItems(productId).single()
        assertThat(item.retryAfter).isNull()
        assertThat(item.attentionNeeded).isTrue()
        assertThat(item.attemptCount).isEqualTo(1)
        assertThat(runtime.requests).hasSize(1)
        assertThat(qualityQueries.findVerifications(VerificationFilter(productId))).hasSize(1)
    }

    @Test
    fun `uitsluitend tijdelijke technische storing hervat na backoff hetzelfde werkitem`() {
        val work = epicVerification("transient-retry")
        completeSession(result(work, "BLOCKED").apply {
            (path("results")[0] as ObjectNode).putArray("testBlockers").addObject()
                .put("type", "TRANSIENT_TECHNICAL_FAILURE")
                .put("reason", "De werkende testorigin antwoordt tijdelijk met HTTP 503 tijdens de gecontroleerde test.")
        })
        val blocked = qualityQueries.findQualityWorkItems(productId).single()
        assertThat(blocked.retryAfter).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)))
        assertThat(blocked.attentionNeeded).isFalse()
        clock.advance(Duration.ofMinutes(14))
        quality.runProcessSession(productId)
        ai.dispatchPending()
        assertThat(runtime.requests).hasSize(1)
        clock.advance(Duration.ofMinutes(1))
        quality.runProcessSession(productId)
        ai.dispatchPending()
        val retried = qualityQueries.findQualityWorkItems(productId).single()
        assertThat(retried.id).isEqualTo(work)
        assertThat(retried.attemptCount).isEqualTo(2)
        assertThat(retried.status).isEqualTo(WorkItemStatus.IN_PROGRESS)
        assertThat(runtime.requests).hasSize(2)
        assertThat(qualityQueries.findVerifications(VerificationFilter(productId))).hasSize(1)
        assertThat(planningQueries.findPlanningWorkItems(productId)).isEmpty()
        quality.runProcessSession(productId)
        ai.dispatchPending()
        assertThat(runtime.requests).hasSize(2)
    }

    @Test
    fun `structurele blokkade heeft voorrang op tijdelijke technische storing`() {
        val work = epicVerification("mixed-blockers")
        completeSession(result(work, "BLOCKED").apply {
            (path("results")[0] as ObjectNode).putArray("testBlockers").apply {
                addObject().put("type", "TRANSIENT_TECHNICAL_FAILURE").put("reason", "De testorigin gaf tijdelijk HTTP 503 terug bij de browsercontrole.")
                addObject().put("type", "TEST_ACCESS_UNAVAILABLE").put("reason", "De benodigde testcredential is niet geconfigureerd en moet eerst worden ingericht.")
            }
        })
        clock.advance(Duration.ofDays(7))
        quality.runProcessSession(productId)
        ai.dispatchPending()
        assertBlockedWithoutDevelopment(work)
        assertThat(qualityQueries.findQualityWorkItems(productId).single().retryAfter).isNull()
        assertThat(runtime.requests).hasSize(1)
    }

    @Test
    fun `ongeldig testerresultaat wacht op herstel zonder automatische nieuwe uitvoering`() {
        val work = epicVerification("invalid-no-retry")
        completeSession(result(work, "PASSED").apply {
            (path("results")[0] as ObjectNode).put("workItemId", "onbekend-workitem")
        })
        clock.advance(Duration.ofDays(7))
        quality.runProcessSession(productId)
        ai.dispatchPending()
        val item = qualityQueries.findQualityWorkItems(productId).single()
        assertThat(item.status).isEqualTo(WorkItemStatus.FAILED)
        assertThat(item.errorCode).isEqualTo("QUALITY_RESULT_INVALID")
        assertThat(item.retryable).isTrue()
        assertThat(item.retryAfter).isNull()
        assertThat(item.attentionNeeded).isTrue()
        assertThat(item.attemptCount).isEqualTo(1)
        assertThat(runtime.requests).hasSize(1)
        assertThat(qualityQueries.findVerifications(VerificationFilter(productId))).isEmpty()
        assertThat(planningQueries.findPlanningWorkItems(productId)).isEmpty()
    }

    @Test
    fun `runtime rate limit krijgt backoff maar onbekende uitvoeringsfout niet`() {
        val work = epicVerification("runtime-rate-limit")
        failRuntime("PROVIDER_RATE_LIMIT")
        assertThat(qualityQueries.findQualityWorkItems(productId).single().retryAfter)
            .isEqualTo(clock.instant().plus(Duration.ofMinutes(15)))
        quality.retryQualityWorkItem(work)
        failRuntime("ENGINE_FAILED")
        val item = qualityQueries.findQualityWorkItems(productId).single()
        assertThat(item.status).isEqualTo(WorkItemStatus.FAILED)
        assertThat(item.retryAfter).isNull()
        assertThat(item.attentionNeeded).isTrue()
        clock.advance(Duration.ofDays(7))
        quality.runProcessSession(productId)
        ai.dispatchPending()
        assertThat(runtime.requests).hasSize(2)
        assertThat(qualityQueries.findVerifications(VerificationFilter(productId))).isEmpty()
        assertThat(planningQueries.findPlanningWorkItems(productId)).isEmpty()
    }

    @Test
    fun `uitgeputte runtime testtijd wordt niet automatisch met een nieuw budget herhaald`() {
        epicVerification("runtime-budget-exhausted")
        failRuntime("EXECUTION_TIMEOUT")
        assertThat(qualityQueries.findQualityWorkItems(productId).single().retryAfter).isNull()
        clock.advance(Duration.ofDays(7))
        quality.runProcessSession(productId)
        ai.dispatchPending()
        assertThat(runtime.requests).hasSize(1)
    }

    @Test
    fun `tijdelijke revisionstoring krijgt backoff en configuratiefout wacht op herstel`() {
        val work = epicVerification("revision-transient")
        revisions.failure = TransientTestEnvironmentFailure("Revisionendpoint antwoordt tijdelijk HTTP 503.")
        quality.runProcessSession(productId)
        assertThat(qualityQueries.findQualityWorkItems(productId).single().retryAfter)
            .isEqualTo(clock.instant().plus(Duration.ofMinutes(15)))
        quality.retryQualityWorkItem(work)
        revisions.failure = InvalidCommand("Revisionendpoint antwoordt HTTP 404 wegens een verkeerde configuratie.")
        quality.runProcessSession(productId)
        assertThat(qualityQueries.findQualityWorkItems(productId).single().retryAfter).isNull()
        assertThat(runtime.requests).isEmpty()
    }

    @Test
    fun `opgeleverde fixture met reproduceerbare 404 vraagt een bugfix en geen nieuwe epicstories`() {
        jdbc.update("UPDATE pf_story_version SET title=?,acceptance_criteria_json=? WHERE story_id=? AND version=1",
            "Acceptatiescenarios bedienen", mapper.writeValueAsString(listOf("De gedocumenteerde acceptatieroute bedient meerdere vergaderingen.")), storyId.value)
        val work = epicVerification("broken-delivered-fixture")
        completeSession(result(work, "NEEDS_WORK").apply {
            (path("results")[0] as ObjectNode).apply {
                putArray("missingCoverage").add("Schermcontrole van drie vergaderingen kon door de defecte scenarioroute niet worden uitgevoerd.")
                putArray("bugs").addObject().apply {
                    put("title", "Opgeleverde acceptatiescenarioroute geeft 404")
                    put("summary", "De opgeleverde fixturebediening is op acceptatie niet bereikbaar.")
                    put("actualBehaviour", "Na succesvolle testlogin geeft de gedocumenteerde /api/test/scenario route herhaald HTTP 404 op revision $SHA.")
                    put("expectedBehaviour", "De in DONE-story ${storyId.value} beloofde route stelt het scenario met meerdere vergaderingen beschikbaar.")
                    putArray("reproductionSteps").add("Controleer acceptatierevision $SHA.").add("Log in met de geconfigureerde testlogin.").add("Open de toegestane /api/test/scenario route tweemaal en observeer 404.")
                    put("impact", "De opgeleverde testvoorziening werkt niet en verhindert de resterende epiccontrole.")
                    put("severity", "P1")
                    set<ObjectNode>("evidence", proof("Login geslaagd en juiste revision gecontroleerd; de toegestane scenarioroute antwoordt reproduceerbaar 404."))
                }
            }
        })
        assertThat(runtime.requests.single().prompt)
            .contains("Een beloofde en als DONE opgeleverde testvoorziening is zelf afgesproken productgedrag")
            .contains("Gebruik voor dezelfde oorzaak geen testBlocker en geen implementationGap")
            .contains("Een reproduceerbare 404, ontbrekende configuratie/fixture/credential, 401/403, uitgeputte testtijd of onbekende oorzaak is niet tijdelijk")
        repeat(2) { quality.runProcessSession(productId) }
        assertThat(qualityQueries.findVerifications(VerificationFilter(productId)).single().outcome).isEqualTo(VerificationOutcome.NEEDS_WORK)
        assertThat(qualityQueries.findBugs(BugFilter(productId))).hasSize(1)
        assertThat(planningQueries.findPlanningWorkItems(productId).map { it.type }).containsExactly(PlanningWorkItemType.PLAN_BUGFIX)
        assertThat(planningQueries.getStory(storyId).status).isEqualTo(StoryStatus.DONE)
        assertThat(planningQueries.findStories(StoryFilter(productId))).hasSize(1)
        assertThat(designQueries.getEpic(epicId).status).isEqualTo(EpicStatus.ACTIVE)
    }

    private fun failRuntime(errorCode: String) {
        quality.runProcessSession(productId)
        ai.dispatchPending()
        val job = runtime.jobs.values.last()
        runtime.jobs[job.id] = job.copy(status = "FAILED", phase = "FAILED", errorCode = errorCode, errorMessage = "Veilige uitvoeringsfout.")
        ai.reconcileActive()
        quality.runProcessSession(productId)
    }

    @Test
    fun `vrije ontbrekende dekking zonder bewezen implementatiegat plant niets`() {
        val work = epicVerification("unclassified-coverage")
        completeSession(result(work, "NEEDS_WORK").apply {
            (path("results")[0] as ObjectNode).putArray("missingCoverage").add("Het scenario is nog niet bewezen.")
        })
        assertBlockedWithoutDevelopment(work)
    }

    @Test
    fun `meerdere lange testblokkades blijven een retrybare verificatie met begrensde reden`() {
        val work = epicVerification("long-test-blockers")
        val firstReason = "Testdata ontbreekt: " + "a".repeat(950)
        val secondReason = "Browser ontbreekt: " + "b".repeat(950)
        completeSession(result(work, "NEEDS_WORK").apply {
            (path("results")[0] as ObjectNode).putArray("testBlockers").apply {
                addObject().put("type", "INSUFFICIENT_TEST_EVIDENCE").put("reason", firstReason)
                addObject().put("type", "TEST_CAPABILITY_UNAVAILABLE").put("reason", secondReason)
            }
        })

        assertBlockedWithoutDevelopment(work)
        assertThat(qualityQueries.findVerifications(VerificationFilter(productId)).single().blockedReason).hasSize(1000)
        val stored = jdbc.queryForObject("SELECT response_json FROM pf_ai_task_result WHERE task_id IN (SELECT id FROM pf_ai_task WHERE product_id=?)", String::class.java, productId.value)
        assertThat(stored).contains(firstReason, secondReason)
    }

    @Test
    fun `lang geldig implementatiebewijs blijft volledig bewaard met begrensde plansamenvatting`() {
        val anotherCriterion = "De Stakeholder archiveert het bewijs vanuit het overzicht."
        jdbc.update("UPDATE pf_epic_version SET acceptance_criteria_json=? WHERE epic_id=? AND version=1", mapper.writeValueAsString(listOf(CRITERION, MISSING_CRITERION, anotherCriterion)), epicId.value)
        val work = epicVerification("long-gap-evidence")
        val proofText = "Observeerbaar bewijs: " + "c".repeat(9500)
        completeSession(result(work, "NEEDS_WORK").apply {
            (path("results")[0] as ObjectNode).putArray("implementationGaps").apply {
                listOf(MISSING_CRITERION, anotherCriterion).forEach { criterion ->
                    add(implementationGap(criterion).apply {
                        put("observedBehaviour", "Waargenomen: " + "a".repeat(1900))
                        put("coverageExplanation", "Beoordeling van DONE-stories: " + "b".repeat(1900))
                        set<ObjectNode>("evidence", proof(proofText))
                    })
                }
            }
        })

        assertThat(qualityQueries.findQualityWorkItems(productId).single().status).isEqualTo(WorkItemStatus.DONE)
        val planned = planningQueries.findPlanningWorkItems(productId).single()
        assertThat(planned.type).isEqualTo(PlanningWorkItemType.PLAN_EPIC_GAP)
        assertThat(planned.explanation.length).isBetween(9000, 10_000)
        assertThat(planned.explanation).contains("Epiccriterium[1]", "Epiccriterium[2]", MISSING_CRITERION, anotherCriterion)
        val stored = jdbc.queryForObject("SELECT response_json FROM pf_ai_task_result WHERE task_id IN (SELECT id FROM pf_ai_task WHERE product_id=?)", String::class.java, productId.value)
        assertThat(stored).contains(proofText)
    }

    @Test
    fun `bestaand DONE criterium kan niet opnieuw als implementatiegat worden gepland`() {
        val work = epicVerification("already-delivered-gap")
        completeSession(result(work, "NEEDS_WORK").apply {
            (path("results")[0] as ObjectNode).putArray("implementationGaps").add(implementationGap(CRITERION))
        })
        assertBlockedWithoutDevelopment(work)
    }

    @Test
    fun `implementatiegat buiten het bevroren epiccriterium wordt afgewezen`() {
        val work = epicVerification("out-of-scope-gap")
        completeSession(result(work, "NEEDS_WORK").apply {
            (path("results")[0] as ObjectNode).putArray("implementationGaps").add(implementationGap(MISSING_CRITERION))
        })
        assertThat(qualityQueries.findQualityWorkItems(productId).single().status).isEqualTo(WorkItemStatus.FAILED)
        assertThat(qualityQueries.findVerifications(VerificationFilter(productId))).isEmpty()
        assertThat(planningQueries.findPlanningWorkItems(productId)).isEmpty()
    }

    @Test
    fun `implementatiegat zonder review van alle DONE stories wordt afgewezen`() {
        jdbc.update("UPDATE pf_epic_version SET acceptance_criteria_json=? WHERE epic_id=? AND version=1", mapper.writeValueAsString(listOf(CRITERION, MISSING_CRITERION)), epicId.value)
        val work = epicVerification("incomplete-done-review")
        completeSession(result(work, "NEEDS_WORK").apply {
            (path("results")[0] as ObjectNode).putArray("implementationGaps").add(implementationGap(MISSING_CRITERION).apply {
                putArray("reviewedStoryIds")
            })
        })
        assertThat(qualityQueries.findQualityWorkItems(productId).single().status).isEqualTo(WorkItemStatus.FAILED)
        assertThat(qualityQueries.findVerifications(VerificationFilter(productId))).isEmpty()
        assertThat(planningQueries.findPlanningWorkItems(productId)).isEmpty()
    }

    @Test
    fun `lopende oude NEEDS_WORK wordt opnieuw getest zonder oude dekkingsgaten te plannen`() {
        val work = epicVerification("legacy-gap")
        quality.runProcessSession(productId)
        jdbc.update("UPDATE pf_ai_task SET prompt_template_version=2 WHERE product_id=?", productId.value)
        ai.dispatchPending()
        val job = runtime.onlyJob()
        runtime.results[job.id] = result(work, "NEEDS_WORK").apply {
            (path("results")[0] as ObjectNode).apply {
                remove("implementationGaps")
                remove("testBlockers")
                putArray("missingCoverage").add("Meerdere vergaderingen en screenshots zijn niet verifieerbaar.")
            }
        }
        runtime.jobs[job.id] = job.copy(status = "SUCCEEDED", phase = "COMPLETED", progressPercent = 100)
        ai.reconcileActive()
        quality.runProcessSession(productId)

        assertBlockedWithoutDevelopment(work)
    }

    @Test
    fun `lopende v3 NEEDS_WORK behoudt bewezen implementatiegaten na browserpromptupdate`() {
        jdbc.update("UPDATE pf_epic_version SET acceptance_criteria_json=? WHERE epic_id=? AND version=1", mapper.writeValueAsString(listOf(CRITERION, MISSING_CRITERION)), epicId.value)
        val work = epicVerification("v3-proven-gap")
        completeSession(result(work, "NEEDS_WORK").apply {
            (path("results")[0] as ObjectNode).putArray("implementationGaps").add(implementationGap(MISSING_CRITERION))
        }, promptVersion = 3L)

        assertThat(qualityQueries.findVerifications(VerificationFilter(productId)).single().outcome).isEqualTo(VerificationOutcome.NEEDS_WORK)
        assertThat(qualityQueries.findQualityWorkItems(productId).single().status).isEqualTo(WorkItemStatus.DONE)
        assertThat(planningQueries.findPlanningWorkItems(productId).map { it.type }).containsExactly(PlanningWorkItemType.PLAN_EPIC_GAP)
        assertThat(designQueries.getEpic(epicId).status).isEqualTo(EpicStatus.ACTIVE)
    }

    @Test
    fun `lopende oude PASSED kan nog steeds worden gepubliceerd`() {
        val work = epicVerification("legacy-passed")
        quality.runProcessSession(productId)
        jdbc.update("UPDATE pf_ai_task SET prompt_template_version=2 WHERE product_id=?", productId.value)
        ai.dispatchPending()
        val job = runtime.onlyJob()
        runtime.results[job.id] = result(work, "PASSED").apply {
            (path("results")[0] as ObjectNode).apply {
                remove("implementationGaps")
                remove("testBlockers")
            }
        }
        runtime.jobs[job.id] = job.copy(status = "SUCCEEDED", phase = "COMPLETED", progressPercent = 100)
        ai.reconcileActive()
        quality.runProcessSession(productId)

        assertThat(designQueries.getEpic(epicId).status).isEqualTo(EpicStatus.COMPLETED)
        assertThat(qualityQueries.findVerifications(VerificationFilter(productId)).single().outcome).isEqualTo(VerificationOutcome.PASSED)
    }

    private fun epicVerification(key: String): QualityWorkItemId {
        jdbc.update("UPDATE pf_epic SET status='VERIFYING' WHERE id=?", epicId.value)
        jdbc.update("UPDATE pf_epic_version SET status='VERIFYING' WHERE epic_id=? AND version=1", epicId.value)
        val epic = designQueries.getEpic(epicId)
        return quality.requestEpicVerification(RequestEpicVerificationCommand(productId, epic.id, epic.version, "acceptance", 80, key))
    }

    private fun assertBlockedWithoutDevelopment(work: QualityWorkItemId) {
        assertThat(designQueries.getEpic(epicId).status).isEqualTo(EpicStatus.VERIFYING)
        assertThat(qualityQueries.findVerifications(VerificationFilter(productId)).single().outcome).isEqualTo(VerificationOutcome.BLOCKED)
        val item = qualityQueries.findQualityWorkItems(productId).single()
        assertThat(item.id).isEqualTo(work)
        assertThat(item.status).isEqualTo(WorkItemStatus.BLOCKED)
        assertThat(item.retryable).isTrue()
        assertThat(planningQueries.findPlanningWorkItems(productId)).isEmpty()
        assertThat(qualityQueries.findBugs(BugFilter(productId))).isEmpty()
        assertThat(planningQueries.findStories(StoryFilter(productId))).hasSize(1)
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

    @Test
    fun `epiccontrole lost eerder gemelde bug op via resolvedBugIds`() {
        val bugId = BugId(UUID.randomUUID().toString())
        insertBug(bugId)
        jdbc.update("UPDATE pf_epic SET status='VERIFYING' WHERE id=?", epicId.value)
        jdbc.update("UPDATE pf_epic_version SET status='VERIFYING' WHERE epic_id=? AND version=1", epicId.value)
        val epic = designQueries.getEpic(epicId)
        val work = quality.requestEpicVerification(RequestEpicVerificationCommand(productId, epic.id, epic.version, "acceptance", 80, "resolve-bug"))
        completeSession(result(work, "PASSED").apply {
            (path("results")[0] as ObjectNode).putArray("resolvedBugIds").add(bugId.value)
        })

        assertThat(qualityQueries.getBug(bugId).status).isEqualTo(BugStatus.RESOLVED)
        assertThat(designQueries.getEpic(epicId).status).isEqualTo(EpicStatus.COMPLETED)
    }

    private fun insertBug(id: BugId) {
        val now = clock.instant()
        jdbc.update(
            "INSERT INTO pf_bug(id,product_id,epic_id,status,current_version,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
            id.value, productId.value, epicId.value, "OPEN", 1L, now, now,
        )
        jdbc.update(
            """INSERT INTO pf_bug_version(bug_id,version,title,summary,actual_behaviour,expected_behaviour,reproduction_steps_json,environment,
               evidence_json,impact,severity,source_signal_ids_json,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            id.value, 1L, "Bewijsroute faalt", "De bewijsactie eindigt in een fout.", "Foutmelding zonder bewijsdetail.",
            "Toont het opgeslagen bewijsdetail.", mapper.writeValueAsString(listOf("Open het overzicht.", "Activeer de bewijsactie.")),
            "acceptance", mapper.writeValueAsString(proof("Browserobservatie toont de fout.")), "Bewijs is niet controleerbaar.", "P1",
            mapper.writeValueAsString(emptyList<String>()), now,
        )
    }

    @Test
    fun `publicatieherstel publiceert dezelfde testpoging met immutable bron en controleerbare audit`() {
        val bugId = BugId(UUID.randomUUID().toString())
        insertBug(bugId)
        val fixture = failedPublication(resolvedBug = bugId)
        val sourceBefore = jdbc.queryForMap("SELECT * FROM pf_quality_process_session WHERE id=?", fixture.command.failedSessionId.value)
        val resultBefore = jdbc.queryForMap("SELECT * FROM pf_ai_task_result WHERE task_id=?", fixture.command.sourceTaskId.value)
        val attemptsBefore = qualityQueries.findQualityWorkItems(productId).single().attempts
        val command = mapper.readValue(mapper.writeValueAsString(fixture.command), RepairQualityPublicationCommand::class.java)

        val recovery = quality.repairQualityPublication(command)

        assertThat(qualityQueries.getProcessSession(recovery).status).isEqualTo(ProcessSessionStatus.SUCCEEDED)
        assertThat(qualityQueries.getProcessSession(recovery).aiTaskIds).containsExactly(command.sourceTaskId)
        assertThat(jdbc.queryForMap("SELECT * FROM pf_quality_process_session WHERE id=?", command.failedSessionId.value)).isEqualTo(sourceBefore)
        assertThat(jdbc.queryForMap("SELECT * FROM pf_ai_task_result WHERE task_id=?", command.sourceTaskId.value)).isEqualTo(resultBefore)
        val work = qualityQueries.findQualityWorkItems(productId).single()
        assertThat(work.status).isEqualTo(WorkItemStatus.DONE)
        assertThat(work.attemptCount).isEqualTo(3)
        assertThat(work.attempts).isEqualTo(attemptsBefore)
        assertThat(work.attempts.map { it.status }).containsOnly(WorkItemStatus.FAILED)
        assertThat(designQueries.getEpic(epicId).status).isEqualTo(EpicStatus.COMPLETED)
        assertThat(qualityQueries.getBug(bugId).status).isEqualTo(BugStatus.RESOLVED)
        assertThat(runtime.requests).hasSize(1)
        val verification = qualityQueries.findVerifications(VerificationFilter(productId)).single()
        assertThat(verification.outcome).isEqualTo(VerificationOutcome.PASSED)
        assertThat(verification.evidence.artifacts).hasSize(1)
        assertThat(verification.testedRevision).isEqualTo(SHA)
        assertThat(qualityQueries.getCurrentQuality(productId)).isNotNull()
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pf_ai_artifact_domain_reference WHERE domain_id=?", Long::class.java, recovery.value)).isEqualTo(1L)
        val audit = jdbc.queryForMap("SELECT * FROM pf_quality_publication_repair WHERE idempotency_key=?", command.idempotencyKey)
        val expected = mapper.readTree(resultBefore["response_json"] as String)
        (expected.path("results")[0].path("evidence").path("artifacts") as com.fasterxml.jackson.databind.node.ArrayNode).remove(0)
        assertThat(mapper.readTree(audit["corrected_response_json"] as String)).isEqualTo(expected)
        assertThat(audit["source_response_sha256"]).isEqualTo(command.expectedResponseSha256)
        assertThat(audit["source_manifest_sha256"]).isEqualTo(command.expectedManifestSha256)
        assertThat(audit["actor_id"]).isEqualTo(STAKEHOLDER.id)
        assertThat(audit["reason"]).isEqualTo(command.reason)

        // A replay succeeds even though publication has legitimately changed the epic and work versions.
        assertThat(quality.repairQualityPublication(command)).isEqualTo(recovery)
        assertThat(qualityQueries.findVerifications(VerificationFilter(productId))).hasSize(1)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pf_quality_publication_repair", Long::class.java)).isEqualTo(1L)
        assertThatThrownBy { quality.repairQualityPublication(command.copy(reason = "Een andere herstelreden voor dezelfde sleutel.")) }
            .isInstanceOf(IdempotencyConflict::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["response", "manifest", "context", "work", "task", "claim", "source-error", "epic", "story-commit", "story-set", "open-bug", "configuration", "deployment", "deployment-during-repair", "active-session"])
    fun `publicatieherstel vervalt bij gewijzigde bron of testvoorwaarden`(change: String) {
        val fixture = failedPublication()
        val command = fixture.command
        when (change) {
            "response" -> jdbc.update("UPDATE pf_ai_task_result SET response_json=response_json || ' ' WHERE task_id=?", command.sourceTaskId.value)
            "manifest" -> jdbc.update("UPDATE pf_ai_task_result SET artifacts_json=artifacts_json || ' ' WHERE task_id=?", command.sourceTaskId.value)
            "context" -> jdbc.update("UPDATE pf_quality_process_session SET frozen_context_json=frozen_context_json || ' ' WHERE id=?", command.failedSessionId.value)
            "work" -> jdbc.update("UPDATE pf_quality_work_item SET version=version+1 WHERE id=?", fixture.work.value)
            "task" -> jdbc.update("UPDATE pf_ai_task SET requester_session_id='another-session' WHERE id=?", command.sourceTaskId.value)
            "claim" -> jdbc.update("UPDATE pf_quality_work_item SET claimed_by_session_id='another-session' WHERE id=?", fixture.work.value)
            "source-error" -> jdbc.update("UPDATE pf_quality_process_session SET error_code='QUALITY_TASK_FAILED' WHERE id=?", command.failedSessionId.value)
            "epic" -> jdbc.update("UPDATE pf_epic_version SET summary='Inhoud gewijzigd zonder herhaalde verificatie' WHERE epic_id=?", epicId.value)
            "story-commit" -> jdbc.update("UPDATE pf_story SET delivered_commit_sha=? WHERE id=?", "b".repeat(40), storyId.value)
            "story-set" -> insertStory(StoryId(UUID.randomUUID().toString()), StoryType.PRODUCT_STORY, null, null)
            "open-bug" -> insertBug(BugId(UUID.randomUUID().toString()))
            "configuration" -> {
                val configuration = productQueries.getTestableProduct(productId)
                productCommands.configureTestableProduct(ConfigureTestableProductCommand(
                    productId, configuration.acceptance.copy(baseUrl = "https://changed.example.test"), null,
                    configuration.version, STAKEHOLDER, "changed-configuration",
                ))
            }
            "deployment" -> revisions.revision = "b".repeat(40)
            "deployment-during-repair" -> revisions.afterResolve = { revisions.revision = "b".repeat(40) }
            "active-session" -> jdbc.update(
                """INSERT INTO pf_quality_process_session(id,product_id,active_product_id,status,implementation_artifact,implementation_variant,
                   implementation_version,implementation_revision,inputs_json,claimed_work_items_json,memory_version_ids_json,ai_task_ids_json,
                   publications_json,started_at,updated_at) SELECT 'other-session',product_id,product_id,'RUNNING',implementation_artifact,
                   implementation_variant,implementation_version,implementation_revision,'[]','[]','[]','[]','[]',started_at,updated_at
                   FROM pf_quality_process_session WHERE id=?""", command.failedSessionId.value,
            )
        }

        assertThatThrownBy { quality.repairQualityPublication(command) }
            .isInstanceOfAny(InvalidCommand::class.java, VersionConflict::class.java, ProcessAlreadyRunning::class.java)

        assertRepairNotPublished(fixture)
    }

    @ParameterizedTest
    @ValueSource(strings = ["existing-removal", "extra-removal", "duplicate-link", "mime", "not-ready", "sha256", "size", "empty-manifest", "unlinked-artifact"])
    fun `publicatieherstel vereist exact ontbrekende refs en intact eenmalig gekoppeld bewijs`(change: String) {
        val fixture = failedPublication(duplicate = change == "duplicate-link")
        var command = fixture.command
        when (change) {
            "existing-removal" -> command = command.copy(removeArtifactNames = setOf("evidence-01", "evidence-02"))
            "extra-removal" -> command = command.copy(removeArtifactNames = setOf("evidence-02", "evidence-03"))
            "mime" -> jdbc.update("UPDATE pf_ai_artifact SET mime_type='text/plain' WHERE id=?", fixture.artifactId)
            "not-ready" -> jdbc.update("UPDATE pf_ai_artifact SET artifact_status='FAILED' WHERE id=?", fixture.artifactId)
            "sha256" -> jdbc.update("UPDATE pf_ai_artifact SET sha256=? WHERE id=?", "0".repeat(64), fixture.artifactId)
            "size" -> jdbc.update("UPDATE pf_ai_artifact SET size_bytes=size_bytes+1 WHERE id=?", fixture.artifactId)
            "empty-manifest" -> {
                jdbc.update("UPDATE pf_ai_task_result SET artifacts_json='[]' WHERE task_id=?", command.sourceTaskId.value)
                command = command.copy(expectedManifestSha256 = hash("[]"), removeArtifactNames = setOf("evidence-01", "evidence-02"))
            }
            "unlinked-artifact" -> {
                val manifest = mapper.readTree(jdbc.queryForObject("SELECT artifacts_json FROM pf_ai_task_result WHERE task_id=?", String::class.java, command.sourceTaskId.value)) as com.fasterxml.jackson.databind.node.ArrayNode
                manifest.add(manifest[0].deepCopy<ObjectNode>().put("name", "evidence-03"))
                val json = mapper.writeValueAsString(manifest)
                jdbc.update("UPDATE pf_ai_task_result SET artifacts_json=? WHERE task_id=?", json, command.sourceTaskId.value)
                command = command.copy(expectedManifestSha256 = hash(json))
            }
        }

        assertThatThrownBy { quality.repairQualityPublication(command) }.isInstanceOf(InvalidCommand::class.java)

        assertRepairNotPublished(fixture)
    }

    private fun assertRepairNotPublished(fixture: FailedPublication) {
        assertThat(qualityQueries.findVerifications(VerificationFilter(productId))).isEmpty()
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pf_quality_publication_repair", Long::class.java)).isZero()
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pf_ai_artifact_domain_reference", Long::class.java)).isZero()
        assertThat(qualityQueries.findQualityWorkItems(productId).single().attemptCount).isEqualTo(3)
        assertThat(qualityQueries.findQualityWorkItems(productId).single().status).isEqualTo(WorkItemStatus.FAILED)
        assertThat(qualityQueries.getProcessSession(fixture.command.failedSessionId).status).isEqualTo(ProcessSessionStatus.FAILED)
        assertThat(designQueries.getEpic(epicId).status).isEqualTo(EpicStatus.VERIFYING)
        assertThat(runtime.requests).hasSize(1)
    }

    private data class FailedPublication(val work: QualityWorkItemId, val artifactId: String, val command: RepairQualityPublicationCommand)

    private fun failedPublication(duplicate: Boolean = false, resolvedBug: BugId? = null): FailedPublication {
        val work = epicVerification("repair-publication")
        jdbc.update("UPDATE pf_quality_work_item SET attempt_count=2 WHERE id=?", work.value)
        for (attempt in 1..2) jdbc.update(
            "INSERT INTO pf_quality_attempt(work_item_id,attempt_number,status,started_at,finished_at,reason) VALUES (?,?,'FAILED',?,?,?)",
            work.value, attempt, clock.instant().minusSeconds(60), clock.instant().minusSeconds(30), "Eerdere poging $attempt blijft onveranderd.",
        )
        quality.runProcessSession(productId)
        ai.dispatchPending()
        val job = runtime.onlyJob()
        val output = result(work, "PASSED")
        resolvedBug?.let { (output.path("results")[0] as ObjectNode).putArray("resolvedBugIds").add(it.value) }
        val artifacts = output.path("results")[0].path("evidence").path("artifacts") as com.fasterxml.jackson.databind.node.ArrayNode
        fun reference(name: String) = artifacts.addObject().apply {
            put("name", name); put("type", "RUNTIME_ARTIFACT"); put("artifactName", name); put("mediaType", "image/png"); putNull("url")
        }
        reference("evidence-02") // Missing reference fails publication before validating the rest.
        reference("evidence-01")
        if (duplicate) reference("evidence-01")
        val artifactId = UUID.randomUUID().toString()
        val bytes = "werkelijk bewaard bewijs".toByteArray()
        val sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
        runtime.results[job.id] = output
        runtime.resultArtifacts[job.id] = listOf(RuntimeArtifactView(artifactId, job.id, "evidence-01", "image/png", bytes.size.toLong(), sha, clock.instant()))
        runtime.jobs[job.id] = job.copy(status = "SUCCEEDED", phase = "COMPLETED", progressPercent = 100)
        ai.reconcileActive()
        quality.runProcessSession(productId)
        val item = qualityQueries.findQualityWorkItems(productId).single()
        val sessionId = item.claimedBySessionId!!
        val taskId = qualityQueries.getProcessSession(sessionId).aiTaskIds.single()
        // Construct durable v2 evidence while keeping the source result and its local URI untouched.
        val storageKey = "quality-repair-test/$artifactId"
        repairStorageKeys += storageKey
        artifactStore.append(storageKey).use { it.write(bytes) }
        artifactStore.verifyAndPromote(storageKey, bytes.size.toLong(), sha)
        jdbc.update(
            """INSERT INTO pf_ai_artifact(id,task_id,runtime_job_id,runtime_object_id,logical_name,filename,mime_type,size_bytes,sha256,storage_key,artifact_status,created_at,ready_at)
               VALUES (?,?,?,?,'evidence-01','evidence-01','image/png',?,?,?,'READY',?,?)""",
            artifactId, taskId.value, job.id, UUID.randomUUID().toString(), bytes.size, sha, storageKey, clock.instant(), clock.instant(),
        )
        jdbc.update("UPDATE pf_ai_runtime_outbox SET api_version='v2' WHERE task_id=?", taskId.value)
        val response = jdbc.queryForObject("SELECT response_json FROM pf_ai_task_result WHERE task_id=?", String::class.java, taskId.value)!!
        val manifest = jdbc.queryForObject("SELECT artifacts_json FROM pf_ai_task_result WHERE task_id=?", String::class.java, taskId.value)!!
        val context = jdbc.queryForObject("SELECT frozen_context_json FROM pf_quality_process_session WHERE id=?", String::class.java, sessionId.value)!!
        return FailedPublication(work, artifactId, RepairQualityPublicationCommand(
            sessionId, taskId, mapOf(work.value to item.version), hash(response), hash(manifest), hash(context), setOf("evidence-02"),
            STAKEHOLDER, "Verwijder uitsluitend de bewezen niet bestaande screenshotverwijzing.", "repair-${work.value}",
        ))
    }

    private fun hash(value: String) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))

    private fun completeSession(result: ObjectNode, promptVersion: Long? = null) {
        quality.runProcessSession(productId)
        promptVersion?.let { jdbc.update("UPDATE pf_ai_task SET prompt_template_version=? WHERE product_id=?", it, productId.value) }
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
            putArray("implementationGaps")
            putArray("testBlockers")
            putArray("bugs")
            put("explanation", "De waarneming is herleidbaar tot het exacte bevroren doel.")
            put("signalOutcome", "Geen probleem gevonden")
        }
        putArray("memoryChanges")
    }

    private fun implementationGap(criterion: String) = mapper.createObjectNode().apply {
        put("criterion", criterion)
        put("observedBehaviour", "De uitgevoerde exportactie ontbreekt aantoonbaar in de geconfigureerde acceptatieomgeving.")
        put("coverageExplanation", "De opgeleverde story biedt alleen het bewijsdetail; geen van de opgeleverde stories bevat exportgedrag.")
        putArray("reviewedStoryIds").add(storyId.value)
        set<ObjectNode>("evidence", proof("De test van de concrete exportroute bevestigt dat de vereiste functionaliteit ontbreekt."))
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
        @Bean @Primary fun qualityClock() = MutableQualityClock()
        @Bean @Primary fun fakeGit() = FakeGitRevisionResolver()
        @Bean @Primary fun fakeDeploymentRevision() = FakeDeploymentRevisionResolver()
    }

    class FakeGitRevisionResolver : PublicGitRevisionResolver {
        val ancestors = mutableMapOf<String, Set<String>>()
        val checks = mutableListOf<Pair<String, Set<String>>>()
        var onCheck: (() -> Unit)? = null
        fun reset() {
            ancestors.clear()
            ancestors[SHA] = emptySet()
            checks.clear()
            onCheck = null
        }
        override fun resolveHead(publicGitUrl: String) = SHA
        override fun containsCommits(publicGitUrl: String, deployedRevision: String, requiredCommitShas: Set<String>): Boolean {
            checks += deployedRevision to requiredCommitShas
            onCheck?.invoke()
            val known = ancestors[deployedRevision] ?: throw InvalidCommand("Deployed revision is onbekend.")
            return (known + deployedRevision).containsAll(requiredCommitShas)
        }
    }

    class FakeDeploymentRevisionResolver : DeploymentRevisionResolver {
        var revision: String = SHA
        var failure: Throwable? = null
        var afterResolve: (() -> Unit)? = null
        var resolveCalls = 0
        override fun resolve(baseUrl: String, revisionEndpoint: String, revisionJsonPath: String): String {
            resolveCalls++
            failure?.let { throw it }
            val resolved = revision
            afterResolve?.invoke()
            return resolved
        }
    }

    class MutableQualityClock(var now: Instant = Instant.parse("2026-10-13T08:00:00Z")) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        fun advance(duration: Duration) { now = now.plus(duration) }
    }

    companion object {
        private const val SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val MISSING_CRITERION = "De Stakeholder exporteert het bewijs vanuit het overzicht."
        private const val CRITERION = "De Stakeholder opent aantoonbaar het opgeslagen bewijs vanuit het overzicht."
        private val STAKEHOLDER = ActorReference(ActorType.STAKEHOLDER, "stakeholder@example.com")
    }
}
