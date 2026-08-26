package nl.vdzon.productfactory.product

import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContext
import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContributor
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

@Component
@Profile("acceptance")
class AcceptanceProductFixtureContributor(
    private val service: ProductApplicationService,
) : AcceptanceFixtureContributor {
    override val key: String = "product-and-stakeholder"
    override val order: Int = 200

    override fun reset(context: AcceptanceFixtureContext) {
        service.deleteAllOwnedData()
        seedPrimary(context)
        seedSecondary(context)
    }

    private fun seedPrimary(context: AcceptanceFixtureContext) {
        val productId = ProductId("synthetic-history")
        service.createProduct(CreateProductCommand(productId, "Synthetische geschiedenis", ProductStatus.ACTIVE, SYSTEM, key(context, "product-primary")))
        service.updateProductAssignment(UpdateProductAssignmentCommand(
            productId, "Nieuwsgierige inwoners en onderzoekers", "Maak synthetische geschiedenis toegankelijk.",
            listOf("Alle data is synthetisch", "Bronvermelding is verplicht"), "https://github.com/example/synthetic-history.git",
            0, SYSTEM, key(context, "assignment-primary"),
        ))
        service.configureTestableProduct(ConfigureTestableProductCommand(
            productId,
            TestEnvironmentConfiguration(
                "Synthetische acceptatie", "https://synthetic-acceptance.example.com", listOf("/", "/api/version"),
                "/api/version", "commit", dataBoundaries = listOf("Alleen fixturedata"), accessBoundaries = listOf("Mutaties alleen in acceptatie"),
            ),
            null, 0, SYSTEM, key(context, "environment-primary"),
        ))
        service.updateProcessSchedule(UpdateProcessScheduleCommand(
            productId, ScheduledProcess.PRODUCT_DESIGN, true, "Europe/Amsterdam",
            SchedulePattern(weeklyRules = listOf(WeeklyScheduleRule(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), setOf(LocalTime.of(9, 0))))),
            1, SYSTEM, key(context, "schedule-primary-design"),
        ))
        val openSignal = service.submitUserSignal(SubmitUserSignalCommand(
            productId, UserSignalCategory.FEEDBACK, UserSignalUrgency.NORMAL, "acceptatie-fixture",
            "Maak de bronverwijzing duidelijker.", actor = SYSTEM, idempotencyKey = key(context, "signal-open"),
        ))
        service.markUserSignalInReview(MarkUserSignalInReviewCommand(openSignal, 1, SYSTEM, key(context, "signal-review")))
        val processedSignal = service.submitUserSignal(SubmitUserSignalCommand(
            productId, UserSignalCategory.QUALITY_CONCERN, UserSignalUrgency.HIGH, "synthetisch-overleg",
            "Controleer de mobiele leesvolgorde.", actor = SYSTEM, idempotencyKey = key(context, "signal-processed"),
        ))
        service.recordSignalInvestigation(RecordSignalInvestigationCommand(
            processedSignal, VerificationId(UUID.randomUUID().toString()), "De synthetische leesvolgorde is gecontroleerd.",
            1, SYSTEM, key(context, "signal-investigated"),
        ))

        val answeredQuestion = service.askStakeholder(AskStakeholderCommand(
            productId, "PRODUCT_DESIGNER", "Welke bronsoort heeft voorrang?", "Keuze nodig voor de synthetische navigatie.",
            ProcessSessionId(UUID.randomUUID().toString()), actor = PROCESS, idempotencyKey = key(context, "question-answered"),
        ))
        val meeting = service.startMeeting(StartMeetingCommand(
            productId, "Richting voor brongebruik", listOf("Bronprioriteit"), emptyList(), actor = SYSTEM,
            idempotencyKey = key(context, "meeting-closed"),
        ))
        service.recordMeetingMessage(RecordMeetingMessageCommand(
            meeting, MeetingSenderRole.STAKEHOLDER, "Lokale archiefbronnen hebben voorrang.", expectedVersion = 1,
            actor = STAKEHOLDER, idempotencyKey = key(context, "meeting-answer-message"),
        ))
        val message = service.getMeeting(meeting).messages.single()
        service.recordStakeholderAnswer(RecordStakeholderAnswerCommand(
            answeredQuestion, meeting, message.id, message.text, 1, SYSTEM, key(context, "question-record-answer"),
        ))
        service.closeMeeting(CloseMeetingCommand(
            meeting, "De Stakeholder gaf lokale archiefbronnen voorrang.", listOf(MeetingOutcomeDetails(
                "Bronprioriteit vastgelegd", "recordStakeholderAnswer", status = MeetingOutcomeStatus.SUCCEEDED,
            )),
            2, SYSTEM, key(context, "meeting-close"),
        ))
        service.askStakeholder(AskStakeholderCommand(
            productId, "QUALITY_TESTER", "Welke synthetische route is kritiek?", "Nodig voor de volgende kwaliteitscontrole.",
            ProcessSessionId(UUID.randomUUID().toString()), actor = PROCESS, idempotencyKey = key(context, "question-open"),
        ))
        service.startMeeting(StartMeetingCommand(
            productId, "Open kwaliteitsvraag", listOf("Kritieke route"), emptyList(), requested = true, actor = SYSTEM,
            idempotencyKey = key(context, "meeting-open"),
        ))
    }

    private fun seedSecondary(context: AcceptanceFixtureContext) {
        val productId = ProductId("synthetic-archive")
        service.createProduct(CreateProductCommand(productId, "Synthetisch archief", ProductStatus.INACTIVE, SYSTEM, key(context, "product-secondary")))
        service.updateProductAssignment(UpdateProductAssignmentCommand(
            productId, "Archiefbeheerders", "Bewijs meerdere productconfiguraties.", listOf("Geen automatische verwerking"),
            "https://github.com/example/synthetic-archive.git", 0, SYSTEM, key(context, "assignment-secondary"),
        ))
    }

    private fun key(context: AcceptanceFixtureContext, suffix: String) = "fixture:${context.datasetVersion}:${context.scenarioKey}:$suffix"

    companion object {
        private val SYSTEM = ActorReference(ActorType.SYSTEM, "acceptance-fixture")
        private val PROCESS = ActorReference(ActorType.PROCESS, "acceptance-process-fixture")
        private val STAKEHOLDER = ActorReference(ActorType.STAKEHOLDER, "acceptance-stakeholder")
    }
}
