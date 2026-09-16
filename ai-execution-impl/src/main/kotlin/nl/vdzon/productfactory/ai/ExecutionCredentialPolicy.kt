package nl.vdzon.productfactory.ai

import nl.vdzon.productfactory.api.ai.RequestAiTaskCommand

fun executionCredentialAllowed(key: String, command: RequestAiTaskCommand): Boolean {
    if (key == "PVDD__PRODUCTION_READ_ONLY_TOKEN") {
        return command.productId?.value == "pvdd" && command.agentRole == "PRODUCT_ADVISOR" &&
            command.jobKey.value == "PRODUCT_ADVISOR.CONVERSE" && command.requesterCapability == "product-advisor"
    }
    return key.matches(Regex("[A-Z][A-Z0-9_]*__(TEST|ACCEPTANCE|PREVIEW)_[A-Z][A-Z0-9_]*")) &&
        key.substringAfter("__").split('_').none {
            it in setOf("PRODUCTION", "PROD", "PRD", "KUBECONFIG", "CLUSTER", "SIGNING", "REMEMBER", "PRIVATE", "WORKER", "GITHUB")
        }
}
