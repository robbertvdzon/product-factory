package nl.vdzon.productfactory.product

import nl.vdzon.productfactory.api.decisions.*
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.shared.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.springframework.jdbc.core.JdbcTemplate
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

@SpringBootTest(properties = ["PF_AUTH_REQUIRED=false"])
@ActiveProfiles("test")
@Transactional
class ProductAndDecisionIntegrationTest(
    @Autowired private val products: ProductCommandService,
    @Autowired private val productQueries: ProductQueryService,
    @Autowired private val decisions: DecisionService,
    @Autowired private val decisionQueries: DecisionQueryService,
    @Autowired private val deletion: ProductDeletionService,
    @Autowired private val jdbc: JdbcTemplate,
) {
    @Test
    fun `productopdracht kan zonder apart doelgroepveld worden vastgelegd`() {
        val productId = ProductId("no-audience-${java.util.UUID.randomUUID()}")
        products.createProduct(CreateProductCommand(productId, "Product zonder doelgroepveld", actor = STAKEHOLDER, idempotencyKey = "create-${productId.value}"))
        products.updateProductAssignment(UpdateProductAssignmentCommand(productId, "", "Een helder productdoel", "https://github.com/example/product.git", 0, STAKEHOLDER, "assignment-${productId.value}"))
        assertThat(productQueries.getProductAssignment(productId).audience).isEmpty()
    }

    @Test
    fun `product wordt met alle lokale configuratie en historie definitief verwijderd`() {
        val id = createProduct("product-delete")
        products.updateProductAssignment(UpdateProductAssignmentCommand(
            id, "Gebruikers", "Tijdelijk testproduct", "https://github.com/example/test.git", 0, STAKEHOLDER, "delete-assignment-${id.value}",
        ))
        products.submitUserSignal(SubmitUserSignalCommand(
            id, UserSignalCategory.FEEDBACK, UserSignalUrgency.NORMAL, "test", "Mag na de test verdwijnen", actor=STAKEHOLDER, idempotencyKey="delete-signal-${id.value}",
        ))
        decisions.createDecision(CreateDecisionCommand(id,"Testbesluit",DecisionOrigin.STAKEHOLDER,STAKEHOLDER,"delete-decision-${id.value}"))
        insertPublicationRepair(id)
        val retainedProduct = createProduct("product-retain")
        insertPublicationRepair(retainedProduct)

        deletion.delete(id)

        assertThatThrownBy { productQueries.getProduct(id) }.isInstanceOf(AggregateNotFound::class.java)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pf_product_assignment WHERE product_id=?",Long::class.java,id.value)).isZero()
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pf_user_signal WHERE product_id=?",Long::class.java,id.value)).isZero()
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pf_decision WHERE product_id=?",Long::class.java,id.value)).isZero()
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pf_quality_publication_repair WHERE idempotency_key=?", Long::class.java, "repair-${id.value}")).isZero()
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pf_quality_process_session WHERE product_id=?", Long::class.java, id.value)).isZero()
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pf_quality_publication_repair WHERE idempotency_key=?", Long::class.java, "repair-${retainedProduct.value}")).isEqualTo(1L)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pf_quality_process_session WHERE product_id=?", Long::class.java, retainedProduct.value)).isEqualTo(2L)
    }

    private fun insertPublicationRepair(productId: ProductId) {
        val source = "failed-${productId.value}"
        val recovery = "recovery-${productId.value}"
        for ((session, status) in listOf(source to "FAILED", recovery to "SUCCEEDED")) {
            jdbc.update(
                """INSERT INTO pf_quality_process_session(id,product_id,status,implementation_artifact,implementation_variant,
                   implementation_version,implementation_revision,inputs_json,claimed_work_items_json,memory_version_ids_json,
                   ai_task_ids_json,publications_json,started_at,updated_at)
                   VALUES (?,?,?,'quality-impl-mvp','publication-repair','test','test','[]','[]','[]','[]','[]',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)""",
                session, productId.value, status,
            )
        }
        val hash = "0".repeat(64)
        jdbc.update(
            """INSERT INTO pf_quality_publication_repair(idempotency_key,request_fingerprint,failed_session_id,source_task_id,
               recovery_session_id,source_response_sha256,source_manifest_sha256,source_context_sha256,
               expected_work_item_versions_json,removed_artifact_names_json,corrected_response_json,corrected_response_sha256,
               actor_type,actor_id,reason,created_at)
               VALUES (?,?,?,?,?,?,?,?,'{}','[]','{}',?,'STAKEHOLDER',?,'Beoordeeld publicatieherstel',CURRENT_TIMESTAMP)""",
            "repair-${productId.value}", hash, source, UUID.randomUUID().toString(), recovery, hash, hash, hash, hash, STAKEHOLDER.id,
        )
    }

    @Test
    fun `product heeft vier uitgeschakelde schedules en bewaakt versies en idempotentie`() {
        val id = createProduct("product-version")
        assertThat(productQueries.getProcessSchedules(id)).hasSize(4).allSatisfy {
            assertThat(it.enabled).isFalse()
            assertThat(it.nextRunAt).isNull()
        }

        val command = SetProductDispatchingCommand(id, true, 1, STAKEHOLDER, "dispatch-1")
        products.setProductDispatching(command)
        products.setProductDispatching(command)
        assertThat(productQueries.getProduct(id).dispatchingEnabled).isTrue()
        assertThat(productQueries.getProduct(id).version).isEqualTo(2)

        assertThatThrownBy {
            products.setProductStatus(SetProductStatusCommand(id, ProductStatus.INACTIVE, 1, STAKEHOLDER, "status-stale"))
        }.isInstanceOf(VersionConflict::class.java)
        assertThatThrownBy {
            products.setProductDispatching(command.copy(enabled = false))
        }.isInstanceOf(IdempotencyConflict::class.java)
    }

    @Test
    fun `schedule ondersteunt weekregels interval en aan-uit zonder automatische uitvoering`() {
        val id = createProduct("product-schedule")
        products.updateProcessSchedule(
            UpdateProcessScheduleCommand(
                id, ScheduledProcess.PRODUCT_DESIGN, true, "Europe/Amsterdam",
                SchedulePattern(weeklyRules = listOf(
                    WeeklyScheduleRule(setOf(DayOfWeek.MONDAY), setOf(LocalTime.of(9, 0))),
                    WeeklyScheduleRule(setOf(DayOfWeek.MONDAY), setOf(LocalTime.of(9, 0))),
                )), 1, STAKEHOLDER, "schedule-weekly",
            ),
        )
        val enabled = productQueries.getProcessSchedule(id, ScheduledProcess.PRODUCT_DESIGN)
        assertThat(enabled.pattern.weeklyRules).hasSize(1)
        assertThat(enabled.nextRunAt).isAfter(Instant.now())

        products.updateProcessSchedule(
            UpdateProcessScheduleCommand(id, ScheduledProcess.PRODUCT_DESIGN, false, "Europe/Amsterdam", enabled.pattern, 2, STAKEHOLDER, "schedule-off"),
        )
        val disabled = productQueries.getProcessSchedule(id, ScheduledProcess.PRODUCT_DESIGN)
        assertThat(disabled.nextRunAt).isNull()
        assertThat(disabled.pattern).isEqualTo(enabled.pattern)

        products.updateProcessSchedule(
            UpdateProcessScheduleCommand(id, ScheduledProcess.PRODUCT_PLANNING, true, "UTC", SchedulePattern(intervalMinutes = 15), 1, STAKEHOLDER, "schedule-interval"),
        )
        assertThat(productQueries.getProcessSchedule(id, ScheduledProcess.PRODUCT_PLANNING).nextRunAt).isNotNull()
        assertThatThrownBy {
            products.updateProcessSchedule(
                UpdateProcessScheduleCommand(id, ScheduledProcess.QUALITY_ASSURANCE, true, "UTC", SchedulePattern(), 1, STAKEHOLDER, "schedule-empty"),
            )
        }.isInstanceOf(InvalidCommand::class.java)
        assertThatThrownBy {
            products.updateProcessSchedule(
                UpdateProcessScheduleCommand(
                    id, ScheduledProcess.QUALITY_ASSURANCE, true, "UTC",
                    SchedulePattern(listOf(WeeklyScheduleRule(setOf(DayOfWeek.TUESDAY), setOf(LocalTime.NOON))), 10),
                    1, STAKEHOLDER, "schedule-mixed",
                ),
            )
        }.isInstanceOf(InvalidCommand::class.java)
    }

    @Test
    fun `signalen vragen en overleggen behouden bron en geldige antwoordkoppeling`() {
        val id = createProduct("product-meeting")
        val signal = products.submitUserSignal(
            SubmitUserSignalCommand(id, UserSignalCategory.FEEDBACK, UserSignalUrgency.HIGH, "formulier", "Brontekst", actor = STAKEHOLDER, idempotencyKey = "signal-1"),
        )
        products.markUserSignalInReview(MarkUserSignalInReviewCommand(signal, 1, STAKEHOLDER, "signal-review"))
        products.recordSignalInvestigation(RecordSignalInvestigationCommand(signal, VerificationId("verification-1"), "Bevestigd", 2, STAKEHOLDER, "signal-done"))
        val processedSignal = productQueries.getUserSignal(signal)
        assertThat(processedSignal.text).isEqualTo("Brontekst")
        assertThat(processedSignal.status).isEqualTo(UserSignalStatus.PROCESSED)

        val question = products.askStakeholder(
            AskStakeholderCommand(id, "PRODUCT_DESIGNER", "Welke richting?", "Context", ProcessSessionId("session-1"), actor = PROCESS, idempotencyKey = "question-1"),
        )
        assertThatThrownBy {
            products.askStakeholder(
                AskStakeholderCommand(id, "PRODUCT_DESIGNER", "Vrije vraag", "Context", ProcessSessionId("session-2"), actor = STAKEHOLDER, idempotencyKey = "question-untrusted"),
            )
        }.isInstanceOf(InvalidCommand::class.java)

        val meeting = products.startMeeting(StartMeetingCommand(id, "Richting", emptyList(), emptyList(), actor = STAKEHOLDER, idempotencyKey = "meeting-1"))
        assertThat(productQueries.getMeeting(meeting).agenda).anyMatch { it.contains("Welke richting?") }
        val messageCommand = RecordMeetingMessageCommand(meeting, MeetingSenderRole.STAKEHOLDER, "Kies eenvoud.", expectedVersion = 1, actor = STAKEHOLDER, idempotencyKey = "message-1")
        products.recordMeetingMessage(messageCommand)
        products.recordMeetingMessage(messageCommand)
        val message = productQueries.getMeeting(meeting).messages.single()
        products.recordStakeholderAnswer(RecordStakeholderAnswerCommand(question, meeting, message.id, message.text, 1, STAKEHOLDER, "answer-1"))
        assertThat(productQueries.getStakeholderQuestion(question).answerMessageId).isEqualTo(message.id)
        products.closeMeeting(CloseMeetingCommand(meeting, "Eenvoud gekozen.", listOf(MeetingOutcomeDetails(
            "Richting bevestigd", "recordStakeholderAnswer", status = MeetingOutcomeStatus.SUCCEEDED,
        )), 2, STAKEHOLDER, "meeting-close"))
        assertThat(productQueries.getMeeting(meeting).status).isEqualTo(MeetingStatus.CLOSED)
    }

    @Test
    fun `stakeholdervraag direct beantwoorden bewaakt status en versie zonder overleg`() {
        val id = createProduct("product-direct-answer")
        val question = products.askStakeholder(
            AskStakeholderCommand(id, "PLANNER", "Welk scenario?", "Context", ProcessSessionId("session-1"), actor = PROCESS, idempotencyKey = "direct-question-1"),
        )

        assertThatThrownBy {
            products.answerStakeholderQuestionDirectly(
                AnswerStakeholderQuestionDirectlyCommand(question, "Scenario A", 2, STAKEHOLDER, "direct-answer-conflict"),
            )
        }.isInstanceOf(VersionConflict::class.java)

        products.answerStakeholderQuestionDirectly(
            AnswerStakeholderQuestionDirectlyCommand(question, "Scenario A", 1, STAKEHOLDER, "direct-answer-1"),
        )
        val answered = productQueries.getStakeholderQuestion(question)
        assertThat(answered.status).isEqualTo(StakeholderQuestionStatus.ANSWERED)
        assertThat(answered.answer).isEqualTo("Scenario A")
        assertThat(answered.meetingId).isNull()
        assertThat(answered.answerMessageId).isNull()

        assertThatThrownBy {
            products.answerStakeholderQuestionDirectly(
                AnswerStakeholderQuestionDirectlyCommand(question, "Scenario B", 2, STAKEHOLDER, "direct-answer-2"),
            )
        }.isInstanceOf(InvalidCommand::class.java)
    }

    @Test
    fun `besluiten bewaren halfopen historie en onderscheiden intrekken van vervangen`() {
        val id = createProduct("product-decisions")
        val first = decisions.createDecision(CreateDecisionCommand(id, "Eerste keuze", DecisionOrigin.STAKEHOLDER, STAKEHOLDER, "decision-1"))
        decisions.reviseDecision(ReviseDecisionCommand(first, "Herziene keuze", 1, STAKEHOLDER, "decision-revise"))
        decisions.reviseDecision(ReviseDecisionCommand(first, "Herziene keuze", 1, STAKEHOLDER, "decision-revise"))
        assertThat(decisionQueries.getDecisionArchive(id).single().history).hasSize(2)
        assertThat(decisionQueries.getDecisions(id).single().decision).isEqualTo("Herziene keuze")

        val withdrawn = decisions.createDecision(CreateDecisionCommand(id, "Tijdelijke keuze", DecisionOrigin.STAKEHOLDER, STAKEHOLDER, "decision-2"))
        decisions.withdrawDecision(WithdrawDecisionCommand(withdrawn, "Niet meer nodig", 1, STAKEHOLDER, "decision-withdraw"))
        val replacement = decisions.supersedeDecisions(
            SupersedeDecisionsCommand(id, setOf(first), "Vervangende keuze", DecisionOrigin.STAKEHOLDER, mapOf(first to 2L), STAKEHOLDER, "decision-supersede"),
        )
        val archive = decisionQueries.getDecisionArchive(id)
        assertThat(archive.single { it.id == withdrawn }.state).isEqualTo(DecisionState.WITHDRAWN)
        assertThat(archive.single { it.id == first }.supersededByDecisionId).isEqualTo(replacement)
        assertThat(archive.single { it.id == replacement }.state).isEqualTo(DecisionState.ACTIVE)
    }

    private fun createProduct(id: String): ProductId {
        val productId = ProductId("$id-${UUID.randomUUID().toString().take(8)}")
        products.createProduct(CreateProductCommand(productId, id, actor = STAKEHOLDER, idempotencyKey = "create-${productId.value}"))
        return productId
    }

    companion object {
        private val STAKEHOLDER = ActorReference(ActorType.STAKEHOLDER, "stakeholder@example.com")
        private val PROCESS = ActorReference(ActorType.PROCESS, "trusted-test-process")
    }
}
