package nl.vdzon.productfactory.preview

import nl.vdzon.productfactory.iteration.ShadowIterationRepository
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

data class PreviewSeedResult(val applied: List<String>, val alreadyPresent: List<String>)

/**
 * Vult een lege previewdatabase met representatieve demodata via de bestaande domeinrepositories,
 * zodat een tester direct een gevulde applicatie ziet in plaats van een leeg scherm. Idempotent:
 * iedere seed-set wordt bijgehouden in `preview_seed_history` en slaat over wat al is toegepast.
 */
@Service
class PreviewDataSeeder(
    private val jdbcTemplate: JdbcTemplate,
    private val products: ProductCatalog,
    private val iterations: ShadowIterationRepository,
    private val preview: PreviewRuntimeConfig,
) {
    @Transactional
    fun ensure(): PreviewSeedResult {
        val prNumber = preview.requireSeedingAllowed()

        val applied = mutableListOf<String>()
        val alreadyPresent = mutableListOf<String>()
        listOf(ITERATIONS_SEED_KEY to ::seedIterations, RECORDS_SEED_KEY to ::seedProductRecords).forEach { (key, seed) ->
            if (isApplied(key)) {
                alreadyPresent += key
            } else {
                seed()
                markApplied(key, prNumber)
                applied += key
            }
        }
        return PreviewSeedResult(applied, alreadyPresent)
    }

    private fun isApplied(seedKey: String): Boolean = jdbcTemplate.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM preview_seed_history WHERE seed_key = ?)",
        Boolean::class.java,
        seedKey,
    ) == true

    private fun markApplied(seedKey: String, prNumber: Int) {
        jdbcTemplate.update(
            "INSERT INTO preview_seed_history (seed_key, pr_number) VALUES (?, ?)",
            seedKey,
            prNumber,
        )
    }

    private fun seedIterations() {
        val accepted = iterations.create(PRODUCT_SLUG, "Onderzoek welke bronervaring bezoekers het meest waarderen bij het ontdekken van de Kerkweg.")
        runStep(accepted.id, "RESEARCHER", """{"findings":"Vrijwilligers waarderen foto's met herkenbare huisnummers en namen het meest."}""")
        runStep(accepted.id, "PRODUCT_OWNER", """{"decision":"Prioriteer bronnen met een concrete plaats- en persoonsverwijzing."}""")
        runStep(accepted.id, "UX_DESIGNER", """{"flow":"Kaartweergave met foto-pins die doorklikken naar het verhaal."}""")
        runStep(accepted.id, "STORY_WRITER", """{"story":"Als bezoeker wil ik foto's op een kaart zien, zodat ik verhalen bij een plek kan ontdekken."}""")
        runStep(accepted.id, "CRITIC", """{"verdict":"ACCEPT","reason":"Kleine, toetsbare eerste stap met duidelijke gebruikerswaarde."}""")
        iterations.saveSource(
            accepted.id, PRODUCT_SLUG,
            "https://historischekringheemskerk.nl/kerkweg-1926",
            LocalDate.parse("2026-07-28"),
            "publiek, met bronvermelding",
            "Enige beschikbare gedateerde fotoserie van deze straat.",
        )
        iterations.saveKnowledge(
            accepted.id, PRODUCT_SLUG,
            "Bronervaring Kerkweg", "Foto's met naam, huisnummer en bronverwijzing scoren het best in gebruikerstests.", "https://historischekringheemskerk.nl/kerkweg-1926",
            "Prioriteer verrijkte foto's", "We tonen eerst bronnen met plaats- en persoonsverwijzing, generieke foto's later.",
            "Kaart met foto-pins", "Een kaartweergave met foto-pins die doorklikken naar het volledige verhaal.",
        )
        iterations.saveCandidate(
            accepted.id, PRODUCT_SLUG,
            "Kaartweergave met foto-pins",
            "Toon foto's met een plaatsverwijzing als pin op een kaart van Heemskerk.",
            "- Een bezoeker ziet minimaal 5 foto-pins\n- Een klik op een pin toont het volledige verhaal",
            "preview-seed-map-pins",
            "ACCEPT",
            "Kleine, toetsbare eerste stap met duidelijke gebruikerswaarde.",
            null,
        )
        iterations.saveCandidate(
            accepted.id, PRODUCT_SLUG,
            "AR-weergave van historische gevels",
            "Toon via de camera hoe een gevel er honderd jaar geleden uitzag.",
            "- Werkt op minimaal 80% van de ondersteunde toestellen",
            "preview-seed-ar-facades",
            "REJECT",
            "Te veel technisch risico voor een eerste stap; nog geen onderbouwde gebruikersbehoefte.",
            null,
        )
        iterations.markAccepted(accepted.id, "ACCEPT", "preview-seed-workspace-run-1", null, null)
        iterations.saveSummary(accepted.id, "Onderzoek en ontwerp voor een kaartweergave met foto-pins zijn afgerond en geaccepteerd.")

        val failed = iterations.create(PRODUCT_SLUG, "Onderzoek herbruikbare structuur voor toekomstige bronnen zoals audio en video.")
        iterations.saveCandidate(
            failed.id, PRODUCT_SLUG,
            "Audiofragmenten bij historische locaties",
            "Voeg afspeelbare mondelinge-geschiedenisfragmenten toe aan bestaande locaties.",
            "- Een fragment is maximaal 3 minuten\n- Transcriptie is beschikbaar voor toegankelijkheid",
            "preview-seed-audio-fragments",
            "ACCEPT",
            "Inhoudelijk geaccepteerd, maar workspace-publicatie is mislukt.",
            null,
        )
        iterations.markFailed(failed.id, "Workspace-publicatie tijdelijk niet beschikbaar (previewdata, gesimuleerd).")

        val needsRevision = iterations.create(PRODUCT_SLUG, "Onderzoek of een tijdlijnweergave bezoekers helpt gebeurtenissen in context te plaatsen.")
        runStep(needsRevision.id, "RESEARCHER", """{"findings":"Onvoldoende gedateerde bronnen beschikbaar voor een sluitende tijdlijn."}""")
        iterations.saveCandidate(
            needsRevision.id, PRODUCT_SLUG,
            "Tijdlijnweergave per wijk",
            "Toon gebeurtenissen per wijk chronologisch op een tijdlijn.",
            "- Minimaal 10 gedateerde gebeurtenissen per wijk",
            "preview-seed-timeline",
            "REVISE",
            "Te weinig gedateerde bronnen om een geloofwaardige tijdlijn te vullen; eerst brononderzoek verdiepen.",
            null,
        )
        iterations.markReviewed(needsRevision.id, "REVISE", "NEEDS_REVISION")
    }

    private fun runStep(iterationId: String, role: String, outputJson: String) {
        val runId = "preview-$iterationId-${role.lowercase()}"
        iterations.startStep(iterationId, PRODUCT_SLUG, role, 1, runId)
        iterations.completeStep(iterationId, role, 1, outputJson)
    }

    private fun seedProductRecords() {
        products.addRecord(
            PRODUCT_SLUG, "research",
            "Vergelijkbare toepassingen",
            "Andere lokale-erfgoedapps combineren kaart, tijdlijn en zoekfunctie; gebruikers starten meestal vanaf een kaart.",
            "https://historischekringheemskerk.nl",
        )
        products.addRecord(
            PRODUCT_SLUG, "memory",
            "Gebruikers starten vanaf de kaart",
            "Herhaald gevonden in onderzoek: een kaartweergave is het meest gebruikte startpunt voor ontdekking.",
        )
        products.addRecord(
            PRODUCT_SLUG, "decision",
            "Eerste verticale functionaliteit is kaart met foto-pins",
            "Gekozen boven een tijdlijn omdat er voldoende gedateerde, verrijkte foto's beschikbaar zijn.",
        )
    }

    private companion object {
        const val PRODUCT_SLUG = "hkh-autopilot"
        const val ITERATIONS_SEED_KEY = "shadow-iterations-v1"
        const val RECORDS_SEED_KEY = "product-records-v1"
    }
}

@Component
@ConditionalOnProperty(name = ["PF_PREVIEW_ENABLED"], havingValue = "true")
class PreviewDataStartup(private val seeder: PreviewDataSeeder) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        seeder.ensure()
    }
}
