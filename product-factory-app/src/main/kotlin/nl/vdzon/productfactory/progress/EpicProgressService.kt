package nl.vdzon.productfactory.progress

import nl.vdzon.productfactory.api.design.EpicDetails
import nl.vdzon.productfactory.api.design.EpicStatus
import nl.vdzon.productfactory.api.design.ProductDesignQueryService
import nl.vdzon.productfactory.api.dispatcher.DeliveryAttemptDetails
import nl.vdzon.productfactory.api.dispatcher.DeliveryAttemptFilter
import nl.vdzon.productfactory.api.dispatcher.DeliveryAttemptStatus
import nl.vdzon.productfactory.api.dispatcher.SoftwareFactoryDispatcherQueryService
import nl.vdzon.productfactory.api.planning.ProductPlanningQueryService
import nl.vdzon.productfactory.api.planning.StoryDetails
import nl.vdzon.productfactory.api.planning.StoryFilter
import nl.vdzon.productfactory.api.planning.StoryStatus
import nl.vdzon.productfactory.api.planning.StoryType
import nl.vdzon.productfactory.api.quality.*
import nl.vdzon.productfactory.api.shared.*
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

enum class EpicProgressStepState { DONE, FAILED, CURRENT, PENDING }
enum class EpicWaitingActor { PRODUCT_DESIGN, ARCHITECT, FACTORY_OWNER, PRODUCT_OWNER, PRODUCT_PLANNING, DISPATCHER, SOFTWARE_FACTORY, QUALITY }
enum class EpicTimelineSeverity { INFO, SUCCESS, WARNING, ERROR }

data class EpicProgress(
    val epicId: String,
    val productId: String,
    val title: String,
    val status: EpicStatus,
    val version: Long,
    val phase: String,
    val steps: List<EpicProgressStep>,
    val waitingOn: EpicWaitingOn?,
    val stories: List<EpicProgressStory>,
    val cancelledStoryCount: Int,
    val openBugs: List<EpicProgressBug>,
    val timeline: List<EpicTimelineEvent>,
)

