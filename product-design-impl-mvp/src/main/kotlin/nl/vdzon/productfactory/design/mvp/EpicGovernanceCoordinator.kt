package nl.vdzon.productfactory.design.mvp

import nl.vdzon.productfactory.api.shared.EpicId
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory

/** Re-evaluates pending content after a mandate changes, without invoking an AI task. */
@Component
class EpicGovernanceCoordinator(private val jdbc: JdbcTemplate, private val governance: EpicGovernanceApplicationService,
    @Value("\${PF_AI_RUNTIME_SCHEDULING_ENABLED:false}") private val enabled: Boolean) {
    @Scheduled(fixedDelayString = "\${PF_GOVERNANCE_RECONCILE_DELAY_MS:10000}")
    fun reconcile() {
        if (!enabled) return
        val ids=jdbc.query("""SELECT e.id FROM pf_epic e JOIN pf_epic_version v ON v.epic_id=e.id AND v.version=e.current_version
            WHERE v.status NOT IN ('COMPLETED','NOT_SUCCESSFUL','CANCELLED','WITHDRAWN','SUPERSEDED')""", {rs,_->EpicId(rs.getString(1))})
        ids.forEach { id -> runCatching { governance.recordAutomaticReviews(id) }.onFailure {
            LoggerFactory.getLogger(javaClass).warn("governance_reconcile_failed epicId={} type={}", id.value, it.javaClass.simpleName)
        } }
    }
}
