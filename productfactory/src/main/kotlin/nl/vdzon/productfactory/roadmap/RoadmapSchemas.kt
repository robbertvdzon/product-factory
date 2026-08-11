package nl.vdzon.productfactory.roadmap

internal object RoadmapSchemas {
    val session = schema(
        """
        "summary":{"type":"string","minLength":40,"maxLength":4000},
        "themeUpdates":{"type":"array","maxItems":10,"items":{"type":"object","additionalProperties":false,"required":["action","themeId","title","description","priority"],"properties":{"action":{"type":"string","enum":["CREATE","UPDATE","CLOSE"]},"themeId":{"type":["string","null"]},"title":{"type":"string","minLength":3,"maxLength":240},"description":{"type":"string","minLength":10,"maxLength":2000},"priority":{"type":"string","enum":["HIGH","MEDIUM","LOW"]}}}},
        "settledQuestions":{"type":"array","maxItems":10,"items":{"type":"string","minLength":10,"maxLength":1000}}
        """.trimIndent(),
        listOf("summary", "themeUpdates", "settledQuestions"),
    )

    private fun schema(properties: String, required: List<String>) =
        """{"type":"object","additionalProperties":false,"required":[${required.joinToString(",") { "\"$it\"" }}],"properties":{$properties}}"""
}