data class EpicProgressStep(val key: String, val label: String, val state: EpicProgressStepState, val detail: String?)
data class EpicWaitingOn(
    val actor: EpicWaitingActor,
    val title: String,
    val detail: String?,
    val since: Instant?,
    val reference: String?,
    val next: String?,
)
data class EpicProgressStory(
    val id: String,
    val sequenceNumber: Long,
    val title: String,
    val type: StoryType,
    val status: StoryStatus,
    val externalStoryId: String?,
    val deliveredCommitSha: String?,
    val verificationPassed: Boolean?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
data class EpicProgressBug(val id: String, val title: String, val severity: BugSeverity, val status: BugStatus, val createdAt: Instant)
data class EpicTimelineEvent(val at: Instant, val kind: String, val severity: EpicTimelineSeverity, val title: String, val detail: String?)

/** Stelt de voortgang van één epic samen uit ontwerp, planning, kwaliteit en dispatcher. */
@Service
class EpicProgressService(
    private val design: ProductDesignQueryService,
    private val planning: ProductPlanningQueryService,
    private val quality: QualityQueryService,
    private val dispatcher: SoftwareFactoryDispatcherQueryService,
    private val products: nl.vdzon.productfactory.api.product.ProductQueryService,
) {
    fun progress(epicId: EpicId): EpicProgress {
        val epic = design.getEpic(epicId)
        val history = design.getEpicHistory(epicId).sortedBy { it.version }
        val allStories = planning.findStories(StoryFilter(productId = epic.productId, epicId = epicId)).sortedBy { it.sequenceNumber }
        val stories = allStories.filter { it.status != StoryStatus.CANCELLED }
        val bugs = quality.findBugs(BugFilter(productId = epic.productId, epicId = epicId))
        val storyIds = allStories.map { it.id }.toSet()
        val attempts = dispatcher.findDeliveryAttempts(DeliveryAttemptFilter(productId = epic.productId)).filter { it.storyId in storyIds }
        val targetIds = storyIds.map { it.value }.toSet()
        val verifications = quality.findVerifications(VerificationFilter(productId = epic.productId)).filter {
            (it.targetType == VerificationTargetType.EPIC && it.targetId == epicId.value) ||
                (it.targetType in setOf(VerificationTargetType.STORY, VerificationTargetType.BUGFIX) && it.targetId in targetIds)
        }
        val context = Context(epic, history, stories, allStories, bugs, attempts, verifications)
        val (steps, phase) = steps(context)
        return EpicProgress(
            epicId = epic.id.value,
            productId = epic.productId.value,
            title = epic.title,
            status = epic.status,
            version = epic.version,
            phase = phase,
            steps = steps,
            waitingOn = products.findStakeholderQuestions(nl.vdzon.productfactory.api.product.StakeholderQuestionFilter(epic.productId)).firstOrNull {
                it.status==nl.vdzon.productfactory.api.product.StakeholderQuestionStatus.OPEN &&
                    (it.epicLinkId==epic.id || it.storyLinkId in storyIds || it.linkedObjects.any { ref -> (ref.type=="EPIC" && ref.id==epic.id.value) || (ref.type=="STORY" && ref.id in targetIds) })
            }?.let { EpicWaitingOn(if(it.requestedRole.name=="ARCHITECT") EpicWaitingActor.ARCHITECT else EpicWaitingActor.PRODUCT_OWNER,
                "Antwoord nodig",it.question,it.createdAt,it.id.value,"Beantwoord de vraag om het gekoppelde werk te hervatten.") }
                ?: epic.review?.takeIf { !it.ready && epic.status in setOf(EpicStatus.IN_PLANNING,EpicStatus.ACTIVE,EpicStatus.VERIFYING) }?.let {
                    EpicWaitingOn(if(!it.productOwnerApproved) EpicWaitingActor.PRODUCT_OWNER else EpicWaitingActor.ARCHITECT,
                        "Beoordeling nodig",it.blockers.joinToString("\n"),epic.updatedAt,epic.id.value,"Bekijk de actuele inhoud en productafspraken.")
                } ?: waitingOn(context),
            stories = stories.map {
                EpicProgressStory(
                    it.id.value, it.sequenceNumber, it.title, it.type, it.status, it.externalStoryId, it.deliveredCommitSha,
                    it.verificationPassed, it.createdAt, it.updatedAt,
                )
            },
            cancelledStoryCount = allStories.size - stories.size,
            openBugs = bugs.filter { it.status == BugStatus.OPEN }.sortedBy { it.createdAt }
                .map { EpicProgressBug(it.id.value, it.title, it.severity, it.status, it.createdAt) },
            timeline = timeline(context),
        )
    }

    private class Context(
        val epic: EpicDetails,
        val history: List<EpicDetails>,
        val stories: List<StoryDetails>,
        val allStories: List<StoryDetails>,
        val bugs: List<BugDetails>,
        val attempts: List<DeliveryAttemptDetails>,
        val verifications: List<VerificationDetails>,
    ) {
        val epicVerifications = verifications.filter { it.targetType == VerificationTargetType.EPIC }.sortedBy { it.createdAt }
        val failedEpicVerification = epicVerifications.firstOrNull { it.outcome in REJECTED_OUTCOMES }
        private val bugIds = bugs.map { it.id }.toSet()

        /** Bugfix-stories die volgden op een afgekeurde epicverificatie; alleen dan is er een bugfix-lus. */
        val loopStories: List<StoryDetails> = failedEpicVerification?.let { failed ->
            stories.filter { (it.type == StoryType.BUGFIX || it.bugId in bugIds) && !it.createdAt.isBefore(failed.createdAt) }
        }.orEmpty()
        val hasBugfixLoop = loopStories.isNotEmpty()
        val buildStories = stories - loopStories.toSet()
    }

    private fun steps(context: Context): Pair<List<EpicProgressStep>, String> {
        val epic = context.epic
        val keys = if (context.hasBugfixLoop) LOOP_STEPS else BASE_STEPS
        val stopped = epic.status in STOPPED_STATUSES
        val completed = epic.status == EpicStatus.COMPLETED
        val position = if (stopped) {
            context.history.lastOrNull { it.status !in STOPPED_STATUSES }?.let { stepKey(context, it.status) } ?: "DESIGN"
        } else {
            stepKey(context, epic.status)
        }
        val currentIndex = if (completed) keys.size else keys.indexOf(position).coerceAtLeast(0)
        val steps = keys.mapIndexed { index, key ->
            var state = when {
                index < currentIndex -> EpicProgressStepState.DONE
                index == currentIndex -> if (stopped) EpicProgressStepState.FAILED else EpicProgressStepState.CURRENT
                else -> EpicProgressStepState.PENDING
            }
            if (key == "VERIFICATION" && context.hasBugfixLoop) state = EpicProgressStepState.FAILED
            EpicProgressStep(key, STEP_LABELS.getValue(key), state, stepDetail(context, key, state))
        }
        return steps to if (completed) "DONE" else keys[currentIndex]
    }

    private fun stepKey(context: Context, status: EpicStatus): String = when (status) {
        in DESIGN_STATUSES -> "DESIGN"
        EpicStatus.AVAILABLE, EpicStatus.IN_PLANNING -> "PLANNING"
        EpicStatus.ACTIVE -> when {
            context.hasBugfixLoop -> if (context.loopStories.all { it.status == StoryStatus.DONE }) "RETEST" else "BUGFIX"
            context.buildStories.isEmpty() || context.buildStories.any { it.status != StoryStatus.DONE } -> "BUILD"
            else -> "VERIFICATION"
        }
        EpicStatus.VERIFYING, EpicStatus.NOT_SUCCESSFUL -> when {
            context.hasBugfixLoop -> if (context.loopStories.all { it.status == StoryStatus.DONE }) "RETEST" else "BUGFIX"
            else -> "VERIFICATION"
        }
        EpicStatus.COMPLETED -> "DONE"
        else -> "DESIGN"
    }

    private fun stepDetail(context: Context, key: String, state: EpicProgressStepState): String? {
        if (state == EpicProgressStepState.PENDING && key != "BUILD") return null
        val epic = context.epic
        return when (key) {
            "DESIGN" -> when (state) {
                EpicProgressStepState.DONE -> context.history.firstOrNull { it.status !in DESIGN_STATUSES && it.status !in STOPPED_STATUSES }?.updatedAt?.toString()
                else -> STATUS_LABELS.getValue(epic.status).replaceFirstChar { it.uppercase() }
            }
            "PLANNING" -> when (state) {
                EpicProgressStepState.DONE -> storyCount(context.buildStories.size)
                else -> STATUS_LABELS.getValue(epic.status).replaceFirstChar { it.uppercase() }
            }
            "BUILD" -> if (state == EpicProgressStepState.PENDING && context.buildStories.isEmpty()) null
            else "${context.buildStories.count { it.status == StoryStatus.DONE }} / ${context.buildStories.size}"
            "VERIFICATION" -> when (state) {
                EpicProgressStepState.FAILED -> (context.failedEpicVerification ?: context.epicVerifications.lastOrNull())?.createdAt?.toString()
                EpicProgressStepState.DONE -> context.epicVerifications.lastOrNull { it.outcome == VerificationOutcome.PASSED }?.createdAt?.toString()
                else -> null
            }
            "BUGFIX" -> when (state) {
                EpicProgressStepState.DONE -> "${context.loopStories.count { it.status == StoryStatus.DONE }} / ${context.loopStories.size}"
                else -> context.loopStories.firstOrNull { it.status != StoryStatus.DONE }?.let { it.externalStoryId ?: "#${it.sequenceNumber}" }
            }
            "RETEST" -> when (state) {
                EpicProgressStepState.DONE -> context.epicVerifications.lastOrNull { it.outcome == VerificationOutcome.PASSED }?.createdAt?.toString()
                else -> null
            }
            "DONE" -> context.history.lastOrNull { it.status == EpicStatus.COMPLETED }?.updatedAt?.toString()
            else -> null
        }
    }

    private fun waitingOn(context: Context): EpicWaitingOn? {
        val epic = context.epic
        return when (epic.status) {
            EpicStatus.NEEDS_RESEARCH -> EpicWaitingOn(
                EpicWaitingActor.PRODUCT_DESIGN, "Wacht op Productontwerp", "De epic heeft nog onderzoek nodig voordat hij klaar is.",
                epic.updatedAt, null, "Na het onderzoek volgt goedkeuring en daarna planning.",
            )
            EpicStatus.NEEDS_REFINEMENT -> EpicWaitingOn(
                EpicWaitingActor.PRODUCT_DESIGN, "Wacht op Productontwerp",
                epic.refinementReason?.let { "Verfijning gevraagd: ${shorten(it)}" } ?: "De epic wordt verder uitgewerkt.",
                epic.updatedAt, null, "Na de verfijning volgt opnieuw goedkeuring.",
            )
            EpicStatus.AWAITING_APPROVAL, EpicStatus.AWAITING_FACTORY_OWNER_APPROVAL -> EpicWaitingOn(
                EpicWaitingActor.ARCHITECT, "Wacht op architectbeoordeling", "De architect beoordeelt technische impact en product-AI.",
                epic.updatedAt, null, "Na goedkeuring pakt Productplanning de epic op.",
            )
            EpicStatus.AWAITING_PRODUCT_OWNER_APPROVAL -> EpicWaitingOn(
                EpicWaitingActor.PRODUCT_OWNER, "Wacht op goedkeuring product owner", "De epic is uitgewerkt en wacht op goedkeuring.",
                epic.updatedAt, null, "Daarna volgt zo nodig architectbeoordeling en vervolgens planning.",
            )
            EpicStatus.AVAILABLE -> EpicWaitingOn(
                EpicWaitingActor.PRODUCT_PLANNING, "Wacht op Productplanning", "De epic is goedgekeurd en wacht tot planning hem oppakt.",
                epic.updatedAt, null, "Planning knipt de epic in stories.",
            )
            EpicStatus.IN_PLANNING -> EpicWaitingOn(
                EpicWaitingActor.PRODUCT_PLANNING, "Wacht op Productplanning", "Planning knipt de epic in stories.",
                epic.updatedAt, null, "Daarna verstuurt de dispatcher de stories naar Software Factory.",
            )
            EpicStatus.ACTIVE -> activeWaitingOn(context)
            EpicStatus.VERIFYING -> EpicWaitingOn(
                EpicWaitingActor.QUALITY, "Wacht op Kwaliteit", "Kwaliteit verifieert de epic als geheel.",
                epic.updatedAt, null, "Bij goedkeuring is de epic afgerond; bij afkeur volgen bugfixes.",
            )
            else -> null
        }
    }

    private fun activeWaitingOn(context: Context): EpicWaitingOn {
        val open = context.stories.filter { it.status == StoryStatus.TODO || it.status == StoryStatus.IN_PROGRESS }
        if (open.isNotEmpty()) {
            val status = dispatcher.getDispatchStatus(context.epic.productId)
            if (status.blocked) {
                return EpicWaitingOn(
                    EpicWaitingActor.DISPATCHER, "Dispatcher geblokkeerd", status.blockedReason?.let(::shorten) ?: "De dispatcher is geblokkeerd.",
                    status.updatedAt, status.externalStoryId, "Na herstel verstuurt de dispatcher de volgende story.",
                )
            }
        }
        context.stories.filter { it.status == StoryStatus.IN_PROGRESS }.forEach { story ->
            val attempt = context.attempts.filter { it.storyId == story.id && it.status !in CLOSED_ATTEMPTS }.maxByOrNull { it.createdAt }
                ?: return@forEach
            val bugfix = story.type == StoryType.BUGFIX
            val reference = attempt.externalStoryId ?: story.externalStoryId
            val where = attempt.externalStatus?.let { " en staat daar op ${it.name}" }.orEmpty()
            return EpicWaitingOn(
                EpicWaitingActor.SOFTWARE_FACTORY, "Wacht op Software Factory",
                "${if (bugfix) "Bugfix-story" else "Story"} #${story.sequenceNumber} is${reference?.let { " als $it" }.orEmpty()} verstuurd$where.",
                attempt.createdAt, reference,
                if (bugfix) "Na oplevering volgen deploy naar acceptatie en een hertest door Kwaliteit."
                else "Na oplevering volgen deploy naar acceptatie en een verificatie door Kwaliteit.",
            )
        }
        val todo = context.stories.filter { it.status == StoryStatus.TODO }
        if (todo.isNotEmpty()) {
            return EpicWaitingOn(
                EpicWaitingActor.DISPATCHER, "Wacht op Dispatcher",
                if (todo.size == 1) "Story #${todo.single().sequenceNumber} staat klaar om te versturen."
                else "${todo.size} stories staan klaar om te versturen.",
                todo.minOf { it.createdAt }, null, "De dispatcher verstuurt één story tegelijk naar Software Factory.",
            )
        }
        if (open.isNotEmpty()) {
            val story = open.first()
            return EpicWaitingOn(
                EpicWaitingActor.DISPATCHER, "Wacht op Dispatcher", "Story #${story.sequenceNumber} is in uitvoering zonder lopende verzending.",
                story.updatedAt, story.externalStoryId, "De dispatcher controleert de status bij Software Factory.",
            )
        }
        // Hier zijn er geen open stories meer: een open bug zonder (actieve) bugfix-story wacht dus op planning.
        val bugWithoutStory = context.bugs.filter { it.status == BugStatus.OPEN }.sortedBy { it.createdAt }.firstOrNull { bug ->
            context.stories.none { it.bugId == bug.id || it.id in bug.linkedStoryIds }
        }
        if (bugWithoutStory != null) {
            return EpicWaitingOn(
                EpicWaitingActor.PRODUCT_PLANNING, "Wacht op Productplanning",
                "Bug \"${shorten(bugWithoutStory.title)}\" (${bugWithoutStory.severity}) wacht op een bugfix-story.",
                bugWithoutStory.createdAt, null, "Daarna verstuurt de dispatcher de bugfix naar Software Factory.",
            )
        }
        val lastDelivery = context.attempts.filter { it.status == DeliveryAttemptStatus.COMPLETED }.maxOfOrNull { it.updatedAt }
            ?: context.stories.filter { it.status == StoryStatus.DONE }.maxOfOrNull { it.updatedAt }
        return EpicWaitingOn(
            EpicWaitingActor.QUALITY, "Wacht op Kwaliteit", "Alle stories zijn opgeleverd; Kwaliteit verifieert het resultaat.",
            lastDelivery ?: context.epic.updatedAt, null, "Na goedkeuring wordt de epic als geheel geverifieerd.",
        )
    }

    private fun timeline(context: Context): List<EpicTimelineEvent> {
        val epic = context.epic
        val events = mutableListOf<EpicTimelineEvent>()
        events += EpicTimelineEvent(epic.createdAt, "EPIC_CREATED", EpicTimelineSeverity.INFO, "Epic aangemaakt", shorten(epic.title))
        context.history.filter { it.version > 1 }.forEach { version ->
            events += EpicTimelineEvent(
                version.updatedAt, "EPIC_VERSION", versionSeverity(version.status),
                "Epic v${version.version}: ${STATUS_LABELS.getValue(version.status)}",
                version.refinementReason?.takeIf { version.status == EpicStatus.NEEDS_REFINEMENT }?.let(::shorten),
            )
        }
        val sequenceById = context.allStories.associate { it.id to it.sequenceNumber }
        context.allStories.groupBy { it.createdAt.truncatedTo(ChronoUnit.MINUTES) }.toSortedMap().forEach { (_, created) ->
            val numbers = created.map { "#${it.sequenceNumber}" }
            val label = if (created.all { it.type == StoryType.BUGFIX }) "bugfix " else ""
            events += EpicTimelineEvent(
                created.minOf { it.createdAt }, "STORIES_CREATED", EpicTimelineSeverity.INFO,
                "Planning maakt $label${joinDutch(numbers)}", shorten(created.joinToString("; ") { it.title }),
            )
        }
        context.attempts.forEach { attempt ->
            val number = sequenceById[attempt.storyId]?.let { "#$it" } ?: "?"
            events += EpicTimelineEvent(
                attempt.createdAt, "STORY_DISPATCHED", EpicTimelineSeverity.INFO,
                "Story $number verstuurd${attempt.externalStoryId?.let { " als $it" }.orEmpty()}", null,
            )
            if (attempt.status == DeliveryAttemptStatus.COMPLETED) {
                events += EpicTimelineEvent(
                    attempt.updatedAt, "STORY_DELIVERED", EpicTimelineSeverity.SUCCESS, "Story $number opgeleverd",
                    attempt.deliveredCommitSha?.let { "Commit $it" },
                )
            }
        }
        context.allStories.filter { it.status == StoryStatus.CANCELLED }.forEach { story ->
            events += EpicTimelineEvent(
                story.updatedAt, "STORY_CANCELLED", EpicTimelineSeverity.WARNING, "Story #${story.sequenceNumber} geannuleerd",
                story.cancellationReason?.let(::shorten),
            )
        }
        context.verifications.forEach { verification ->
            val subject = when (verification.targetType) {
                VerificationTargetType.EPIC -> "Epicverificatie"
                VerificationTargetType.BUGFIX -> "Hertest bugfix #${sequenceById[StoryId(verification.targetId)] ?: "?"}"
                else -> "Verificatie story #${sequenceById[StoryId(verification.targetId)] ?: "?"}"
            }
            val passed = verification.outcome == VerificationOutcome.PASSED
            events += EpicTimelineEvent(
                verification.createdAt,
                if (passed) "VERIFICATION_PASSED" else "VERIFICATION_FAILED",
                when (verification.outcome) {
                    VerificationOutcome.PASSED -> EpicTimelineSeverity.SUCCESS
                    VerificationOutcome.BLOCKED -> EpicTimelineSeverity.WARNING
                    else -> EpicTimelineSeverity.ERROR
                },
                "$subject ${OUTCOME_LABELS.getValue(verification.outcome)}",
                if (passed) null else (verification.blockedReason ?: verification.missingCoverage.firstOrNull())?.let(::shorten),
            )
        }
        context.bugs.forEach { bug ->
            events += EpicTimelineEvent(bug.createdAt, "BUG_OPENED", EpicTimelineSeverity.WARNING, "Bug gemeld: ${shorten(bug.title, 120)}", bug.severity.name)
            if (bug.status != BugStatus.OPEN) {
                events += EpicTimelineEvent(
                    bug.updatedAt, "BUG_RESOLVED", EpicTimelineSeverity.SUCCESS,
                    "Bug ${if (bug.status == BugStatus.INVALID) "ongeldig verklaard" else "opgelost"}: ${shorten(bug.title, 120)}", null,
                )
            }
        }
        val failedFilter = ProcessSessionFilter(
            epic.productId, FAILED_SESSION_STATUSES, TimeRange(from = epic.createdAt), limit = MAX_FAILED_SESSIONS,
        )
        events += mergedSessionEvents(quality.findProcessSessions(failedFilter), "SESSION_FAILED", "Kwaliteitssessie mislukt")
        events += mergedSessionEvents(dispatcher.findDispatchSessions(failedFilter), "DISPATCH_BLOCKED", "Dispatcher geblokkeerd")
        return events.sortedByDescending { it.at }.take(MAX_TIMELINE_EVENTS)
    }

    /** Voegt opeenvolgende sessies met dezelfde melding samen tot één event. */
    private fun mergedSessionEvents(sessions: List<ProcessSessionDetails>, kind: String, title: String): List<EpicTimelineEvent> {
        val groups = mutableListOf<MutableList<ProcessSessionDetails>>()
        sessions.sortedBy { it.startedAt }.forEach { session ->
            val last = groups.lastOrNull()
            if (last != null && sessionMessage(last.last()) == sessionMessage(session)) last += session else groups += mutableListOf(session)
        }
        return groups.map { group ->
            val latest = group.last()
            val at = latest.finishedAt ?: latest.startedAt
            val message = sessionMessage(latest)
            val longest = group.mapNotNull { session -> session.finishedAt?.let { Duration.between(session.startedAt, it) } }.maxOrNull()
            val runtime = longest?.takeIf { it.toHours() >= 1 }?.let { " · liep ${it.toHours()} uur" }.orEmpty()
            EpicTimelineEvent(
                at, kind, if (latest.status == ProcessSessionStatus.FAILED) EpicTimelineSeverity.ERROR else EpicTimelineSeverity.WARNING, title,
                if (group.size == 1) shorten("${message.orEmpty()}$runtime") else shorten("${group.size}× · laatst $at$runtime${message?.let { " · $it" }.orEmpty()}"),
            )
        }
    }

    private fun sessionMessage(session: ProcessSessionDetails): String? =
        (session.blockedReason ?: session.errorCode ?: session.resultSummary)?.let(::shorten)

    private fun versionSeverity(status: EpicStatus) = when (status) {
        EpicStatus.COMPLETED -> EpicTimelineSeverity.SUCCESS
        EpicStatus.NOT_SUCCESSFUL, EpicStatus.CANCELLED -> EpicTimelineSeverity.ERROR
        EpicStatus.NEEDS_REFINEMENT, EpicStatus.WITHDRAWN -> EpicTimelineSeverity.WARNING
        else -> EpicTimelineSeverity.INFO
    }

    private fun storyCount(count: Int) = if (count == 1) "1 story" else "$count stories"

    private fun joinDutch(items: List<String>) = when (items.size) {
        0 -> ""
        1 -> items.single()
        else -> items.dropLast(1).joinToString(", ") + " en " + items.last()
    }

    private fun shorten(text: String, max: Int = MAX_DETAIL_LENGTH): String {
        val trimmed = text.trim()
        return if (trimmed.length <= max) trimmed else trimmed.take(max - 1).trimEnd() + "…"
    }

    companion object {
        private const val MAX_TIMELINE_EVENTS = 60
        private const val MAX_DETAIL_LENGTH = 300
        private const val MAX_FAILED_SESSIONS = 500
        private val BASE_STEPS = listOf("DESIGN", "PLANNING", "BUILD", "VERIFICATION", "DONE")
        private val LOOP_STEPS = listOf("DESIGN", "PLANNING", "BUILD", "VERIFICATION", "BUGFIX", "RETEST", "DONE")
        private val STEP_LABELS = mapOf(
            "DESIGN" to "Ontwerp", "PLANNING" to "Planning", "BUILD" to "Bouw", "VERIFICATION" to "Verificatie",
            "BUGFIX" to "Bugfix", "RETEST" to "Hertest", "DONE" to "Afgerond",
        )
        private val DESIGN_STATUSES = setOf(
            EpicStatus.NEEDS_RESEARCH, EpicStatus.NEEDS_REFINEMENT, EpicStatus.AWAITING_APPROVAL,
            EpicStatus.AWAITING_PRODUCT_OWNER_APPROVAL, EpicStatus.AWAITING_FACTORY_OWNER_APPROVAL,
        )
        private val STOPPED_STATUSES = setOf(EpicStatus.NOT_SUCCESSFUL, EpicStatus.SUPERSEDED, EpicStatus.WITHDRAWN, EpicStatus.CANCELLED)
        private val REJECTED_OUTCOMES = setOf(VerificationOutcome.FAILED, VerificationOutcome.NEEDS_WORK, VerificationOutcome.NOT_SUCCESSFUL)
        private val CLOSED_ATTEMPTS = setOf(DeliveryAttemptStatus.COMPLETED, DeliveryAttemptStatus.CANCELLED)
        private val FAILED_SESSION_STATUSES = setOf(ProcessSessionStatus.FAILED, ProcessSessionStatus.BLOCKED)
        private val STATUS_LABELS = mapOf(
            EpicStatus.NEEDS_RESEARCH to "onderzoek nodig",
            EpicStatus.NEEDS_REFINEMENT to "verfijning nodig",
            EpicStatus.AWAITING_APPROVAL to "wacht op goedkeuring",
            EpicStatus.AWAITING_PRODUCT_OWNER_APPROVAL to "wacht op goedkeuring product owner",
            EpicStatus.AWAITING_FACTORY_OWNER_APPROVAL to "wacht op architectbeoordeling",
            EpicStatus.AVAILABLE to "beschikbaar voor planning",
            EpicStatus.IN_PLANNING to "in planning",
            EpicStatus.ACTIVE to "in uitvoering",
            EpicStatus.VERIFYING to "in verificatie",
            EpicStatus.COMPLETED to "afgerond",
            EpicStatus.NOT_SUCCESSFUL to "niet geslaagd",
            EpicStatus.SUPERSEDED to "vervangen",
            EpicStatus.WITHDRAWN to "ingetrokken",
            EpicStatus.CANCELLED to "geannuleerd",
        )
        private val OUTCOME_LABELS = mapOf(
            VerificationOutcome.PASSED to "geslaagd",
            VerificationOutcome.FAILED to "mislukt",
            VerificationOutcome.NEEDS_WORK to "afgekeurd",
            VerificationOutcome.BLOCKED to "geblokkeerd",
            VerificationOutcome.NOT_SUCCESSFUL to "niet geslaagd",
        )
    }
}
