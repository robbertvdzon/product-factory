package nl.vdzon.productfactory.foundation

import nl.vdzon.productfactory.api.foundation.BuildIdentity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class BuildIdentityService(
    @Value("\${PF_APPLICATION_VERSION:}") applicationVersion: String,
    @Value("\${PF_GIT_REVISION:}") gitRevision: String,
    @Value("\${PF_BUILD_TIME:}") buildTime: String,
    @Value("\${PF_ENVIRONMENT:}") environment: String,
) {
    final val identity: BuildIdentity

    init {
        val validVersion = applicationVersion.takeIf { SEMANTIC_VERSION.matches(it) } ?: UNKNOWN
        val validRevision = gitRevision.lowercase().takeIf { GIT_REVISION.matches(it) } ?: UNKNOWN
        val validBuildTime = buildTime.takeIf(::isUtcInstant) ?: UNKNOWN
        val validEnvironment = environment.takeIf { it in ENVIRONMENTS } ?: UNKNOWN
        val shortRevision = validRevision.takeIf { it != UNKNOWN }?.take(12) ?: UNKNOWN
        identity = BuildIdentity(
            applicationVersion = validVersion,
            apiVersion = API_VERSION,
            gitRevision = validRevision,
            buildTime = validBuildTime,
            environment = validEnvironment,
            backendBuildIdentity = if (validVersion == UNKNOWN || shortRevision == UNKNOWN) {
                UNKNOWN
            } else {
                "$validVersion+$shortRevision"
            },
        )
    }

    private fun isUtcInstant(value: String): Boolean =
        value.endsWith("Z") && runCatching { Instant.parse(value) }.isSuccess

    companion object {
        const val UNKNOWN = "Onbekend"
        const val API_VERSION = "1"
        private val SEMANTIC_VERSION = Regex("[0-9]+\\.[0-9]+\\.[0-9]+")
        private val GIT_REVISION = Regex("[0-9a-f]{40}")
        private val ENVIRONMENTS = setOf("local", "acceptance", "production")
    }
}
