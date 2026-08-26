package nl.vdzon.productfactory.memory

import nl.vdzon.productfactory.ai.AiSettingsApplicationService
import nl.vdzon.productfactory.api.ai.*
import nl.vdzon.productfactory.api.memory.*
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.shared.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@SpringBootTest(properties = ["PF_AUTH_REQUIRED=false"])
@ActiveProfiles("test")
@Transactional
class MemoryAndAiIntegrationTest(
    @Autowired private val productCommands: ProductCommandService,
    @Autowired private val productQueries: ProductQueryService,
    @Autowired private val memory: AgentMemoryApplicationService,
    @Autowired private val ai: AiSettingsApplicationService,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val clock: Clock,
) {
    @Test
    fun `agent leest uitsluitend eigen product en rol en iedere gelezen versie is herleidbaar`() {
        val first = createProduct("memory-scope-a")
        val second = createProduct("memory-scope-b")
        val role = AgentRoleKey("PRODUCT_DESIGNER_MVP")
        val otherRole = AgentRoleKey("PLANNER_MVP")
        val firstItem = add(first, role, "Doelgroep", "Teams met terugkerend werk", "scope-add-a")
        add(first, otherRole, "Planning", "Eerst de kern", "scope-add-planner")
        add(second, role, "Ander product", "Niet zichtbaar", "scope-add-b")

        val context = AgentExecutionContext(first, role, ProcessSessionId("process-a"), AiTaskId("task-a"))
        val visible = memory.getActiveMemory(context)

        assertThat(visible).extracting<String> { it.title }.containsExactly("Doelgroep")
        val read = memory.getMemoryHistory(first, role, firstItem).single().readBy.single()
        assertThat(read.processSessionId).isEqualTo(context.processSessionId)
        assertThat(read.aiTaskId).isEqualTo(context.aiTaskId)
        assertThatThrownBy { memory.getMemoryHistory(first, otherRole, firstItem) }
            .isInstanceOf(AggregateNotFound::class.java)
    }

    @Test
    fun `vervangen en intrekken zijn append only idempotent en bewaken versie en budget`() {
        val product = createProduct("memory-history")
        val role = AgentRoleKey("TESTER_MVP")
        val item = add(product, role, "Risico", "Eerste versie", "history-add")
        val initial = memory.getMemoryAt(product, role, clock.instant()).single()
        val replace = ReplaceAgentMemoryCommand(
            stakeholder(product, role), item, initial.activeVersionId, "Risico", "Aangescherpte versie",
            "Nieuwe informatie", "history-replace",
        )
        val replacement = memory.replaceAgentMemory(replace)
        assertThat(memory.replaceAgentMemory(replace)).isEqualTo(replacement)
        assertThatThrownBy { memory.replaceAgentMemory(replace.copy(idempotencyKey = "history-stale")) }
            .isInstanceOf(VersionConflict::class.java)

        val retract = RetractAgentMemoryCommand(
            stakeholder(product, role), item, replacement, "Niet meer geldig", "history-retract",
        )
        memory.retractAgentMemory(retract)
        assertThat(memory.retractAgentMemory(retract)).isNotNull()
        assertThat(memory.getMemoryAt(product, role, clock.instant())).isEmpty()
        assertThat(memory.getMemoryHistory(product, role, item)).extracting<MemoryVersionStatus> { it.status }
            .containsExactly(MemoryVersionStatus.SUPERSEDED, MemoryVersionStatus.RETRACTED)

        assertThatThrownBy {
            memory.addAgentMemory(
                AddAgentMemoryCommand(stakeholder(product, role), "Te groot", "x".repeat(4_001), "Budgettest", "budget-too-large"),
            )
        }.isInstanceOf(InvalidCommand::class.java).hasMessageContaining("maximaal 4000")
    }

    @Test
    fun `open overleg bevriest een snapshot en gesloten overleg past een atomische batch toe`() {
        val product = createProduct("memory-meeting")
        val role = AgentRoleKey("PRODUCT_DESIGNER_MVP")
        val item = add(product, role, "Richting", "Eenvoud", "meeting-add")
        val activeVersion = memory.getMemoryAt(product, role, clock.instant()).single().activeVersionId
        val meetingId = productCommands.startMeeting(
            StartMeetingCommand(product, "Richting bespreken", emptyList(), emptyList(), actor = STAKEHOLDER, idempotencyKey = "meeting-open-${product.value}"),
        )
        val snapshotContext = MeetingExecutionContext(product, meetingId, AiTaskId("meeting-task"), SYSTEM)
        val snapshot = memory.getMeetingMemorySnapshot(snapshotContext)
        assertThat(snapshot.memory[role]!!.single().id).isEqualTo(item)
        assertThat(memory.getMemoryHistory(product, role, item).single().readBy).anySatisfy {
            assertThat(it.meetingId).isEqualTo(meetingId)
            assertThat(it.aiTaskId).isEqualTo(AiTaskId("meeting-task"))
        }

        assertThatThrownBy {
            memory.applyMeetingMemoryChanges(
                ApplyMeetingMemoryChangesCommand(
                    MeetingExecutionContext(product, meetingId, AiTaskId("minutes-before-close"), MINUTES),
                    listOf(MeetingMemoryChange(role, MemoryChangeType.REPLACE, item, activeVersion, "Richting", "Snelheid", "Te vroeg")),
                    "meeting-too-early",
                ),
            )
        }.isInstanceOf(InvalidCommand::class.java)

        productCommands.closeMeeting(CloseMeetingCommand(meetingId, "Eenvoud bevestigd", emptyList(), 1, STAKEHOLDER, "meeting-close-${product.value}"))
        assertThatThrownBy { memory.getMeetingMemorySnapshot(snapshotContext) }.isInstanceOf(InvalidCommand::class.java)
        val batch = ApplyMeetingMemoryChangesCommand(
            MeetingExecutionContext(product, meetingId, AiTaskId("minutes-task"), MINUTES),
            listOf(
                MeetingMemoryChange(role, MemoryChangeType.REPLACE, item, activeVersion, "Richting", "Snelheid en eenvoud", "Expliciet besproken"),
                MeetingMemoryChange(AgentRoleKey("PLANNER_MVP"), MemoryChangeType.ADD, title = "Volgorde", content = "Eerst valideren", reason = "Afspraak"),
            ),
            "meeting-batch-${product.value}",
        )
        val result = memory.applyMeetingMemoryChanges(batch)
        assertThat(memory.applyMeetingMemoryChanges(batch)).isEqualTo(result)
        assertThat(memory.getMemoryAt(product, role, clock.instant()).single().content).isEqualTo("Snelheid en eenvoud")
        assertThat(memory.getMemoryHistory(product, role, item).last().sourceMeetingId).isEqualTo(meetingId)

        val before = memory.getMemoryAt(product, role, clock.instant())
        assertThatThrownBy {
            memory.applyMeetingMemoryChanges(
                batch.copy(
                    changes = listOf(
                        MeetingMemoryChange(role, MemoryChangeType.ADD, title = "Niet opslaan", content = "x", reason = "test"),
                        MeetingMemoryChange(AgentRoleKey("ONBEKEND"), MemoryChangeType.ADD, title = "Fout", content = "x", reason = "test"),
                    ),
                    idempotencyKey = "meeting-invalid-${product.value}",
                ),
            )
        }.isInstanceOf(InvalidCommand::class.java)
        assertThat(memory.getMemoryAt(product, role, clock.instant())).isEqualTo(before)
    }

    @Test
    fun `globale AI instellingen zijn gevalideerd en geversioneerd`() {
        assertThat(ai.getAiJobConfigurations()).extracting<String> { it.jobKey.value }.containsExactly(
            "MEETING.CONVERSE", "MEETING.SUMMARIZE", "PLANNING.SLICE_EPIC", "PRODUCT_DESIGN.CREATE_EPIC", "QUALITY.VERIFY_EPIC",
        )
        val command = UpdateAiJobConfigurationCommand(
            AiJobKey("PRODUCT_DESIGN.CREATE_EPIC"), AiProvider.CLAUDE, "claude-sonnet-4-5", true, 0,
            STAKEHOLDER, "ai-settings-${UUID.randomUUID()}",
        )
        val updated = ai.updateAiJobConfiguration(command)
        assertThat(ai.updateAiJobConfiguration(command)).isEqualTo(updated)
        assertThat(updated.version).isEqualTo(1)
        assertThatThrownBy { ai.updateAiJobConfiguration(command.copy(model = "onbekend", expectedVersion = 1, idempotencyKey = "ai-invalid-${UUID.randomUUID()}")) }
            .isInstanceOf(InvalidCommand::class.java)
        assertThatThrownBy { ai.updateAiJobConfiguration(command.copy(expectedVersion = 0, idempotencyKey = "ai-stale-${UUID.randomUUID()}")) }
            .isInstanceOf(VersionConflict::class.java)

        val production = AiSettingsApplicationService(jdbc, clock, "production")
        assertThatThrownBy {
            production.updateAiJobConfiguration(
                UpdateAiJobConfigurationCommand(AiJobKey("MEETING.CONVERSE"), AiProvider.MOCKED, "scenario", true, 0, STAKEHOLDER, "prod-mocked-${UUID.randomUUID()}"),
            )
        }.isInstanceOf(InvalidCommand::class.java).hasMessageContaining("productie")
        assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE lower(table_name)='pf_ai_task'")).hasSize(1)
    }

    @Test
    fun `proceswijziging zonder volledige vertrouwde context laat geen geheugen achter`() {
        val product = createProduct("memory-failed-task")
        val role = AgentRoleKey("PLANNER_MVP")
        assertThatThrownBy {
            memory.addAgentMemory(
                AddAgentMemoryCommand(
                    MemoryWriteContext(product, role, ActorReference(ActorType.PROCESS, "planner"), processSessionId = ProcessSessionId("failed-process")),
                    "Niet bewaren", "Een mislukt resultaat", "Geen geslaagde taak", "failed-task-memory",
                ),
            )
        }.isInstanceOf(InvalidCommand::class.java)
        assertThat(memory.getMemoryAt(product, role, clock.instant())).isEmpty()
    }

    private fun createProduct(prefix: String): ProductId {
        val id = ProductId("$prefix-${UUID.randomUUID().toString().take(8)}")
        productCommands.createProduct(CreateProductCommand(id, prefix, actor = STAKEHOLDER, idempotencyKey = "create-${id.value}"))
        return id
    }

    private fun add(product: ProductId, role: AgentRoleKey, title: String, content: String, key: String) =
        memory.addAgentMemory(AddAgentMemoryCommand(stakeholder(product, role), title, content, "Testinvoer", "$key-${product.value}"))

    private fun stakeholder(product: ProductId, role: AgentRoleKey) = MemoryWriteContext(product, role, STAKEHOLDER)

    companion object {
        private val STAKEHOLDER = ActorReference(ActorType.STAKEHOLDER, "stakeholder@example.com")
        private val SYSTEM = ActorReference(ActorType.SYSTEM, "meeting-orchestrator")
        private val MINUTES = ActorReference(ActorType.MEETING_MINUTES_AGENT, "trusted-minutes")
    }
}
