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
        private const val DATASET_VERSION = "product-planning-mvp-v1"
        private const val TESTBED_VERSION = "0.6.0"
        private const val STARTUP_SESSION = "startup"
        private val LOCK_DURATION = Duration.ofMinutes(15)
        private val BROWSER_SESSION = Regex("[A-Za-z0-9._-]{3,100}")
    }
}
