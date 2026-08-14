package nl.vdzon.productfactory.preview

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

data class AcceptanceSeedResult(val applied: Boolean)

/**
 * De volledige, versieerbare acceptatiecatalogus. De opslagcode accepteert uitsluitend een exacte
 * kopie van dit model; er is bewust geen invoerpad voor vrije fixturewaarden.
 */
object AcceptanceFixtureCatalog {
    const val PRODUCT_SLUG = "product-factory"
    const val SEED_KEY = "acceptance-product-factory-cycles-v1"

    private val template: Map<String, Any?> = linkedMapOf(
        "seedHistory" to listOf(
            linkedMapOf(
                "seedKey" to SEED_KEY,
                "prNumber" to 0,
            ),
        ),
        "productSlug" to PRODUCT_SLUG,
        "iterations" to listOf(
            iteration(
                id = "acceptance-pf-accepted-v1",
                sequenceNumber = 9202,
                focus = "Synthetische cyclus voor twee voltooide acceptatieleveringen.",
                status = "ACCEPTED",
                criticVerdict = "ACCEPT",
                workspaceRunId = "acceptance-pf-workspace-v1",
                summary = "Twee synthetische stories zijn lokaal voltooid.",
                createdAt = "2026-01-02T09:00:00Z",
                startedAt = "2026-01-02T09:05:00Z",
                completedAt = "2026-01-02T09:10:00Z",
                generatedCandidateCount = 2,
                acceptedCandidateCount = 2,
                outcomeReason = "ACCEPT",
            ),
            iteration(
                id = "acceptance-pf-failed-v1",
                sequenceNumber = 9203,
                focus = "Synthetische cyclus voor expliciete handmatige annulering.",
                status = "FAILED",
                errorMessage = "Synthetische handmatige annulering.",
                summary = "Deze cyclus is uitsluitend als acceptatiescenario handmatig geannuleerd.",
                createdAt = "2026-01-03T09:00:00Z",
                startedAt = "2026-01-03T09:05:00Z",
                completedAt = "2026-01-03T09:10:00Z",
                outcomeReason = "TECHNICAL_FAILURE",
            ),
            iteration(
                id = "acceptance-pf-rejected-v1",
                sequenceNumber = 9201,
                focus = "Synthetische guardrailcyclus zonder gekoppelde opbrengst.",
                status = "REJECTED",
                criticVerdict = "ACCEPT",
                summary = "Een positief criticusoordeel is conservatief door de guardrail afgewezen.",
                createdAt = "2026-01-01T09:00:00Z",
                startedAt = "2026-01-01T09:05:00Z",
                completedAt = "2026-01-01T09:10:00Z",
                outcomeReason = "REJECT",
            ),
            iteration(
                id = "acceptance-pf-running-v1",
                sequenceNumber = 9204,
                focus = "Synthetische actieve cyclus voor de acceptatieweergave.",
                status = "RUNNING",
                currentAgentRole = "RESEARCHER",
                createdAt = "2026-01-04T09:00:00Z",
                startedAt = "2026-01-04T09:05:00Z",
            ),
        ),
        "decisions" to listOf(
            linkedMapOf(
                "iterationId" to "acceptance-pf-failed-v1",
                "actorType" to "HUMAN",
                "mechanism" to "MANUAL_CANCELLATION",
                "reasonCode" to "MANUALLY_CANCELLED",
                "decidedAt" to "2026-01-03T09:10:00Z",
            ),
        ),
        "candidates" to listOf(
            candidate(
                id = -920002L,
                title = "Synthetische story: toegankelijke cyclusstatus",
                description = "Toon de vaste cyclusstatus begrijpelijk zonder afhankelijkheid van kleur.",
                acceptanceCriteria = "De status is zichtbaar en toegankelijk als tekst.",
                fingerprint = "acceptance-pf-accessible-cycle-status-v1",
                criticReason = "Vast synthetisch scenario voor acceptatie.",
                createdAt = "2026-01-02T09:07:00Z",
            ),
            candidate(
                id = -920001L,
                title = "Synthetische story: deterministische opbrengstkoppeling",
                description = "Koppel een vaste lokale levering exact aan productslug en cyclus-id.",
                acceptanceCriteria = "De levering telt exact eenmaal als gekoppelde opbrengst.",
                fingerprint = "acceptance-pf-deterministic-yield-link-v1",
                criticReason = "Vast synthetisch scenario voor acceptatie.",
                createdAt = "2026-01-02T09:06:00Z",
            ),
        ),
        "deliveries" to listOf(
            delivery(
                id = -920002L,
                candidateId = -920002L,
                workspaceCommitSha = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                artifactPath = "acceptance-fixtures/accessible-cycle-status.md",
                idempotencyKey = "acceptance-pf-delivery-accessible-cycle-status-v1",
                externalStoryKey = "SYNTH-PF-102",
                createdAt = "2026-01-02T09:08:00Z",
                deliveredAt = "2026-01-02T09:09:00Z",
                completedAt = "2026-01-02T09:10:00Z",
                deployedAt = "2026-01-02T09:10:00Z",
                evaluationWorkspaceRunId = "acceptance-pf-evaluation-2-v1",
                evaluatedAt = "2026-01-02T09:10:00Z",
            ),
            delivery(
                id = -920001L,
                candidateId = -920001L,
                workspaceCommitSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                artifactPath = "acceptance-fixtures/deterministic-yield-link.md",
                idempotencyKey = "acceptance-pf-delivery-deterministic-yield-link-v1",
                externalStoryKey = "SYNTH-PF-101",
                createdAt = "2026-01-02T09:07:00Z",
                deliveredAt = "2026-01-02T09:08:00Z",
                completedAt = "2026-01-02T09:09:00Z",
                deployedAt = "2026-01-02T09:09:00Z",
                evaluationWorkspaceRunId = "acceptance-pf-evaluation-1-v1",
                evaluatedAt = "2026-01-02T09:09:00Z",
            ),
        ),
    )

