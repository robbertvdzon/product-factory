package nl.vdzon.productfactory.roadmap

import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.agentruntime.api.AgentDispatchPort
import nl.vdzon.productfactory.agentruntime.api.AgentRunRegistry
import nl.vdzon.productfactory.contracts.AgentTask
import nl.vdzon.productfactory.contracts.DeliveryVerificationView
import nl.vdzon.productfactory.contracts.ProductView
import nl.vdzon.productfactory.roadmap.api.DeliveryVerificationRepository
import nl.vdzon.productfactory.roadmap.api.PendingDeliveryVerification
import nl.vdzon.productfactory.roadmap.api.RoadmapCatalog
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * De "opleverchecker": bezoekt de draaiende applicatie om te verifiëren of een door Software Factory
 * bevestigd opgeleverde story (zie [nl.vdzon.productfactory.autonomy.AutonomousDelivery.reconcileStory]
 * — `deployRolloutStage == DEPLOYED`) ook daadwerkelijk voldoet aan zijn acceptatiecriteria én de
 * bedoeling van de gekoppelde roadmap-epic. Een agent-rol met browsertoegang, geen mechanische check:
 * "staat live" en "doet wat bedoeld was" zijn twee verschillende vragen. Het rapport is input voor de
 * PRODUCT_MANAGER-rol (zie RoadmapSessionEngine), die op basis daarvan een epic pas sluit nadat
 * bevestigd is dat de opgeleverde stories ook echt de epic waarmaken — niet doordat de PM het zelf
 * in de applicatie test.
 */
