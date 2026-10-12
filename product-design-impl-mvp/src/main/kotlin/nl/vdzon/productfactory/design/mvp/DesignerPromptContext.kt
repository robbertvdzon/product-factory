package nl.vdzon.productfactory.design.mvp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

/** Keep design decisions complete; spend only the remaining prompt budget on historical summaries. */
class DesignerPromptContext(private val mapper: ObjectMapper) {
    fun serialize(snapshot: ObjectNode, sessionId: String, maxCharacters: Int): String {
        val context = snapshot.deepCopy()
        context.path("agentMemory").forEach { (it as? ObjectNode)?.remove("readBy") }
        val currentEpicId = context.path("currentEpicToRefine").path("id").asText()
        val requestId = context.path("directedProductRequest").path("requestId").asText()
        val relevantEpicIds = context.path("epics").filter {
            it.path("status").asText() in COMPLETE_EPIC_STATUSES ||
                (requestId.isNotEmpty() && it.path("sourceProductRequestId").asText() == requestId)
        }.map { it.path("id").asText() }.toSet() + currentEpicId

        val history = listOf(
            History("epics", context.path("epics").filter { it.path("id").asText() != currentEpicId },
                { it.path("id").asText() in relevantEpicIds },
                { project(it, "id", "version", "contentVersion", "epicNumber", "title", "summary", "status", "sourceProductRequestId", "sourceProductRequestVersion") }),
            History("downstreamStories", context.path("downstreamStories").toList(), { false },
                { project(it, "id", "version", "epicId", "epicVersion", "sequenceNumber", "type", "title", "summary", "status", "bugId", "deliveredCommitSha") }),
            History("openAndHistoricalBugs", context.path("openAndHistoricalBugs").toList(), { false },
                { project(it, "id", "version", "epicId", "sourceStoryId", "title", "summary", "status", "severity") }),
            History("stakeholderQuestions", context.path("stakeholderQuestions").toList(), {
                it.path("status").asText() == "OPEN" || it.path("processSessionId").asText() == sessionId ||
                    (requestId.isNotEmpty() && it.path("productRequestId").asText() == requestId) ||
                    (it.path("epicLinkId").asText().isNotEmpty() && it.path("epicLinkId").asText() in relevantEpicIds) ||
                    it.path("linkedObjects").any { link -> link.path("type").asText() == "EPIC" && link.path("id").asText() in relevantEpicIds }
            }, {
                project(it, "id", "version", "status", "requestedRole").apply {
                    put("questionExcerpt", it.path("question").asText(it.path("questionExcerpt").asText()).take(600))
                    put("answerExcerpt", it.path("answer").asText(it.path("answerExcerpt").asText()).take(600))
                }
            }),
        )
        val coverage = mapper.createObjectNode().put(
            "instruction",
            "Historische records met contextDetail SUMMARY zijn alleen achtergrond, geen volledige specificaties. " +
                "Herzie uitsluitend volledig opgenomen epics. omitted telt weggelaten historische samenvattingen; " +
                "hun ontbreken betekent niet dat dit werk of deze besluiten nooit bestonden.",
        )
        val counts = coverage.putObject("history")
        val candidates = mutableListOf<Candidate>()
        history.forEach { group ->
            val previousOmissions = context.path("contextCoverage").path("history").path(group.field).path("omitted").asInt()
            val protected = group.records.filter(group.required)
            val optional = group.records.filterNot(group.required)
            context.putArray(group.field).addAll(protected)
            counts.putObject(group.field).put("available", optional.size + previousOmissions)
                .put("included", 0).put("omitted", optional.size + previousOmissions)
            optional.forEach { row ->
                candidates += Candidate(group.field, group.summarize(row).put("contextDetail", "SUMMARY"),
                    if (row.path("status").asText() in HISTORICAL_STATUSES) 1 else 0,
                    row.path("updatedAt").asText(row.path("createdAt").asText()))
            }
        }
        // Source references are preserved in the session inputs; they are not design instructions.
        (context.path("qualitySnapshot") as? ObjectNode)?.let { quality ->
            coverage.put("qualitySourceReferenceCount", quality.remove("sources")?.size()
                ?: context.path("contextCoverage").path("qualitySourceReferenceCount").asInt())
        }
        context.set<ObjectNode>("contextCoverage", coverage)
        val requiredJson = mapper.writeValueAsString(context)
        if (requiredJson.length > maxCharacters) {
            throw DesignContextTooLarge(
                "De noodzakelijke ontwerpcontext is te groot (${requiredJson.length} tekens; maximaal $maxCharacters). " +
                    "Opdracht, besluiten, actuele epics en gerichte vragen zijn behouden; beperk deze invoer voordat het ontwerp opnieuw wordt gestart.",
            )
        }
        // Reserve space for changing count widths. Measure serialized JSON, so escaped characters count too.
        var remaining = maxCharacters - requiredJson.length - 128
        for (candidate in candidates.sortedWith(compareBy<Candidate> { it.priority }.thenByDescending { it.updatedAt })) {
            val size = mapper.writeValueAsString(candidate.summary).length + 1
            if (size > remaining) continue
            context.withArray(candidate.field).add(candidate.summary)
            val count = counts.path(candidate.field) as ObjectNode
            count.put("included", count.path("included").asInt() + 1)
            count.put("omitted", count.path("omitted").asInt() - 1)
            remaining -= size
        }
        return mapper.writeValueAsString(context).also {
            check(it.length <= maxCharacters) { "Ontwerpcontext overschrijdt het berekende promptbudget." }
        }
    }

    private fun project(record: JsonNode, vararg fields: String): ObjectNode = mapper.createObjectNode().apply {
        fields.forEach { field -> record.get(field)?.let { set<JsonNode>(field, it) } }
    }

    private data class History(
        val field: String,
        val records: List<JsonNode>,
        val required: (JsonNode) -> Boolean,
        val summarize: (JsonNode) -> ObjectNode,
    )
    private data class Candidate(val field: String, val summary: ObjectNode, val priority: Int, val updatedAt: String)

    private companion object {
        val COMPLETE_EPIC_STATUSES = setOf("NEEDS_RESEARCH", "NEEDS_REFINEMENT", "AWAITING_APPROVAL", "AWAITING_PRODUCT_OWNER_APPROVAL", "AWAITING_FACTORY_OWNER_APPROVAL", "AVAILABLE")
        val HISTORICAL_STATUSES = setOf("DONE", "COMPLETED", "NOT_SUCCESSFUL", "SUPERSEDED", "WITHDRAWN", "CANCELLED", "CLOSED", "ANSWERED", "RESOLVED")
    }
}

internal class DesignContextTooLarge(message: String) : RuntimeException(message)
