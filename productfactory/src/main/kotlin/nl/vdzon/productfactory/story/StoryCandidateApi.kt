package nl.vdzon.productfactory.story

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.contracts.StoryCandidateView
import nl.vdzon.productfactory.autonomy.api.StoryDeliveryPort
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

data class CreateStoryCandidateRequest(val productSlug: String, val title: String, val description: String)
data class PublishStoryCandidateRequest(val productSlug: String)

@RestController
@RequestMapping("/api/story-candidates")
class StoryCandidateController(
    private val jdbc: JdbcTemplate,
    private val products: ProductCatalog,
    private val deliveryService: StoryDeliveryPort,
    private val objectMapper: ObjectMapper,
) {
    // REJECTED/DUPLICATE kandidaten gaan nooit naar de Software Factory; die vervuilen de wachtrijweergave alleen maar.
    // Een kandidaat wiens iteratie FAILED is, heeft nooit een gemergede workspace-publicatie gekregen en kan
    // daardoor nooit meer geleverd worden (zie AutonomousDelivery.eligible) — die hoort dus ook niet als "in
    // wachtrij" te ogen.
    @GetMapping fun list(@RequestParam productSlug: String): List<StoryCandidateView> {
        val product = products.requireContext(productSlug)
        val rows = jdbc.query(
            """select c.id, c.product_slug, c.title, c.description, c.status, c.created_at,
                      i.sequence_number, c.acceptance_criteria, c.critic_reason, i.id as iteration_id
                 from story_candidate c
                 left join shadow_iteration i on i.id = c.iteration_id
                where c.product_slug = ? and c.status not in ('REJECTED', 'DUPLICATE')
                  and (i.id is null or i.status <> 'FAILED')
                order by c.id""".trimIndent(),
            mapperWithIteration,
            product.slug,
        )
        val dependsOnArtifactsByIteration = loadDependsOnArtifacts(rows.mapNotNull { it.second }.distinct())
        return rows.map { (view, iterationId) ->
            val (blocked, blockedReason) = resolveBlockedInfo(dependsOnArtifactsByIteration[iterationId], view.id)
            view.copy(blocked = blocked, blockedReason = blockedReason)
        }
    }

    /** Leest het bestaande, reeds opgeslagen dependson_resolution-artefact; berekent of schrijft niets nieuws. */
    private fun loadDependsOnArtifacts(iterationIds: List<String>): Map<String, String> {
        if (iterationIds.isEmpty()) return emptyMap()
        val placeholders = iterationIds.joinToString(",") { "?" }
        return jdbc.query(
            """select iteration_id, content_json from shadow_iteration_artifact
                where artifact_type = 'dependson_resolution' and iteration_id in ($placeholders)""".trimIndent(),
            { row, _ -> row.getString(1) to row.getString(2) },
            *iterationIds.toTypedArray(),
        ).toMap()
    }

    /** Zoekt het array-item met matchende backlogId en leidt blocked/blockedReason af; faalt nooit op onverwachte JSON. */
    private fun resolveBlockedInfo(contentJson: String?, candidateId: Long): Pair<Boolean, String?> {
        if (contentJson == null) return false to null
        return try {
            val root: JsonNode = objectMapper.readTree(contentJson)
            if (!root.isArray) return false to null
            val item = root.firstOrNull { it.path("backlogId").let { b -> b.isIntegralNumber && b.asLong() == candidateId } }
                ?: return false to null
            if (!item.path("blocked").asBoolean(false)) return false to null
            val unresolvedKeys = item.path("dependsOn")
                .filter { !it.path("resolved").asBoolean(true) }
                .map { it.path("rawValue").asText() }
                .filter(String::isNotBlank)
            val reason = if (unresolvedKeys.isEmpty()) null else "verwijzing naar onbekende sleutels: ${unresolvedKeys.joinToString(", ")}"
            true to reason
        } catch (e: Exception) {
            log.warn("Kon dependson_resolution-artefact niet parsen voor storykandidaat {}: {}", candidateId, e.message)
            false to null
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateStoryCandidateRequest): StoryCandidateView {
        if (request.title.isBlank() || request.description.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Titel en omschrijving zijn verplicht")
        val product = products.requireActive(request.productSlug)
        jdbc.update("insert into story_candidate(product_slug, title, description) values (?, ?, ?)", product.slug, request.title.trim(), request.description.trim())
        return jdbc.query(
            "select id, product_slug, title, description, status, created_at from story_candidate where product_slug = ? order by id desc",
            mapper,
            product.slug,
        ).first()
    }

    @PostMapping("/{id}/publish")
    fun publish(@PathVariable id: Long, @RequestBody request: PublishStoryCandidateRequest): StoryCandidateView {
        products.requireStoryPublication(request.productSlug)
        deliveryService.deliverCandidate(request.productSlug, id)
        return jdbc.query(
            "select id, product_slug, title, description, status, created_at from story_candidate where id = ? and product_slug = ?",
            mapper,
            id,
            request.productSlug,
        ).single()
    }

    private val mapper = { row: java.sql.ResultSet, _: Int -> StoryCandidateView(row.getLong(1), row.getString(2), row.getString(3), row.getString(4), row.getString(5), row.getTimestamp(6).toInstant()) }
    private val mapperWithIteration = { row: java.sql.ResultSet, _: Int ->
        StoryCandidateView(
            row.getLong(1), row.getString(2), row.getString(3), row.getString(4), row.getString(5), row.getTimestamp(6).toInstant(),
            row.getObject(7, java.lang.Integer::class.java)?.toInt(), row.getString(8), row.getString(9),
        ) to row.getString(10)
    }

    companion object {
        private val log = LoggerFactory.getLogger(StoryCandidateController::class.java)
    }
}