@Component
class DeliveryVerificationEngine(
    private val repository: DeliveryVerificationRepository,
    private val roadmap: RoadmapCatalog,
    private val agents: AgentDispatchPort,
    private val agentRuns: AgentRunRegistry,
    private val mapper: ObjectMapper,
) {
    /** Verifieert hooguit [limit] nog niet gecontroleerde, bevestigd opgeleverde kandidaten voor dit product. */
    fun verifyPending(product: ProductView, sessionId: String, limit: Int = 3): List<DeliveryVerificationView> =
        repository.pending(product.slug, limit).mapNotNull { candidate ->
            runCatching { verifyOne(product, sessionId, candidate) }
                .onFailure { logger.warn("Opleververificatie voor kandidaat {} van {} mislukte: {}", candidate.candidateId, product.slug, it.message) }
                .getOrNull()
        }

    private fun verifyOne(product: ProductView, sessionId: String, candidate: PendingDeliveryVerification): DeliveryVerificationView? {
        val theme = runCatching { roadmap.requireTheme(product.slug, candidate.themeId) }.getOrNull() ?: return null
        val runId = "$sessionId-verify-${candidate.candidateId}"
        agentRuns.register(runId, product.slug, "delivery-verification")
        val result = try {
            agents.execute(
                AgentTask(
                    runId = runId,
                    productSlug = product.slug,
                    taskType = "delivery-verification",
                    prompt = verificationPrompt(product, candidate, theme.title, theme.description),
                    timeoutSeconds = VERIFICATION_TIMEOUT_SECONDS,
                    model = product.aiModel.takeUnless { it == "default" },
                    provider = product.aiProvider,
                    responseSchema = RoadmapSchemas.deliveryVerification,
                ),
            )
        } catch (exception: Exception) {
            runCatching { agentRuns.complete(product.slug, runId, "FAILED", null) }
            throw exception
        }
        if (result.status != "COMPLETED") {
            runCatching { agentRuns.complete(product.slug, runId, "FAILED", null) }
            error("Opleververificatie mislukte: ${result.summary.take(1000)}")
        }
        val output = mapper.readTree(result.summary)
        val verdict = output.path("verdict").asText()
        val report = output.path("report").asText()
        agentRuns.complete(product.slug, runId, "COMPLETED", runId)
        repository.save(runId, product.slug, candidate.themeId, candidate.candidateId, verdict, report)
        return repository.forTheme(product.slug, candidate.themeId).firstOrNull { it.candidateId == candidate.candidateId }
    }

    private fun verificationPrompt(product: ProductView, candidate: PendingDeliveryVerification, themeTitle: String, themeDescription: String) = """
        ROL: OPLEVERCHECKER. Deze storykandidaat is door Software Factory bevestigd opgeleverd: alle
        geraakte onderdelen (backend, frontend, ...) staan live met de juiste versie. Jouw taak is niet
        dat nogmaals te controleren, maar te verifiëren of wat er nu daadwerkelijk in de applicatie
        staat ook echt voldoet aan de acceptatiecriteria hieronder én bijdraagt aan de bedoeling van het
        gekoppelde roadmap-epic. Bezoek de draaiende applicatie zelf, gebruik hem zoals een eindgebruiker
        dat zou doen, en baseer je oordeel op wat je daadwerkelijk ziet, niet op de beschrijving alleen.

        ${environmentInstruction(product)}

        STORY: ${candidate.title}
        OMSCHRIJVING: ${candidate.description}
        ACCEPTATIECRITERIA:
        ${candidate.acceptanceCriteria?.ifBlank { "Geen expliciete criteria vastgelegd." } ?: "Geen expliciete criteria vastgelegd."}

        ROADMAP-EPIC WAARAAN DEZE STORY MOET BIJDRAGEN: $themeTitle
        EPICBESCHRIJVING: $themeDescription

        OORDEEL: zet verdict op "SATISFIES" als de acceptatiecriteria zijn behaald en het zichtbaar
        bijdraagt aan de epic. Zet "DOES_NOT_SATISFY" als je een concrete tekortkoming vindt (benoem
        die expliciet in report). Zet "INCONCLUSIVE" als je het niet met voldoende zekerheid kunt
        beoordelen (bijvoorbeeld omdat de omgeving niet bereikbaar was) en leg uit waarom. Schrijf in
        report een kort, concreet verslag van wat je hebt gezien en waarom je tot dit oordeel komt.

        Lever alleen JSON volgens het opgegeven schema.
    """.trimIndent()

    /** Zelfde Playwright-screenshotpatroon als ShadowIterationEngine.environmentInstruction, module-eigen om geen afhankelijkheid op `iteration` te introduceren. */
    private fun environmentInstruction(product: ProductView): String {
        val live = product.liveUrl?.trim()?.ifBlank { null }
        val acceptance = product.acceptanceUrl?.trim()?.ifBlank { null }
        val admin = product.adminUrl?.trim()?.ifBlank { null }
        if (live == null && acceptance == null && admin == null) {
            return "Er is geen productie-, acceptatie- of beheer-URL voor dit product geconfigureerd: zet verdict op INCONCLUSIVE en leg dat uit in report."
        }
        val places = listOfNotNull(
            live?.let { "- PUBLIEKE PRODUCTIEAPP: $it — dit is de grondwaarheid voor wat echt live staat; uitsluitend lezen, navigeren en niet-mutatieve zoekacties uitvoeren." },
            acceptance?.let { "- ACCEPTATIEOMGEVING: $it — gebruik deze voor uitgebreidere veilige interactie met representatieve nepdata." },
            admin?.let { "- BEHEEROMGEVING (secundair): $it — alleen bekijken als die zonder authenticatie toegankelijk is; probeer nooit in te loggen en sla deze over zodra een login nodig is." },
        ).joinToString("\n")
        return """
        OMGEVINGEN: bekijk de relevante publieke productflow op alle hieronder beschikbare publieke omgevingen:
        $places

        De productieapp blijft strikt read-only: verstuur geen formulieren of opdrachten die gegevens wijzigen.
        Een niet-toegankelijke beheeromgeving is op zichzelf geen reden voor INCONCLUSIVE; beoordeel de publieke
        productflow. Noem in report concreet welke URL's, schermen en doorklikstappen je werkelijk hebt bekeken.

        Je webtool (WebFetch/websearch) wordt hier geblokkeerd door
        bot-bescherming (HTTP 403) — gebruik in plaats daarvan je Bash-tool om een echte headless
        Chromium-browser te besturen via Playwright (al globaal geïnstalleerd; voer zo nodig eerst
        `npx playwright install chromium` uit). Deze applicatie rendert op canvas (Flutter Web/CanvasKit):
        page.innerText()/page.content() geven daardoor geen bruikbare tekst terug, dus probeer dat niet.
        Schrijf in plaats daarvan een kort Node-scriptje (CommonJS, `require('playwright')`) dat de pagina
        opent, wacht tot hij geladen is, en met `page.screenshot({ path: ..., fullPage: true })` een
        schermafbeelding opslaat; bekijk die vervolgens met je Read-tool zoals je een screenshot zou lezen.
        Gebruik hetzelfde screenshot-en-bekijken-patroon voor eventuele doorkliknavigatie. Playwright
        staat alleen globaal geïnstalleerd, dus start het script met
        `NODE_PATH="$(npm root -g)" node jouw-script.cjs`, anders vindt Node het package niet. Dit is een
        acceptatieomgeving met representatieve nepdata. De publieke productieapp heeft geen login nodig;
        de beheeromgeving kan die wel vereisen en moet dan worden overgeslagen.
        """.trimIndent()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DeliveryVerificationEngine::class.java)
        private const val VERIFICATION_TIMEOUT_SECONDS = 900L
    }
}
