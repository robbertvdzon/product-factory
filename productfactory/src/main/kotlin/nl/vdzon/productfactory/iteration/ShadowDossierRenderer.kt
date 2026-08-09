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
    val candidateKey: String,
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
    /** Sleutels uit [dependsOn] die binnen dezelfde batch daadwerkelijk naar een candidateKey verwijzen. */
    val resolvedDependsOn: List<String> = emptyList(),
    /** Resolutiedetail per dependsOn-waarde, inclusief legacy-fallback- en blokkade-informatie voor de mapping-log. */
    val dependencyResolutions: List<DependencyResolution> = emptyList(),
    /** True zodra minstens één dependsOn-waarde niet naar een kandidaat binnen deze batch kon worden vertaald. */
    val blocked: Boolean = false,
)

/** Resultaat van het vertalen van één dependsOn-waarde naar een kandidaat binnen dezelfde batch. */
internal data class DependencyResolution(
    val rawValue: String,
    val resolvedCandidateKey: String?,
    val viaLegacyFallback: Boolean,
) {
    val resolved: Boolean get() = resolvedCandidateKey != null
}

/** Herkent het oude batch-relatieve volgnummerformaat "Kandidaat <n>" (case-insensitive). */
internal val LEGACY_POSITIONAL_DEPENDSON_PATTERN = Regex("""(?i)\bkandidaat\s*(\d+)\b""")

/**
 * Vertaalt elke dependsOn-waarde naar de kandidaat binnen dezelfde batch waar hij naar verwijst.
 * Probeert eerst een candidateKey-lookup (kaart, geen arrayindex, dus stabiel ongeacht batch-/
 * reviewvolgorde). Faalt die, dan wordt de waarde herkend als legacy batch-relatief volgnummer
 * ("Kandidaat <n>") en automatisch vertaald naar het batch-item op die nulgebaseerde positie
 * ([candidatesByPosition], dezelfde volgorde als candidates[]/candidateIndex). Lukt geen van beide,
 * dan blijft de resolutie onopgelost (resolvedCandidateKey == null) zodat de aanroeper alleen die
 * ene kandidaat kan blokkeren zonder de rest van de batch te raken.
 */
internal fun resolveDependencyReferences(
    candidatesByKey: Map<String, ReviewedCandidate>,
    candidatesByPosition: List<ReviewedCandidate>,
    dependsOn: List<String>,
): List<DependencyResolution> = dependsOn.map { raw ->
    val direct = candidatesByKey[raw]
    if (direct != null) {
        DependencyResolution(raw, direct.candidateKey, viaLegacyFallback = false)
    } else {
        val legacyPosition = LEGACY_POSITIONAL_DEPENDSON_PATTERN.find(raw)?.groupValues?.get(1)?.toIntOrNull()
        val legacyCandidate = legacyPosition?.let(candidatesByPosition::getOrNull)
        DependencyResolution(raw, legacyCandidate?.candidateKey, viaLegacyFallback = legacyCandidate != null)
    }
}

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
        appendLine(
            if (iteration.mode == "autonomous") "# Productcyclus ${iteration.sequenceNumber}"
            else "# Productcyclus ${iteration.sequenceNumber} (niet doorgezet)",
        )
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
        appendLine("### Huidige applicatie")
        appendLine()
        appendLine("**Doel:** ${research.path("currentState").path("purpose").asText()}")
        appendLine()
        appendLine("**Wat ontbreekt:**")
        research.path("currentState").path("gaps").forEach { appendLine("- ${it.asText()}") }
        appendLine()
        appendLine("### Verbetermogelijkheden")
        appendLine()
        research.path("improvementOpportunities").forEach { appendLine("- ${it.asText()}") }
        if (research.path("inspiration").size() > 0) {
            appendLine()
            appendLine("### Inspiratiebronnen")
            appendLine()
            research.path("inspiration").forEach { inspiration ->
                appendLine(
                    "- [${inspiration.path("name").asText()}](${inspiration.path("url").asText()}) — " +
                        inspiration.path("relevance").asText(),
                )
            }
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
            appendLine("_Sleutel: `${candidate.candidateKey}`_")
            appendLine()
            appendLine(candidate.description)
            appendLine()
            appendLine("**Acceptatiecriteria**")
            candidate.acceptanceCriteria.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Bronnen: ${candidate.sourceUrls.joinToString(", ") { "[$it]($it)" }}")
            if (candidate.dependsOn.isNotEmpty()) {
                val resolvedTitles = candidate.resolvedDependsOn.joinToString()
                val suffix = if (resolvedTitles.isNotBlank()) " (binnen deze batch herkend als: $resolvedTitles)" else ""
                appendLine("\nAfhankelijkheden (candidateKey): ${candidate.dependsOn.joinToString()}$suffix")
            }
            if (candidate.risks.isNotEmpty()) appendLine("\nRisico's: ${candidate.risks.joinToString()}")
        }
        appendLine()
        appendLine(
            if (iteration.mode == "autonomous") {
                "_Dit product stond op autonoom toen deze cyclus draaide: geaccepteerde stories mogen na het mergen " +
                    "van deze workspace-publicatie automatisch naar de Software Factory worden gestuurd._"
            } else {
                "_Dit product stond niet op autonoom toen deze cyclus draaide. Deze cyclus is NIET doorgezet: er is " +
                    "en wordt geen story naar de Software Factory gestuurd op basis van dit dossier._"
            },
        )
    }

    private fun table(value: String) = value.replace("|", "\\|").replace("\n", " ")
}
