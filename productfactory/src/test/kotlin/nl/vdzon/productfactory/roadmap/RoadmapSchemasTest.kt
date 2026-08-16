package nl.vdzon.productfactory.roadmap

import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertTrue

class RoadmapSchemasTest {
    private val mapper = ObjectMapper()

    @Test
    fun `all roadmap role schemas are valid strict JSON schemas`() {
        listOf(RoadmapSchemas.visionary, RoadmapSchemas.strategy, RoadmapSchemas.session).forEach { schema ->
            val parsed = mapper.readTree(schema)
            assertTrue(parsed.isObject)
            assertTrue(parsed.path("additionalProperties").isBoolean)
            assertTrue(parsed.path("required").isArray)
            assertTrue(parsed.path("properties").isObject)
        }
    }
}
