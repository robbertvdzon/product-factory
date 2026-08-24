package nl.vdzon.productfactory.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class EnvironmentFilesTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `latere configuratiebronnen hebben aantoonbaar voorrang`() {
        Files.writeString(directory.resolve("properties.default.env"), "PF_VALUE=default\nPF_DEFAULT_ONLY=present\n")
        Files.writeString(directory.resolve("properties.env"), "PF_VALUE=override\n")
        Files.writeString(directory.resolve("secrets.env"), "PF_VALUE=secret\nPF_SECRET_ONLY=present\n")

        val result = EnvironmentFiles.load(directory, mapOf("PF_VALUE" to "process"))

        assertThat(result).containsEntry("PF_VALUE", "process")
        assertThat(result).containsEntry("PF_DEFAULT_ONLY", "present")
        assertThat(result).containsEntry("PF_SECRET_ONLY", "present")
    }

    @Test
    fun `zonder proceswaarde wint secrets van overrides en defaults`() {
        Files.writeString(directory.resolve("properties.default.env"), "PF_VALUE=default\n")
        Files.writeString(directory.resolve("properties.env"), "PF_VALUE=override\n")
        Files.writeString(directory.resolve("secrets.env"), "PF_VALUE=secret\n")

        assertThat(EnvironmentFiles.load(directory, emptyMap())).containsEntry("PF_VALUE", "secret")
    }
}
