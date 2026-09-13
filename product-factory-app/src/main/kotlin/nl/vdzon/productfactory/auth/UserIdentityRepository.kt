package nl.vdzon.productfactory.auth

import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.api.advisor.*
import nl.vdzon.productfactory.api.shared.*
import nl.vdzon.productfactory.api.design.ProductIdentityQuery
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

@Repository
class UserIdentityRepository(
    private val jdbc: JdbcTemplate,
    private val clock: Clock,
    private val mapper: ObjectMapper,
) : ProductIdentityQuery {
    override fun getIdentity(userId: UserId) = get(userId)
    override fun productMembers(productId: ProductId, role: ProductMembershipRole): List<UserDetails> = jdbc.query(
        "SELECT user_id FROM pf_product_membership WHERE product_id=? AND role=? AND status='ACTIVE'",
        { rs, _ -> UserId(rs.getString(1)) }, productId.value, role.name,
    ).map(::get).filter { it.active }

    @Transactional
    fun resolveOrCreate(email: String, factoryOwner: Boolean): UserDetails {
        val normalized = normalizeEmail(email)
        var id = jdbc.query(
            "SELECT user_id FROM pf_user_account WHERE normalized_email=?",
            { rs, _ -> rs.getString(1) }, normalized,
        ).singleOrNull()
        if (id == null) {
            id = UUID.randomUUID().toString()
            jdbc.update(
                "INSERT INTO pf_user_account(user_id,normalized_email,active,created_at,updated_at) VALUES (?,?,TRUE,?,?)",
                id, normalized, clock.instant(), clock.instant(),
            )
        }
        if (factoryOwner) {
            val count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pf_user_global_role WHERE user_id=? AND role='FACTORY_OWNER'",
                Long::class.java, id,
            ) ?: 0
            if (count == 0L) jdbc.update(
                "INSERT INTO pf_user_global_role(user_id,role,granted_at,granted_by) VALUES (?,'FACTORY_OWNER',?,?)",
                id, clock.instant(), id,
            )

        }
        return get(UserId(id))
    }

    fun get(userId: UserId): UserDetails {
        val base = jdbc.query(
            "SELECT normalized_email,display_name,active,acting_role FROM pf_user_account WHERE user_id=?",
            { rs, _ -> listOf(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getString(4)) }, userId.value,
        ).singleOrNull() ?: throw IllegalArgumentException("Onbekende gebruiker.")
        val roles = jdbc.query(
            "SELECT role FROM pf_user_global_role WHERE user_id=?",
            { rs, _ -> GlobalRole.valueOf(rs.getString(1)) }, userId.value,
        ).toSet()
        val memberships = jdbc.query(
            """SELECT product_id,role,status,granted_at,revoked_at,revoke_reason,version
                FROM pf_product_membership WHERE user_id=? ORDER BY product_id""".trimIndent(),
            { rs, _ -> ProductMembershipDetails(
                ProductId(rs.getString(1)), ProductMembershipRole.valueOf(rs.getString(2)),
                MembershipStatus.valueOf(rs.getString(3)), rs.getTimestamp(4).toInstant(),
                rs.getTimestamp(5)?.toInstant(), rs.getString(6), rs.getLong(7),
            ) }, userId.value,
        )
        val available = buildSet {
            if (GlobalRole.FACTORY_OWNER in roles) add(ActingRole.FACTORY_OWNER)
            memberships.filter { it.status == MembershipStatus.ACTIVE }.forEach { add(ActingRole.valueOf(it.role.name)) }
        }
        val requested = (base[3] as String?)?.let { runCatching { ActingRole.valueOf(it) }.getOrNull() }
        val actingRole = requested?.takeIf { it in available } ?: available.firstOrNull() ?: ActingRole.PRODUCT_OWNER
        return UserDetails(userId, base[0] as String, base[1] as String?, base[2] as Boolean, roles, memberships, actingRole)
    }

    /** Een factory owner kan zelf kiezen of hij als factory owner of als product owner werkt. */
    fun setActingRole(userId: UserId, role: ActingRole) {
        val user = get(userId)
        val allowed = if (role == ActingRole.FACTORY_OWNER) GlobalRole.FACTORY_OWNER in user.globalRoles
            else user.memberships.any { it.role.name == role.name && it.status == MembershipStatus.ACTIVE }
        if (!allowed) throw InvalidCommand("Deze rol is niet aan je toegekend.")
        jdbc.update(
            "UPDATE pf_user_account SET acting_role=?,updated_at=? WHERE user_id=?",
            role.name, clock.instant(), userId.value,
        )
    }

    fun findAll(): List<UserDetails> = jdbc.query(
        "SELECT user_id FROM pf_user_account ORDER BY normalized_email",
        { rs, _ -> UserId(rs.getString(1)) },
    ).map(::get)

    fun findByEmail(email: String): UserDetails? = jdbc.query(
        "SELECT user_id FROM pf_user_account WHERE normalized_email=?",
        { rs, _ -> UserId(rs.getString(1)) }, normalizeEmail(email),
    ).singleOrNull()?.let(::get)

    fun findMembershipHistory(): List<ProductMembershipHistoryDetails> = jdbc.query(
        """SELECT history_id,user_id,product_id,role,action,reason,actor_user_id,occurred_at
            FROM pf_product_membership_history ORDER BY occurred_at DESC""".trimIndent(),
        { rs, _ -> ProductMembershipHistoryDetails(
            rs.getString(1), UserId(rs.getString(2)), ProductId(rs.getString(3)), ProductMembershipRole.valueOf(rs.getString(4)),
            rs.getString(5), rs.getString(6), UserId(rs.getString(7)), rs.getTimestamp(8).toInstant(),
        ) },
    )

    @Transactional
    fun createForAdministration(email: String, idempotencyKey: String): UserDetails {
        val normalized = normalizeEmail(email)
        val requestFingerprint = fingerprint(listOf("CREATE_USER", normalized))
        replay(idempotencyKey, "CREATE_USER", requestFingerprint)?.let { return get(UserId(it)) }
        val user = resolveOrCreate(normalized, false)
        recordCommand(idempotencyKey, "CREATE_USER", requestFingerprint, user.id.value)
        return user
    }

    @Transactional
    fun grantProductOwner(
        userId: UserId,
        productId: ProductId,
        actor: UserId,
        expectedVersion: Long,
        idempotencyKey: String,
        role: ProductMembershipRole = ProductMembershipRole.PRODUCT_OWNER,
    ) {
        val requestFingerprint = fingerprint(listOf("GRANT_PRODUCT_OWNER", userId, productId, actor, expectedVersion, role))
        replay(idempotencyKey, "GRANT_PRODUCT_OWNER", requestFingerprint)?.let { return }
        val now = clock.instant()
        val current = jdbc.query(
            "SELECT status,version FROM pf_product_membership WHERE user_id=? AND product_id=? AND role='${role.name}'",
            { rs, _ -> rs.getString(1) to rs.getLong(2) }, userId.value, productId.value,
        ).singleOrNull()
        if ((current?.second ?: 0L) != expectedVersion) throw VersionConflict("Het productlidmaatschap is intussen gewijzigd.")
        if (current?.first == "ACTIVE") throw InvalidCommand("Het productlidmaatschap is al actief.")
        if (current == null) {
            jdbc.update(
                """INSERT INTO pf_product_membership(user_id,product_id,role,status,granted_at,granted_by,version)
                    VALUES (?,?,'${role.name}','ACTIVE',?,?,1)""".trimIndent(),
                userId.value, productId.value, now, actor.value,
            )
        } else {
            val changed = jdbc.update(
                """UPDATE pf_product_membership SET status='ACTIVE',granted_at=?,granted_by=?,revoked_at=NULL,
                    revoked_by=NULL,revoke_reason=NULL,version=version+1 WHERE user_id=? AND product_id=? AND role='${role.name}' AND version=?""".trimIndent(),
                now, actor.value, userId.value, productId.value, expectedVersion,
            )
            if (changed != 1) throw VersionConflict("Het productlidmaatschap is intussen gewijzigd.")
        }
        recordHistory(userId, productId, "GRANTED", null, actor, role)
        recordCommand(idempotencyKey, "GRANT_PRODUCT_OWNER", requestFingerprint, userId.value)
    }

    @Transactional
    fun revokeProductOwner(
        userId: UserId,
        productId: ProductId,
        reason: String,
        actor: UserId,
        expectedVersion: Long,
        idempotencyKey: String,
        role: ProductMembershipRole = ProductMembershipRole.PRODUCT_OWNER,
    ) {
        require(reason.isNotBlank()) { "Een reden voor intrekken is verplicht." }
        val requestFingerprint = fingerprint(listOf("REVOKE_PRODUCT_OWNER", userId, productId, reason.trim(), actor, expectedVersion, role))
        replay(idempotencyKey, "REVOKE_PRODUCT_OWNER", requestFingerprint)?.let { return }
        val changed = jdbc.update(
            """UPDATE pf_product_membership SET status='REVOKED',revoked_at=?,revoked_by=?,revoke_reason=?,version=version+1
                WHERE user_id=? AND product_id=? AND role='${role.name}' AND status='ACTIVE' AND version=?""".trimIndent(),
            clock.instant(), actor.value, reason.trim(), userId.value, productId.value, expectedVersion,
        )
        if (changed != 1) throw VersionConflict("Het productlidmaatschap is intussen gewijzigd of niet actief.")
        recordHistory(userId, productId, "REVOKED", reason.trim(), actor, role)
        recordCommand(idempotencyKey, "REVOKE_PRODUCT_OWNER", requestFingerprint, userId.value)
    }

    private fun recordHistory(userId: UserId, productId: ProductId, action: String, reason: String?, actor: UserId, role: ProductMembershipRole) {
        jdbc.update(
            """INSERT INTO pf_product_membership_history(history_id,user_id,product_id,role,action,reason,actor_user_id,occurred_at)
                VALUES (?,?,?,'${role.name}',?,?,?,?)""".trimIndent(),
            UUID.randomUUID().toString(), userId.value, productId.value, action, reason, actor.value, clock.instant(),
        )
    }

    private fun replay(key: String, type: String, requestFingerprint: String): String? {
        require(key.isNotBlank() && key.length <= 200) { "Ongeldige idempotentiesleutel." }
        val row = jdbc.query(
            "SELECT command_type,request_fingerprint,result_id FROM pf_user_command WHERE idempotency_key=?",
            { rs, _ -> Triple(rs.getString(1), rs.getString(2), rs.getString(3)) }, key,
        ).singleOrNull() ?: return null
        if (row.first != type || row.second != requestFingerprint) {
            throw IdempotencyConflict("Idempotentiesleutel is al voor een andere gebruikersopdracht gebruikt.")
        }
        return row.third
    }

    private fun recordCommand(key: String, type: String, requestFingerprint: String, result: String) = jdbc.update(
        "INSERT INTO pf_user_command(idempotency_key,command_type,request_fingerprint,result_id,applied_at) VALUES (?,?,?,?,?)",
        key, type, requestFingerprint, result, clock.instant(),
    )

    private fun fingerprint(value: Any): String = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(value)),
    )

    companion object {
        fun normalizeEmail(email: String): String = email.trim().lowercase().also {
            require(it.length in 3..320 && '@' in it) { "Ongeldig e-mailadres." }
        }
    }
}