    @Suppress("UNCHECKED_CAST")
    fun fixture(): Map<String, Any?> = deepCopy(template) as Map<String, Any?>

    internal fun expected(): Map<String, Any?> = template

    @Suppress("LongParameterList")
    private fun iteration(
        id: String,
        sequenceNumber: Int,
        focus: String,
        status: String,
        currentAgentRole: String? = null,
        criticVerdict: String? = null,
        workspaceRunId: String? = null,
        errorMessage: String? = null,
        summary: String? = null,
        createdAt: String,
        startedAt: String?,
        completedAt: String? = null,
        generatedCandidateCount: Int = 0,
        acceptedCandidateCount: Int = 0,
        outcomeReason: String? = null,
    ) = linkedMapOf<String, Any?>(
        "id" to id,
        "productSlug" to PRODUCT_SLUG,
        "sequenceNumber" to sequenceNumber,
        "focus" to focus,
        "mode" to "shadow",
        "status" to status,
        "currentAgentRole" to currentAgentRole,
        "criticVerdict" to criticVerdict,
        "workspaceRunId" to workspaceRunId,
        "workspacePullRequestUrl" to null,
        "workspaceCommitSha" to null,
        "errorMessage" to errorMessage,
        "summary" to summary,
        "createdAt" to createdAt,
        "startedAt" to startedAt,
        "completedAt" to completedAt,
        "resumeFromIterationId" to null,
        "generatedCandidateCount" to generatedCandidateCount,
        "acceptedCandidateCount" to acceptedCandidateCount,
        "revisionRounds" to 0,
        "outcomeReason" to outcomeReason,
    )

    private fun candidate(
        id: Long,
        title: String,
        description: String,
        acceptanceCriteria: String,
        fingerprint: String,
        criticReason: String,
        createdAt: String,
    ) = linkedMapOf<String, Any?>(
        "id" to id,
        "productSlug" to PRODUCT_SLUG,
        "title" to title,
        "description" to description,
        "status" to "PUBLISHED",
        "createdAt" to createdAt,
        "iterationId" to "acceptance-pf-accepted-v1",
        "fingerprint" to fingerprint,
        "acceptanceCriteria" to acceptanceCriteria,
        "criticStatus" to "ACCEPT",
        "criticReason" to criticReason,
        "duplicateOfId" to null,
        "themeId" to null,
    )

