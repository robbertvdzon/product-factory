package nl.vdzon.productfactory.common.config

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EnvironmentFilesTest {
    @Test fun `layers files and environment without executing content`() {
        val directory = Files.createTempDirectory("pf-config")
        directory.resolve("properties.default.env").writeText("PF_VALUE=default\nPF_LITERAL=$(touch /tmp/never)\n")
        directory.resolve("properties.env").writeText("PF_VALUE=local\n")
        directory.resolve("secrets.env").writeText("PF_SECRET=secret\n")
        val values = EnvironmentFiles.load(directory, mapOf("PF_VALUE" to "environment"))
        assertEquals("environment", values["PF_VALUE"])
        assertEquals("secret", values["PF_SECRET"])
        assertEquals("$(touch /tmp/never)", values["PF_LITERAL"])
    }

    @Test fun `rejects malformed input and only reports location`() {
        val file = Files.createTempFile("pf-invalid", ".env")
        file.writeText("not-valid\n")
        assertFailsWith<IllegalArgumentException> { EnvironmentFiles.parse(file) }
    }
}
