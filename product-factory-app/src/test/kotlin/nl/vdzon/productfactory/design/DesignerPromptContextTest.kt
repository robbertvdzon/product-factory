package nl.vdzon.productfactory.design

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import nl.vdzon.productfactory.design.mvp.DesignerPromptContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DesignerPromptContextTest {
    private val mapper = ObjectMapper()
    private val projector = DesignerPromptContext(mapper)

    @Test
    fun `grote historie en leesaudit verdringen geen actuele opdracht feedback of geheugen`() {
        val snapshot = necessaryContext()
        val memory = snapshot.withArray("agentMemory").addObject().apply {
            put("id", "memory-1"); put("activeVersionId", "memory-v3")
            put("title", "Bewaar de vastgestelde ontwerpkeuze")
            put("content", "De PO wil een duidelijke lege toestand. ".repeat(85))
            putArray("readBy").apply {
                repeat(344) { addObject().put("aiTaskId", "old-audit-$it").put("processSessionId", "old-session-$it").put("readAt", "2026-09-01T12:00:00Z") }
            }
        }
        repeat(400) { index ->
            snapshot.withArray("downstreamStories").addObject().apply {
                put("id", "story-$index"); put("version", 1); put("status", "DONE")
                put("title", "Historische story $index"); put("summary", "Samenvatting \"met escapes\".\n".repeat(20))
                put("content", "Uitgebreide oude technische uitvoering. ".repeat(500))
            }
        }
        snapshot.withArray("downstreamStories").addObject().apply {
            put("id", "current-story"); put("status", "IN_PROGRESS"); put("title", "Werk dat nu loopt")
        }
        snapshot.withArray("epics").addObject().apply {
            put("id", "completed-epic"); put("version", 4); put("status", "COMPLETED")
            put("title", "Afgerond werk"); put("summary", "De bestaande route is al opgeleverd.")
            put("solution", "Historische uitwerking. ".repeat(2_000))
        }
        snapshot.withArray("openAndHistoricalBugs").addObject().apply {
            put("id", "closed-bug"); put("status", "CLOSED"); put("title", "Oude fout")
            put("summary", "De oude fout is verholpen."); put("actualBehaviour", "Oude details. ".repeat(2_000))
        }
        val original = snapshot.deepCopy()

        val json = projector.serialize(snapshot, "current-session", 40_000)
        val actual = mapper.readTree(json)

        assertThat(json.length).isLessThanOrEqualTo(40_000)
        assertThat(json).doesNotContain("old-audit-", "readBy", "Uitgebreide oude technische uitvoering")
        listOf("assignment", "governancePolicy", "decisions", "signals", "directedProductRequest", "currentEpicToRefine", "directedQuestionAnswer", "referenceImages").forEach {
            assertThat(actual.path(it)).describedAs(it).isEqualTo(original.path(it))
        }
        assertThat(actual.path("agentMemory").single()).isEqualTo(memory.deepCopy().apply { remove("readBy") })
        assertThat(actual.path("stakeholderQuestions")).isEqualTo(original.path("stakeholderQuestions"))
        assertThat(actual.path("epics").first { it.path("id").asText() == "first-refinement" }).isEqualTo(original.path("epics").first())
        assertThat(actual.path("downstreamStories").first().path("id").asText()).isEqualTo("current-story")
        assertThat(actual.at("/contextCoverage/history/downstreamStories/omitted").asInt()).isPositive()
        assertThat(snapshot).isEqualTo(original)
        val retried = mapper.readTree(projector.serialize(actual as ObjectNode, "current-session", 40_000))
        assertThat(retried.at("/contextCoverage/history/downstreamStories/available").asInt()).isEqualTo(401)
        assertThat(retried.path("currentEpicToRefine")).isEqualTo(original.path("currentEpicToRefine"))
    }

    @Test
    fun `noodzakelijke inhoud krijgt een specifieke fout en wordt nooit stil afgekapt`() {
        val snapshot = necessaryContext()
        (snapshot.path("assignment") as ObjectNode).put("goal", "Essentiële opdracht. ".repeat(12_000))
        val original = snapshot.deepCopy()

        assertThatThrownBy { projector.serialize(snapshot, "current-session", 190_000) }
            .hasMessageContaining("De noodzakelijke ontwerpcontext is te groot")
            .hasMessageContaining("maximaal 190000")
        assertThat(snapshot).isEqualTo(original)
    }

    private fun necessaryContext(): ObjectNode = mapper.createObjectNode().apply {
        putObject("assignment").put("version", 5).put("goal", "Maak het bestaande dossier begrijpelijk.")
        putObject("governancePolicy").put("productOwnerMode", "HUMAN").put("monthlyProductBudgetEuro", "50")
        putArray("decisions").addObject().put("id", "decision-1").put("version", 3).put("decision", "Geen extra productie-AI.")
        putArray("signals").addObject().put("id", "signal-1").put("version", 1).put("text", "Het dossier is onduidelijk.")
        putObject("directedProductRequest").put("requestId", "request-1").putObject("approvedContent")
            .put("desired_behavior", "Laat precies deze gegevens zien.").put("boundaries_json", "[\"Geen automatische verzending\"]")
        putObject("currentEpicToRefine").put("id", "current-epic").put("version", 6).apply {
            put("solution", "Behoud de bestaande technische afspraken.")
            putArray("acceptanceCriteria").add("Beide bestaande schermen blijven werken.")
            putArray("uxArtifacts").addObject().put("name", "ux-01").put("uri", "artifact://original")
            putArray("uxScreens").addObject().put("screenKey", "original-screen")
        }
        putArray("epics").addObject().apply {
            put("id", "first-refinement"); put("version", 2); put("status", "NEEDS_REFINEMENT")
            put("solution", "Volledige uitwerking voor de eerste revisie.")
            putArray("uxArtifacts").addObject().put("name", "ux-02").put("uri", "artifact://refinement")
        }
        putArray("stakeholderQuestions").apply {
            addObject().put("id", "answer-1").put("status", "ANSWERED").put("processSessionId", "current-session")
                .put("question", "Eerste vraag?").put("answer", "Eerste besluit blijft gelden.")
            addObject().put("id", "answer-2").put("status", "ANSWERED").put("productRequestId", "request-1")
                .put("question", "Tweede vraag?").put("answer", "Tweede besluit vult het eerste aan.")
            addObject().put("id", "open-question").put("status", "OPEN").put("question", "Welke architectuurgrens geldt?")
            addObject().put("id", "legacy-answer").put("status", "ANSWERED")
                .put("question", "Welke bestaande afspraken gelden?").put("answer", "Volledig behouden antwoord. ".repeat(80))
                .putArray("linkedObjects").addObject().put("type", "EPIC").put("id", "first-refinement").put("version", 2)
        }
        putObject("directedQuestionAnswer").put("questionId", "answer-2").put("answer", "Tweede besluit vult het eerste aan.")
        putArray("referenceImages").addObject().put("id", "image-1").put("filename", "referentie.png")
    }
}
