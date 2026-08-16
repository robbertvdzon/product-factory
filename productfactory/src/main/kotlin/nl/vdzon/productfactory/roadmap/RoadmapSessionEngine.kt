package nl.vdzon.productfactory.roadmap

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.agentruntime.api.AgentDispatchPort
import nl.vdzon.productfactory.agentruntime.api.AgentRunRegistry
import nl.vdzon.productfactory.bug.api.BugCatalog
import nl.vdzon.productfactory.bug.api.BugMutation
import nl.vdzon.productfactory.contracts.AgentTask
import nl.vdzon.productfactory.contracts.ProductView
import nl.vdzon.productfactory.contracts.RoadmapEpicView
import nl.vdzon.productfactory.contracts.RoadmapSessionView
import nl.vdzon.productfactory.meeting.api.MeetingCatalog
import nl.vdzon.productfactory.product.api.ProductCatalog
import nl.vdzon.productfactory.roadmap.api.DeliveryVerificationRepository
import nl.vdzon.productfactory.roadmap.api.RoadmapCatalog
import nl.vdzon.productfactory.roadmap.api.RoadmapSessionRepository
import nl.vdzon.productfactory.roadmap.api.RoadmapVisionCatalog
import nl.vdzon.productfactory.workspace.api.WorkspaceArtifact
import nl.vdzon.productfactory.workspace.api.WorkspacePublicationPort
import nl.vdzon.productfactory.workspace.api.WorkspaceVisionPort
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Component
class RoadmapSessionRunner(
    private val engine: RoadmapSessionEngine,
    private val repository: RoadmapSessionRepository,
) {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun start(event: RoadmapSessionStarted) {
        runCatching { engine.run(event.sessionId) }
            .onFailure { repository.markFailed(event.sessionId, it.message ?: it.javaClass.simpleName) }
    }
}

/** Applies a completely validated strategy as one transaction; a bad update can never leave a half roadmap. */
@Component
class RoadmapSessionApplier(
    private val roadmap: RoadmapCatalog,
    private val visions: RoadmapVisionCatalog,
    private val bugs: BugCatalog,
) {
    @Transactional
    fun apply(productSlug: String, sessionId: String, strategy: JsonNode, output: JsonNode) {
        val capabilityKeys = strategy.path("capabilities").mapTo(linkedSetOf()) { it.path("key").asText() }
        output.path("epicUpdates").forEach { update ->
            val capabilityKey = update.path("capabilityKey").takeIf(JsonNode::isTextual)?.asText()
            require(capabilityKey == null || capabilityKey in capabilityKeys) {
                "Epic verwijst naar onbekende capability '$capabilityKey'"
            }
        }
        visions.createVersion(
            productSlug,
            sessionId,
            strategy,
            strategy.path("visionChangeSummary").asText(),
        )
        output.path("epicUpdates").forEach { update ->
            val action = update.path("action").asText()
            val title = update.path("title").asText().trim()
            val description = update.path("description").asText().trim()
            val processRank = update.path("processRank").asInt(1)
            val dependencyIds = update.path("dependencyIds").mapTo(linkedSetOf()) { it.asText() }
            val epicId = update.path("epicId").takeIf(JsonNode::isTextual)?.asText()
            val horizon = update.path("horizon").asText()
            val kind = update.path("kind").asText()
            val capabilityKey = update.path("capabilityKey").takeIf(JsonNode::isTextual)?.asText()
            when (action) {
                "CREATE" -> {
                    require(epicId == null) { "CREATE mag geen epicId bevatten" }
                    roadmap.createEpic(productSlug, title, description, processRank, dependencyIds, horizon, kind, capabilityKey)
                }
                "UPDATE", "CLOSE" -> {
                    requireNotNull(epicId) { "$action vereist een bestaand epicId" }
                    roadmap.updateEpicFromProcess(
                        productSlug = productSlug,
                        id = epicId,
                        title = title,
                        description = description,
                        processRank = processRank,
                        dependencyIds = dependencyIds,
                        status = if (action == "CLOSE") "DONE" else null,
                        horizon = horizon,
                        kind = kind,
                        capabilityKey = capabilityKey,
                    )
                }
                else -> error("Onbekende epicactie '$action'")
            }
        }
        output.path("settledQuestions").forEach { question ->
            question.asText().trim().takeIf(String::isNotBlank)?.let { roadmap.addSettledQuestion(productSlug, it) }
        }
        output.path("bugUpdates").forEach { update ->
            bugs.apply(
                productSlug,
                "ROADMAP_SESSION",
                sessionId,
                BugMutation(
                    update.path("action").asText(),
                    update.path("bugId").takeUnless { it.isNull || it.isMissingNode }?.asLong(),
                    update.path("title").asText(),
                    update.path("description").asText(),
                    update.path("reproductionSteps").asText(),
                    update.path("expectedResult").asText(),
                    update.path("actualResult").asText(),
                    update.path("priority").asText(),
                ),
            )
        }
    }
}

