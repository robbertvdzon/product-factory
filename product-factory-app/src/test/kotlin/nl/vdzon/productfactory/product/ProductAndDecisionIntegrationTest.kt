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
) {
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
