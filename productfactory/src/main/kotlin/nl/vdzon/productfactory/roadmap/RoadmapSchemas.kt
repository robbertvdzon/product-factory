package nl.vdzon.productfactory.roadmap

internal object RoadmapSchemas {
    private val experience = """{"type":"object","additionalProperties":false,"required":["key","title","promise","scenario","wowFactor"],"properties":{"key":{"type":"string","minLength":3,"maxLength":80,"pattern":"^[a-z0-9]+(-[a-z0-9]+)*$"},"title":{"type":"string","minLength":3,"maxLength":100},"promise":{"type":"string","minLength":20,"maxLength":1000},"scenario":{"type":"string","minLength":40,"maxLength":2000},"wowFactor":{"type":"string","minLength":20,"maxLength":1000}}}"""
    private val conceptScreen = """{"type":"object","additionalProperties":false,"required":["key","title","viewport","eyebrow","headline","body","primaryAction","secondaryAction","visualDescription","highlights"],"properties":{"key":{"type":"string","minLength":3,"maxLength":80,"pattern":"^[a-z0-9]+(-[a-z0-9]+)*$"},"title":{"type":"string","minLength":3,"maxLength":120},"viewport":{"type":"string","enum":["MOBILE","DESKTOP"]},"eyebrow":{"type":"string","minLength":2,"maxLength":80},"headline":{"type":"string","minLength":5,"maxLength":160},"body":{"type":"string","minLength":20,"maxLength":800},"primaryAction":{"type":"string","minLength":2,"maxLength":80},"secondaryAction":{"type":"string","maxLength":80},"visualDescription":{"type":"string","minLength":20,"maxLength":1000},"highlights":{"type":"array","minItems":2,"maxItems":6,"items":{"type":"string","minLength":3,"maxLength":240}}}}"""

    val visionary = schema(
        """
        "northStarTitle":{"type":"string","minLength":5,"maxLength":160},
        "northStar":{"type":"string","minLength":80,"maxLength":3000},
        "futureNarrative":{"type":"string","minLength":200,"maxLength":6000},
        "experiences":{"type":"array","minItems":8,"maxItems":15,"items":$experience},
        "wildIdeas":{"type":"array","minItems":3,"maxItems":8,"items":{"type":"string","minLength":30,"maxLength":1200}},
        "conceptScreens":{"type":"array","minItems":3,"maxItems":5,"items":$conceptScreen}
        """.trimIndent(),
        listOf("northStarTitle", "northStar", "futureNarrative", "experiences", "wildIdeas", "conceptScreens"),
    )

    val strategy = schema(
        """
        "northStarTitle":{"type":"string","minLength":5,"maxLength":160},
        "northStar":{"type":"string","minLength":80,"maxLength":3000},
        "futureNarrative":{"type":"string","minLength":200,"maxLength":6000},
        "experiences":{"type":"array","minItems":6,"maxItems":12,"items":$experience},
        "capabilities":{"type":"array","minItems":6,"maxItems":18,"items":{"type":"object","additionalProperties":false,"required":["key","title","outcome","successMeasure","horizon","experienceKeys","feasibility"],"properties":{"key":{"type":"string","minLength":3,"maxLength":80,"pattern":"^[a-z0-9]+(-[a-z0-9]+)*$"},"title":{"type":"string","minLength":3,"maxLength":120},"outcome":{"type":"string","minLength":20,"maxLength":1200},"successMeasure":{"type":"string","minLength":10,"maxLength":800},"horizon":{"type":"string","enum":["NOW","NEXT","LATER","HORIZON"]},"experienceKeys":{"type":"array","minItems":1,"maxItems":8,"items":{"type":"string","maxLength":80}},"feasibility":{"type":"string","enum":["UNKNOWN","PLAUSIBLE","PROVEN","CURRENTLY_BLOCKED","FUNDAMENTALLY_IMPOSSIBLE"]}}}},
        "assumptions":{"type":"array","minItems":1,"maxItems":12,"items":{"type":"object","additionalProperties":false,"required":["key","statement","risk","probeType","proposedProbe","capabilityKeys","feasibility"],"properties":{"key":{"type":"string","minLength":3,"maxLength":80,"pattern":"^[a-z0-9]+(-[a-z0-9]+)*$"},"statement":{"type":"string","minLength":20,"maxLength":1200},"risk":{"type":"string","minLength":10,"maxLength":800},"probeType":{"type":"string","enum":["DESK_RESEARCH","TECHNICAL_PROTOTYPE","UX_PROTOTYPE","OWNER_DEPENDENCY"]},"proposedProbe":{"type":"string","minLength":20,"maxLength":1500},"capabilityKeys":{"type":"array","minItems":1,"maxItems":8,"items":{"type":"string","maxLength":80}},"feasibility":{"type":"string","enum":["UNKNOWN","TESTING","VALIDATED","INVALIDATED","CURRENTLY_BLOCKED"]}}}},
        "conceptScreens":{"type":"array","minItems":3,"maxItems":5,"items":$conceptScreen},
        "visionChangeSummary":{"type":"string","minLength":20,"maxLength":2000}
        """.trimIndent(),
        listOf("northStarTitle", "northStar", "futureNarrative", "experiences", "capabilities", "assumptions", "conceptScreens", "visionChangeSummary"),
    )

    val session = schema(
        """
        "summary":{"type":"string","minLength":40,"maxLength":4000},
        "epicUpdates":{"type":"array","maxItems":15,"items":{"type":"object","additionalProperties":false,"required":["action","epicId","title","description","processRank","dependencyIds","horizon","kind","capabilityKey"],"properties":{"action":{"type":"string","enum":["CREATE","UPDATE","CLOSE"]},"epicId":{"type":["string","null"]},"title":{"type":"string","minLength":3,"maxLength":80},"description":{"type":"string","minLength":10,"maxLength":10000},"processRank":{"type":"integer","minimum":1,"maximum":1000},"dependencyIds":{"type":"array","maxItems":20,"items":{"type":"string","maxLength":120}},"horizon":{"type":"string","enum":["NOW","NEXT","LATER","HORIZON"]},"kind":{"type":"string","enum":["DELIVERY","DISCOVERY"]},"capabilityKey":{"type":["string","null"],"maxLength":80}}}},
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
