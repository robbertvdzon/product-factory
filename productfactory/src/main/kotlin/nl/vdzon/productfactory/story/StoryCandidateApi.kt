package nl.vdzon.productfactory.story

import nl.vdzon.productfactory.contracts.StoryCandidateView
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

data class CreateStoryCandidateRequest(val productSlug: String, val title: String, val description: String)

@RestController
@RequestMapping("/api/story-candidates")
class StoryCandidateController(private val jdbc: JdbcTemplate) {
    @GetMapping fun list(@RequestParam(required = false) productSlug: String?): List<StoryCandidateView> {
        val sql = "select id, product_slug, title, description, status, created_at from story_candidate" +
            if (productSlug == null) " order by id" else " where product_slug = ? order by id"
        return if (productSlug == null) jdbc.query(sql, mapper) else jdbc.query(sql, mapper, productSlug)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateStoryCandidateRequest): StoryCandidateView {
        if (request.title.isBlank() || request.description.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Titel en omschrijving zijn verplicht")
        val productExists = jdbc.queryForObject("select count(*) from product_definition where slug = ?", Long::class.java, request.productSlug) ?: 0
        if (productExists == 0L) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Onbekend product")
        jdbc.update("insert into story_candidate(product_slug, title, description) values (?, ?, ?)", request.productSlug, request.title.trim(), request.description.trim())
        return jdbc.queryForObject("select id, product_slug, title, description, status, created_at from story_candidate where id = (select max(id) from story_candidate)", mapper)!!
    }

    private val mapper = { row: java.sql.ResultSet, _: Int -> StoryCandidateView(row.getLong(1), row.getString(2), row.getString(3), row.getString(4), row.getString(5), row.getTimestamp(6).toInstant()) }
}
