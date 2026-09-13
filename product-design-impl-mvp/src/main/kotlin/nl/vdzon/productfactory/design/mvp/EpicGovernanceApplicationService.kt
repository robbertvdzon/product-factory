package nl.vdzon.productfactory.design.mvp

import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.api.advisor.*
import nl.vdzon.productfactory.api.design.*
import nl.vdzon.productfactory.api.shared.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

@Service
class EpicGovernanceApplicationService(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper,
    private val products: nl.vdzon.productfactory.api.product.ProductQueryService,
    private val policies: ProductGovernanceService, private val identities: ProductIdentityQuery, private val clock: Clock) : EpicGovernanceService {
    data class Snapshot(val productId: ProductId, val version: Long, val content: Long, val impact: EpicImpactAssessment, val ready: Boolean, val status: String)
    fun snapshot(id: EpicId): Snapshot = jdbc.query(
        """SELECT e.product_id,e.current_version,v.content_version,v.impact_json,v.readiness_json,v.status
            FROM pf_epic e JOIN pf_epic_version v ON v.epic_id=e.id AND v.version=e.current_version WHERE e.id=?""",
        { rs, _ -> Snapshot(ProductId(rs.getString(1)),rs.getLong(2),rs.getLong(3),mapper.readValue(rs.getString(4),EpicImpactAssessment::class.java),
            mapper.readTree(rs.getString(5)).path("readyForPlanning").asBoolean(false),rs.getString(6)) },id.value,
    ).singleOrNull() ?: throw AggregateNotFound("Epic bestaat niet.")

    fun managed(id: EpicId): Boolean { val s=snapshot(id); return policies.getPolicy(s.productId).configured || s.impact.items.isNotEmpty() }

    override fun reviewState(epicId: EpicId): EpicReviewState {
        val s=snapshot(epicId); val policy=policies.getPolicy(s.productId)
        val records=jdbc.query("SELECT * FROM pf_epic_review WHERE epic_id=? ORDER BY review_sequence", { rs,_ -> EpicReviewRecord(
            rs.getString("id"),rs.getLong("content_version"),rs.getLong("policy_version"),ProductMembershipRole.valueOf(rs.getString("role")),
            rs.getString("actor_id"),ReviewDecision.valueOf(rs.getString("decision")),rs.getString("reason"),rs.getBoolean("automatic"),rs.getTimestamp("created_at").toInstant()) },epicId.value)
        val current=records.filter { it.contentVersion==s.content && it.policyVersion==policy.version }
        fun approved(role: ProductMembershipRole): Boolean {
            val last=current.lastOrNull { it.role==role } ?: return false
            if (last.decision != ReviewDecision.APPROVE) return false
            if (last.automatic) return true
            return runCatching { identities.getIdentity(UserId(last.actorId)) }.getOrNull()?.let { u -> u.active && u.memberships.any {
                it.productId==s.productId && it.role==role && it.status==MembershipStatus.ACTIVE
            } } == true
        }
        val missing=ImpactCategory.entries.take(6).any { c -> s.impact.items.none { it.category==c } }
        val unknown=missing || s.impact.items.any { it.level==ImpactLevel.UNKNOWN || it.summary.isBlank() || it.evidence.isEmpty() }
        val outside=s.impact.items.any { it.level==ImpactLevel.MATERIAL && it.category !in policy.automaticCategories }
        val ai=s.impact.productAi
        val aiOutside=ai.changed && (ai.estimatedAdditionalJobsPerDay?.let { it < 0 } == true ||
            ai.estimatedMonthlyCostEuro?.toBigDecimalOrNull()?.signum()?.let { it < 0 } == true ||ai.estimatedAdditionalJobsPerDay==null || policy.maximumAdditionalJobsPerDay==null ||
            ai.estimatedAdditionalJobsPerDay!!>policy.maximumAdditionalJobsPerDay!! ||
            ai.estimatedMonthlyCostEuro==null || policy.monthlyProductBudgetEuro==null ||
            ai.estimatedMonthlyCostEuro!!.toBigDecimalOrNull()==null ||
            ai.estimatedMonthlyCostEuro!!.toBigDecimal()>policy.monthlyProductBudgetEuro!!.toBigDecimal() ||
            (policy.maximumGrowthPercent!=null && (ai.estimatedGrowthPercent==null || ai.estimatedGrowthPercent!!>policy.maximumGrowthPercent!!)))
        val required=unknown || outside || aiOutside
        val po=approved(ProductMembershipRole.PRODUCT_OWNER)
        val arch=approved(ProductMembershipRole.ARCHITECT)
        val blockers=buildList {
            if (!policy.configured) add("Productrollen en mandaat moeten eerst worden ingesteld.")
            if (!s.ready) add("De functionele uitwerking of UX is nog niet gereed.")
            if (unknown) add("Architectuurimpact is nog niet volledig onderbouwd; onderzoek is nodig.")
            if (!po) add(if(policy.productOwnerMode==ResponsibilityMode.AI) "Automatische productbeoordeling wacht op een complete uitwerking." else "Functioneel akkoord van de product owner ontbreekt.")
            if (!arch) add(if(required) "Architectbeoordeling nodig voor technische impact of product-AI." else "Beoordeling tegen de productafspraken is nog niet vastgelegd.")
            current.groupBy { it.role }.values.mapNotNull { it.lastOrNull() }.filter { it.decision!=ReviewDecision.APPROVE }.forEach { add(it.reason) }
        }
        return EpicReviewState(s.content,policy.version,blockers.isEmpty(),po,arch,required,blockers,records)
    }

    /** Called in the publishing transaction; policy validation, not AI text, authorizes automatic work. */
    @Transactional
    fun recordAutomaticReviews(id: EpicId) {
        jdbc.query("SELECT id FROM pf_epic WHERE id=? FOR UPDATE",{rs,_->rs.getString(1)},id.value)
        val s=snapshot(id); val p=policies.getPolicy(s.productId)
        if (!p.configured || !s.ready || s.impact.items.isEmpty()) return
        val state=reviewState(id)
        if (p.productOwnerMode==ResponsibilityMode.AI && state.records.none { it.contentVersion==s.content && it.policyVersion==p.version && it.role==ProductMembershipRole.PRODUCT_OWNER })
            record(id,s,p.version,ProductMembershipRole.PRODUCT_OWNER,"PRODUCT_DESIGNER_MVP",ReviewDecision.APPROVE,"Complete functionele uitwerking en UX gevalideerd binnen de productopdracht.",true,"auto-po-${id.value}-${s.content}-${p.version}")
        if (!state.architectRequired && state.records.none { it.contentVersion==s.content && it.policyVersion==p.version && it.role==ProductMembershipRole.ARCHITECT })
            record(id,s,p.version,ProductMembershipRole.ARCHITECT,"PRODUCT_DESIGNER_MVP",ReviewDecision.APPROVE,"De onderbouwde impact past binnen de geversioneerde productafspraken.",true,"auto-arch-${id.value}-${s.content}-${p.version}")
    }

    @Transactional
    override fun review(command: ReviewEpicCommand) {
        val user=identities.getIdentity(command.userId)
        jdbc.query("SELECT id FROM pf_epic WHERE id=? FOR UPDATE",{rs,_->rs.getString(1)},command.epicId.value)
        val s=snapshot(command.epicId)
        if (!user.active || user.actingRole.name!=command.role.name || user.memberships.none { it.productId==s.productId && it.role==command.role && it.status==MembershipStatus.ACTIVE })
            throw InvalidCommand("Je hebt geen actieve bevoegdheid voor deze productrol.")
        val fingerprint=fingerprint(command)
        jdbc.query("SELECT fingerprint FROM pf_epic_review WHERE idempotency_key=?",{rs,_->rs.getString(1)},command.idempotencyKey).singleOrNull()?.let {
            if (it!=fingerprint) throw IdempotencyConflict("Deze sleutel is al voor een ander besluit gebruikt.")
            return
        }
        if (s.version!=command.expectedVersion) throw VersionConflict("De epic is gewijzigd. Bekijk de nieuwe versie.")
        require(command.reason.trim().length in 1..10000 && command.idempotencyKey.length in 1..200)
        val p=policies.getPolicy(s.productId)
        if (!p.configured) throw InvalidCommand("Stel eerst de productrollen en afspraken in.")
        if (command.decision==ReviewDecision.APPROVE && (!s.ready || s.status in setOf("NEEDS_REFINEMENT","NEEDS_RESEARCH","CANCELLED","WITHDRAWN"))) throw InvalidCommand("Deze epic is nog niet gereed voor goedkeuring.")
        if (command.decision==ReviewDecision.APPROVE && command.role==ProductMembershipRole.ARCHITECT &&
            (ImpactCategory.entries.take(6).any { c->s.impact.items.none { it.category==c } } || s.impact.items.any { it.level==ImpactLevel.UNKNOWN }))
            throw InvalidCommand("Onderzoek eerst de onbekende architectuurimpact.")
        record(command.epicId,s,p.version,command.role,user.id.value,command.decision,command.reason.trim(),false,command.idempotencyKey,fingerprint)
    }

    private fun record(id: EpicId,s: Snapshot,policyVersion: Long,role: ProductMembershipRole,actor: String,decision: ReviewDecision,reason: String,automatic: Boolean,key: String,hash: String=fingerprint(listOf(id,s.content,policyVersion,role,decision))) {
        jdbc.update("INSERT INTO pf_epic_review(id,epic_id,content_version,policy_version,role,actor_id,decision,reason,automatic,created_at,idempotency_key,fingerprint) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID().toString(),id.value,s.content,policyVersion,role.name,actor,decision.name,reason,automatic,clock.instant(),key,hash)
    }
    override fun canDispatch(epicId: EpicId, plannedVersion: Long): Boolean {
        val product=snapshot(epicId).productId
        if(products.findStakeholderQuestions(nl.vdzon.productfactory.api.product.StakeholderQuestionFilter(product)).any {
            it.status==nl.vdzon.productfactory.api.product.StakeholderQuestionStatus.OPEN &&
                (it.epicLinkId==epicId || it.linkedObjects.any { source->source.type=="EPIC" && source.id==epicId.value })
        }) return false
        if (!managed(epicId)) return true // Existing pre-governance packages retain their original lifecycle.
        val s=snapshot(epicId)
        val planned=jdbc.query("SELECT content_version FROM pf_epic_version WHERE epic_id=? AND version=?",{rs,_->rs.getLong(1)},epicId.value,plannedVersion).singleOrNull()
        return planned==s.content && s.status in setOf("IN_PLANNING","ACTIVE","VERIFYING") && reviewState(epicId).ready
    }
    private fun fingerprint(value: Any)=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(value)))
}
