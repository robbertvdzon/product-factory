package nl.vdzon.productfactory.roadmap

import nl.vdzon.productfactory.contracts.RoadmapIdeaStatus
import nl.vdzon.productfactory.roadmap.api.ConceptVersionMutation
import nl.vdzon.productfactory.roadmap.api.IdeaMutation
import nl.vdzon.productfactory.roadmap.api.LivingVisionCatalog
import nl.vdzon.productfactory.roadmap.api.RoadmapVisionCatalog
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/products/{slug}/roadmap/living-vision")
class LivingVisionController(
    private val catalog: LivingVisionCatalog,
    private val migration: LegacyVisionMigrationService,
) {
    @GetMapping("/portfolio")
    fun portfolio(@PathVariable slug: String): Map<String, Any> {
        val concepts = catalog.concepts(slug)
        return mapOf(
            "ideas" to catalog.ideas(slug),
            "concepts" to concepts,
            "conceptVersions" to concepts.flatMap { catalog.conceptVersions(slug, it.conceptKey) },
            "inspiration" to catalog.inspiration(slug),
            "research" to catalog.research(slug),
        )
    }

    @GetMapping("/ideas/{ideaKey}/history")
    fun ideaHistory(@PathVariable slug: String, @PathVariable ideaKey: String) = catalog.ideaHistory(slug, ideaKey)

    @GetMapping("/concepts/{conceptKey}/history")
    fun conceptHistory(@PathVariable slug: String, @PathVariable conceptKey: String) = catalog.conceptVersions(slug, conceptKey)

    @GetMapping("/sessions/{sessionId}/steps")
    fun steps(@PathVariable slug: String, @PathVariable sessionId: String) = catalog.steps(sessionId).also { steps ->
        require(steps.isEmpty() || steps.all { it.productSlug == slug }) { "Sessiestappen horen bij een ander product" }
    }

    @PostMapping("/migrate-legacy")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun migrate(@PathVariable slug: String) = migration.migrate(slug)
}

