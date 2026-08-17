package nl.vdzon.productfactory.roadmap.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import nl.vdzon.productfactory.contracts.ProductMediaView
import nl.vdzon.productfactory.contracts.RoadmapHandoffView
import nl.vdzon.productfactory.contracts.RoadmapIdeaStatus
import nl.vdzon.productfactory.contracts.RoadmapIdeaVersionView
import nl.vdzon.productfactory.contracts.RoadmapIdeaView
import nl.vdzon.productfactory.contracts.RoadmapInspirationView
import nl.vdzon.productfactory.contracts.RoadmapResearchResultView
import nl.vdzon.productfactory.contracts.RoadmapResearchStatus
import nl.vdzon.productfactory.contracts.RoadmapSessionStepView
import nl.vdzon.productfactory.contracts.RoadmapStepStatus
import nl.vdzon.productfactory.contracts.RoadmapUxConceptVersionView
import nl.vdzon.productfactory.contracts.RoadmapUxConceptView
import nl.vdzon.productfactory.media.api.ProductMediaCatalog
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class IdeaMutation(
    val ideaKey: String,
    val status: RoadmapIdeaStatus,
    val promise: String,
    val primaryAudience: String,
    val need: String,
    val origin: String,
    val changeReason: String,
    val evidence: String?,
    val details: Map<String, Any?> = emptyMap(),
    val sessionId: String? = null,
    val role: String,
)

data class InspirationMutation(
    val sessionId: String,
    val producerRole: String,
    val sourceUrl: String,
    val title: String,
    val accessedAt: Instant,
    val observation: String,
    val interpretation: String,
    val relevance: String,
    val limitations: String?,
    val confidence: Int,
    val screenshotMediaId: String? = null,
    val rightsNote: String? = null,
)

data class ConceptVersionMutation(
    val conceptKey: String,
    val ideaKey: String,
    val ideaVersion: Int,
    val viewport: String,
    val flowPosition: Int,
    val userGoal: String,
    val interaction: String,
    val content: String,
    val states: List<String>,
    val decisions: List<String>,
    val assumptions: List<String>,
    val openQuestions: List<String>,
    val review: String? = null,
    val sessionId: String? = null,
    val mediaIds: List<String> = emptyList(),
)

data class ConceptFrameMutation(
    val viewport: String,
    val flowPosition: Int,
    val mediaIds: List<String>,
)

data class ConceptFlowMutation(
    val conceptKey: String,
    val ideaKey: String,
    val ideaVersion: Int,
    val userGoal: String,
    val interaction: String,
    val content: String,
    val states: List<String>,
    val decisions: List<String>,
    val assumptions: List<String>,
    val openQuestions: List<String>,
    val review: String? = null,
    val sessionId: String? = null,
    val frames: List<ConceptFrameMutation>,
)

data class ResearchMutation(
    val sessionId: String,
    val ideaKey: String,
    val conceptKey: String?,
    val capabilityKey: String?,
    val researchType: String,
    val question: String,
    val evidence: String,
    val sources: List<String>,
    val limitations: String,
    val confidence: Int,
    val status: RoadmapResearchStatus,
    val conclusion: String,
    val recommendedNextStep: String,
    val dependencies: List<String>,
)

data class StepDefinition(
    val id: String,
    val role: String,
    val scopeKey: String,
    val required: Boolean,
    val dependencyIds: List<String>,
)

