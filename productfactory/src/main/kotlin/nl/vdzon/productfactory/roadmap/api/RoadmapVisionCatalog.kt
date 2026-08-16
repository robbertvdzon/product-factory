package nl.vdzon.productfactory.roadmap.api

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.contracts.RoadmapFutureVisionView
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

/** Versioned source of truth for the ambitious product horizon created by roadmap sessions. */
@Service
class RoadmapVisionCatalog(
    private val jdbc: JdbcTemplate,
    private val products: ProductCatalog,
    private val mapper: ObjectMapper,
) {
    fun current(productSlug: String): RoadmapFutureVisionView? {
        val product = products.requireContext(productSlug)
        return jdbc.query(
            SELECT + " where product_slug = ? order by version desc limit 1",
            ::mapVision,
            product.slug,
        ).singleOrNull()
    }

    fun history(productSlug: String): List<RoadmapFutureVisionView> {
        val product = products.requireContext(productSlug)
        return jdbc.query(SELECT + " where product_slug = ? order by version desc", ::mapVision, product.slug)
    }

    @Transactional
    fun createVersion(
        productSlug: String,
        sessionId: String,
        content: JsonNode,
        changeSummary: String,
    ): RoadmapFutureVisionView {
        val product = products.requireActive(productSlug)
        require(content.isObject) { "Toekomstvisie moet een JSON-object zijn" }
        require(changeSummary.isNotBlank()) { "Wijzigingssamenvatting van toekomstvisie ontbreekt" }
        jdbc.query("select id from product_definition where slug = ? for update", { row, _ -> row.getString(1) }, product.slug)
        val version = (jdbc.queryForObject(
            "select coalesce(max(version), 0) + 1 from roadmap_future_vision where product_slug = ?",
            Int::class.java,
            product.slug,
        ) ?: 1)
        val id = "roadmap-vision-${product.slug}-${version.toString().padStart(4, '0')}"
        jdbc.update(
            """insert into roadmap_future_vision(
                id, product_slug, version, content_json, change_summary, created_by_session_id
            ) values (?, ?, ?, ?, ?, ?)""".trimIndent(),
            id,
            product.slug,
            version,
            mapper.writeValueAsString(content),
            changeSummary.trim(),
            sessionId,
        )
        return current(product.slug)!!
    }

    fun contextForCycle(productSlug: String): String = current(productSlug)?.let { vision ->
        "ACTIEVE TOEKOMSTVISIE v${vision.version}:\n${mapper.writerWithDefaultPrettyPrinter().writeValueAsString(vision.content)}"
    } ?: "Er is nog geen concrete toekomstvisie door een roadmapsessie vastgesteld."

    private fun mapVision(row: ResultSet, ignored: Int): RoadmapFutureVisionView {
        val content = mapper.readValue(row.getString("content_json"), MAP_TYPE)
        return RoadmapFutureVisionView(
            id = row.getString("id"),
            productSlug = row.getString("product_slug"),
            version = row.getInt("version"),
            content = content,
            changeSummary = row.getString("change_summary"),
            createdBySessionId = row.getString("created_by_session_id"),
            createdAt = row.getTimestamp("created_at").toInstant(),
        )
    }

    companion object {
        private const val SELECT = "select * from roadmap_future_vision"
        private val MAP_TYPE = object : TypeReference<Map<String, Any?>>() {}
    }
}
