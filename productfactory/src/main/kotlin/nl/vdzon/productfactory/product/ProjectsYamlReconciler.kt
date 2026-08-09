package nl.vdzon.productfactory.product

import nl.vdzon.productfactory.product.api.ProductCatalog
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.yaml.snakeyaml.Yaml

/**
 * Past bij het opstarten de vaste projectgegevens (GitHub-repo, acceptatie-URL, admin-URL) uit
 * `projects.yaml` toe op reeds bestaande producten. De database blijft de bron voor alles wat via het
 * dashboard wijzigt (status, cyclustijden, AI-instellingen); dit bestand is de bron voor wat in de praktijk
 * nooit los van een codewijziging verandert, zodat die gegevens met een gewone pull request te herstellen
 * zijn in plaats van met een handmatige aanroep op de draaiende runtime.
 *
 * Een slug die hier staat maar nog geen product heeft, wordt overgeslagen: dit bestand kan geen product
 * aanmaken (daarvoor zijn meer velden nodig dan hier bijgehouden worden), alleen bijwerken.
 */
@Component
class ProjectsYamlReconciler(private val catalog: ProductCatalog) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun reconcile() {
        val resource = ClassPathResource("projects.yaml")
        if (!resource.exists()) return
        val root = resource.inputStream.use { Yaml().load<Map<String, Any>>(it) } ?: return
        @Suppress("UNCHECKED_CAST")
        val entries = root["products"] as? List<Map<String, Any>> ?: return
        entries.forEach { entry ->
            val slug = entry["slug"] as? String
            if (slug.isNullOrBlank()) {
                logger.warn("projects.yaml bevat een entry zonder slug; overgeslagen.")
                return@forEach
            }
            runCatching {
                catalog.reconcileFixedFields(
                    slug,
                    entry["targetRepositoryName"] as? String,
                    entry["acceptanceUrl"] as? String,
                    entry["adminUrl"] as? String,
                )
            }.onFailure { logger.warn("projects.yaml-entry voor '$slug' kon niet worden toegepast: ${it.message}") }
        }
    }
}
