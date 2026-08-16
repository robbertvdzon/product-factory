package nl.vdzon.productfactory.roadmap

internal object RoadmapSchemas {
    val session = schema(
        """
        "summary":{"type":"string","minLength":40,"maxLength":4000},
        "epicUpdates":{"type":"array","maxItems":10,"items":{"type":"object","additionalProperties":false,"required":["action","epicId","title","description","processRank","dependencyIds"],"properties":{"action":{"type":"string","enum":["CREATE","UPDATE","CLOSE"]},"epicId":{"type":["string","null"]},"title":{"type":"string","minLength":3,"maxLength":80},"description":{"type":"string","minLength":10,"maxLength":10000},"processRank":{"type":"integer","minimum":1,"maximum":1000},"dependencyIds":{"type":"array","maxItems":20,"items":{"type":"string","maxLength":120}}}}},
        "settledQuestions":{"type":"array","maxItems":10,"items":{"type":"string","minLength":10,"maxLength":1000}},
        "bugUpdates":{"type":"array","maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["action","bugId","title","description","reproductionSteps","expectedResult","actualResult","priority"],"properties":{"action":{"type":"string","enum":["CREATE","UPDATE"]},"bugId":{"type":["integer","null"]},"title":{"type":"string","minLength":3,"maxLength":240},"description":{"type":"string","minLength":10,"maxLength":10000},"reproductionSteps":{"type":"string","minLength":5,"maxLength":10000},"expectedResult":{"type":"string","minLength":5,"maxLength":5000},"actualResult":{"type":"string","minLength":5,"maxLength":5000},"priority":{"type":"string","enum":["P0","P1","P2","P3"]}}}}
        """.trimIndent(),
        listOf("summary", "epicUpdates", "settledQuestions", "bugUpdates"),
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
