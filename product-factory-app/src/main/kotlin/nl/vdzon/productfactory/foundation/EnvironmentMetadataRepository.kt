package nl.vdzon.productfactory.foundation

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class EnvironmentMetadataRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun find(key: String): String? = jdbcTemplate.query(
        "SELECT metadata_value FROM environment_metadata WHERE metadata_key = ?",
        { resultSet, _ -> resultSet.getString(1) },
        key,
    ).singleOrNull()

    fun insertIfAbsent(key: String, value: String, now: Instant): Boolean = jdbcTemplate.update(
        "INSERT INTO environment_metadata (metadata_key, metadata_value, updated_at) " +
            "SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM environment_metadata WHERE metadata_key = ?)",
        key,
        value,
        now,
        key,
    ) == 1
}
