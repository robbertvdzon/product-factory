package nl.vdzon.productfactory.iteration

internal object ShadowSchemas {
    val research = schema(
        """
        "summary":{"type":"string","minLength":40,"maxLength":4000},
        "findings":{"type":"array","minItems":1,"maxItems":8,"items":{"type":"object","additionalProperties":false,"required":["title","finding","sourceUrls"],"properties":{"title":{"type":"string","minLength":3,"maxLength":200},"finding":{"type":"string","minLength":20,"maxLength":3000},"sourceUrls":{"type":"array","minItems":1,"maxItems":8,"items":{"type":"string"}}}}},
        "sources":{"type":"array","minItems":2,"maxItems":12,"items":{"type":"object","additionalProperties":false,"required":["url","consultedOn","rightsIndication","rationale"],"properties":{"url":{"type":"string"},"consultedOn":{"type":"string"},"rightsIndication":{"type":"string","minLength":3,"maxLength":500},"rationale":{"type":"string","minLength":10,"maxLength":1000}}}},
        "currentState":{"type":"object","additionalProperties":false,"required":["purpose","gaps"],"properties":{"purpose":{"type":"string","minLength":20,"maxLength":1500},"gaps":{"type":"array","minItems":1,"maxItems":8,"items":{"type":"string","minLength":5,"maxLength":1000}}}},
        "improvementOpportunities":{"type":"array","minItems":1,"maxItems":8,"items":{"type":"string","minLength":10,"maxLength":1000}},
        "inspiration":{"type":"array","maxItems":8,"items":{"type":"object","additionalProperties":false,"required":["name","url","relevance"],"properties":{"name":{"type":"string","minLength":2,"maxLength":200},"url":{"type":"string"},"relevance":{"type":"string","minLength":10,"maxLength":1000}}}}
        """.trimIndent(),
        listOf("summary", "findings", "sources", "currentState", "improvementOpportunities", "inspiration"),
    )

    val productOwner = schema(
        """
        "productDirection":{"type":"string","minLength":20,"maxLength":3000},
        "rationale":{"type":"string","minLength":20,"maxLength":3000},
        "priorities":{"type":"array","minItems":1,"maxItems":5,"items":{"type":"string","minLength":3,"maxLength":500}},
        "decisions":{"type":"array","minItems":1,"maxItems":5,"items":{"type":"object","additionalProperties":false,"required":["decision","rationale","sourceUrls"],"properties":{"decision":{"type":"string","minLength":5,"maxLength":1000},"rationale":{"type":"string","minLength":10,"maxLength":2000},"sourceUrls":{"type":"array","minItems":1,"maxItems":8,"items":{"type":"string"}}}}},
        "rejectedOptions":{"type":"array","maxItems":5,"items":{"type":"string","minLength":3,"maxLength":1000}}
        """.trimIndent(),
        listOf("productDirection", "rationale", "priorities", "decisions", "rejectedOptions"),
    )

    val ux = schema(
        """
        "flowName":{"type":"string","minLength":3,"maxLength":200},
        "userGoal":{"type":"string","minLength":10,"maxLength":1000},
        "steps":{"type":"array","minItems":2,"maxItems":12,"items":{"type":"string","minLength":3,"maxLength":500}},
        "wireframe":{"type":"string","minLength":20,"maxLength":6000},
        "hypotheses":{"type":"array","minItems":1,"maxItems":6,"items":{"type":"string","minLength":10,"maxLength":1000}},
        "accessibility":{"type":"array","minItems":1,"maxItems":8,"items":{"type":"string","minLength":5,"maxLength":1000}},
        "privacyConsiderations":{"type":"array","minItems":1,"maxItems":8,"items":{"type":"string","minLength":5,"maxLength":1000}}
        """.trimIndent(),
        listOf("flowName", "userGoal", "steps", "wireframe", "hypotheses", "accessibility", "privacyConsiderations"),
    )

    val stories = schema(
        """
        "candidates":{"type":"array","minItems":1,"maxItems":3,"items":{"type":"object","additionalProperties":false,"required":["title","description","acceptanceCriteria","sourceUrls","dependsOn","risks"],"properties":{"title":{"type":"string","minLength":5,"maxLength":240},"description":{"type":"string","minLength":20,"maxLength":3000},"acceptanceCriteria":{"type":"array","minItems":1,"maxItems":8,"items":{"type":"string","minLength":5,"maxLength":1000}},"sourceUrls":{"type":"array","minItems":1,"maxItems":8,"items":{"type":"string"}},"dependsOn":{"type":"array","maxItems":5,"items":{"type":"string","maxLength":500}},"risks":{"type":"array","maxItems":8,"items":{"type":"string","maxLength":1000}}}}}
        """.trimIndent(),
        listOf("candidates"),
    )

    val critic = schema(
        """
        "overallVerdict":{"type":"string","enum":["ACCEPT","REVISE","REJECT"]},
        "summary":{"type":"string","minLength":10,"maxLength":3000},
        "issues":{"type":"array","maxItems":12,"items":{"type":"object","additionalProperties":false,"required":["severity","category","description","candidateIndex"],"properties":{"severity":{"type":"string","enum":["INFO","WARNING","BLOCKING"]},"category":{"type":"string","enum":["SOURCE","RIGHTS","PRIVACY","ACCESSIBILITY","SCOPE","CONSISTENCY"]},"description":{"type":"string","minLength":5,"maxLength":1000},"candidateIndex":{"type":"integer","minimum":-1,"maximum":2}}}},
        "candidateReviews":{"type":"array","minItems":1,"maxItems":3,"items":{"type":"object","additionalProperties":false,"required":["candidateIndex","verdict","reason"],"properties":{"candidateIndex":{"type":"integer","minimum":0,"maximum":2},"verdict":{"type":"string","enum":["ACCEPT","REVISE","REJECT"]},"reason":{"type":"string","minLength":5,"maxLength":1000}}}},
        "requiredChanges":{"type":"array","maxItems":10,"items":{"type":"string","minLength":5,"maxLength":1000}}
        """.trimIndent(),
        listOf("overallVerdict", "summary", "issues", "candidateReviews", "requiredChanges"),
    )

    val summary = schema(
        """
        "summary":{"type":"string","minLength":40,"maxLength":4000}
        """.trimIndent(),
        listOf("summary"),
    )

    private fun schema(properties: String, required: List<String>) =
        """{"type":"object","additionalProperties":false,"required":[${required.joinToString(",") { "\"$it\"" }}],"properties":{$properties}}"""
}
