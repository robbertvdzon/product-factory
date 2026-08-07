package nl.vdzon.productfactory.autonomy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant

class DatabaseTimestampTest {
    @Test
    fun `scheduler day boundaries use a timestamp without timezone JDBC value`() {
        val instant = Instant.parse("2026-08-07T00:00:00Z")

        val value = databaseTimestamp(instant)

        assertThat(value).isInstanceOf(Timestamp::class.java)
        assertThat(value.toInstant()).isEqualTo(instant)
    }
}
