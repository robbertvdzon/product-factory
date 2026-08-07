package nl.vdzon.productfactory.iteration

import com.fasterxml.jackson.databind.JsonNode
import nl.vdzon.productfactory.contracts.ShadowIterationView
import java.time.LocalDate

internal data class ValidatedSource(
    val url: String,
    val consultedOn: LocalDate,
    val rightsIndication: String,
    val rationale: String,
)

internal data class ReviewedCandidate(
    val index: Int,
    val title: String,
    val description: String,
    val acceptanceCriteria: List<String>,
    val sourceUrls: List<String>,
    val dependsOn: List<String>,
    val risks: List<String>,
    val verdict: String,
    val reason: String,
    val fingerprint: String,
    val duplicateOfId: Long?,
)

internal object ShadowDossierRenderer {
    fun render(
        iteration: ShadowIterationView,
        research: JsonNode,
        productOwner: JsonNode,
        ux: JsonNode,
        critic: JsonNode,
        sources: List<ValidatedSource>,
        candidates: List<ReviewedCandidate>,
        date: LocalDate,
    ): String = buildString {
        appendLine("---")
        appendLine("product: ${iteration.productSlug}")
        appendLine("artifact_type: research")
        appendLine("run_id: ${iteration.id}")
        appendLine("date: $date")
        appendLine("status: approved")
        appendLine("sources:")
        sources.forEach { appendLine("  - ${it.url}") }
        appendLine("---")
        appendLine("# Shadow-iteratie ${iteration.sequenceNumber}")
        appendLine()
        appendLine("**Focus:** ${iteration.focus}")
        appendLine()
        appendLine("## Onderzoek")
        appendLine()
        appendLine(research.path("summary").asText())
        research.path("findings").forEach { finding ->
            appendLine()
            appendLine("### ${finding.path("title").asText()}")
            appendLine()
            appendLine(finding.path("finding").asText())
            appendLine()
            appendLine("Bronnen: ${finding.path("sourceUrls").map { "[${it.asText()}](${it.asText()})" }.joinToString(", ")}")
        }
        appendLine()
        appendLine("### Bronverantwoording")
        appendLine()
        appendLine("| URL | Geraadpleegd | Rechtenindicatie | Onderbouwing |")
        appendLine("|---|---|---|---|")
        sources.forEach {
            appendLine("| [bron](${it.url}) | ${it.consultedOn} | ${table(it.rightsIndication)} | ${table(it.rationale)} |")
        }
        appendLine()
        appendLine("## Productbeslissing")
        appendLine()
        appendLine(productOwner.path("productDirection").asText())
        appendLine()
        appendLine("**Waarom:** ${productOwner.path("rationale").asText()}")
        appendLine()
        appendLine("### Prioriteiten")
        productOwner.path("priorities").forEach { appendLine("- ${it.asText()}") }
        appendLine()
        appendLine("### Besluiten")
        productOwner.path("decisions").forEach { decision ->
            appendLine("- **${decision.path("decision").asText()}** — ${decision.path("rationale").asText()}")
        }
        appendLine()
        appendLine("## UX-voorstel: ${ux.path("flowName").asText()}")
        appendLine()
        appendLine("**Gebruikersdoel:** ${ux.path("userGoal").asText()}")
        appendLine()
        appendLine("### Flow")
        ux.path("steps").forEachIndexed { index, step -> appendLine("${index + 1}. ${step.asText()}") }
        appendLine()
        appendLine("### Wireframe")
        appendLine()
        appendLine(ux.path("wireframe").asText())
        appendLine()
        appendLine("### Interactiehypotheses")
        ux.path("hypotheses").forEach { appendLine("- ${it.asText()}") }
        appendLine()
        appendLine("### Toegankelijkheid")
        ux.path("accessibility").forEach { appendLine("- ${it.asText()}") }
        appendLine()
        appendLine("### Privacy")
        ux.path("privacyConsiderations").forEach { appendLine("- ${it.asText()}") }
        appendLine()
        appendLine("## Kritische beoordeling")
        appendLine()
        appendLine("**Oordeel:** ${critic.path("overallVerdict").asText()}")
        appendLine()
        appendLine(critic.path("summary").asText())
        critic.path("issues").forEach { issue ->
            appendLine("- **${issue.path("severity").asText()} · ${issue.path("category").asText()}** — ${issue.path("description").asText()}")
        }
        appendLine()
        appendLine("## Geaccepteerde storykandidaten")
        candidates.filter { it.verdict == "ACCEPT" && it.duplicateOfId == null }.forEach { candidate ->
            appendLine()
            appendLine("### ${candidate.title}")
            appendLine()
            appendLine(candidate.description)
            appendLine()
            appendLine("**Acceptatiecriteria**")
            candidate.acceptanceCriteria.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Bronnen: ${candidate.sourceUrls.joinToString(", ") { "[$it]($it)" }}")
            if (candidate.dependsOn.isNotEmpty()) appendLine("\nAfhankelijkheden: ${candidate.dependsOn.joinToString()}")
            if (candidate.risks.isNotEmpty()) appendLine("\nRisico's: ${candidate.risks.joinToString()}")
        }
        appendLine()
        appendLine("_Dit dossier is in shadow mode gemaakt. Er is geen story naar Software Factory gestuurd._")
    }

    private fun table(value: String) = value.replace("|", "\\|").replace("\n", " ")
}