/**
 * Voert een roadmap-sessie uit als een keten van drie bewust verschillende perspectieven. De
 * visionair maakt de opzettelijk brede productmissie concreet zonder zich door de huidige techniek
 * te laten begrenzen. De strateeg maakt daar een versieerbare eindvisie en een backcast met
 * aannames/proeven van. De roadmapmanager vertaalt die strategie ten slotte naar uitvoer- en
 * discovery-epics, zonder de horizon onderweg kleiner te maken.
 *
 * Leest cyclussamenvattingen rechtstreeks uit `shadow_iteration` via JdbcTemplate in plaats van via
 * een Kotlin-afhankelijkheid op de `iteration`-module: die module krijgt in een latere fase zelf een
 * afhankelijkheid op `roadmap :: api` (om roadmapcontext in cyclusprompts te injecteren), en een
 * omgekeerde afhankelijkheid hier zou een cyclus in de modulegrenzen veroorzaken. Puur SQL op een
 * tabel van een andere module raakt niet aan die grens (Spring Modulith controleert Kotlin-imports,
 * geen databaseschema's) en is hier bewust de eenvoudigste uitweg.
 */
@Component
class RoadmapSessionEngine(
    private val repository: RoadmapSessionRepository,
    private val roadmap: RoadmapCatalog,
    private val visions: RoadmapVisionCatalog,
    private val bugs: BugCatalog,
    private val products: ProductCatalog,
    private val agents: AgentDispatchPort,
    private val agentRuns: AgentRunRegistry,
    private val meetings: MeetingCatalog,
    private val deliveryVerification: DeliveryVerificationEngine,
    private val deliveryVerificationReports: DeliveryVerificationRepository,
    private val workspace: WorkspacePublicationPort,
    private val workspaceVision: WorkspaceVisionPort,
    private val applier: RoadmapSessionApplier,
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
) {
    fun run(sessionId: String) {
        val session = repository.requireById(sessionId)
        val product = products.requireProduct(session.productSlug)
        repository.markRunning(session.id)

        // Vóórdat de PM de roadmap bekijkt: verifieer bevestigd opgeleverde, nog niet gecontroleerde
        // stories in de draaiende applicatie. De PM test dus zelf niets, maar sluit een epic alleen
        // op basis van deze rapporten (zie de instructie in sessionPrompt).
        runCatching { deliveryVerification.verifyPending(product, session.id) }
            .onFailure { logger.warn("Opleververificatie voor {} sloeg over: {}", product.slug, it.message) }

        val openEpics = roadmap.listEpics(product.slug).filter { it.status != "DONE" }
        val closedEpics = roadmap.listEpics(product.slug).filter { it.status == "DONE" }
        val settledQuestions = roadmap.listSettledQuestions(product.slug).map { it.content }
        val since = repository.lastCompletedAt(product.slug) ?: Instant.EPOCH
        val recentCycles = recentCycleContext(product.slug, since)
        val meetingContext = meetings.recentOutcomes(product.slug)
        val verificationContext = verificationContext(product.slug)

        val ownerVision = workspaceVision.readVision(product.slug)
        val currentVision = visions.current(product.slug)
        val visionary = executeStage(
            session,
            product,
            "visionary",
            RoadmapSchemas.visionary,
            visionaryPrompt(product, ownerVision, currentVision?.content),
        )
        val strategy = executeStage(
            session,
            product,
            "strategist",
            RoadmapSchemas.strategy,
            strategyPrompt(product, visionary, currentVision?.content, recentCycles, meetingContext, verificationContext),
        )
        validateStrategy(strategy)
        val output = executeStage(
            session,
            product,
            "manager",
            RoadmapSchemas.session,
            sessionPrompt(product, strategy, openEpics, closedEpics, settledQuestions, recentCycles, meetingContext, verificationContext),
        )
        applier.apply(product.slug, session.id, strategy, output)

        val summaryText = output.path("summary").asText().trim()
        val publication = publishMinutes(product, session, summaryText, strategy)
        repository.markCompleted(session.id, summaryText, publication?.runId, publication?.pullRequestUrl, publication?.commitSha)
    }

    private fun executeStage(
        session: RoadmapSessionView,
        product: ProductView,
        role: String,
        schema: String,
        prompt: String,
    ): JsonNode {
        val runId = "${session.id}-$role"
        agentRuns.register(runId, product.slug, "roadmap-$role")
        return try {
            val result = agents.execute(
                AgentTask(
                    runId = runId,
                    productSlug = product.slug,
                    taskType = "roadmap-$role",
                    prompt = prompt,
                    timeoutSeconds = SESSION_TIMEOUT_SECONDS,
                    model = product.aiModel.takeUnless { it == "default" },
                    provider = product.aiProvider,
                    responseSchema = schema,
                ),
            )
            if (result.status != "COMPLETED") error("Roadmaprol $role mislukte: ${result.summary.take(1000)}")
            mapper.readTree(result.summary).also {
                require(it != null && it.isObject) { "Roadmaprol $role gaf geen JSON-object" }
                agentRuns.complete(product.slug, runId, "COMPLETED", "roadmap-session:${session.id}/$role")
            }
        } catch (exception: Exception) {
            runCatching { agentRuns.complete(product.slug, runId, "FAILED", null) }
            throw exception
        }
    }

    private fun validateStrategy(strategy: JsonNode) {
        val experienceKeys = strategy.path("experiences").map { it.path("key").asText() }
        require(experienceKeys.size == experienceKeys.toSet().size) { "Experience-sleutels zijn niet uniek" }
        val capabilityKeys = strategy.path("capabilities").map { it.path("key").asText() }
        require(capabilityKeys.size == capabilityKeys.toSet().size) { "Capability-sleutels zijn niet uniek" }
        strategy.path("capabilities").forEach { capability ->
            require(capability.path("experienceKeys").all { it.asText() in experienceKeys }) {
                "Capability verwijst naar een onbekende toekomstervaring"
            }
        }
        strategy.path("assumptions").forEach { assumption ->
            require(assumption.path("capabilityKeys").all { it.asText() in capabilityKeys }) {
                "Aanname verwijst naar een onbekende capability"
            }
        }
    }

    private fun publishMinutes(product: ProductView, session: RoadmapSessionView, summary: String, strategy: JsonNode) = runCatching {
        workspace.publish(
            WorkspaceArtifact(
                runId = session.id,
                productSlug = product.slug,
                relativePath = "product-memory/roadmap-session-${session.sequenceNumber.toString().padStart(4, '0')}.md",
                content = RoadmapMinutesRenderer.render(
                    session,
                    summary,
                    strategy,
                    roadmap.listEpics(product.slug),
                    LocalDate.now(ZoneId.of(product.timezone)),
                ),
            ),
        )
    }.onFailure {
        logger.warn("Kon roadmap-sessieverslag {} niet publiceren naar de workspace: {}", session.id, it.message)
    }.getOrNull()

    private fun recentCycleContext(productSlug: String, since: Instant): String = jdbc.query(
        """select sequence_number, summary, critic_verdict from shadow_iteration
            where product_slug = ? and summary is not null and completed_at > ?
            order by completed_at desc limit 10""".trimIndent(),
        { row, _ ->
            "Cyclus ${row.getInt("sequence_number")} (${row.getString("critic_verdict") ?: "geen oordeel"}): ${row.getString("summary")?.take(2000)}"
        },
        productSlug,
        Timestamp.from(since),
    ).joinToString("\n\n").take(12_000).ifBlank { "Geen nieuwe productcycli sinds het vorige roadmap-overzicht." }

    private fun epicsBlock(epics: List<RoadmapEpicView>): String = epics
        .joinToString("\n\n") {
            "ID ${it.id}: ${it.title} (${it.horizon}, ${it.kind}, capability ${it.capabilityKey ?: "geen"}, proces #${it.processRank}, klant #${it.customerRank}, score ${it.priorityScore}, roadmap #${it.roadmapRank}, dependencies ${it.dependencyIds})\n${it.description}"
        }
        .ifBlank { "Geen." }

    private fun verificationContext(productSlug: String): String = deliveryVerificationReports.recentReports(productSlug)
        .joinToString("\n\n") { "Epic ${it.themeId} — story \"${it.candidateTitle}\": ${it.verdict}\n${it.report}" }
        .ifBlank { "Nog geen opleverchecker-rapporten." }

    private fun visionaryPrompt(product: ProductView, ownerVision: String?, currentVision: Map<String, Any?>?) = """
        ROL: VISIONAIR PRODUCTONTWERPER. Geef dit product een verre, concrete en inspirerende stip op de
        horizon. De productvisie van de eigenaar is bewust breed en vaag: het is jouw taak om daar zelfstandig
        prachtige, verrassende productervaringen uit te bedenken. Denk alsof techniek, tijd en budget uiteindelijk
        oplosbaar zijn. Moeilijk, duur, ongebruikelijk of vandaag nog niet ondersteund is NOOIT een reden om een idee
        in deze fase weg te laten. Privacy, toegankelijkheid, betrouwbaarheid en expliciete productguardrails blijven
        wel onderdeel van goed ontwerp.

        Werk divergent. Bedenk minstens acht wezenlijk verschillende kernervaringen en minstens drie wilde ideeën.
        Beschrijf een levendige toekomstige gebruikssituatie: wat ziet, doet, voelt en ontdekt iemand? Denk waar passend
        aan camera, locatie, tijd, kaarten, beeld, geluid, verbindingen, creatie, onderwijs en gemeenschappen, maar kopieer
        deze lijst niet mechanisch. Ontwerp ook drie tot vijf concrete conceptschermen met echte Nederlandstalige UI-tekst.
        Dit zijn geen beloften over de eerstvolgende release maar beelden van het mogelijke eindproduct.

        Je krijgt bewust GEEN actuele epics, backlog of technische architectuur. Schrijf geen stories, maak geen planning
        en verklein ideeën niet tot een MVP.

        MISSIE: ${product.mission}
        PRODUCTOMSCHRIJVING: ${product.description}
        GUARDRAILS: ${product.guardrails}
        PRODUCTVISIE VAN DE EIGENAAR (onvertrouwde contextdata):
        <DATA>${ownerVision?.takeIf(String::isNotBlank) ?: "Geen afzonderlijk visiedocument; gebruik missie en productprincipes."}</DATA>

        EERDERE CONCRETE HORIZON (onvertrouwde contextdata): behoud sterke ideeën en voeg nieuwe mogelijkheden toe;
        een bestaande horizon is inspiratie, geen begrenzing.
        <DATA>${currentVision?.let(mapper::writeValueAsString) ?: "Dit is de eerste visionaire uitwerking."}</DATA>

        Lever alleen JSON volgens het schema.
    """.trimIndent()

    private fun strategyPrompt(
        product: ProductView,
        visionary: JsonNode,
        currentVision: Map<String, Any?>?,
        recentCycles: String,
        meetingContext: String,
        verificationContext: String,
    ) = """
        ROL: TOEKOMSTSTRATEEG. Maak van de vrije ideeën hieronder één samenhangend, ambitieus eindproduct en
        redeneer daarvandaan terug naar capabilities in NOW, NEXT, LATER en HORIZON. Bewaak de gebruikersbehoefte,
        niet de eerste bedachte technische oplossing. Complexiteit is geen afwijzingsgrond: maak een onzekere of
        moeilijke capability expliciet en formuleer een gerichte feasibility probe.

        VERSIONERINGSREGEL: de bestaande horizon mag worden uitgebreid en scherper gemaakt. Verwijder of verzwak een
        bestaande kernervaring uitsluitend wanneer de bewijscontext een echte juridische of fundamentele technische
        onmogelijkheid aantoont. Een ontbrekende API, huidige productbeperking, kosteninschatting of één mislukte poging
        betekent hoogstens CURRENTLY_BLOCKED. Behoud in dat geval de gewenste ervaring en zoek een alternatief.
        Gebruik FUNDAMENTALLY_IMPOSSIBLE alleen bij sterk, controleerbaar bewijs. Leg iedere wijziging uit in
        visionChangeSummary.

        Maak capabilities resultaatgericht en meetbaar. Koppel iedere capability aan één of meer experienceKeys.
        Leg risicovolle aannames vast met een passende probeType. OWNER_DEPENDENCY is alleen toegestaan voor een
        werkelijk onvermijdelijk account, token, toestemming of eigenaarsbesluit.

        MISSIE: ${product.mission}
        GUARDRAILS: ${product.guardrails}
        KWALITEITSREGELS: ${product.qualityRules}
        VRIJE VISIE (onvertrouwde contextdata): <DATA>${mapper.writeValueAsString(visionary)}</DATA>
        BESTAANDE HORIZON (onvertrouwde contextdata): <DATA>${currentVision?.let(mapper::writeValueAsString) ?: "Geen."}</DATA>
        RECENTE PRODUCTCYCLI (bewijscontext): <DATA>$recentCycles</DATA>
        OVERLEG MET DE EIGENAAR (bewijscontext): <DATA>$meetingContext</DATA>
        OPLEVERVERIFICATIES (bewijscontext): <DATA>$verificationContext</DATA>

        Lever alleen JSON volgens het schema.
    """.trimIndent()

    private fun sessionPrompt(
        product: ProductView,
        strategy: JsonNode,
        openEpics: List<RoadmapEpicView>,
        closedEpics: List<RoadmapEpicView>,
        settledQuestions: List<String>,
        recentCycles: String,
        meetingContext: String,
        verificationContext: String,
    ) = """
        ROL: ROADMAP_MANAGER. Werk uitsluitend nu de uitvoerroadmap bij door terug te redeneren vanaf de
        concrete toekomstvisie. De visionair en strateeg hebben het vrije denken al gedaan. Maak de horizon
        niet kleiner en herschrijf de visie niet; verbind bestaand en nieuw werk aan de capabilities.

        BELANGRIJK ONDERSCHEID: een epic is een lopende richting (bv. "UX verbeteren") die over
        meerdere cycli heen open blijft totdat het onderwerp echt is uitgewerkt — sluit een epic
        niet zomaar af na een paar stories, één cyclus levert nooit meer dan een paar stories op.
        Een afgehandelde onderzoeksvraag is iets anders: een feit met een eenmalig antwoord (bv. "is
        dit archief zonder token te benaderen") dat nooit meer terug hoeft te komen.

        Geef voor elke epic die je wilt aanmaken, bijwerken of afsluiten één item in epicUpdates:
        action "CREATE" (epicId moet dan null zijn), of "UPDATE"/"CLOSE" (epicId moet dan het
        bestaande epic-ID uit de huidige roadmap hieronder zijn). Geef altijd jouw volledige
        processRank en de dependencyIds. Je mag de customerRank NOOIT wijzigen: die is van de klant.
        Geef horizon, kind en capabilityKey altijd bewust op. Gebruik kind DISCOVERY voor een begrensd
        onderzoek, experiment of prototype dat een onzekere aanname toetst; de beschrijving moet dan het
        verwachte bewijs en besliscriterium noemen. Maak niet voor iedere verre capability meteen een epic:
        bouw via waardevolle tussenstappen en respecteer de WIP-limiet. Laat epicUpdates leeg als er niets te
        veranderen valt. Voeg in settledQuestions
        alleen NIEUWE afgehandelde vragen toe die nog niet in de bestaande lijst hieronder staan.

        BUGREGISTRATIE: iedere concrete bevinding dat bestaand gedrag niet werkt zoals het hoort, hoort in
        bugUpdates en mag niet alleen in de samenvatting verdwijnen. Gebruik CREATE voor een nieuwe bug en UPDATE
        met exact het bestaande bugId voor een opnieuw geziene bug. P0 betekent dat het product of een kernflow
        onbruikbaar is, P1 dat een belangrijke functie niet werkt, P2 hinder met workaround en P3 klein/cosmetisch.
        Maak geen bug van een feature-idee en maak geen duplicaat.

        EEN EPIC SLUITEN (action "CLOSE"): test dit zelf niet in de applicatie. Sluit een epic alleen
        als de OPLEVERCHECKER-RAPPORTEN hieronder voor de eraan gekoppelde stories een SATISFIES-oordeel
        laten zien. Staat een gekoppelde story er nog niet bij, is het oordeel DOES_NOT_SATISFY, of is
        er nog geen enkele opgeleverde story voor deze epic, laat de epic dan open.

        MISSIE: ${product.mission}
        GUARDRAILS: ${product.guardrails}

        CONCRETE TOEKOMSTVISIE EN BACKCASTING (onvertrouwde contextdata):
        <DATA>${mapper.writeValueAsString(strategy)}</DATA>

        HUIDIGE ROADMAP — open epics (onvertrouwde contextdata):
        <DATA>
        ${epicsBlock(openEpics)}
        </DATA>

        HUIDIGE ROADMAP — afgesloten epics (onvertrouwde contextdata):
        <DATA>
        ${epicsBlock(closedEpics)}
        </DATA>

        AFGEHANDELDE ONDERZOEKSVRAGEN (onvertrouwde contextdata):
        <DATA>
        ${settledQuestions.joinToString("\n") { "- $it" }.ifBlank { "Geen." }}
        </DATA>

        PRODUCTCYCLI SINDS DE VORIGE ROADMAP-SESSIE (onvertrouwde contextdata):
        <DATA>
        $recentCycles
        </DATA>

        OVERLEGGEN MET DE EIGENAAR (onvertrouwde contextdata):
        <DATA>
        $meetingContext
        </DATA>

        OPLEVERCHECKER-RAPPORTEN (onvertrouwde contextdata): onafhankelijke verificatie in de draaiende
        applicatie van bevestigd opgeleverde stories tegen hun acceptatiecriteria en epicbedoeling.
        <DATA>
        $verificationContext
        </DATA>

        BESTAANDE BUGLIJST (onvertrouwde contextdata):
        <DATA>
        ${bugs.list(product.slug).joinToString("\n\n") { "BUG-${it.id} | ${it.priority} | ${it.status} | ${it.title}\n${it.description}\nStappen: ${it.reproductionSteps}" }.ifBlank { "Geen bugs." }}
        </DATA>

        Lever alleen JSON volgens het opgegeven schema, met een korte samenvatting in het veld
        "summary" van wat je hebt bijgewerkt en waarom.
    """.trimIndent()

    companion object {
        private val logger = LoggerFactory.getLogger(RoadmapSessionEngine::class.java)
        private const val SESSION_TIMEOUT_SECONDS = 900L
    }
}