    @Suppress("LongParameterList")
    private fun delivery(
        id: Long,
        candidateId: Long,
        workspaceCommitSha: String,
        artifactPath: String,
        idempotencyKey: String,
        externalStoryKey: String,
        createdAt: String,
        deliveredAt: String,
        completedAt: String,
        deployedAt: String,
        evaluationWorkspaceRunId: String,
        evaluatedAt: String,
    ) = linkedMapOf<String, Any?>(
        "id" to id,
        "productSlug" to PRODUCT_SLUG,
        "candidateId" to candidateId,
        "iterationId" to "acceptance-pf-accepted-v1",
        "workspaceRunId" to "acceptance-pf-workspace-v1",
        "workspaceCommitSha" to workspaceCommitSha,
        "artifactPath" to artifactPath,
        "idempotencyKey" to idempotencyKey,
        "externalStoryKey" to externalStoryKey,
        "status" to "DONE",
        "remotePhase" to "developed",
        "errorMessage" to null,
        "createdAt" to createdAt,
        "deliveredAt" to deliveredAt,
        "completedAt" to completedAt,
        "lastReconciledAt" to completedAt,
        "evaluationWorkspaceRunId" to evaluationWorkspaceRunId,
        "evaluatedAt" to evaluatedAt,
        "confirmedDeployed" to true,
        "deployedAt" to deployedAt,
    )

    private fun deepCopy(value: Any?): Any? = when (value) {
        is Map<*, *> -> LinkedHashMap(value.entries.associate { (key, child) -> key as String to deepCopy(child) })
        is List<*> -> value.map(::deepCopy).toMutableList()
        else -> value
    }
}

/** Vergelijkt recursief zowel de gesloten veldenset als iedere exacte waarde. */
class AcceptanceFixtureValidator {
    fun validate(candidate: Map<String, Any?>) {
        compare(AcceptanceFixtureCatalog.expected(), candidate, "fixture")
    }

    private fun compare(expected: Any?, actual: Any?, path: String) {
        when (expected) {
            is Map<*, *> -> {
                require(actual is Map<*, *>) { "$path moet een object zijn" }
                val expectedKeys = expected.keys
                val actualKeys = actual.keys
                require(actualKeys == expectedKeys) {
                    val unknown = actualKeys - expectedKeys
                    val missing = expectedKeys - actualKeys
                    "$path bevat niet-toegestane velden $unknown of mist verplichte velden $missing"
                }
                expected.forEach { (key, value) -> compare(value, actual[key], "$path.$key") }
            }
            is List<*> -> {
                require(actual is List<*>) { "$path moet een lijst zijn" }
                require(actual.size == expected.size) {
                    "$path moet exact ${expected.size} records bevatten, maar bevat ${actual.size}"
                }
                expected.indices.forEach { index -> compare(expected[index], actual[index], "$path[$index]") }
            }
            else -> require(actual == expected) {
                "$path heeft niet-toegestane waarde ${safeValue(actual)}"
            }
        }
    }

    private fun safeValue(value: Any?): String = when (value) {
        null, is Number, is Boolean -> "$value"
        else -> "<afwijkende tekst>"
    }
}

/**
 * Slaat de catalogus direct in bestaande tabellen op. Een enkele transactie omvat controle,
 * inserts, seedhistorie en nacontrole, zodat een botsing nooit een halve dataset achterlaat.
 */
