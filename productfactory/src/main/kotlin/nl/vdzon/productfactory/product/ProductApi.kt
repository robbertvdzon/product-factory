package nl.vdzon.productfactory.product

import nl.vdzon.productfactory.contracts.ProductView
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.sql.Timestamp

data class CreateProductRequest(val slug: String, val name: String, val mission: String, val guardrails: String = "")

@RestController
@RequestMapping("/api/products")
class ProductController(private val jdbc: JdbcTemplate) {
    @GetMapping fun list(): List<ProductView> = jdbc.query(
        "select slug, name, mission, guardrails, created_at from product_definition order by slug"
    ) { row, _ -> ProductView(row.getString(1), row.getString(2), row.getString(3), row.getString(4), row.getTimestamp(5).toInstant()) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateProductRequest): ProductView {
        val slug = request.slug.trim().lowercase()
        if (!slug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ongeldige productslug")
        if (request.name.isBlank() || request.mission.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Naam en missie zijn verplicht")
        jdbc.update("insert into product_definition(slug, name, mission, guardrails) values (?, ?, ?, ?)", slug, request.name.trim(), request.mission.trim(), request.guardrails.trim())
        return jdbc.queryForObject("select slug, name, mission, guardrails, created_at from product_definition where slug = ?", { row, _ ->
            ProductView(row.getString(1), row.getString(2), row.getString(3), row.getString(4), row.getTimestamp(5).toInstant())
        }, slug)!!
    }
}