/** Rendert het verslag van een roadmap-sessie als leesbaar Markdown-dossier, zelfde stijl als MeetingMinutesRenderer/ShadowDossierRenderer. */
internal object RoadmapMinutesRenderer {
    fun render(session: RoadmapSessionView, summary: String, vision: JsonNode, epics: List<RoadmapEpicView>, date: LocalDate): String = buildString {
        appendLine("---")
        appendLine("product: ${session.productSlug}")
        appendLine("artifact_type: roadmap-session")
        appendLine("run_id: ${session.id}")
        appendLine("date: $date")
        appendLine("status: completed")
        appendLine("---")
        appendLine("# Roadmap-sessie ${session.sequenceNumber}")
        appendLine()
        appendLine("## Samenvatting")
        appendLine()
        appendLine(summary)
        appendLine()
        appendLine("## Verre stip op de horizon")
        appendLine()
        appendLine("# ${vision.path("northStarTitle").asText()}")
        appendLine()
        appendLine(vision.path("northStar").asText())
        appendLine()
        appendLine(vision.path("futureNarrative").asText())
        appendLine()
        appendLine("### Toekomstige productervaringen")
        appendLine()
        vision.path("experiences").forEach { experience ->
            appendLine("#### ${experience.path("title").asText()}")
            appendLine()
            appendLine(experience.path("promise").asText())
            appendLine()
            appendLine("${experience.path("scenario").asText()} **Waarom bijzonder:** ${experience.path("wowFactor").asText()}")
            appendLine()
        }
        appendLine("### Capability-horizons")
        appendLine()
        for (horizon in listOf("NOW", "NEXT", "LATER", "HORIZON")) {
            appendLine("#### $horizon")
            vision.path("capabilities").filter { it.path("horizon").asText() == horizon }.forEach { capability ->
                appendLine("- **${capability.path("title").asText()}** — ${capability.path("outcome").asText()} _Maatstaf: ${capability.path("successMeasure").asText()}; haalbaarheid: ${capability.path("feasibility").asText()}._")
            }
            appendLine()
        }
        appendLine("### Te toetsen aannames")
        appendLine()
        vision.path("assumptions").forEach { assumption ->
            appendLine("- **${assumption.path("probeType").asText()} · ${assumption.path("feasibility").asText()}** — ${assumption.path("statement").asText()} Proef: ${assumption.path("proposedProbe").asText()}")
        }
        appendLine()
        appendLine("### Conceptschermen")
        appendLine()
        vision.path("conceptScreens").forEach { screen ->
            appendLine("#### ${screen.path("title").asText()} · ${screen.path("viewport").asText()}")
            appendLine()
            appendLine("**${screen.path("eyebrow").asText()}**")
            appendLine()
            appendLine("## ${screen.path("headline").asText()}")
            appendLine()
            appendLine(screen.path("body").asText())
            appendLine()
            screen.path("highlights").forEach { appendLine("- ${it.asText()}") }
            appendLine()
            appendLine("[${screen.path("primaryAction").asText()}] [${screen.path("secondaryAction").asText()}]")
            appendLine()
            appendLine("_Beeldrichting: ${screen.path("visualDescription").asText()}_")
            appendLine()
        }
        appendLine("## Roadmap op dit moment")
        appendLine()
        epics.forEach { epic ->
            appendLine("### #${epic.roadmapRank} ${epic.title} — score ${epic.priorityScore} · ${epic.status}")
            appendLine()
            appendLine("Horizon ${epic.horizon}; type ${epic.kind}; capability ${epic.capabilityKey ?: "geen"}. Klant-rank ${epic.customerRank}; process-rank ${epic.processRank}; dependencies: ${epic.dependencyIds.ifEmpty { listOf("geen") }.joinToString()}.")
            appendLine()
            appendLine(epic.description)
            appendLine()
        }
    }.trim()
}