@Service
class AcceptanceDataSeeder(
    private val jdbc: JdbcTemplate,
    private val preview: PreviewRuntimeConfig,
) {
    private val validator = AcceptanceFixtureValidator()

    @Transactional
    fun ensure(): AcceptanceSeedResult {
        preview.requireAcceptanceSeedingAllowed()
        val fixture = AcceptanceFixtureCatalog.fixture()
        validator.validate(fixture)
        ensureSupportingProduct()

        val stored = readStoredFixture()
        if (stored.values.filterIsInstance<List<*>>().all(List<*>::isEmpty)) {
            insertFixture(fixture)
            validator.validate(readStoredFixture())
            return AcceptanceSeedResult(applied = true)
        }

        try {
            validator.validate(stored)
        } catch (exception: IllegalArgumentException) {
            throw IllegalStateException(
                "Gereserveerde acceptatiefixture wijkt af; bestaande gegevens worden niet overschreven: ${exception.message}",
                exception,
            )
        }
        return AcceptanceSeedResult(applied = false)
    }

    internal fun readStoredFixture(): Map<String, Any?> = linkedMapOf(
        "seedHistory" to jdbc.query(
            "select seed_key, pr_number from preview_seed_history where seed_key = ? order by seed_key",
            { row, _ -> linkedMapOf("seedKey" to row.getString(1), "prNumber" to row.getInt(2)) },
            AcceptanceFixtureCatalog.SEED_KEY,
        ),
        "productSlug" to AcceptanceFixtureCatalog.PRODUCT_SLUG,
        "iterations" to readIterations(),
        "decisions" to readDecisions(),
        "candidates" to readCandidates(),
        "deliveries" to readDeliveries(),
    )

    /**
     * De productrij is alleen de bestaande FK-context en valt buiten de fixturecatalogus. Een reeds
     * bestaand product blijft volledig ongemoeid; op een lege acceptatiedatabase maken we een vaste,
     * gepauzeerde context aan die ProjectsYamlReconciler later alleen voor vaste URL/repositoryvelden
     * mag bijwerken.
     */
    private fun ensureSupportingProduct() {
        val rows = jdbc.query(
            "select id, slug from product_definition where slug = ? or id = ? order by slug",
            { row, _ -> row.getString(1) to row.getString(2) },
            AcceptanceFixtureCatalog.PRODUCT_SLUG,
            "acceptance-product-product-factory-v1",
        )
        if (rows.any { it.second == AcceptanceFixtureCatalog.PRODUCT_SLUG }) return
        require(rows.isEmpty()) { "Gereserveerde acceptatieproduct-id is al aan een ander product gekoppeld" }
        jdbc.update(
            """insert into product_definition(
                id, slug, name, mission, guardrails, description, software_factory_project_key,
                target_repository_name, workspace_directory, workspace_ownership, live_url,
                preview_url_pattern, acceptance_url, admin_url, status, development_mode, timezone,
                max_stories_per_cycle, wip_limit, ai_provider, ai_model, daily_budget_cents,
                monthly_budget_cents, escalation_policy, privacy_rules, accessibility_rules,
                quality_rules, meeting_requested_at, meeting_requested_topics, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
            "acceptance-product-product-factory-v1", AcceptanceFixtureCatalog.PRODUCT_SLUG,
            "Product Factory (synthetische acceptatie)", "Toon uitsluitend de vaste synthetische acceptatiescenario's.",
            "Geen autonome uitvoering, externe publicatie of echte productgegevens.",
            "Lokale productcontext voor de synthetische acceptatiecatalogus.", "SYNTH-PF", "synthetic/product-factory",
            "products/product-factory", "owner", null, null, "https://product-factory-acceptance.vdzonsoftware.nl", null,
            "paused", "manual", "Europe/Amsterdam", 2, 1, "codex", "default", 0, 0,
            "Geen escalaties vanuit synthetische acceptatiedata.", "Gebruik uitsluitend vooraf vastgelegde synthetische gegevens.",
            "Alle scenario's blijven tekstueel begrijpelijk.", "De catalogus blijft deterministisch en idempotent.",
            null, null, timestamp("2025-12-31T09:00:00Z"), timestamp("2025-12-31T09:00:00Z"),
        )
    }

    private fun readIterations(): List<Map<String, Any?>> = jdbc.query(
        """select id, product_slug, sequence_number, focus, mode, status, current_agent_role,
                  critic_verdict, workspace_run_id, workspace_pull_request_url, workspace_commit_sha,
                  error_message, summary, created_at, started_at, completed_at, resume_from_iteration_id,
                  generated_candidate_count, accepted_candidate_count, revision_rounds, outcome_reason
             from shadow_iteration
            where id in (?, ?, ?, ?)
               or (product_slug = ? and sequence_number in (?, ?, ?, ?))
            order by id""".trimIndent(),
        { row, _ ->
            linkedMapOf(
                "id" to row.getString(1), "productSlug" to row.getString(2), "sequenceNumber" to row.getInt(3),
                "focus" to row.getString(4), "mode" to row.getString(5), "status" to row.getString(6),
                "currentAgentRole" to row.getString(7), "criticVerdict" to row.getString(8),
                "workspaceRunId" to row.getString(9), "workspacePullRequestUrl" to row.getString(10),
                "workspaceCommitSha" to row.getString(11), "errorMessage" to row.getString(12),
                "summary" to row.getString(13), "createdAt" to instant(row.getTimestamp(14)),
                "startedAt" to instant(row.getTimestamp(15)), "completedAt" to instant(row.getTimestamp(16)),
                "resumeFromIterationId" to row.getString(17), "generatedCandidateCount" to row.getInt(18),
                "acceptedCandidateCount" to row.getInt(19), "revisionRounds" to row.getInt(20),
                "outcomeReason" to row.getString(21),
            )
        },
        "acceptance-pf-accepted-v1", "acceptance-pf-failed-v1", "acceptance-pf-rejected-v1", "acceptance-pf-running-v1",
        AcceptanceFixtureCatalog.PRODUCT_SLUG, 9201, 9202, 9203, 9204,
    )

    private fun readDecisions(): List<Map<String, Any?>> = jdbc.query(
        """select iteration_id, actor_type, mechanism, reason_code, decided_at
             from shadow_iteration_decision where iteration_id in (?, ?, ?, ?) order by iteration_id""".trimIndent(),
        { row, _ ->
            linkedMapOf(
                "iterationId" to row.getString(1), "actorType" to row.getString(2),
                "mechanism" to row.getString(3), "reasonCode" to row.getString(4),
                "decidedAt" to instant(row.getTimestamp(5)),
            )
        },
        "acceptance-pf-accepted-v1", "acceptance-pf-failed-v1", "acceptance-pf-rejected-v1", "acceptance-pf-running-v1",
    )

    private fun readCandidates(): List<Map<String, Any?>> = jdbc.query(
        """select id, product_slug, title, description, status, created_at, iteration_id, fingerprint,
                  acceptance_criteria, critic_status, critic_reason, duplicate_of_id, theme_id
             from story_candidate
            where iteration_id in (?, ?, ?, ?) or id in (?, ?)
               or fingerprint in (?, ?)
            order by id""".trimIndent(),
        { row, _ ->
            linkedMapOf(
                "id" to row.getLong(1), "productSlug" to row.getString(2), "title" to row.getString(3),
                "description" to row.getString(4), "status" to row.getString(5), "createdAt" to instant(row.getTimestamp(6)),
                "iterationId" to row.getString(7), "fingerprint" to row.getString(8),
                "acceptanceCriteria" to row.getString(9), "criticStatus" to row.getString(10),
                "criticReason" to row.getString(11), "duplicateOfId" to row.getObject(12, java.lang.Long::class.java)?.toLong(),
                "themeId" to row.getString(13),
            )
        },
        "acceptance-pf-accepted-v1", "acceptance-pf-failed-v1", "acceptance-pf-rejected-v1", "acceptance-pf-running-v1",
        -920002L, -920001L,
        "acceptance-pf-accessible-cycle-status-v1", "acceptance-pf-deterministic-yield-link-v1",
    )

    private fun readDeliveries(): List<Map<String, Any?>> = jdbc.query(
        """select id, product_slug, candidate_id, iteration_id, workspace_run_id, workspace_commit_sha,
                  artifact_path, idempotency_key, external_story_key, status, remote_phase, error_message,
                  created_at, delivered_at, completed_at, last_reconciled_at, evaluation_workspace_run_id,
                  evaluated_at, confirmed_deployed, deployed_at
             from story_delivery
            where iteration_id in (?, ?, ?, ?) or id in (?, ?) or candidate_id in (?, ?)
               or idempotency_key in (?, ?) or external_story_key in (?, ?)
            order by id""".trimIndent(),
        { row, _ ->
            linkedMapOf(
                "id" to row.getLong(1), "productSlug" to row.getString(2), "candidateId" to row.getLong(3),
                "iterationId" to row.getString(4), "workspaceRunId" to row.getString(5),
                "workspaceCommitSha" to row.getString(6), "artifactPath" to row.getString(7),
                "idempotencyKey" to row.getString(8), "externalStoryKey" to row.getString(9),
                "status" to row.getString(10), "remotePhase" to row.getString(11), "errorMessage" to row.getString(12),
                "createdAt" to instant(row.getTimestamp(13)), "deliveredAt" to instant(row.getTimestamp(14)),
                "completedAt" to instant(row.getTimestamp(15)), "lastReconciledAt" to instant(row.getTimestamp(16)),
                "evaluationWorkspaceRunId" to row.getString(17), "evaluatedAt" to instant(row.getTimestamp(18)),
                "confirmedDeployed" to row.getBoolean(19), "deployedAt" to instant(row.getTimestamp(20)),
            )
        },
        "acceptance-pf-accepted-v1", "acceptance-pf-failed-v1", "acceptance-pf-rejected-v1", "acceptance-pf-running-v1",
        -920002L, -920001L, -920002L, -920001L,
        "acceptance-pf-delivery-accessible-cycle-status-v1", "acceptance-pf-delivery-deterministic-yield-link-v1",
        "SYNTH-PF-102", "SYNTH-PF-101",
    )

    @Suppress("LongMethod")
    private fun insertFixture(fixture: Map<String, Any?>) {
        rows(fixture, "iterations").forEach { row ->
            jdbc.update(
                """insert into shadow_iteration(
                    id, product_slug, sequence_number, focus, mode, status, current_agent_role,
                    critic_verdict, workspace_run_id, workspace_pull_request_url, workspace_commit_sha,
                    error_message, summary, created_at, started_at, completed_at, resume_from_iteration_id,
                    generated_candidate_count, accepted_candidate_count, revision_rounds, outcome_reason
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
                row["id"], row["productSlug"], row["sequenceNumber"], row["focus"], row["mode"], row["status"],
                row["currentAgentRole"], row["criticVerdict"], row["workspaceRunId"], row["workspacePullRequestUrl"],
                row["workspaceCommitSha"], row["errorMessage"], row["summary"], timestamp(row["createdAt"]),
                timestamp(row["startedAt"]), timestamp(row["completedAt"]), row["resumeFromIterationId"],
                row["generatedCandidateCount"], row["acceptedCandidateCount"], row["revisionRounds"], row["outcomeReason"],
            )
        }
        rows(fixture, "decisions").forEach { row ->
            jdbc.update(
                "insert into shadow_iteration_decision(iteration_id, actor_type, mechanism, reason_code, decided_at) values (?, ?, ?, ?, ?)",
                row["iterationId"], row["actorType"], row["mechanism"], row["reasonCode"], timestamp(row["decidedAt"]),
            )
        }
        rows(fixture, "candidates").forEach { row ->
            jdbc.update(
                """insert into story_candidate(
                    id, product_slug, title, description, status, created_at, iteration_id, fingerprint,
                    acceptance_criteria, critic_status, critic_reason, duplicate_of_id, theme_id
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
                row["id"], row["productSlug"], row["title"], row["description"], row["status"], timestamp(row["createdAt"]),
                row["iterationId"], row["fingerprint"], row["acceptanceCriteria"], row["criticStatus"], row["criticReason"],
                row["duplicateOfId"], row["themeId"],
            )
        }
        rows(fixture, "deliveries").forEach { row ->
            jdbc.update(
                """insert into story_delivery(
                    id, product_slug, candidate_id, iteration_id, workspace_run_id, workspace_commit_sha,
                    artifact_path, idempotency_key, external_story_key, status, remote_phase, error_message,
                    created_at, delivered_at, completed_at, last_reconciled_at, evaluation_workspace_run_id,
                    evaluated_at, confirmed_deployed, deployed_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
                row["id"], row["productSlug"], row["candidateId"], row["iterationId"], row["workspaceRunId"],
                row["workspaceCommitSha"], row["artifactPath"], row["idempotencyKey"], row["externalStoryKey"],
                row["status"], row["remotePhase"], row["errorMessage"], timestamp(row["createdAt"]),
                timestamp(row["deliveredAt"]), timestamp(row["completedAt"]), timestamp(row["lastReconciledAt"]),
                row["evaluationWorkspaceRunId"], timestamp(row["evaluatedAt"]), row["confirmedDeployed"], timestamp(row["deployedAt"]),
            )
        }
        jdbc.update(
            "insert into preview_seed_history(seed_key, pr_number) values (?, ?)",
            AcceptanceFixtureCatalog.SEED_KEY,
            0,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun rows(fixture: Map<String, Any?>, key: String): List<Map<String, Any?>> =
        fixture.getValue(key) as List<Map<String, Any?>>

    private fun timestamp(value: Any?): Timestamp? = (value as String?)?.let { Timestamp.from(Instant.parse(it)) }
    private fun instant(value: Timestamp?): String? = value?.toInstant()?.toString()
}
