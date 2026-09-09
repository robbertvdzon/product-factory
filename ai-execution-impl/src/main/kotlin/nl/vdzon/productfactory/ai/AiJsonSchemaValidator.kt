package nl.vdzon.productfactory.ai

import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import org.springframework.stereotype.Component

@Component
class AiJsonSchemaValidator {
    private val registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)

    fun isValid(schema: String, result: JsonNode): Boolean =
        runCatching { registry.getSchema(schema).validate(result).isEmpty() }.getOrDefault(false)
}
