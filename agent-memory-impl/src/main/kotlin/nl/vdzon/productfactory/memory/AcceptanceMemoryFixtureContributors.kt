package nl.vdzon.productfactory.memory

import nl.vdzon.productfactory.api.memory.*
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContext
import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContributor
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@Profile("acceptance")
class AcceptanceMemoryCleanupContributor(
    private val memory: AgentMemoryApplicationService,
) : AcceptanceFixtureContributor {
    override val key = "agent-memory-cleanup"
    override val order = 150
    override fun reset(context: AcceptanceFixtureContext) = memory.deleteAllOwnedData()
}

@Component
@Profile("acceptance")
class AcceptanceMemoryFixtureContributor(
    private val memory: AgentMemoryApplicationService,
    private val products: ProductQueryService,
    private val productCommands: ProductCommandService,
) : AcceptanceFixtureContributor {
    override val key = "agent-memory"
    override val order = 350

    override fun reset(context: AcceptanceFixtureContext) {
        val product = ProductId("synthetic-history")
        val designContext = systemContext(product, "PRODUCT_DESIGNER_MVP")
        val designItem = memory.addAgentMemory(AddAgentMemoryCommand(
            designContext, "Bronpresentatie", "Koppel iedere ontwerpkeuze aan één zichtbare bron.",
            "Synthetische beginles", key(context, "design-add"),
        ))
        val initialDesign = memory.getMemoryAt(product, AgentRoleKey("PRODUCT_DESIGNER_MVP"), FAR_FUTURE).single { it.id == designItem }
        memory.replaceAgentMemory(ReplaceAgentMemoryCommand(
            designContext, designItem, initialDesign.activeVersionId, "Bronpresentatie",
            "Koppel iedere ontwerpkeuze aan een zichtbare bron en benoem de bronsoort.",
            "Synthetische correctie", key(context, "design-replace"),
        ))

        val plannerContext = systemContext(product, "PLANNER_MVP")
        val plannerItem = memory.addAgentMemory(AddAgentMemoryCommand(
            plannerContext, "Te grote stories", "Maak iedere story maximaal één uur groot.",
            "Synthetische achterhaalde les", key(context, "planner-add"),
        ))
        val plannerVersion = memory.getMemoryAt(product, AgentRoleKey("PLANNER_MVP"), FAR_FUTURE).single { it.id == plannerItem }.activeVersionId
        memory.retractAgentMemory(RetractAgentMemoryCommand(
            plannerContext, plannerItem, plannerVersion, "Een vaste urengrens is geen geldige productregel.", key(context, "planner-retract"),
        ))

        val closedMeeting = products.findMeetings(product, MeetingStatus.CLOSED).first()
        memory.applyMeetingMemoryChanges(ApplyMeetingMemoryChangesCommand(
            MeetingExecutionContext(product, closedMeeting.id, AiTaskId(UUID.randomUUID().toString()), MINUTES),
            listOf(MeetingMemoryChange(
                AgentRoleKey("TESTER_MVP"), MemoryChangeType.ADD, title = "Mobiele leesvolgorde",
                content = "Controleer op acceptatie altijd de synthetische mobiele leesvolgorde.",
                reason = "Blijvende les uit het synthetische overleg.",
            )), key(context, "meeting-batch"),
        ))

        memory.getActiveMemory(AgentExecutionContext(
            product, AgentRoleKey("TESTER_MVP"), ProcessSessionId(UUID.randomUUID().toString()), AiTaskId(UUID.randomUUID().toString()),
        ))
        val openMeeting = productCommands.startMeeting(StartMeetingCommand(
            product, "Synthetische geheugensnapshot", listOf("Controleer rolcontext"), emptyList(),
            actor = SYSTEM, idempotencyKey = key(context, "open-memory-meeting"),
        ))
        memory.getMeetingMemorySnapshot(MeetingExecutionContext(
            product, openMeeting, AiTaskId(UUID.randomUUID().toString()), SYSTEM,
        ))
    }

    private fun systemContext(product: ProductId, role: String) = MemoryWriteContext(product, AgentRoleKey(role), SYSTEM)
    private fun key(context: AcceptanceFixtureContext, suffix: String) = "fixture:${context.datasetVersion}:${context.scenarioKey}:memory:$suffix"

    companion object {
        private val FAR_FUTURE = java.time.Instant.parse("9999-12-31T23:59:59Z")
        private val SYSTEM = ActorReference(ActorType.SYSTEM, "acceptance-memory-fixture")
        private val MINUTES = ActorReference(ActorType.MEETING_MINUTES_AGENT, "acceptance-minutes-fixture")
    }
}
