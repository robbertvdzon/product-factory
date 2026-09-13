package nl.vdzon.productfactory.product

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

@Service
class ProductGovernanceApplicationService(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper,
    private val identities: ProductIdentityQuery, private val clock: Clock) : ProductGovernanceService {
    override fun getPolicy(productId: ProductId): ProductGovernancePolicy = jdbc.query(
        "SELECT policy_json FROM pf_product_governance_policy WHERE product_id=?",
        { rs, _ -> mapper.readValue(rs.getString(1), ProductGovernancePolicy::class.java) }, productId.value,
    ).singleOrNull() ?: ProductGovernancePolicy(productId)

    @Transactional
    override fun updatePolicy(command: UpdateGovernancePolicyCommand): ProductGovernancePolicy {
        val p = command.policy
        val user = identities.getIdentity(command.userId)
        val architect = user.active && user.actingRole == ActingRole.ARCHITECT && user.memberships.any {
            it.productId == p.productId && it.role == ProductMembershipRole.ARCHITECT && it.status == MembershipStatus.ACTIVE
        }
        val factory = user.active && user.actingRole == ActingRole.FACTORY_OWNER && GlobalRole.FACTORY_OWNER in user.effectiveGlobalRoles
        if (!architect && !factory) throw InvalidCommand("Alleen de architect of factorybeheer kan deze configuratie wijzigen.")
        require(command.idempotencyKey.length in 1..200)
        val fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(command)))
        jdbc.query("SELECT fingerprint,policy_json FROM pf_product_governance_history WHERE idempotency_key=?",
            { rs, _ -> rs.getString(1) to rs.getString(2) }, command.idempotencyKey).singleOrNull()?.let {
            if (it.first != fingerprint) throw IdempotencyConflict("Deze sleutel is al gebruikt.")
            return mapper.readValue(it.second, ProductGovernancePolicy::class.java)
        }
        // Serializes initial configuration as well as edits without a missing-row race.
        if (jdbc.query("SELECT product_id FROM pf_product WHERE product_id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, p.productId.value).isEmpty()) throw AggregateNotFound("Product bestaat niet.")
        val current = getPolicy(p.productId)
        if (current.version != p.version) throw VersionConflict("Productafspraken zijn intussen gewijzigd.")
        if (!architect && p.copy(configured=current.configured, productOwnerMode=current.productOwnerMode, architectMode=current.architectMode) != current)
            throw InvalidCommand("Productafspraken en budgetten worden uitsluitend door de architect beheerd.")
        if (!factory && (p.productOwnerMode != current.productOwnerMode || p.architectMode != current.architectMode))
            throw InvalidCommand("Besturingsrollen worden door factorybeheer toegewezen.")
        require(p.architectureRules.length <= 20000 && p.productAiRules.length <= 20000)
        require(p.maximumAdditionalJobsPerDay == null || p.maximumAdditionalJobsPerDay!! >= 0)
        require(p.maximumGrowthPercent == null || p.maximumGrowthPercent!! >= 0)
        p.monthlyProductBudgetEuro?.let { require(it.toBigDecimalOrNull()?.signum()?.let { n -> n >= 0 } == true) }
        val next = p.copy(configured=true, version=current.version+1)
        val json = mapper.writeValueAsString(next)
        if (current.version == 0L) jdbc.update("INSERT INTO pf_product_governance_policy(product_id,policy_json,version,updated_by,updated_at) VALUES (?,?,?,?,?)",p.productId.value,json,next.version,user.id.value,clock.instant())
        else jdbc.update("UPDATE pf_product_governance_policy SET policy_json=?,version=?,updated_by=?,updated_at=? WHERE product_id=?",json,next.version,user.id.value,clock.instant(),p.productId.value)
        jdbc.update("INSERT INTO pf_product_governance_history(product_id,version,policy_json,updated_by,updated_at,idempotency_key,fingerprint) VALUES (?,?,?,?,?,?,?)",p.productId.value,next.version,json,user.id.value,clock.instant(),command.idempotencyKey,fingerprint)
        return next
    }
}
