package nl.vdzon.productfactory.product.api

/**
 * Bekende AI-providers/modellen voor zowel de interne shadow-iteratiepipeline (draait altijd via de lokale
 * `codex`- of `claude`-CLI in agentworker) als de aiSupplier/aiModel die naar de Software Factory wordt
 * doorgestuurd. "default" laat de betreffende CLI zijn eigen standaardmodel kiezen.
 */
object AiCatalog {
    val MODELS_BY_PROVIDER: Map<String, List<String>> = mapOf(
        "codex" to listOf("default", "gpt-5.6-terra", "gpt-5.6-sol", "gpt-5.6-luna"),
        "claude" to listOf(
            "default",
            "claude-opus-5",
            "claude-opus-4-8",
            "claude-opus-4-7",
            "claude-opus-4-6",
            "claude-opus-4-5",
            "claude-sonnet-5",
            "claude-sonnet-4-6",
            "claude-haiku-4-5",
        ),
    )

    val PROVIDERS: Set<String> = MODELS_BY_PROVIDER.keys

    fun isValid(provider: String, model: String): Boolean = MODELS_BY_PROVIDER[provider]?.contains(model) == true
}
