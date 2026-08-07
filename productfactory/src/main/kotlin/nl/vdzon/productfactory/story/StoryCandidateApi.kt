package nl.vdzon.productfactory.story

import nl.vdzon.productfactory.contracts.StoryCandidateView
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

data class CreateStoryCandidateRequest(val productSlug: String, val title: String, val description: String)
data class PublishStoryCandidateRequest(val productSlug: String)

@RestController
@RequestMapping("/api/story-candidates")
class StoryCandidateController(private val jdbc: JdbcTemplate, private val products: ProductCatalog) {
    @GetMapping fun list(@RequestParam productSlug: String): List<StoryCandidateView> {
        val product = products.requireContext(productSlug)
        return jdbc.query(
            "select id, product_slug, title, description, status, created_at from story_candidate where product_slug = ? order by id",
            mapper,
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
        if (jdbc.update("update story_candidate set status = 'PUBLISHED' where id = ? and product_slug = ?", id, request.productSlug) == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Onbekende storykandidaat voor dit product")
        }
        return jdbc.query(
            "select id, product_slug, title, description, status, created_at from story_candidate where id = ? and product_slug = ?",
            mapper,
            id,
            request.productSlug,
        ).single()
    }

    private val mapper = { row: java.sql.ResultSet, _: Int -> StoryCandidateView(row.getLong(1), row.getString(2), row.getString(3), row.getString(4), row.getString(5), row.getTimestamp(6).toInstant()) }
}