@Service
class LivingVisionCatalog(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val products: ProductCatalog,
    private val media: ProductMediaCatalog,
) {
    @Transactional
    fun appendIdea(productSlug: String, mutation: IdeaMutation): RoadmapIdeaView {
        val slug = products.requireContext(productSlug).slug
        validateKey(mutation.ideaKey)
        require(mutation.promise.isNotBlank() && mutation.primaryAudience.isNotBlank() && mutation.need.isNotBlank())
        require(mutation.changeReason.isNotBlank()) { "Een ideeversie vereist een wijzigingsreden" }
        val existing = findIdea(slug, mutation.ideaKey)
        val ideaId = existing?.id ?: "idea-${UUID.randomUUID()}"
        val version = (existing?.currentVersion ?: 0) + 1
        if (existing == null) {
            jdbc.update(
                """insert into roadmap_idea(id, product_slug, idea_key, status, current_version, promise,
                    primary_audience, need, origin, first_session_id, status_reason, evidence)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
                ideaId, slug, mutation.ideaKey, mutation.status.name, version, mutation.promise.trim(),
                mutation.primaryAudience.trim(), mutation.need.trim(), mutation.origin.take(80), mutation.sessionId,
                mutation.changeReason.trim(), mutation.evidence?.trim(),
            )
        } else {
            jdbc.update(
                """update roadmap_idea set status = ?, current_version = ?, promise = ?, primary_audience = ?,
                    need = ?, status_reason = ?, evidence = ?, updated_at = current_timestamp
                    where id = ? and product_slug = ?""".trimIndent(),
                mutation.status.name, version, mutation.promise.trim(), mutation.primaryAudience.trim(),
                mutation.need.trim(), mutation.changeReason.trim(), mutation.evidence?.trim(), ideaId, slug,
            )
        }
        jdbc.update(
            """insert into roadmap_idea_version(idea_id, product_slug, version, promise, primary_audience, need,
                details_json, change_reason, created_by_session_id, created_by_role)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
            ideaId, slug, version, mutation.promise.trim(), mutation.primaryAudience.trim(), mutation.need.trim(),
            mapper.writeValueAsString(mutation.details), mutation.changeReason.trim(), mutation.sessionId,
            mutation.role.take(80),
        )
        return requireNotNull(findIdea(slug, mutation.ideaKey))
    }

    fun ideas(productSlug: String): List<RoadmapIdeaView> {
        val slug = products.requireContext(productSlug).slug
        return jdbc.query("select * from roadmap_idea where product_slug = ? order by updated_at desc, idea_key", ::mapIdea, slug)
    }

    fun ideaHistory(productSlug: String, ideaKey: String): List<RoadmapIdeaVersionView> {
        val slug = products.requireContext(productSlug).slug
        requireNotNull(findIdea(slug, ideaKey)) { "Onbekend idee voor dit product" }
        return jdbc.query(
            "select * from roadmap_idea_version where product_slug = ? and idea_id = (select id from roadmap_idea where product_slug = ? and idea_key = ?) order by version",
            ::mapIdeaVersion,
            slug, slug, ideaKey,
        )
    }

    fun addInspiration(productSlug: String, mutation: InspirationMutation): RoadmapInspirationView {
        val slug = products.requireContext(productSlug).slug
        require(mutation.sourceUrl.startsWith("https://") || mutation.sourceUrl.startsWith("http://")) { "Bron vereist een geldige URL" }
        require(mutation.title.isNotBlank() && mutation.observation.isNotBlank() && mutation.interpretation.isNotBlank())
        require(mutation.confidence in 0..100)
        mutation.screenshotMediaId?.let { media.require(slug, it) }
        val id = "inspiration-${UUID.randomUUID()}"
        jdbc.update(
            """insert into roadmap_inspiration(id, product_slug, session_id, producer_role, source_url, title,
                accessed_at, observation, interpretation, relevance, limitations, confidence, screenshot_media_id, rights_note)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
            id, slug, mutation.sessionId, mutation.producerRole, mutation.sourceUrl, mutation.title.trim(),
            Timestamp.from(mutation.accessedAt), mutation.observation.trim(), mutation.interpretation.trim(),
            mutation.relevance.trim(), mutation.limitations?.trim(), mutation.confidence,
            mutation.screenshotMediaId, mutation.rightsNote?.trim(),
        )
        return inspiration(slug).single { it.id == id }
    }

    fun inspiration(productSlug: String): List<RoadmapInspirationView> {
        val slug = products.requireContext(productSlug).slug
        return jdbc.query("select * from roadmap_inspiration where product_slug = ? order by created_at desc", ::mapInspiration, slug)
    }

    @Transactional
    fun appendConcept(productSlug: String, mutation: ConceptVersionMutation): RoadmapUxConceptVersionView {
        return appendConceptFlow(
            productSlug,
            ConceptFlowMutation(
                mutation.conceptKey,
                mutation.ideaKey,
                mutation.ideaVersion,
                mutation.userGoal,
                mutation.interaction,
                mutation.content,
                mutation.states,
                mutation.decisions,
                mutation.assumptions,
                mutation.openQuestions,
                mutation.review,
                mutation.sessionId,
                listOf(ConceptFrameMutation(mutation.viewport, mutation.flowPosition, mutation.mediaIds)),
            ),
        ).single()
    }

    @Transactional
    fun appendConceptFlow(productSlug: String, mutation: ConceptFlowMutation): List<RoadmapUxConceptVersionView> {
        val slug = products.requireContext(productSlug).slug
        validateKey(mutation.conceptKey)
        val idea = requireNotNull(findIdea(slug, mutation.ideaKey)) { "Onbekend idee voor dit product" }
        require(mutation.ideaVersion in 1..idea.currentVersion) { "Onbekende ideeversie" }
        require(mutation.frames.isNotEmpty()) { "Een conceptflow vereist minimaal één frame" }
        require(mutation.frames.all { it.viewport in setOf("MOBILE", "DESKTOP") && it.flowPosition >= 0 })
        require(mutation.frames.map { it.viewport to it.flowPosition }.distinct().size == mutation.frames.size) {
            "Viewport en flowpositie moeten uniek zijn binnen een conceptversie"
        }
        val frameMedia = mutation.frames.associateWith { frame ->
            frame.mediaIds.distinct().map { media.require(slug, it) }
        }
        val existing = concepts(slug).singleOrNull { it.conceptKey == mutation.conceptKey }
        require(existing == null || existing.ideaKey == mutation.ideaKey) { "Conceptsleutel hoort al bij een ander idee" }
        val conceptId = existing?.id ?: "concept-${UUID.randomUUID()}"
        val version = (existing?.currentVersion ?: 0) + 1
        if (existing == null) {
            jdbc.update(
                "insert into roadmap_ux_concept(id, product_slug, concept_key, idea_key, status, current_version) values (?, ?, ?, ?, 'DRAFT', ?)",
                conceptId, slug, mutation.conceptKey, mutation.ideaKey, version,
            )
        } else {
            jdbc.update(
                "update roadmap_ux_concept set current_version = ?, status = 'DRAFT', updated_at = current_timestamp where id = ? and product_slug = ?",
                version, conceptId, slug,
            )
        }
        mutation.frames.forEach { frame ->
            val versionId = insertConceptFrame(conceptId, slug, version, mutation, frame)
            frameMedia.getValue(frame).forEachIndexed { index, item ->
                jdbc.update(
                    "insert into roadmap_ux_concept_asset(concept_version_id, media_id, position) values (?, ?, ?)",
                    versionId,
                    item.id,
                    index,
                )
            }
        }
        return conceptVersions(slug, mutation.conceptKey).filter { it.version == version }
    }

    private fun insertConceptFrame(
        conceptId: String,
        productSlug: String,
        version: Int,
        mutation: ConceptFlowMutation,
        frame: ConceptFrameMutation,
    ): Long {
        val keyHolder = GeneratedKeyHolder()
        jdbc.update({ connection ->
            connection.prepareStatement(
                """insert into roadmap_ux_concept_version(concept_id, product_slug, version, idea_version, viewport,
                    flow_position, user_goal, interaction, content_text, states_json, decisions_json, assumptions_json,
                    open_questions_json, review, created_by_session_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
                arrayOf("id"),
            ).apply {
                listOf<Any?>(
                    conceptId, productSlug, version, mutation.ideaVersion, frame.viewport, frame.flowPosition,
                    mutation.userGoal.trim(), mutation.interaction.trim(), mutation.content.trim(),
                    mapper.writeValueAsString(mutation.states), mapper.writeValueAsString(mutation.decisions),
                    mapper.writeValueAsString(mutation.assumptions), mapper.writeValueAsString(mutation.openQuestions),
                    mutation.review?.trim(), mutation.sessionId,
                ).forEachIndexed { index, value -> setObject(index + 1, value) }
            }
        }, keyHolder)
        return keyHolder.keys?.get("ID")?.let { it as Number }?.toLong()
            ?: keyHolder.keys?.get("id")?.let { it as Number }?.toLong()
            ?: keyHolder.key!!.toLong()
    }

    fun concepts(productSlug: String): List<RoadmapUxConceptView> {
        val slug = products.requireContext(productSlug).slug
        return jdbc.query("select * from roadmap_ux_concept where product_slug = ? order by updated_at desc", ::mapConcept, slug)
    }

    fun conceptVersions(productSlug: String, conceptKey: String): List<RoadmapUxConceptVersionView> {
        val slug = products.requireContext(productSlug).slug
        val concept = concepts(slug).singleOrNull { it.conceptKey == conceptKey }
            ?: throw IllegalArgumentException("Onbekend concept voor dit product")
        return jdbc.query("select * from roadmap_ux_concept_version where product_slug = ? and concept_id = ? order by version, viewport, flow_position", { row, _ ->
            val id = row.getLong("id")
            mapConceptVersion(row, assets(slug, id))
        }, slug, concept.id)
    }

    fun addResearch(productSlug: String, mutation: ResearchMutation): RoadmapResearchResultView {
        val slug = products.requireContext(productSlug).slug
        requireNotNull(findIdea(slug, mutation.ideaKey)) { "Onbekend idee voor dit product" }
        mutation.conceptKey?.let { key -> require(concepts(slug).any { it.conceptKey == key }) { "Onbekend concept voor dit product" } }
        require(mutation.confidence in 0..100)
        if (mutation.status == RoadmapResearchStatus.FUNDAMENTALLY_IMPOSSIBLE) {
            require(mutation.confidence >= 90 && mutation.sources.distinct().size >= 2 && mutation.evidence.length >= 40) {
                "Fundamentele onmogelijkheid vereist sterk controleerbaar bewijs uit meerdere bronnen"
            }
        }
        val id = "research-${UUID.randomUUID()}"
        jdbc.update(
            """insert into roadmap_research_result(id, product_slug, session_id, idea_key, concept_key,
                capability_key, research_type, question, evidence, sources_json, limitations, confidence, status,
                conclusion, recommended_next_step, dependencies_json) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
            id, slug, mutation.sessionId, mutation.ideaKey, mutation.conceptKey, mutation.capabilityKey,
            mutation.researchType, mutation.question.trim(), mutation.evidence.trim(), mapper.writeValueAsString(mutation.sources.distinct()),
            mutation.limitations.trim(), mutation.confidence, mutation.status.name, mutation.conclusion.trim(),
            mutation.recommendedNextStep.trim(), mapper.writeValueAsString(mutation.dependencies.distinct()),
        )
        return research(slug).single { it.id == id }
    }

    fun research(productSlug: String): List<RoadmapResearchResultView> {
        val slug = products.requireContext(productSlug).slug
        return jdbc.query("select * from roadmap_research_result where product_slug = ? order by created_at desc", ::mapResearch, slug)
    }

    @Transactional
    fun initializeSteps(sessionId: String, productSlug: String, processVersion: String, definitions: List<StepDefinition>) {
        val slug = products.requireContext(productSlug).slug
        require(definitions.map { it.id }.toSet().size == definitions.size)
        definitions.forEach { definition ->
            try {
                jdbc.update(
                    """insert into roadmap_session_step(id, session_id, product_slug, role, scope_key, status,
                        process_version, required_step) values (?, ?, ?, ?, ?, 'PENDING', ?, ?)""".trimIndent(),
                    definition.id, sessionId, slug, definition.role, definition.scopeKey, processVersion, definition.required,
                )
            } catch (_: DuplicateKeyException) {
                // Idempotent graph initialization after a process restart.
            }
        }
        definitions.forEach { definition -> definition.dependencyIds.forEach { dependency ->
            require(definitions.any { it.id == dependency }) { "Onbekende stapafhankelijkheid" }
            try {
                jdbc.update("insert into roadmap_session_step_dependency(step_id, depends_on_step_id) values (?, ?)", definition.id, dependency)
            } catch (_: DuplicateKeyException) {
                // Idempotent.
            }
        } }
    }

    fun steps(sessionId: String): List<RoadmapSessionStepView> = jdbc.query(
        "select * from roadmap_session_step where session_id = ? order by created_at, id",
        ::mapStep,
        sessionId,
    )

    fun dependencies(stepId: String): List<String> = jdbc.queryForList(
        "select depends_on_step_id from roadmap_session_step_dependency where step_id = ? order by depends_on_step_id",
        String::class.java,
        stepId,
    )

    fun readySteps(sessionId: String): List<RoadmapSessionStepView> = jdbc.query(
        """select s.* from roadmap_session_step s where s.session_id = ? and s.status in ('PENDING', 'READY')
            and not exists (select 1 from roadmap_session_step_dependency d join roadmap_session_step p on p.id = d.depends_on_step_id
                where d.step_id = s.id and (p.status not in ('COMPLETED', 'SKIPPED') or (p.required_step = true and p.status = 'SKIPPED')))
            order by s.created_at, s.id""".trimIndent(),
        ::mapStep,
        sessionId,
    )

    fun markRunning(stepId: String, provider: String, model: String, inputIds: List<String>): Boolean = jdbc.update(
        """update roadmap_session_step set status = 'RUNNING', provider = ?, model = ?, input_artifact_ids_json = ?,
            started_at = current_timestamp where id = ? and status in ('PENDING', 'READY')""".trimIndent(),
        provider, model, mapper.writeValueAsString(inputIds.distinct()), stepId,
    ) == 1

    fun markCompleted(stepId: String, outputIds: List<String>, handoff: RoadmapHandoffView) {
        require(jdbc.update(
            """update roadmap_session_step set status = 'COMPLETED', output_artifact_ids_json = ?, handoff_json = ?,
                completed_at = current_timestamp where id = ? and status = 'RUNNING'""".trimIndent(),
            mapper.writeValueAsString(outputIds.distinct()), mapper.writeValueAsString(handoff), stepId,
        ) == 1) { "Stap is niet actief of al afgehandeld" }
    }

    fun markFailed(stepId: String, error: String, skipOptional: Boolean) {
        val status = if (skipOptional) "SKIPPED" else "FAILED"
        jdbc.update(
            "update roadmap_session_step set status = ?, error_message = ?, completed_at = current_timestamp where id = ? and status = 'RUNNING'",
            status, error.take(4_000), stepId,
        )
    }

    fun markForRetry(stepId: String, error: String) {
        jdbc.update(
            """update roadmap_session_step set status = 'PENDING', attempt = attempt + 1, error_message = ?,
                started_at = null where id = ? and status = 'RUNNING'""".trimIndent(),
            error.take(4_000), stepId,
        )
    }

    fun resetInterrupted(sessionId: String) {
        jdbc.update(
            """update roadmap_session_step set status = 'PENDING', attempt = attempt + 1, started_at = null,
                error_message = 'Hervat na onderbroken proces' where session_id = ? and status = 'RUNNING'""".trimIndent(),
            sessionId,
        )
    }

    private fun findIdea(productSlug: String, ideaKey: String): RoadmapIdeaView? = jdbc.query(
        "select * from roadmap_idea where product_slug = ? and idea_key = ?",
        ::mapIdea,
        productSlug, ideaKey,
    ).singleOrNull()

    private fun assets(productSlug: String, conceptVersionId: Long): List<ProductMediaView> = jdbc.queryForList(
        "select media_id from roadmap_ux_concept_asset where concept_version_id = ? order by position",
        String::class.java,
        conceptVersionId,
    ).map { media.require(productSlug, it) }

    private fun mapIdea(row: ResultSet, ignored: Int) = RoadmapIdeaView(
        row.getString("id"), row.getString("product_slug"), row.getString("idea_key"),
        RoadmapIdeaStatus.valueOf(row.getString("status")), row.getInt("current_version"), row.getString("promise"),
        row.getString("primary_audience"), row.getString("need"), row.getString("origin"), row.getString("first_session_id"),
        row.getString("status_reason"), row.getString("evidence"), row.getTimestamp("created_at").toInstant(),
        row.getTimestamp("updated_at").toInstant(),
    )

    private fun mapIdeaVersion(row: ResultSet, ignored: Int) = RoadmapIdeaVersionView(
        row.getLong("id"), row.getString("idea_id"), row.getString("product_slug"), row.getInt("version"),
        row.getString("promise"), row.getString("primary_audience"), row.getString("need"),
        mapper.readValue(row.getString("details_json")), row.getString("change_reason"),
        row.getString("created_by_session_id"), row.getString("created_by_role"), row.getTimestamp("created_at").toInstant(),
    )

    private fun mapInspiration(row: ResultSet, ignored: Int) = RoadmapInspirationView(
        row.getString("id"), row.getString("product_slug"), row.getString("session_id"), row.getString("producer_role"),
        row.getString("source_url"), row.getString("title"), row.getTimestamp("accessed_at").toInstant(),
        row.getString("observation"), row.getString("interpretation"), row.getString("relevance"), row.getString("limitations"),
        row.getInt("confidence"), row.getString("screenshot_media_id"), row.getString("rights_note"),
        row.getTimestamp("created_at").toInstant(),
    )

    private fun mapConcept(row: ResultSet, ignored: Int) = RoadmapUxConceptView(
        row.getString("id"), row.getString("product_slug"), row.getString("concept_key"), row.getString("idea_key"),
        row.getString("status"), row.getInt("current_version"), row.getTimestamp("created_at").toInstant(),
        row.getTimestamp("updated_at").toInstant(),
    )

    private fun mapConceptVersion(row: ResultSet, assets: List<ProductMediaView>) = RoadmapUxConceptVersionView(
        row.getLong("id"), row.getString("concept_id"), row.getString("product_slug"), row.getInt("version"),
        row.getInt("idea_version"), row.getString("viewport"), row.getInt("flow_position"), row.getString("user_goal"),
        row.getString("interaction"), row.getString("content_text"), mapper.readValue(row.getString("states_json")),
        mapper.readValue(row.getString("decisions_json")), mapper.readValue(row.getString("assumptions_json")),
        mapper.readValue(row.getString("open_questions_json")), row.getString("review"), row.getString("created_by_session_id"),
        row.getTimestamp("created_at").toInstant(), assets,
    )

    private fun mapResearch(row: ResultSet, ignored: Int) = RoadmapResearchResultView(
        row.getString("id"), row.getString("product_slug"), row.getString("session_id"), row.getString("idea_key"),
        row.getString("concept_key"), row.getString("capability_key"), row.getString("research_type"), row.getString("question"),
        row.getString("evidence"), mapper.readValue(row.getString("sources_json")), row.getString("limitations"),
        row.getInt("confidence"), RoadmapResearchStatus.valueOf(row.getString("status")), row.getString("conclusion"),
        row.getString("recommended_next_step"), mapper.readValue(row.getString("dependencies_json")),
        row.getTimestamp("created_at").toInstant(),
    )

    private fun mapStep(row: ResultSet, ignored: Int): RoadmapSessionStepView {
        val handoff = row.getString("handoff_json")?.let { mapper.readValue<RoadmapHandoffView>(it) }
        return RoadmapSessionStepView(
            row.getString("id"), row.getString("session_id"), row.getString("product_slug"), row.getString("role"),
            row.getString("scope_key"), RoadmapStepStatus.valueOf(row.getString("status")), row.getString("process_version"),
            row.getBoolean("required_step"), row.getInt("attempt"), row.getString("provider"), row.getString("model"),
            mapper.readValue(row.getString("input_artifact_ids_json")), mapper.readValue(row.getString("output_artifact_ids_json")),
            handoff, row.getString("error_message"), row.getTimestamp("started_at")?.toInstant(),
            row.getTimestamp("completed_at")?.toInstant(),
        )
    }

    private fun validateKey(value: String) {
        require(value.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")) && value.length <= 100) { "Ongeldige stabiele sleutel" }
    }
}
