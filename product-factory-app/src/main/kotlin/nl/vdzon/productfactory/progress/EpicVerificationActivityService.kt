package nl.vdzon.productfactory.progress

import nl.vdzon.productfactory.api.design.EpicDetails
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant

/** The latest work for this exact epic version determines whether a tester is actually running. */
@Service
class EpicVerificationActivityService(private val jdbc: JdbcTemplate) {
    fun current(epic: EpicDetails): EpicVerificationActivity {
        val work = jdbc.query(
            """SELECT w.id,w.status,w.blocked_reason,w.retry_after,w.updated_at,
                    s.status AS session_status,t.status AS task_status
                FROM pf_quality_work_item w
                LEFT JOIN pf_quality_process_session s ON s.id=w.claimed_by_session_id AND s.product_id=w.product_id
                    AND s.active_product_id=w.product_id AND s.finished_at IS NULL
                LEFT JOIN pf_ai_task t ON t.id=s.current_ai_task_id AND t.product_id=w.product_id
                    AND t.requester_session_id=s.id
                WHERE w.product_id=? AND w.type='VERIFY_EPIC' AND w.source_type='EPIC'
                    AND w.source_id=? AND w.source_version=?
                ORDER BY w.created_at DESC,w.id DESC LIMIT 1""",
            { rs, _ -> Work(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4)?.toInstant(),
                rs.getTimestamp(5).toInstant(), rs.getString(6), rs.getString(7)) },
            epic.productId.value, epic.id.value, epic.version,
        ).singleOrNull()
        fun result(state: EpicActivityState, label: String, detail: String, next: String? = null) =
            EpicVerificationActivity(state, label, detail, next, work?.updatedAt ?: epic.updatedAt, work?.id)

        if (work?.status in setOf("BLOCKED", "FAILED")) {
            val reason = work?.reason?.takeIf { it.isNotBlank() } ?: "De kwaliteitscontrole kan nog niet verder."
            return if (work?.retryAfter != null) result(
                EpicActivityState.BLOCKED, "Verificatie wacht op herpoging", reason,
                "Een nieuwe poging is gepland vanaf ${work.retryAfter}. Er draait nu geen test.",
            ) else result(
                EpicActivityState.BLOCKED, "Verificatie geblokkeerd", reason,
                "Herstel de testvoorwaarde en hervat de verificatie. Er is geen automatische herhaling gepland.",
            )
        }
        if (work?.status == "IN_PROGRESS") {
            if (work.sessionStatus in setOf("BLOCKED", "FAILED") || work.taskStatus in setOf("FAILED", "CANCELLED"))
                return result(EpicActivityState.BLOCKED, "Verificatie onderbroken", "De huidige testopdracht is onderbroken.",
                    "De Product Factory moet de storing verwerken voordat de verificatie verder kan.")
            if (work.taskStatus == "RUNNING") return result(EpicActivityState.WORKING, "Product Factory test de epic",
                "De tester voert de kwaliteitscontrole uit.", "Na een geslaagde controle kan de epic worden afgerond.")
            if (work.sessionStatus == "RUNNING") return result(EpicActivityState.WORKING, "Product Factory verwerkt de verificatie",
                "De Product Factory bereidt de controle voor of verwerkt het testresultaat.")
            if (work.taskStatus == "SUCCEEDED") return result(EpicActivityState.QUEUED, "Testresultaat wacht op verwerking",
                "De tester is klaar. De Product Factory moet het resultaat nog verwerken.")
            if (work.taskStatus == "WAITING_FOR_WORKER") return result(EpicActivityState.QUEUED, "Verificatie wacht op AI-capaciteit",
                "De testopdracht staat klaar en wacht op een beschikbare worker.")
        }
        return result(EpicActivityState.QUEUED, "Verificatie staat klaar", "De kwaliteitscontrole wacht tot de Product Factory de volgende stap oppakt.")
    }

    private data class Work(val id: String, val status: String, val reason: String?, val retryAfter: Instant?,
        val updatedAt: Instant, val sessionStatus: String?, val taskStatus: String?)
}

data class EpicVerificationActivity(
    val state: EpicActivityState,
    val label: String,
    val detail: String,
    val next: String?,
    val since: Instant,
    val reference: String?,
)
