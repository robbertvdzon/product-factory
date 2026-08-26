package nl.vdzon.productfactory.testbed

import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContext
import nl.vdzon.productfactory.api.testbed.AcceptanceFixtureContributor
import nl.vdzon.productfactory.api.testbed.ActivateTestScenarioCommand
import nl.vdzon.productfactory.api.testbed.AdvanceTestScenarioCommand
import nl.vdzon.productfactory.api.testbed.InjectTestFaultCommand
import nl.vdzon.productfactory.api.testbed.ResetAcceptanceEnvironmentCommand
import nl.vdzon.productfactory.api.testbed.TestControlService
import nl.vdzon.productfactory.api.testbed.TestScenarioDetails
import nl.vdzon.productfactory.api.testbed.TestScenarioLock
import nl.vdzon.productfactory.api.testbed.TestScenarioSummary
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@Service
@Profile("acceptance")
@Order(100)
class AcceptanceTestControlService(
    contributors: List<AcceptanceFixtureContributor>,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) : TestControlService, ApplicationRunner {
    private val contributors = contributors.sortedWith(compareBy({ it.order }, { it.key }))
    private val scenarios = listOf(
        TestScenarioSummary("mvp-01-happy-flow", "1", "Happy flow", "Productinput wordt epic, complete story, externe levering, story- en epictest en een voltooide epic."),
        TestScenarioSummary("mvp-02-prioritized-backlog", "1", "Geprioriteerde backlog", "Complete storysets vormen één unieke productbrede TODO-volgorde."),
        TestScenarioSummary("mvp-03-stakeholder-priority", "1", "Stakeholderprioriteit", "Alleen TODO-werk wordt met blijvende reden en historie opnieuw geprioriteerd."),
        TestScenarioSummary("mvp-04-story-rejected", "1", "Story afgekeurd", "De opgeleverde story blijft DONE terwijl bug- en herstelwerk ontstaan."),
        TestScenarioSummary("mvp-05-bugfix-not-resolved", "1", "Bugfix onvoldoende", "Dezelfde bug blijft OPEN en kan een volgende gewone bugfixstory krijgen."),
        TestScenarioSummary("mvp-06-factory-cancelled", "1", "Factory annuleert story", "CANCELLED wordt feitelijk verwerkt en blokkeert een latere epicbeoordeling niet."),
        TestScenarioSummary("mvp-07-temporarily-untestable", "1", "Tijdelijk niet testbaar", "Pogingen en back-off blijven bewaard; Retry now maakt geen dubbele sessie."),
        TestScenarioSummary("mvp-08-missing-epic-coverage", "1", "Ontbrekende epicdekking", "Gerichte dekkingsstories ontstaan binnen dezelfde bevroren epic."),
        TestScenarioSummary("mvp-09-bug-in-epic-check", "1", "Bug in epiccontrole", "Bug en bugfixwerk brengen NEEDS_WORK gecontroleerd terug naar ACTIVE."),
        TestScenarioSummary("mvp-10-epic-check-blocked", "1", "Epiccontrole geblokkeerd", "De epic blijft VERIFYING en hetzelfde workitem blijft retrybaar."),
        TestScenarioSummary("mvp-11-goal-not-reached", "1", "Gebruikersdoel niet bereikt", "Positief bewijs eindigt expliciet in NOT_SUCCESSFUL."),
        TestScenarioSummary("mvp-12-stakeholder-cancels-epic", "1", "Stakeholder stopt epic", "Marker, stories en reservering volgen de atomaire annuleringsvolgorde."),
        TestScenarioSummary("mvp-13-factory-temporarily-offline", "1", "Factory tijdelijk onbereikbaar", "Retry en externe lookup behouden exact één externe story."),
        TestScenarioSummary("mvp-14-planning-terminal-failure", "1", "Planning terminaal mislukt", "De epic blijft IN_PLANNING en dezelfde claim wordt voor nieuw werk hervat."),
        TestScenarioSummary("mvp-15-deployment-pending", "1", "Oplevercommit nog niet live", "Kwaliteit blijft BLOCKED met DEPLOYMENT_PENDING tot de revision exact gelijk is."),
        TestScenarioSummary("mvp-16-two-products", "1", "Twee producten tegelijk", "Per module loopt één sessie per product en verschillende producten lopen onafhankelijk."),
        TestScenarioSummary("mvp-17-dependency-cancelled", "1", "Dependency geannuleerd", "De dependency geldt niet als voldaan en gericht herplanningswerk ontstaat."),
        TestScenarioSummary("mvp-18-process-schedules", "1", "Eigen procesritmes", "Aan/uit, interval, weekregels, tijdzone, DST, wijziging en één inhaalrun zijn deterministisch."),
        TestScenarioSummary("mvp-19-agent-meeting", "1", "Agentvraag in overleg", "Agenda, bronantwoord, rolgesprek, notulen en hervatte procescontext blijven verbonden."),
        TestScenarioSummary(
            key = "foundation-clean",
            version = "2",
            title = "Product- en stakeholderbasis",
            description = "Herhaalbare producten, signalen, vragen, overleggen, schedules en besluiten.",
        ),
        TestScenarioSummary(
            key = "outbound-mutations-blocked",
            version = "1",
            title = "Externe mutaties geblokkeerd",
            description = "Bewijst dat acceptatie geen echte AI- of Software Factory-mutaties uitvoert.",
        ),
        TestScenarioSummary(
            key = "product-history",
            version = "1",
            title = "Producthistorie en overleg",
            description = "Synthetische actuele en historische toestanden voor alle stap-2-schermen.",
        ),
        TestScenarioSummary(
            key = "memory-and-ai-settings",
            version = "1",
            title = "Agentgeheugen en AI-instellingen",
            description = "Vaste rollen, actuele/vervangen/ingetrokken geheugenregels, leesaudit en globale mockconfiguraties.",
        ),
        TestScenarioSummary(
            key = "agent-runtime-recovery",
            version = "1",
            title = "Agent Runtime en overlegagents",
            description = "Duurzame outbox, statusreconciliatie, backend-keygrants, Meeting Agent en notulenagent.",
        ),
        TestScenarioSummary(
            key = "product-design-valid-epic",
            version = "1",
            title = "Productontwerp publiceert geldige epic",
            description = "Eén bevroren ontwerpsessie wacht, hervat en publiceert een complete geversioneerde epic.",
        ),
        TestScenarioSummary(
            key = "product-design-no-op",
            version = "1",
            title = "Productontwerp zonder zinvol werk",
            description = "Ongewijzigde bronnen eindigen zichtbaar en zonder tweede AI-taak als succesvolle no-op.",
        ),
        TestScenarioSummary(
            key = "product-design-invalid-result",
            version = "1",
            title = "Productontwerp blokkeert ongeldige output",
            description = "Ontbrekende UX of onvolledige criteria publiceren geen epic en laten een herstelbare blokkade zien.",
        ),
        TestScenarioSummary("product-planning-complete-epic", "1", "Complete epicplanning", "Selectie, hervatting, volledige storydekking en één productbrede TODO-volgorde."),
        TestScenarioSummary("product-planning-urgent-priority", "1", "Urgente herprioriteit", "Een gericht prioriteitsworkitem herschikt uitsluitend TODO-stories met een zichtbare reden."),
        TestScenarioSummary("product-planning-bugfix", "1", "Bugfixplanning", "Een exacte bugversie wordt één gekoppelde en pas daarna uitvoerbare bugfixstory."),
        TestScenarioSummary("product-planning-epic-gap", "1", "Ontbrekende epicdekking", "Verificatiebewijs leidt tot aanvullende stories binnen de bestaande bevroren epic."),
        TestScenarioSummary("product-planning-cancelled-dependency", "1", "Geannuleerde dependency", "Afhankelijk werk blijft geblokkeerd en maakt gericht herplanningswerk."),
        TestScenarioSummary("product-planning-cancellation-marker", "1", "Annuleringsmarker en reservering", "Late planning en dispatch worden veilig tegen de duurzame epicannulering begrensd."),
        TestScenarioSummary("product-planning-invalid-result", "1", "Ongeldige planneroutput", "Onvolledige dekking publiceert atomair geen gedeeltelijke storyset."),
        TestScenarioSummary("product-planning-ai-recovery", "1", "Wachten en terminale taakfout", "Dezelfde sessie en epicclaim wachten, blokkeren zichtbaar en hervatten zonder duplicaat."),
        TestScenarioSummary("quality-story-pass-fail", "1", "Storycontrole geslaagd of afgekeurd", "Exacte oplevercommit, observeerbaar bewijs en gerichte storyuitkomst."),
        TestScenarioSummary("quality-bugfix-not-resolved", "1", "Bugfix niet opgelost", "De story blijft opgeleverd, dezelfde bug blijft open en nieuw herstelwerk ontstaat."),
        TestScenarioSummary("quality-epic-findings", "1", "Epicbugs en ontbrekende dekking", "NEEDS_WORK publiceert bugs, dekkingsbewijs en uitsluitend gerichte planningcommands."),
        TestScenarioSummary("quality-blocked-not-successful", "1", "Geblokkeerd of niet succesvol", "Retrybare testblokkade blijft historie; NOT_SUCCESSFUL vereist positief bewijs."),
        TestScenarioSummary("quality-deployment-pending", "1", "Oplevercommit nog niet live", "Achterlopende revision geeft DEPLOYMENT_PENDING en nooit een valse productafkeuring."),
        TestScenarioSummary("quality-runtime-retry", "1", "Ontbrekend mockantwoord en retry", "Vaste back-off, onbeperkte poginghistorie en handmatige Retry now zonder dubbele sessie."),
        TestScenarioSummary("software-factory-idempotent", "1", "Idempotente storylevering", "Eén gereserveerde story wordt ook na herhaling exact één externe mockstory."),
        TestScenarioSummary("software-factory-lost-response", "1", "Verloren create-response", "Externe lookup herstelt dezelfde storyKey zonder tweede story of attachment."),
        TestScenarioSummary("software-factory-done", "1", "Externe oplevering", "DONE met volledige commit verwerkt planning en maakt exact één kwaliteitswerkitem."),
        TestScenarioSummary("software-factory-cancelled", "1", "Externe annulering", "CANCELLED wordt lokaal feitelijk verwerkt zonder niet-bestaande storycontrole."),
        TestScenarioSummary("software-factory-temporary-failure", "1", "Tijdelijke storing", "Een tijdelijke fout bewaart attempt, foutcode en begrensde volgende retry."),
        TestScenarioSummary("software-factory-contract-failure", "1", "Contractbreuk", "Een ongeldige response blokkeert dispatch zichtbaar zonder planwerk of aangepaste story."),
    )
    private val active = AtomicReference(details(scenarios.first(), Instant.EPOCH))

    override fun run(args: ApplicationArguments) {
        resetAcceptanceEnvironment(ResetAcceptanceEnvironmentCommand(scenarios.first().key, "startup"))
    }

    override fun getActiveScenario(): TestScenarioDetails = currentDetails()

    override fun getAvailableScenarios(): List<TestScenarioSummary> = scenarios

    @Synchronized
    override fun resetAcceptanceEnvironment(command: ResetAcceptanceEnvironmentCommand) {
        validateBrowserSession(command.browserSessionId)
        val scenario = scenario(command.scenarioKey)
        val now = clock.instant()
        val lock = if (command.browserSessionId == STARTUP_SESSION) {
            null
        } else {
            acquireLock(command.browserSessionId, now)
        }
        transactionTemplate.executeWithoutResult {
            val context = AcceptanceFixtureContext(DATASET_VERSION, scenario.key)
            contributors.forEach { it.reset(context) }
        }
        active.set(details(scenario, now, lock))
    }

    override fun activateScenario(command: ActivateTestScenarioCommand) {
        resetAcceptanceEnvironment(ResetAcceptanceEnvironmentCommand(command.scenarioKey, command.browserSessionId))
    }

    @Synchronized
    override fun advanceScenario(command: AdvanceTestScenarioCommand) {
        validateBrowserSession(command.browserSessionId)
        val current = requireLock(command.browserSessionId)
        check(current.currentStep == command.expectedStep) { "Het acceptatiescenario is intussen gewijzigd." }
        active.set(current.copy(currentStep = current.currentStep + 1, lock = renewedLock(command.browserSessionId)))
    }

    @Synchronized
    override fun injectTestFault(command: InjectTestFaultCommand) {
        validateBrowserSession(command.browserSessionId)
        requireLock(command.browserSessionId)
        check(command.faultKey == "next-outbound-mutation-blocked") { "Onbekende begrensde testfout." }
        active.updateAndGet { it.copy(lock = renewedLock(command.browserSessionId)) }
    }

    private fun scenario(key: String): TestScenarioSummary = scenarios.singleOrNull { it.key == key }
        ?: throw IllegalArgumentException("Onbekend acceptatiescenario.")

    private fun validateBrowserSession(value: String) {
        require(BROWSER_SESSION.matches(value)) { "Ongeldige acceptatie-browsersessie." }
    }

    private fun currentDetails(): TestScenarioDetails {
        val current = active.get()
        val lock = current.lock
        if (lock != null && !lock.expiresAt.isAfter(clock.instant())) {
            val unlocked = current.copy(lock = null)
            active.compareAndSet(current, unlocked)
            return active.get()
        }
        return current
    }

    private fun acquireLock(browserSessionId: String, now: Instant): TestScenarioLock {
        val currentLock = currentDetails().lock
        check(currentLock == null || currentLock.browserSessionId == browserSessionId) {
            "Het acceptatiescenario wordt al door een andere browsersessie bestuurd."
        }
        return TestScenarioLock(browserSessionId, currentLock?.acquiredAt ?: now, now.plus(LOCK_DURATION))
    }

    private fun requireLock(browserSessionId: String): TestScenarioDetails {
        val current = currentDetails()
        check(current.lock?.browserSessionId == browserSessionId) {
            "Deze browsersessie heeft geen actieve scenariolock."
        }
        return current
    }

    private fun renewedLock(browserSessionId: String): TestScenarioLock {
        val current = active.get().lock
        return TestScenarioLock(browserSessionId, current?.acquiredAt ?: clock.instant(), clock.instant().plus(LOCK_DURATION))
    }

    private fun details(scenario: TestScenarioSummary, activatedAt: Instant, lock: TestScenarioLock? = null) = TestScenarioDetails(
        scenario = scenario,
        datasetVersion = DATASET_VERSION,
        testbedVersion = TESTBED_VERSION,
        activatedAt = activatedAt,
        currentStep = 0,
        lock = lock,
    )

    companion object {
        private const val DATASET_VERSION = "complete-mvp-v1"
        private const val TESTBED_VERSION = "0.9.0"
        private const val STARTUP_SESSION = "startup"
        private val LOCK_DURATION = Duration.ofMinutes(15)
        private val BROWSER_SESSION = Regex("[A-Za-z0-9._-]{3,100}")
    }
}
