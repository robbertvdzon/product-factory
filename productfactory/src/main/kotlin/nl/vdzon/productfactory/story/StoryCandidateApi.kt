package nl.vdzon.productfactory.story

import nl.vdzon.productfactory.contracts.StoryCandidateView
import nl.vdzon.productfactory.autonomy.api.StoryDeliveryPort
import nl.vdzon.productfactory.product.api.ProductCatalog
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
) {
    // REJECTED/DUPLICATE kandidaten gaan nooit naar de Software Factory; die vervuilen de wachtrijweergave alleen maar.
    // Een kandidaat wiens iteratie FAILED is, heeft nooit een gemergede workspace-publicatie gekregen en kan
    // daardoor nooit meer geleverd worden (zie AutonomousDelivery.eligible) — die hoort dus ook niet als "in
    // wachtrij" te ogen.
    @GetMapping fun list(@RequestParam productSlug: String): List<StoryCandidateView> {
        val product = products.requireContext(productSlug)
        return jdbc.query(
            """select c.id, c.product_slug, c.title, c.description, c.status, c.created_at,
                      i.sequence_number, c.acceptance_criteria, c.critic_reason
                 from story_candidate c
                 left join shadow_iteration i on i.id = c.iteration_id
                where c.product_slug = ? and c.status not in ('REJECTED', 'DUPLICATE')
                  and (i.id is null or i.status <> 'FAILED')
                order by c.id""".trimIndent(),
            mapperWithIteration,
            product.slug,
        )
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
        )
    }
}
