package nl.vdzon.productfactory.roadmap

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import nl.vdzon.productfactory.agentruntime.api.AgentDispatchPort
import nl.vdzon.productfactory.agentruntime.api.AgentRunRegistry
import nl.vdzon.productfactory.contracts.AgentTask
import nl.vdzon.productfactory.contracts.RoadmapHandoffView
import nl.vdzon.productfactory.contracts.RoadmapIdeaStatus
import nl.vdzon.productfactory.contracts.RoadmapResearchStatus
import nl.vdzon.productfactory.contracts.RoadmapSessionStepView
import nl.vdzon.productfactory.contracts.RoadmapStepStatus
import nl.vdzon.productfactory.media.api.ProductMediaCatalog
import nl.vdzon.productfactory.product.api.ProductCatalog
import nl.vdzon.productfactory.roadmap.api.ConceptFlowMutation
import nl.vdzon.productfactory.roadmap.api.ConceptFrameMutation
import nl.vdzon.productfactory.roadmap.api.IdeaMutation
import nl.vdzon.productfactory.roadmap.api.InspirationMutation
import nl.vdzon.productfactory.roadmap.api.LivingVisionCatalog
import nl.vdzon.productfactory.roadmap.api.ResearchMutation
import nl.vdzon.productfactory.roadmap.api.RoadmapSessionRepository
import nl.vdzon.productfactory.roadmap.api.StepDefinition
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Base64
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@Component
class LivingVisionActivator(
    private val applier: RoadmapSessionApplier,
    private val jdbc: JdbcTemplate,
) {
    @Transactional
    fun activate(productSlug: String, sessionId: String, strategy: JsonNode, plan: JsonNode, changelog: String) {
        val existing = jdbc.queryForObject("select count(*) from roadmap_activation where session_id = ?", Long::class.java, sessionId) ?: 0
        if (existing > 0) return
        applier.apply(productSlug, sessionId, strategy, plan)
        val visionId = jdbc.queryForObject(
            "select id from roadmap_future_vision where product_slug = ? and created_by_session_id = ?",
            String::class.java,
            productSlug, sessionId,
        )
        jdbc.update(
            "insert into roadmap_activation(id, product_slug, session_id, vision_id, changelog) values (?, ?, ?, ?, ?)",
            "activation-$sessionId", productSlug, sessionId, visionId, changelog.take(20_000),
        )
    }
}

@Component
class RoadmapProcessRecovery(
    private val sessions: RoadmapSessionRepository,
    private val catalog: LivingVisionCatalog,
    private val orchestrator: RoadmapProcessOrchestrator,
    @Value("\${product-factory.roadmap.recovery-enabled:true}") private val enabled: Boolean,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newFixedThreadPool(RECOVERY_CONCURRENCY)

    override fun run(arguments: ApplicationArguments) {
        if (!enabled) return
        prepareInterruptedSessions().forEach { sessionId ->
            executor.submit { recover(sessionId) }
        }
    }

    internal fun recover(sessionId: String) {
        runCatching { orchestrator.run(sessionId) }
            .onFailure {
                sessions.markFailed(sessionId, it.message ?: it.javaClass.simpleName)
                logger.error("Hervatten van roadmap-sessie {} mislukte", sessionId, it)
            }
    }

    internal fun prepareInterruptedSessions(): List<String> = sessions.incompleteLivingVisionSessionIds().also { sessionIds ->
        sessionIds.forEach(catalog::resetInterrupted)
    }

    @PreDestroy
    fun stop() = executor.shutdownNow()

    companion object {
        private const val RECOVERY_CONCURRENCY = 4
    }
}

