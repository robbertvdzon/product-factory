package nl.vdzon.productfactory.roadmap

/** Gesloten JSON-schema's: onbekende velden worden voor iedere rol geweigerd. */
internal object LivingVisionSchemas {
    private val stringList = """{"type":"array","maxItems":30,"items":{"type":"string","maxLength":2000}}"""
    private val source = """{"type":"object","additionalProperties":false,"required":["url","title","accessedAt","observation","interpretation","relevance","limitations","confidence","rightsNote"],"properties":{"url":{"type":"string","pattern":"^https?://.+","maxLength":2000},"title":{"type":"string","minLength":1,"maxLength":500},"accessedAt":{"type":"string","format":"date-time"},"observation":{"type":"string","minLength":1,"maxLength":5000},"interpretation":{"type":"string","minLength":1,"maxLength":5000},"relevance":{"type":"string","minLength":1,"maxLength":5000},"limitations":{"type":"string","maxLength":3000},"confidence":{"type":"integer","minimum":0,"maximum":100},"rightsNote":{"type":"string","maxLength":2000}}}"""
    private val idea = """{"type":"object","additionalProperties":false,"required":["action","ideaKey","promise","primaryAudience","need","reason","evidence","mergedIdeaKeys","selectUx","researchQuestions"],"properties":{"action":{"type":"string","enum":["CREATE","REFINE","MERGE","PARK","REJECT","NO_CHANGE"]},"ideaKey":{"type":"string","minLength":1,"maxLength":100},"promise":{"type":"string","minLength":10,"maxLength":4000},"primaryAudience":{"type":"string","minLength":2,"maxLength":2000},"need":{"type":"string","minLength":10,"maxLength":4000},"reason":{"type":"string","minLength":2,"maxLength":3000},"evidence":{"type":"string","maxLength":5000},"mergedIdeaKeys":$stringList,"selectUx":{"type":"boolean"},"researchQuestions":$stringList}}"""
    private val generatedImage = """{"type":"object","additionalProperties":false,"required":["temporaryPath","mediaType","filename","altText","viewport","flowPosition"],"properties":{"temporaryPath":{"type":"string","minLength":1,"maxLength":2000},"mediaType":{"type":"string","enum":["image/png","image/jpeg","image/webp"]},"filename":{"type":"string","minLength":1,"maxLength":255},"altText":{"type":"string","minLength":1,"maxLength":1000},"viewport":{"type":"string","enum":["MOBILE","DESKTOP"]},"flowPosition":{"type":"integer","minimum":0,"maximum":30}}}"""

    val discovery = objectSchema(
        listOf("summary", "sources", "ideas", "surprisingPossibilities"),
        """"summary":{"type":"string","minLength":10,"maxLength":4000},"sources":{"type":"array","maxItems":20,"items":$source},"ideas":{"type":"array","maxItems":20,"items":$idea},"surprisingPossibilities":$stringList""",
    )
    val curator = objectSchema(
        listOf("summary", "ideas", "surprisingPossibilities"),
        """"summary":{"type":"string","minLength":10,"maxLength":4000},"ideas":{"type":"array","minItems":1,"maxItems":20,"items":$idea},"surprisingPossibilities":$stringList""",
    )
    val ux = objectSchema(
        listOf("summary", "ideaKey", "conceptKey", "userGoal", "interaction", "content", "states", "decisions", "assumptions", "openQuestions", "generatedImages"),
        """"summary":{"type":"string","minLength":10,"maxLength":4000},"ideaKey":{"type":"string","minLength":1,"maxLength":100},"conceptKey":{"type":"string","minLength":1,"maxLength":100},"userGoal":{"type":"string","minLength":10,"maxLength":3000},"interaction":{"type":"string","minLength":10,"maxLength":5000},"content":{"type":"string","minLength":10,"maxLength":5000},"states":$stringList,"decisions":$stringList,"assumptions":$stringList,"openQuestions":$stringList,"generatedImages":{"type":"array","minItems":2,"maxItems":6,"items":$generatedImage}""",
    )
    val feasibility = objectSchema(
        listOf("summary", "results"),
        """"summary":{"type":"string","minLength":10,"maxLength":4000},"results":{"type":"array","minItems":1,"maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["ideaKey","conceptKey","capabilityKey","researchType","question","evidence","sources","limitations","confidence","status","conclusion","recommendedNextStep","dependencies"],"properties":{"ideaKey":{"type":"string","minLength":1,"maxLength":100},"conceptKey":{"type":["string","null"]},"capabilityKey":{"type":["string","null"]},"researchType":{"type":"string","enum":["TECHNOLOGY","DATA_INTEGRATION","PRODUCT_VALUE","PRIVACY_RIGHTS","ACCESSIBILITY"]},"question":{"type":"string","minLength":5,"maxLength":3000},"evidence":{"type":"string","minLength":5,"maxLength":7000},"sources":$stringList,"limitations":{"type":"string","maxLength":5000},"confidence":{"type":"integer","minimum":0,"maximum":100},"status":{"type":"string","enum":["UNKNOWN","TESTING","VALIDATED","INVALIDATED","CURRENTLY_BLOCKED","FUNDAMENTALLY_IMPOSSIBLE"]},"conclusion":{"type":"string","minLength":5,"maxLength":5000},"recommendedNextStep":{"type":"string","minLength":5,"maxLength":3000},"dependencies":$stringList}}}""",
    )
    val review = objectSchema(
        listOf("summary", "approved", "revisionRequests"),
        """"summary":{"type":"string","minLength":10,"maxLength":4000},"approved":{"type":"boolean"},"revisionRequests":$stringList""",
    )
    val director = objectSchema(
        listOf("summary", "approved", "revisionRequests", "conceptRevisions"),
        """"summary":{"type":"string","minLength":10,"maxLength":4000},"approved":{"type":"boolean"},"revisionRequests":$stringList,"conceptRevisions":{"type":"array","maxItems":2,"items":{"type":"object","additionalProperties":false,"required":["conceptKey","reason","evidenceImpact","userGoal","interaction","content","states","decisions","assumptions","openQuestions"],"properties":{"conceptKey":{"type":"string","minLength":1,"maxLength":100},"reason":{"type":"string","minLength":5,"maxLength":3000},"evidenceImpact":{"type":"string","minLength":5,"maxLength":5000},"userGoal":{"type":"string","minLength":10,"maxLength":3000},"interaction":{"type":"string","minLength":10,"maxLength":5000},"content":{"type":"string","minLength":10,"maxLength":5000},"states":$stringList,"decisions":$stringList,"assumptions":$stringList,"openQuestions":$stringList}}}""",
    )
    val strategy = RoadmapSchemas.strategy
    val manager = RoadmapSchemas.session

    fun forRole(role: String): String = when (role) {
        "product-market-scout", "domain-source-scout", "wild-ideas" -> discovery
        "vision-curator" -> curator
        "ux-concept" -> ux
        "feasibility" -> feasibility
        "ux-director" -> director
        "vision-critic" -> review
        "future-strategist" -> strategy
        "roadmap-manager" -> manager
        else -> objectSchema(listOf("summary"), """"summary":{"type":"string","minLength":1,"maxLength":4000}""")
    }

    private fun objectSchema(required: List<String>, properties: String) =
        """{"type":"object","additionalProperties":false,"required":[${required.joinToString(",") { "\"$it\"" }}],"properties":{$properties}}"""
}
