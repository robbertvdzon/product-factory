package nl.vdzon.productfactory.progress

import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.api.design.EpicStatus
import nl.vdzon.productfactory.api.design.ProductDesignQueryService
import nl.vdzon.productfactory.api.dispatcher.SoftwareFactoryDispatcherQueryService
import nl.vdzon.productfactory.api.planning.ProductPlanningQueryService
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.quality.QualityQueryService
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.dispatcher.SoftwareFactoryDispatcherMvpService
import nl.vdzon.productfactory.operations.ProcessHistoryRetention
import nl.vdzon.productfactory.planning.mvp.ProductPlanningMvpService
import nl.vdzon.productfactory.quality.mvp.QualityMvpService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest(properties = ["PF_AUTH_REQUIRED=false"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProcessOverviewIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val mapper: ObjectMapper,
    private val jdbc: JdbcTemplate,
    private val clock: Clock,
    private val productCommands: ProductCommandService,
    private val designQueries: ProductDesignQueryService,
    private val planningQueries: ProductPlanningQueryService,
    private val qualityQueries: QualityQueryService,
    private val dispatcherQueries: SoftwareFactoryDispatcherQueryService,
    private val planningImpl: ProductPlanningMvpService,
    private val qualityImpl: QualityMvpService,
    private val dispatcherImpl: SoftwareFactoryDispatcherMvpService,
    private val retention: ProcessHistoryRetention,
) {
    private var productId = ProductId("not-initialized")
    private lateinit var now: Instant

    @BeforeEach
    fun prepare() {
        cleanOwnedData()
        now = clock.instant().truncatedTo(ChronoUnit.MILLIS)
        productId = ProductId("overview-${UUID.randomUUID().toString().take(8)}")
        productCommands.createProduct(CreateProductCommand(productId, "Overzicht test", actor = STAKEHOLDER, idempotencyKey = "create-${productId.value}"))
    }

    @AfterEach
    fun cleanOwnedData() {
        qualityImpl.deleteAllOwnedData()
        dispatcherImpl.deleteAllOwnedData()
        planningImpl.deleteAllOwnedData()
    }

    @Test
    fun `sessielijsten filteren en begrenzen in SQL en markeren no-ops`() {
        val finders = mapOf<String, (ProcessSessionFilter) -> List<ProcessSessionDetails>>(
            "pf_design_process_session" to designQueries::findProcessSessions,
            "pf_planning_process_session" to planningQueries::findProcessSessions,
            "pf_quality_process_session" to qualityQueries::findProcessSessions,
            "pf_dispatcher_process_session" to dispatcherQueries::findDispatchSessions,
        )
        finders.forEach { (table, find) ->
            val noOp = insertSession(table, "SUCCEEDED", "Niets te doen; succesvolle no-op.", now.minus(Duration.ofHours(3)))
            val meaningful = insertSession(table, "SUCCEEDED", "Twee stories gepubliceerd.", now.minus(Duration.ofHours(2)))
            val failed = insertSession(table, "FAILED", null, now.minus(Duration.ofHours(1)))
            val running = insertSession(table, "RUNNING", null, now.minus(Duration.ofMinutes(10)))

            assertThat(find(ProcessSessionFilter(productId)).map { it.id.value }).containsExactly(running, failed, meaningful, noOp)
            assertThat(find(ProcessSessionFilter(productId, limit = 2)).map { it.id.value }).containsExactly(running, failed)
            assertThat(find(ProcessSessionFilter(productId, before = now.minus(Duration.ofMinutes(90)))).map { it.id.value })
                .containsExactly(meaningful, noOp)
            assertThat(find(ProcessSessionFilter(productId, excludeNoOps = true)).map { it.id.value }).containsExactly(running, failed, meaningful)
            assertThat(find(ProcessSessionFilter(productId, setOf(ProcessSessionStatus.SUCCEEDED), excludeNoOps = true)).map { it.id.value })
                .containsExactly(meaningful)
            assertThat(find(ProcessSessionFilter(productId)).filter { it.noOp }.map { it.id.value }).describedAs(table).containsExactly(noOp)
        }

        val before = now.minus(Duration.ofMinutes(30))
        mockMvc.get("/api/products/${productId.value}/dispatcher/sessions?limit=1&excludeNoOps=true&before=$before").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].status") { value("FAILED") }
            jsonPath("$[0].noOp") { value(false) }
        }
        mockMvc.get("/api/products/${productId.value}/planning/sessions?status=SUCCEEDED").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[1].noOp") { value(true) }
        }
        mockMvc.get("/api/products/${productId.value}/quality/sessions?limit=100000").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(4) }
        }
        mockMvc.get("/api/products/${productId.value}/schedule-runs?limit=1").andExpect { status { isOk() } }
    }

    @Test
    fun `retentie verwijdert alleen oude no-ops zonder verwijzing en oude afgeronde schedule-runs`() {
        val old = now.minus(Duration.ofHours(49))
        val noOp = "Niets te doen; succesvolle no-op."
        val oldDesignNoOp = insertSession("pf_design_process_session", "SUCCEEDED", noOp, old)
        val recentDesignNoOp = insertSession("pf_design_process_session", "SUCCEEDED", noOp, now.minus(Duration.ofHours(47)))
        val oldDesignMeaningful = insertSession("pf_design_process_session", "SUCCEEDED", "Epic gepubliceerd.", old)
        val oldPlanningNoOp = insertSession("pf_planning_process_session", "SUCCEEDED", noOp, old)
        val referencedPlanningNoOp = insertSession("pf_planning_process_session", "SUCCEEDED", noOp, old)
        val referencedQualityNoOp = insertSession("pf_quality_process_session", "SUCCEEDED", noOp, old)
        val oldQualityFailed = insertSession("pf_quality_process_session", "FAILED", null, old)
        val oldDispatcherNoOp = insertSession("pf_dispatcher_process_session", "SUCCEEDED", noOp, old)
        val referencedDispatcherNoOp = insertSession("pf_dispatcher_process_session", "SUCCEEDED", noOp, old)
        jdbc.update(
            """INSERT INTO pf_planning_work_item(id,idempotency_key,request_fingerprint,product_id,type,source_type,source_id,source_version,
                explanation,priority,status,claimed_by_session_id,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
            UUID.randomUUID().toString(), "retention-${productId.value}", "x", productId.value, "MANUAL_REPLAN", "PRODUCT", productId.value, 1L,
            "Test", 1, "DONE", referencedPlanningNoOp, old, old, 1L,
        )
        jdbc.update(
            """INSERT INTO pf_quality_snapshot(id,session_id,product_id,captured_at,environment,product_revision,investigated_areas_json,
                stale_or_missing_coverage_json,open_bugs_by_severity_json,verification_outcomes_json,risks_json,sources_json)
                VALUES (?,?,?,?,?,?,'[]','[]','{}','{}','[]','[]')""".trimIndent(),
            UUID.randomUUID().toString(), referencedQualityNoOp, productId.value, old, "acceptance", "abc",
        )
        val epicId = insertEpic(listOf(EpicStatus.ACTIVE), old)
        val storyId = insertStory(epicId, 1, "PRODUCT_STORY", "IN_PROGRESS", old)
        insertAttempt(storyId, "SF-retention", "ACCEPTED", old, lastSessionId = referencedDispatcherNoOp)

        val oldRun = insertScheduleRun("SUCCEEDED", now.minus(Duration.ofDays(8)), "2026-01-01T00:00:00Z")
        val oldClaimedRun = insertScheduleRun("CLAIMED", now.minus(Duration.ofDays(8)), "2026-01-01T00:10:00Z")
        val recentRun = insertScheduleRun("SKIPPED", now.minus(Duration.ofDays(6)), "2026-01-01T00:20:00Z")

        val result = retention.prune(now)

        assertThat(result.designSessions).isGreaterThanOrEqualTo(1)
        assertThat(result.planningSessions).isGreaterThanOrEqualTo(1)
        assertThat(result.dispatcherSessions).isGreaterThanOrEqualTo(1)
        assertThat(result.scheduleRuns).isGreaterThanOrEqualTo(1)
        assertThat(exists("pf_design_process_session", oldDesignNoOp)).isFalse()
        assertThat(exists("pf_planning_process_session", oldPlanningNoOp)).isFalse()
        assertThat(exists("pf_dispatcher_process_session", oldDispatcherNoOp)).isFalse()
        assertThat(exists("pf_design_process_session", recentDesignNoOp)).isTrue()
        assertThat(exists("pf_design_process_session", oldDesignMeaningful)).isTrue()
        assertThat(exists("pf_planning_process_session", referencedPlanningNoOp)).isTrue()
        assertThat(exists("pf_quality_process_session", referencedQualityNoOp)).isTrue()
        assertThat(exists("pf_quality_process_session", oldQualityFailed)).isTrue()
        assertThat(exists("pf_dispatcher_process_session", referencedDispatcherNoOp)).isTrue()
        assertThat(exists("pf_schedule_run", oldRun)).isFalse()
        assertThat(exists("pf_schedule_run", oldClaimedRun)).isTrue()
        assertThat(exists("pf_schedule_run", recentRun)).isTrue()
    }

    @Test
    fun `live-overzicht toont vier processen met 24 uurbuckets, lopende en laatste betekenisvolle sessie`() {
        productCommands.updateProcessSchedule(UpdateProcessScheduleCommand(
            productId, ScheduledProcess.PRODUCT_PLANNING, true, "Europe/Amsterdam", SchedulePattern(intervalMinutes = 10), 1, STAKEHOLDER, "live-schedule",
        ))
        jdbc.update("DELETE FROM pf_process_schedule WHERE product_id=? AND process='QUALITY_ASSURANCE'", productId.value)
        insertSession("pf_design_process_session", "SUCCEEDED", "Epic gepubliceerd.", now.minus(Duration.ofDays(3)))
        insertSession("pf_design_process_session", "SUCCEEDED", "Geen wijziging; succesvolle no-op.", now.minus(Duration.ofMinutes(150)))
        val meaningful = insertSession("pf_design_process_session", "BLOCKED", null, now.minus(Duration.ofMinutes(90)))
        insertSession("pf_design_process_session", "SUCCEEDED", "Geen wijziging; succesvolle no-op.", now.minus(Duration.ofMinutes(20)))
        val running = insertSession("pf_design_process_session", "WAITING_FOR_AI", null, now.minus(Duration.ofDays(2)))

        val body = mockMvc.get("/api/products/${productId.value}/live").andExpect {
            status { isOk() }
            jsonPath("$.productId") { value(productId.value) }
            jsonPath("$.processes.length()") { value(4) }
            jsonPath("$.processes[0].process") { value("PRODUCT_DESIGN") }
            jsonPath("$.processes[1].process") { value("PRODUCT_PLANNING") }
            jsonPath("$.processes[2].process") { value("QUALITY_ASSURANCE") }
            jsonPath("$.processes[3].process") { value("SOFTWARE_FACTORY_DISPATCHER") }
            jsonPath("$.processes[1].enabled") { value(true) }
            jsonPath("$.processes[1].intervalMinutes") { value(10) }
            jsonPath("$.processes[2].enabled") { value(false) }
            jsonPath("$.processes[2].intervalMinutes") { doesNotExist() }
            jsonPath("$.processes[0].running.id") { value(running) }
            jsonPath("$.processes[0].lastSession.noOp") { value(true) }
            jsonPath("$.processes[0].lastMeaningfulSession.id") { value(meaningful) }
            jsonPath("$.processes[0].last24h.total") { value(3) }
            jsonPath("$.processes[0].last24h.noOps") { value(2) }
            jsonPath("$.processes[0].last24h.failed") { value(1) }
            jsonPath("$.processes[0].last24h.meaningful") { value(0) }
            jsonPath("$.processes[0].hourly.length()") { value(24) }
            jsonPath("$.processes[3].hourly.length()") { value(24) }
            jsonPath("$.processes[3].running") { value(null) }
            jsonPath("$.dispatcher.productId") { value(productId.value) }
        }.andReturn().response.contentAsString

        val hourly = mapper.readTree(body).path("processes").path(0).path("hourly")
        val hourStarts = hourly.map { Instant.parse(it.path("hourStart").asText()) }
        assertThat(hourStarts.last()).isEqualTo(Instant.parse(mapper.readTree(body).path("generatedAt").asText()).truncatedTo(ChronoUnit.HOURS))
        assertThat(hourStarts.zipWithNext().all { (a, b) -> Duration.between(a, b) == Duration.ofHours(1) }).isTrue()
        assertThat(hourly.sumOf { it.path("total").asInt() }).isEqualTo(3)
        assertThat(hourly.sumOf { it.path("noOps").asInt() }).isEqualTo(2)
    }

    @Test
    fun `epic-voortgang toont bugfix-lus die wacht op Software Factory`() {
        val t = now.minus(Duration.ofDays(3))
        val epicId = insertEpic(
            listOf(EpicStatus.AVAILABLE, EpicStatus.IN_PLANNING, EpicStatus.ACTIVE, EpicStatus.VERIFYING, EpicStatus.ACTIVE), t,
        )
        val first = insertStory(epicId, 1, "PRODUCT_STORY", "DONE", t.plus(Duration.ofHours(2)), commit = SHA)
        insertStory(epicId, 2, "PRODUCT_STORY", "DONE", t.plus(Duration.ofHours(2)), commit = SHA)
        insertStory(epicId, 3, "PRODUCT_STORY", "CANCELLED", t.plus(Duration.ofHours(2)), cancellationReason = "Dubbel werk.")
        insertAttempt(first, "SF-1", "COMPLETED", t.plus(Duration.ofHours(3)), commit = SHA)
        val verification = insertVerification("EPIC", epicId, "NEEDS_WORK", t.plus(Duration.ofHours(10)), missingCoverage = "Het zoekscherm toont geen lege toestand.")
        val bugId = insertBug(epicId, t.plus(Duration.ofHours(10)))
        val bugfix = insertStory(epicId, 4, "BUGFIX", "IN_PROGRESS", t.plus(Duration.ofHours(11)), bugId = bugId, external = "hkh-208")
        insertAttempt(bugfix, "hkh-208", "ACCEPTED", t.plus(Duration.ofHours(12)))
        repeat(3) { insertSession("pf_dispatcher_process_session", "BLOCKED", null, t.plus(Duration.ofHours(13L + it)), "Software Factory is onbereikbaar.") }

        val history = designQueries.getEpicHistory(EpicId(epicId)).sortedBy { it.version }
        assertThat(history.map { it.updatedAt }).doesNotHaveDuplicates()
        assertThat(history.first().updatedAt).isEqualTo(t)

        val body = mockMvc.get("/api/epics/$epicId/progress").andExpect {
            status { isOk() }
            jsonPath("$.epicId") { value(epicId) }
            jsonPath("$.productId") { value(productId.value) }
            jsonPath("$.status") { value("ACTIVE") }
            jsonPath("$.version") { value(5) }
            jsonPath("$.phase") { value("BUGFIX") }
            jsonPath("$.waitingOn.actor") { value("SOFTWARE_FACTORY") }
            jsonPath("$.waitingOn.reference") { value("hkh-208") }
            jsonPath("$.waitingOn.detail") { value("Bugfix-story #4 is als hkh-208 verstuurd en staat daar op OPEN.") }
            jsonPath("$.waitingOn.since") { value(t.plus(Duration.ofHours(12)).toString()) }
            jsonPath("$.stories.length()") { value(3) }
            jsonPath("$.stories[0].id") { value(first) }
            jsonPath("$.cancelledStoryCount") { value(1) }
            jsonPath("$.openBugs.length()") { value(1) }
            jsonPath("$.openBugs[0].id") { value(bugId) }
            jsonPath("$.openBugs[0].severity") { value("P1") }
        }.andReturn().response.contentAsString

        val json = mapper.readTree(body)
        assertThat(json.path("steps").map { it.path("key").asText() to it.path("state").asText() }).containsExactly(
            "DESIGN" to "DONE", "PLANNING" to "DONE", "BUILD" to "DONE", "VERIFICATION" to "FAILED",
            "BUGFIX" to "CURRENT", "RETEST" to "PENDING", "DONE" to "PENDING",
        )
        assertThat(json.path("steps").map { it.path("detail").takeUnless { d -> d.isNull }?.asText() }).containsExactly(
            history[0].updatedAt.toString(), "2 stories", "2 / 2", verification.toString(), "hkh-208", null, null,
        )
        val kinds = json.path("timeline").map { it.path("kind").asText() }
        assertThat(kinds).contains(
            "EPIC_CREATED", "EPIC_VERSION", "STORIES_CREATED", "STORY_DISPATCHED", "STORY_DELIVERED", "STORY_CANCELLED",
            "VERIFICATION_FAILED", "BUG_OPENED", "DISPATCH_BLOCKED",
        )
        val times = json.path("timeline").map { Instant.parse(it.path("at").asText()) }
        assertThat(times).isSortedAccordingTo(Comparator.reverseOrder<Instant>())
        val blocked = json.path("timeline").filter { it.path("kind").asText() == "DISPATCH_BLOCKED" }
        assertThat(blocked).hasSize(1)
        assertThat(blocked.single().path("detail").asText()).startsWith("3× · laatst ").endsWith("Software Factory is onbereikbaar.")
        assertThat(json.path("timeline").single { it.path("kind").asText() == "STORY_DISPATCHED" && it.path("title").asText().contains("#4") }
            .path("title").asText()).isEqualTo("Story #4 verstuurd als hkh-208")
        assertThat(json.path("timeline").single { it.path("kind").asText() == "VERIFICATION_FAILED" }.path("detail").asText())
            .isEqualTo("Het zoekscherm toont geen lege toestand.")
    }

    @Test
    fun `afgeronde epic heeft geen wachtpunt en alle stappen afgerond`() {
        val t = now.minus(Duration.ofDays(2))
        val epicId = insertEpic(listOf(EpicStatus.AVAILABLE, EpicStatus.IN_PLANNING, EpicStatus.ACTIVE, EpicStatus.VERIFYING, EpicStatus.COMPLETED), t)
        val story = insertStory(epicId, 1, "PRODUCT_STORY", "DONE", t.plus(Duration.ofHours(2)), commit = SHA)
        insertAttempt(story, "SF-done", "COMPLETED", t.plus(Duration.ofHours(3)), commit = SHA)
        insertVerification("STORY", story, "PASSED", t.plus(Duration.ofHours(4)))
        insertVerification("EPIC", epicId, "PASSED", t.plus(Duration.ofHours(5)))

        val progress = mockMvc.get("/api/epics/$epicId/progress").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("COMPLETED") }
            jsonPath("$.phase") { value("DONE") }
            jsonPath("$.waitingOn") { value(null) }
            jsonPath("$.steps.length()") { value(5) }
            jsonPath("$.stories[0].deliveredCommitSha") { value(SHA) }
            jsonPath("$.openBugs.length()") { value(0) }
        }.andReturn().response.contentAsString
        val json = mapper.readTree(progress)
        assertThat(json.path("steps").map { it.path("state").asText() }).containsOnly("DONE")
        assertThat(json.path("timeline").map { it.path("kind").asText() }).contains("VERIFICATION_PASSED", "STORY_DELIVERED")
    }

    private fun insertSession(table: String, status: String, summary: String?, startedAt: Instant, blockedReason: String? = null): String {
        val id = UUID.randomUUID().toString()
        val extraColumns = when (table) {
            "pf_design_process_session" -> ",memory_version_ids_json,ai_task_ids_json" to ",'[]','[]'"
            "pf_planning_process_session" -> ",phase,selected_epics_json,claimed_work_items_json,memory_version_ids_json,ai_task_ids_json" to ",'COMPLETED','[]','[]','[]','[]'"
            "pf_quality_process_session" -> ",claimed_work_items_json,memory_version_ids_json,ai_task_ids_json" to ",'[]','[]','[]'"
            else -> "" to ""
        }
        val finished = if (status in setOf("RUNNING", "WAITING_FOR_AI")) null else startedAt.plusSeconds(5)
        jdbc.update(
            """INSERT INTO $table(id,product_id,status,implementation_artifact,implementation_variant,implementation_version,implementation_revision,
                inputs_json,publications_json,result_summary,blocked_reason,started_at,updated_at,finished_at${extraColumns.first})
                VALUES (?,?,?,'test','test','1','abc','[]','[]',?,?,?,?,?${extraColumns.second})""".trimIndent(),
            id, productId.value, status, summary, blockedReason, startedAt, startedAt, finished,
        )
        return id
    }

    private fun insertEpic(statuses: List<EpicStatus>, createdAt: Instant): String {
        val id = UUID.randomUUID().toString()
        val updatedAt = createdAt.plus(Duration.ofHours(statuses.size.toLong()))
        jdbc.update(
            "INSERT INTO pf_epic(id,product_id,current_version,status,created_at,updated_at) VALUES (?,?,?,?,?,?)",
            id, productId.value, statuses.size.toLong(), statuses.last().name, createdAt, updatedAt,
        )
        statuses.forEachIndexed { index, status ->
            jdbc.update(
                """INSERT INTO pf_epic_version(epic_id,version,title,summary,problem,solution,direction_references_json,ux_design,
                    acceptance_criteria_json,slicability_rationale,source_references_json,status,actor_type,actor_id,created_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
                id, index + 1L, "Zoeken op afspraak", "Gebruikers vinden afspraken snel.", "Zoeken is traag.", "Een zoekscherm.", "[]",
                "Rustig zoekscherm.", "[\"De gebruiker vindt een afspraak.\"]", "Zelfstandig te bouwen.", "[]", status.name, "PROCESS", "overview-test",
                createdAt.plus(Duration.ofHours(index.toLong())),
            )
        }
        return id
    }

    private fun insertStory(
        epicId: String,
        sequence: Long,
        type: String,
        status: String,
        createdAt: Instant,
        commit: String? = null,
        bugId: String? = null,
        external: String? = null,
        cancellationReason: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """INSERT INTO pf_story(id,product_id,epic_id,epic_version,bug_id,bug_version,type,status,current_version,sequence_number,priority_reason,
                external_story_id,delivered_commit_sha,cancellation_reason,bug_link_confirmed,created_at,updated_at)
                VALUES (?,?,?,1,?,?,?,?,1,?,?,?,?,?,?,?,?)""".trimIndent(),
            id, productId.value, epicId, bugId, bugId?.let { 1L }, type, status, sequence, "Vaste volgorde", external, commit, cancellationReason,
            bugId == null, createdAt, createdAt.plus(Duration.ofHours(1)),
        )
        jdbc.update(
            """INSERT INTO pf_story_version(story_id,version,title,summary,content,acceptance_criteria_json,ux_design,dependencies_json,
                source_references_json,created_at) VALUES (?,1,?,?,?,?,?,?,?,?)""".trimIndent(),
            id, "Story $sequence", "Samenvatting $sequence.", "Inhoud van story $sequence.", "[\"Werkt.\"]", "UX.", "[]", "[]", createdAt,
        )
        return id
    }

    private fun insertAttempt(storyId: String, external: String, status: String, createdAt: Instant, commit: String? = null, lastSessionId: String? = null) {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """INSERT INTO pf_delivery_attempt(id,product_id,story_id,story_version,reservation_id,idempotency_key,package_hash,package_json,
                external_story_id,external_status,delivered_commit_sha,status,attempt_count,local_command_status,last_session_id,created_at,updated_at)
                VALUES (?,?,?,1,?,?,'hash','{}',?,?,?,?,1,'APPLIED',?,?,?)""".trimIndent(),
            id, productId.value, storyId, UUID.randomUUID().toString(), "attempt-$id", external,
            if (status == "COMPLETED") "DONE" else "OPEN", commit, status, lastSessionId, createdAt, createdAt.plus(Duration.ofHours(1)),
        )
    }

    private fun insertVerification(targetType: String, targetId: String, outcome: String, createdAt: Instant, missingCoverage: String? = null): Instant {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """INSERT INTO pf_verification(id,publication_key,product_id,work_item_id,target_type,target_id,target_version,outcome,environment,
                checks_json,evidence_json,missing_coverage_json,created_at) VALUES (?,?,?,?,?,?,1,?,'acceptance','[]',?,?,?)""".trimIndent(),
            id, "publication-$id", productId.value, UUID.randomUUID().toString(), targetType, targetId, outcome,
            """{"description":"Bewijs","artifacts":[]}""", mapper.writeValueAsString(listOfNotNull(missingCoverage)), createdAt,
        )
        return createdAt
    }

    private fun insertBug(epicId: String, createdAt: Instant): String {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            "INSERT INTO pf_bug(id,product_id,epic_id,status,current_version,created_at,updated_at) VALUES (?,?,?,'OPEN',1,?,?)",
            id, productId.value, epicId, createdAt, createdAt,
        )
        jdbc.update(
            """INSERT INTO pf_bug_version(bug_id,version,title,summary,actual_behaviour,expected_behaviour,reproduction_steps_json,environment,
                evidence_json,impact,severity,source_signal_ids_json,created_at) VALUES (?,1,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
            id, "Lege zoekresultaten ontbreken", "Geen lege toestand.", "Leeg scherm.", "Melding geen resultaten.", "[\"Zoek op onzin.\"]",
            "acceptance", """{"description":"Screenshot","artifacts":[]}""", "Verwarring.", "P1", "[]", createdAt,
        )
        return id
    }

    private fun insertScheduleRun(status: String, claimedAt: Instant, scheduledFor: String): String {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            "INSERT INTO pf_schedule_run(id,product_id,process,scheduled_for,status,claimed_at) VALUES (?,?,'PRODUCT_DESIGN',?,?,?)",
            id, productId.value, Instant.parse(scheduledFor), status, claimedAt,
        )
        return id
    }

    private fun exists(table: String, id: String) =
        (jdbc.queryForObject("SELECT COUNT(*) FROM $table WHERE id=?", Long::class.java, id) ?: 0L) > 0

    companion object {
        private val STAKEHOLDER = ActorReference(ActorType.STAKEHOLDER, "stakeholder@example.com")
        private const val SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
