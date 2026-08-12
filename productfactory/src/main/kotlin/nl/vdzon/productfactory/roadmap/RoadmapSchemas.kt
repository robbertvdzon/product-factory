package nl.vdzon.productfactory.roadmap

internal object RoadmapSchemas {
    val session = schema(
        """
        "summary":{"type":"string","minLength":40,"maxLength":4000},
        "epicUpdates":{"type":"array","maxItems":10,"items":{"type":"object","additionalProperties":false,"required":["action","epicId","title","description","processRank","dependencyIds"],"properties":{"action":{"type":"string","enum":["CREATE","UPDATE","CLOSE"]},"epicId":{"type":["string","null"]},"title":{"type":"string","minLength":3,"maxLength":80},"description":{"type":"string","minLength":10,"maxLength":10000},"processRank":{"type":"integer","minimum":1,"maximum":1000},"dependencyIds":{"type":"array","maxItems":20,"items":{"type":"string","maxLength":120}}}}},
        "settledQuestions":{"type":"array","maxItems":10,"items":{"type":"string","minLength":10,"maxLength":1000}}
        """.trimIndent(),
        listOf("summary", "epicUpdates", "settledQuestions"),
    )

    val deliveryVerification = schema(
        """
        "verdict":{"type":"string","enum":["SATISFIES","DOES_NOT_SATISFY","INCONCLUSIVE"]},
        "report":{"type":"string","minLength":20,"maxLength":4000}
        """.trimIndent(),
        listOf("verdict", "report"),
    )

    private fun schema(properties: String, required: List<String>) =
        """{"type":"object","additionalProperties":false,"required":[${required.joinToString(",") { "\"$it\"" }}],"properties":{$properties}}"""
}