/** Hervatbare living-vision-v2 graph met databaseclaims en begrensde paralleliteit. */
@Component
class RoadmapProcessOrchestrator(
    private val sessions: RoadmapSessionRepository,
    private val catalog: LivingVisionCatalog,
    private val contexts: RoadmapProductContextBuilder,
    private val prompts: LivingVisionPromptBuilder,
    private val products: ProductCatalog,
    private val agents: AgentDispatchPort,
    private val agentRuns: AgentRunRegistry,
    private val media: ProductMediaCatalog,
    private val activator: LivingVisionActivator,
    private val mapper: ObjectMapper,
    private val metrics: MeterRegistry,
    @Value("\${product-factory.roadmap.v2-parallelism:4}") private val parallelism: Int,
    @Value("\${product-factory.roadmap.v2-max-attempts:2}") private val maxAttempts: Int,
    @Value("\${product-factory.roadmap.v2-max-concepts:2}") private val maxConcepts: Int,
) {
    fun run(sessionId: String) {
        val session = sessions.requireById(sessionId)
        require(session.processVersion == LIVING_VISION_PROCESS_VERSION)
        val product = products.requireProduct(session.productSlug)
        sessions.markRunning(session.id)
        catalog.initializeSteps(session.id, product.slug, session.processVersion, graph(session.id))
        catalog.resetInterrupted(session.id)

        val executor = Executors.newFixedThreadPool(parallelism.coerceIn(1, MAX_PARALLELISM))
        try {
            while (true) {
                val all = catalog.steps(session.id)
                val failedRequired = all.firstOrNull { it.required && it.status == RoadmapStepStatus.FAILED }
                if (failedRequired != null) error("Verplichte stap ${failedRequired.role} mislukte: ${failedRequired.errorMessage}")
                if (all.all { it.status in TERMINAL_STATES }) {
                    val activation = all.single { it.role == "activation" }
                    require(activation.status == RoadmapStepStatus.COMPLETED) { "Sessiegrafiek eindigde zonder activatie" }
                    sessions.markCompleted(session.id, activation.handoff?.summary ?: "Living vision geactiveerd", null, null, null)
                    return
                }
                val ready = catalog.readySteps(session.id)
                require(ready.isNotEmpty()) { "Sessiegrafiek is geblokkeerd zonder uitvoerbare stap" }
                executor.invokeAll(ready.take(parallelism.coerceIn(1, MAX_PARALLELISM)).map { step ->
                    Callable { executeStep(step) }
                }).forEach { future -> future.get() }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun executeStep(step: RoadmapSessionStepView) {
        val timer = Timer.start(metrics)
        val product = products.requireProduct(step.productSlug)
        val allSteps = catalog.steps(step.sessionId)
        val inputs = dependencyClosure(step.id).mapNotNull { dependencyId -> allSteps.single { it.id == dependencyId }.handoff }
        val inputIds = inputs.flatMap { it.outputArtifactIds }.distinct()
        if (!catalog.markRunning(step.id, product.aiProvider, product.aiModel, inputIds)) return
        if (step.role == "activation") {
            try {
                executeActivation(step, inputs)
                timer.stop(metrics.timer("product_factory.roadmap.step.duration", "role", step.role, "status", "completed"))
                metrics.counter("product_factory.roadmap.step.completed", "role", step.role).increment()
            } catch (exception: Exception) {
                timer.stop(metrics.timer("product_factory.roadmap.step.duration", "role", step.role, "status", "failed"))
                throw exception
            }
            return
        }
        val runId = "${step.sessionId}-${step.role}-${step.scopeKey}-a${step.attempt}"
            .replace(Regex("[^A-Za-z0-9._-]"), "-")
            .take(120)
        try {
            val manifest = manifest(step, inputs)
            val prompt = prompts.build(manifest, contexts.build(step.productSlug), inputs)
            val taskType = if (step.role == "roadmap-manager") "roadmap-manager" else "roadmap-${step.role}"
            agentRuns.register(runId, step.productSlug, taskType)
            val result = agents.execute(
                AgentTask(
                    runId = runId,
                    productSlug = step.productSlug,
                    taskType = taskType,
                    prompt = prompt,
                    timeoutSeconds = STEP_TIMEOUT_SECONDS,
                    model = product.aiModel.takeUnless { it == "default" },
                    responseSchema = LivingVisionSchemas.forRole(step.role),
                    provider = product.aiProvider,
                ),
            )
            require(result.status == "COMPLETED") { result.summary.take(2_000) }
            val initialOutput = mapper.readTree(result.summary).also { require(it.isObject) { "Rol gaf geen JSON-object" } }
            val output = if (step.role == "vision-critic" && !initialOutput.path("approved").asBoolean()) {
                executeCriticCorrection(step, inputs, initialOutput)
            } else {
                initialOutput
            }
            val effectiveInputIds = (inputIds + output.path("correctionArtifactIds").map(JsonNode::asText)).distinct()
            val outputIds = persistOutput(step, output)
            val downstream = LivingVisionRoleCatalog.byKey.getValue(step.role).downstreamConsumer
            val handoff = RoadmapHandoffView(
                processVersion = LIVING_VISION_PROCESS_VERSION,
                sessionId = step.sessionId,
                productSlug = step.productSlug,
                producerRole = step.role,
                consumerRole = downstream,
                scopeKey = step.scopeKey,
                inputArtifactIds = effectiveInputIds,
                outputArtifactIds = outputIds,
                summary = output.path("summary").asText().trim().take(4_000),
                payload = handoffPayload(output),
            )
            catalog.markCompleted(step.id, outputIds, handoff)
            agentRuns.complete(step.productSlug, runId, "COMPLETED", "roadmap-session:${step.sessionId}/${step.role}/${step.scopeKey}")
            timer.stop(metrics.timer("product_factory.roadmap.step.duration", "role", step.role, "status", "completed"))
            metrics.counter("product_factory.roadmap.step.completed", "role", step.role).increment()
        } catch (exception: Exception) {
            runCatching { agentRuns.complete(step.productSlug, runId, "FAILED", null) }
            val message = exception.message ?: exception.javaClass.simpleName
            when {
                step.attempt < maxAttempts -> {
                    catalog.markForRetry(step.id, message)
                    metrics.counter("product_factory.roadmap.step.retry", "role", step.role).increment()
                }
                !step.required -> catalog.markFailed(step.id, message, true)
                else -> catalog.markFailed(step.id, message, false)
            }
            timer.stop(metrics.timer("product_factory.roadmap.step.duration", "role", step.role, "status", "failed"))
        }
    }

    /** Eén, en nooit meer dan één, correctieronde terug naar de strateeg met een tweede onafhankelijke review. */
    private fun executeCriticCorrection(
        step: RoadmapSessionStepView,
        inputs: List<RoadmapHandoffView>,
        initialCritique: JsonNode,
    ): JsonNode {
        val critiqueId = "critique:${step.id}:round-1"
        val critiqueHandoff = RoadmapHandoffView(
            LIVING_VISION_PROCESS_VERSION, step.sessionId, step.productSlug, "vision-critic", "future-strategist",
            "critic-correction", inputs.flatMap { it.outputArtifactIds }.distinct(), listOf(critiqueId),
            initialCritique.path("summary").asText(), handoffPayload(initialCritique),
        )
        val correctedStrategy = executeSupplementalRole(
            step, "future-strategist", "critic-correction", inputs + critiqueHandoff, "strategy-correction",
        )
        val correctionId = "strategy-correction:${step.id}:round-1"
        val correctionHandoff = RoadmapHandoffView(
            LIVING_VISION_PROCESS_VERSION, step.sessionId, step.productSlug, "future-strategist", "vision-critic",
            "critic-correction", listOf(critiqueId), listOf(correctionId),
            correctedStrategy.path("visionChangeSummary").asText(), handoffPayload(correctedStrategy),
        )
        val finalCritique = executeSupplementalRole(
            step, "vision-critic", "critic-correction", inputs + critiqueHandoff + correctionHandoff, "critic-rereview",
        ).deepCopy<ObjectNode>()
        finalCritique.set<JsonNode>("correctedStrategy", correctedStrategy)
        finalCritique.put("correctionRound", 1)
        finalCritique.putArray("correctionArtifactIds").add(critiqueId).add(correctionId)
        return finalCritique
    }

    private fun executeSupplementalRole(
        step: RoadmapSessionStepView,
        role: String,
        scopeKey: String,
        inputs: List<RoadmapHandoffView>,
        runSuffix: String,
    ): JsonNode {
        val product = products.requireProduct(step.productSlug)
        val runId = "${step.sessionId}-$runSuffix-a${step.attempt}".replace(Regex("[^A-Za-z0-9._-]"), "-").take(120)
        val roleContract = LivingVisionRoleCatalog.byKey.getValue(role)
        val all = catalog.steps(step.sessionId)
        val correctionGoal = when (role) {
            "future-strategist" -> "Maak één volledige gecorrigeerde strategie. Pas ieder revisionRequest uit de " +
                "criticus-handoff aantoonbaar toe; behoud de overige geldige strategie-inhoud en verlaag claims " +
                "waarvoor het aangeleverde bewijs de gekozen haalbaarheidsstatus niet draagt."
            "vision-critic" -> "Herbeoordeel uitsluitend de gecorrigeerde strategie uit de future-strategist-handoff. " +
                "Controleer ieder eerder revisionRequest afzonderlijk en zet approved alleen op true als alle verzoeken " +
                "zijn opgelost en de volledige strategie het procescontract nog respecteert."
            else -> "Verwerk de begrensde correctieronde zonder producthistorie of onzekerheid te verliezen."
        }
        val manifest = RoadmapSessionManifest(
            step.sessionId, step.productSlug, step.processVersion, stageFor(role), role,
            correctionGoal, scopeKey,
            all.filter { it.status == RoadmapStepStatus.COMPLETED }.map { it.role }, listOf(role),
            listOf(roleContract.downstreamConsumer), inputs.flatMap { it.outputArtifactIds }.distinct(),
            roleContract.downstreamConsumer,
        )
        val taskType = "roadmap-$role"
        agentRuns.register(runId, step.productSlug, taskType)
        try {
            val result = agents.execute(
                AgentTask(
                    runId, step.productSlug, taskType, prompts.build(manifest, contexts.build(step.productSlug), inputs),
                    STEP_TIMEOUT_SECONDS, product.aiModel.takeUnless { it == "default" },
                    LivingVisionSchemas.forRole(role), product.aiProvider,
                ),
            )
            require(result.status == "COMPLETED") { result.summary.take(2_000) }
            val output = mapper.readTree(result.summary).also { require(it.isObject) { "Correctieronde gaf geen JSON-object" } }
            agentRuns.complete(step.productSlug, runId, "COMPLETED", "roadmap-session:${step.sessionId}/$role/$scopeKey")
            return output
        } catch (exception: Exception) {
            runCatching { agentRuns.complete(step.productSlug, runId, "FAILED", null) }
            throw exception
        }
    }

    private fun executeActivation(step: RoadmapSessionStepView, inputs: List<RoadmapHandoffView>) {
        try {
            val all = catalog.steps(step.sessionId)
            val strategyHandoff = all.single { it.role == "future-strategist" }.handoff ?: error("Strategie ontbreekt")
            val critic = all.single { it.role == "vision-critic" }.handoff ?: error("Kritiek ontbreekt")
            require((critic.payload["approved"] as? Boolean) == true) { "Visiecriticus heeft activatie geblokkeerd" }
            val manager = inputs.single { it.producerRole == "roadmap-manager" }
            val strategy = mapper.valueToTree<JsonNode>(critic.payload["correctedStrategy"] ?: strategyHandoff.payload)
            val plan = mapper.valueToTree<JsonNode>(manager.payload)
            validateDiscoveryEpics(plan)
            activator.activate(step.productSlug, step.sessionId, strategy, plan, manager.summary)
            val activationId = "activation:${step.sessionId}"
            val handoff = RoadmapHandoffView(
                LIVING_VISION_PROCESS_VERSION, step.sessionId, step.productSlug, "activation", "dashboard/product-cycles",
                step.scopeKey, inputs.flatMap { it.outputArtifactIds }.distinct(), listOf(activationId),
                "Living vision en roadmap zijn atomair geactiveerd.", mapOf("activationId" to activationId),
            )
            catalog.markCompleted(step.id, listOf(activationId), handoff)
        } catch (exception: Exception) {
            catalog.markFailed(step.id, exception.message ?: exception.javaClass.simpleName, false)
            throw exception
        }
    }

    private fun persistOutput(step: RoadmapSessionStepView, output: JsonNode): List<String> = when (step.role) {
        "product-market-scout", "domain-source-scout" -> persistDiscovery(step, output)
        "wild-ideas" -> emptyList()
        "vision-curator" -> persistCurator(step, output)
        "ux-concept" -> persistConcept(step, output)
        "feasibility" -> persistResearch(step, output)
        "ux-director" -> persistDirectorRevisions(step, output)
        else -> listOf("handoff:${step.id}")
    }

    private fun persistDiscovery(step: RoadmapSessionStepView, output: JsonNode): List<String> = output.path("sources").map { source ->
        catalog.addInspiration(
            step.productSlug,
            nl.vdzon.productfactory.roadmap.api.InspirationMutation(
                step.sessionId, step.role, source.path("url").asText(), source.path("title").asText(),
                Instant.parse(source.path("accessedAt").asText()), source.path("observation").asText(),
                source.path("interpretation").asText(), source.path("relevance").asText(),
                source.path("limitations").asText().takeIf(String::isNotBlank), source.path("confidence").asInt(),
                rightsNote = source.path("rightsNote").asText().takeIf(String::isNotBlank),
            ),
        ).id.also { metrics.counter("product_factory.roadmap.inspiration", "role", step.role).increment() }
    }

    private fun persistCurator(step: RoadmapSessionStepView, output: JsonNode): List<String> = output.path("ideas").mapNotNull { idea ->
        val action = idea.path("action").asText()
        if (action == "NO_CHANGE") return@mapNotNull null
        val key = idea.path("ideaKey").asText()
        val existing = catalog.ideas(step.productSlug).singleOrNull { it.ideaKey == key }
        val status = when (action) {
            "PARK" -> RoadmapIdeaStatus.PARKED
            "REJECT" -> RoadmapIdeaStatus.REJECTED
            "CREATE" -> RoadmapIdeaStatus.SPARK
            else -> RoadmapIdeaStatus.EXPLORED
        }
        catalog.appendIdea(
            step.productSlug,
            IdeaMutation(
                key, status, idea.path("promise").asText(), idea.path("primaryAudience").asText(),
                idea.path("need").asText(), existing?.origin ?: "living-vision-discovery",
                idea.path("reason").asText(), idea.path("evidence").asText().takeIf(String::isNotBlank),
                mapper.convertValue(idea, Map::class.java).entries.associate { it.key.toString() to it.value },
                step.sessionId, step.role,
            ),
        ).let { "idea:${it.ideaKey}:v${it.currentVersion}" }
    }

    private fun persistConcept(step: RoadmapSessionStepView, output: JsonNode): List<String> {
        val ideaKey = output.path("ideaKey").asText()
        val idea = catalog.ideas(step.productSlug).singleOrNull { it.ideaKey == ideaKey }
            ?: throw IllegalArgumentException("Concept verwijst naar onbekend idee")
        val images = output.path("generatedImages").map { image ->
            val bytes = Base64.getDecoder().decode(image.path("base64Content").asText())
            media.store(
                step.productSlug, image.path("filename").asText(), image.path("mediaType").asText(), bytes,
                image.path("altText").asText(), "ai", "${step.sessionId}/${step.id}",
            ).also { metrics.counter("product_factory.roadmap.screenshot", "viewport", image.path("viewport").asText()).increment() } to image
        }
        val versions = catalog.appendConceptFlow(
            step.productSlug,
            ConceptFlowMutation(
                output.path("conceptKey").asText(), ideaKey, idea.currentVersion,
                output.path("userGoal").asText(), output.path("interaction").asText(), output.path("content").asText(),
                output.path("states").map(JsonNode::asText), output.path("decisions").map(JsonNode::asText),
                output.path("assumptions").map(JsonNode::asText), output.path("openQuestions").map(JsonNode::asText),
                sessionId = step.sessionId,
                frames = images.map { (stored, image) ->
                    ConceptFrameMutation(image.path("viewport").asText(), image.path("flowPosition").asInt(), listOf(stored.id))
                },
            ),
        )
        return versions.map { version ->
            "concept:${output.path("conceptKey").asText()}:v${version.version}:${version.viewport}:${version.flowPosition}"
        }
    }

    private fun persistDirectorRevisions(step: RoadmapSessionStepView, output: JsonNode): List<String> {
        val revisions = output.path("conceptRevisions")
        require(output.path("approved").asBoolean() || revisions.size() > 0) {
            "UX-director heeft de conceptset niet goedgekeurd en geen uitvoerbare revisie geleverd"
        }
        return revisions.flatMap { revision ->
            val conceptKey = revision.path("conceptKey").asText()
            val concept = catalog.concepts(step.productSlug).singleOrNull { it.conceptKey == conceptKey }
                ?: throw IllegalArgumentException("Revisie verwijst naar onbekend concept")
            val previous = catalog.conceptVersions(step.productSlug, conceptKey)
                .filter { it.version == concept.currentVersion }
            require(previous.isNotEmpty()) { "Conceptrevisie heeft geen bestaande flow" }
            val idea = catalog.ideas(step.productSlug).single { it.ideaKey == concept.ideaKey }
            val revised = catalog.appendConceptFlow(
                step.productSlug,
                ConceptFlowMutation(
                    conceptKey, concept.ideaKey, idea.currentVersion, revision.path("userGoal").asText(),
                    revision.path("interaction").asText(), revision.path("content").asText(),
                    revision.path("states").map(JsonNode::asText), revision.path("decisions").map(JsonNode::asText),
                    revision.path("assumptions").map(JsonNode::asText), revision.path("openQuestions").map(JsonNode::asText),
                    review = listOf(revision.path("reason").asText(), revision.path("evidenceImpact").asText())
                        .joinToString(" — "),
                    sessionId = step.sessionId,
                    frames = previous.map { frame ->
                        ConceptFrameMutation(frame.viewport, frame.flowPosition, frame.assets.map { it.id })
                    },
                ),
            )
            revised.map { "concept:$conceptKey:v${it.version}:${it.viewport}:${it.flowPosition}" }
        }
    }

    private fun persistResearch(step: RoadmapSessionStepView, output: JsonNode): List<String> = output.path("results").map { result ->
        catalog.addResearch(
            step.productSlug,
            ResearchMutation(
                step.sessionId, result.path("ideaKey").asText(), result.path("conceptKey").takeIf(JsonNode::isTextual)?.asText(),
                result.path("capabilityKey").takeIf(JsonNode::isTextual)?.asText(), result.path("researchType").asText(),
                result.path("question").asText(), result.path("evidence").asText(), result.path("sources").map(JsonNode::asText),
                result.path("limitations").asText(), result.path("confidence").asInt(),
                RoadmapResearchStatus.valueOf(result.path("status").asText()), result.path("conclusion").asText(),
                result.path("recommendedNextStep").asText(), result.path("dependencies").map(JsonNode::asText),
            ),
        ).id
    }

    private fun manifest(step: RoadmapSessionStepView, inputs: List<RoadmapHandoffView>): RoadmapSessionManifest {
        val all = catalog.steps(step.sessionId)
        val role = LivingVisionRoleCatalog.byKey.getValue(step.role)
        return RoadmapSessionManifest(
            step.sessionId, step.productSlug, step.processVersion, stageFor(step.role), step.role,
            "Maak de levende productvisie aantoonbaar concreter zonder producthistorie te verliezen.", step.scopeKey,
            all.filter { it.status == RoadmapStepStatus.COMPLETED }.map { it.role }, listOf(step.role),
            all.filter { it.status in setOf(RoadmapStepStatus.PENDING, RoadmapStepStatus.READY) }.map { it.role },
            inputs.flatMap { it.outputArtifactIds }.distinct(), role.downstreamConsumer,
        )
    }

    private fun validateDiscoveryEpics(plan: JsonNode) {
        plan.path("epicUpdates").filter { it.path("kind").asText() == "DISCOVERY" }.forEach { epic ->
            val description = epic.path("description").asText().lowercase()
            require("bewijs" in description && ("besliscriterium" in description || "besliscriter" in description)) {
                "Discovery-epic vereist bewijsdoel en besliscriterium"
            }
        }
    }

    /**
     * Handoffs blijven compacte, herleidbare metadata. Binaire beelden zijn vóór dit punt al in de mediacatalogus
     * opgeslagen en worden uitsluitend via de outputArtifactIds gerefereerd; base64 opnieuw in iedere downstream
     * prompt opnemen zou geheugen verspillen en kan de OS-limiet voor procesargumenten overschrijden.
     */
    private fun handoffPayload(output: JsonNode): Map<String, Any?> {
        val sanitized = output.deepCopy<JsonNode>()
        fun stripBinaryContent(node: JsonNode) {
            when {
                node.isObject -> {
                    (node as ObjectNode).remove(listOf("base64Content", "temporaryPath"))
                    node.elements().forEachRemaining(::stripBinaryContent)
                }
                node.isArray -> node.forEach(::stripBinaryContent)
            }
        }
        stripBinaryContent(sanitized)
        return mapper.convertValue(sanitized, Map::class.java).entries.associate { it.key.toString() to it.value }
    }

    private fun graph(sessionId: String): List<StepDefinition> {
        fun id(role: String, scope: String = "session") = "$sessionId::$role::$scope"
        val scouts = listOf("product-market-scout", "domain-source-scout", "wild-ideas")
        val curator = id("vision-curator")
        val selections = (1..maxConcepts.coerceIn(1, MAX_PARALLEL_CONCEPTS)).map { "selection-$it" }
        val uxSteps = selections.map { id("ux-concept", it) }
        val feasibilitySteps = selections.map { id("feasibility", it) }
        val director = id("ux-director")
        val strategist = id("future-strategist")
        val critic = id("vision-critic")
        val manager = id("roadmap-manager")
        return buildList {
            scouts.forEach { add(StepDefinition(id(it), it, "session", false, emptyList())) }
            add(StepDefinition(curator, "vision-curator", "session", true, scouts.map { id(it) }))
            uxSteps.forEachIndexed { index, stepId ->
                add(StepDefinition(stepId, "ux-concept", selections[index], index == 0, listOf(curator)))
            }
            feasibilitySteps.forEachIndexed { index, stepId ->
                add(StepDefinition(stepId, "feasibility", selections[index], index == 0, listOf(curator)))
            }
            add(StepDefinition(director, "ux-director", "session", true, uxSteps + feasibilitySteps))
            add(StepDefinition(strategist, "future-strategist", "session", true, listOf(director)))
            add(StepDefinition(critic, "vision-critic", "session", true, listOf(strategist)))
            add(StepDefinition(manager, "roadmap-manager", "session", true, listOf(critic)))
            add(StepDefinition(id("activation"), "activation", "session", true, listOf(manager)))
        }
    }

    private fun dependencyClosure(stepId: String): List<String> {
        val result = linkedSetOf<String>()
        fun visit(id: String) {
            catalog.dependencies(id).forEach { dependency ->
                if (result.add(dependency)) visit(dependency)
            }
        }
        visit(stepId)
        return result.toList()
    }

    private fun stageFor(role: String) = when (role) {
        "product-market-scout", "domain-source-scout", "wild-ideas" -> "DISCOVERY"
        "vision-curator" -> "CURATION"
        "ux-concept", "feasibility", "ux-director" -> "DEEPENING"
        "future-strategist", "vision-critic" -> "SYNTHESIS"
        "roadmap-manager" -> "PLANNING"
        else -> "ACTIVATION"
    }

    companion object {
        const val MAX_PARALLELISM = 8
        const val MAX_PARALLEL_CONCEPTS = 4
        const val STEP_TIMEOUT_SECONDS = 1_800L
        private val TERMINAL_STATES = setOf(RoadmapStepStatus.COMPLETED, RoadmapStepStatus.SKIPPED, RoadmapStepStatus.FAILED)
    }
}