/** Zet de huidige v1-projectie deterministisch om; voegt nooit nieuwe AI-inhoud toe. */
@Service
class LegacyVisionMigrationService(
    private val visions: RoadmapVisionCatalog,
    private val catalog: LivingVisionCatalog,
) {
    @Transactional
    fun migrate(productSlug: String): Map<String, Int> {
        val vision = visions.current(productSlug) ?: return mapOf("ideas" to 0, "concepts" to 0)
        var ideas = 0
        var concepts = 0
        val experiences = vision.content["experiences"] as? List<*> ?: emptyList<Any?>()
        experiences.mapNotNull { it as? Map<*, *> }.forEachIndexed { index, experience ->
            val originalKey = experience["key"]?.toString()?.takeIf(::validKey) ?: "legacy-experience-${index + 1}"
            if (catalog.ideas(productSlug).none { it.ideaKey == originalKey }) {
                catalog.appendIdea(
                    productSlug,
                    IdeaMutation(
                        ideaKey = originalKey,
                        status = RoadmapIdeaStatus.EXPLORED,
                        promise = experience["promise"]?.toString().orEmpty().ifBlank { experience["title"]?.toString().orEmpty() },
                        primaryAudience = "Overgenomen uit bestaande visie; doelgroep was niet afzonderlijk vastgelegd.",
                        need = experience["scenario"]?.toString().orEmpty().ifBlank { experience["promise"]?.toString().orEmpty() },
                        origin = "legacy-migration",
                        changeReason = "Idempotente migratie uit roadmap_future_vision versie ${vision.version}.",
                        evidence = null,
                        details = experience.entries.associate { it.key.toString() to it.value },
                        sessionId = vision.createdBySessionId,
                        role = "legacy-migration",
                    ),
                )
                ideas++
            }
        }
        val assumptions = vision.content["assumptions"] as? List<*> ?: emptyList<Any?>()
        assumptions.mapNotNull { it as? Map<*, *> }.forEachIndexed { index, assumption ->
            val originalKey = assumption["key"]?.toString()?.takeIf(::validKey) ?: "legacy-assumption-${index + 1}"
            val ideaKey = availableMigrationKey(productSlug, originalKey, "assumption")
            if (catalog.ideas(productSlug).none { it.ideaKey == ideaKey }) {
                catalog.appendIdea(
                    productSlug,
                    IdeaMutation(
                        ideaKey = ideaKey,
                        status = RoadmapIdeaStatus.TESTING,
                        promise = assumption["statement"]?.toString().orEmpty(),
                        primaryAudience = "Niet afzonderlijk vastgelegd in v1.",
                        need = assumption["risk"]?.toString().orEmpty()
                            .ifBlank { assumption["proposedProbe"]?.toString().orEmpty() },
                        origin = "legacy-migration-assumption",
                        changeReason = "Idempotente migratie uit roadmap_future_vision versie ${vision.version}.",
                        evidence = null,
                        details = assumption.entries.associate { it.key.toString() to it.value },
                        sessionId = vision.createdBySessionId,
                        role = "legacy-migration",
                    ),
                )
                ideas++
            }
        }
        val capabilities = vision.content["capabilities"] as? List<*> ?: emptyList<Any?>()
        capabilities.mapNotNull { it as? Map<*, *> }.forEachIndexed { index, capability ->
            val originalKey = capability["key"]?.toString()?.takeIf(::validKey) ?: "legacy-capability-${index + 1}"
            val ideaKey = availableMigrationKey(productSlug, originalKey, "capability")
            if (catalog.ideas(productSlug).none { it.ideaKey == ideaKey }) {
                val proven = capability["feasibility"]?.toString() == "PROVEN"
                catalog.appendIdea(
                    productSlug,
                    IdeaMutation(
                        ideaKey = ideaKey,
                        status = if (proven) RoadmapIdeaStatus.VALIDATED else RoadmapIdeaStatus.EXPLORED,
                        promise = capability["outcome"]?.toString().orEmpty()
                            .ifBlank { capability["title"]?.toString().orEmpty() },
                        primaryAudience = "Niet afzonderlijk vastgelegd in v1.",
                        need = capability["successMeasure"]?.toString().orEmpty(),
                        origin = "legacy-migration-capability",
                        changeReason = "Idempotente migratie uit roadmap_future_vision versie ${vision.version}.",
                        evidence = null,
                        details = capability.entries.associate { it.key.toString() to it.value },
                        sessionId = vision.createdBySessionId,
                        role = "legacy-migration",
                    ),
                )
                ideas++
            }
        }
        val knownIdeas = catalog.ideas(productSlug)
        val defaultIdea = knownIdeas.firstOrNull()?.ideaKey
        val screens = vision.content["conceptScreens"] as? List<*> ?: emptyList<Any?>()
        screens.mapNotNull { it as? Map<*, *> }.forEachIndexed { index, screen ->
            val conceptKey = screen["key"]?.toString()?.takeIf(::validKey) ?: "legacy-concept-${index + 1}"
            val ideaKey = screen["experienceKey"]?.toString()?.takeIf { key -> knownIdeas.any { it.ideaKey == key } } ?: defaultIdea
            if (ideaKey != null && catalog.concepts(productSlug).none { it.conceptKey == conceptKey }) {
                val idea = knownIdeas.single { it.ideaKey == ideaKey }
                catalog.appendConcept(
                    productSlug,
                    ConceptVersionMutation(
                        conceptKey = conceptKey,
                        ideaKey = ideaKey,
                        ideaVersion = idea.currentVersion,
                        viewport = screen["viewport"]?.toString()?.takeIf { it in setOf("MOBILE", "DESKTOP") } ?: "DESKTOP",
                        flowPosition = index,
                        userGoal = screen["title"]?.toString().orEmpty().ifBlank { "Bestaand conceptscherm begrijpen" },
                        interaction = listOfNotNull(screen["primaryAction"], screen["secondaryAction"]).joinToString("; ").ifBlank { "Niet vastgelegd in v1" },
                        content = listOfNotNull(screen["headline"], screen["body"], screen["visualDescription"]).joinToString("\n"),
                        states = listOf("legacy-import"),
                        decisions = listOf("Ongewijzigd overgenomen uit de bestaande visieprojectie."),
                        assumptions = emptyList(),
                        openQuestions = emptyList(),
                        review = "Gemigreerd; nog niet door een UX-director beoordeeld.",
                        sessionId = vision.createdBySessionId,
                    ),
                )
                concepts++
            }
        }
        return mapOf("ideas" to ideas, "concepts" to concepts)
    }

    private fun validKey(value: String) = value.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")) && value.length <= 100

    private fun availableMigrationKey(productSlug: String, originalKey: String, category: String): String {
        val existing = catalog.ideas(productSlug).singleOrNull { it.ideaKey == originalKey } ?: return originalKey
        if (existing.origin == "legacy-migration-$category") return originalKey
        return "legacy-$category-$originalKey".take(100)
    }
}
