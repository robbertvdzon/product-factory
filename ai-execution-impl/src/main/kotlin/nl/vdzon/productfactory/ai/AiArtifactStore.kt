package nl.vdzon.productfactory.ai

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.HexFormat

interface AiArtifactStore {
    fun partialSize(storageKey: String): Long
    fun append(storageKey: String): OutputStream
    fun verifyAndPromote(storageKey: String, expectedSize: Long, expectedSha256: String)
    fun open(storageKey: String, offset: Long = 0): InputStream
    fun readySize(storageKey: String): Long
    fun delete(storageKey: String)
}

@Component
class FileAiArtifactStore(
    @Value("\${PF_AI_ARTIFACT_STORAGE_PATH:./var/ai-artifacts}") rootPath: String,
) : AiArtifactStore {
    private val root = Path.of(rootPath).toAbsolutePath().normalize().also { Files.createDirectories(it) }

    override fun partialSize(storageKey: String): Long = partial(storageKey).let { if (Files.exists(it)) Files.size(it) else 0L }

    override fun append(storageKey: String): OutputStream {
        val path = partial(storageKey)
        Files.createDirectories(path.parent)
        return Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    override fun verifyAndPromote(storageKey: String, expectedSize: Long, expectedSha256: String) {
        val source = partial(storageKey)
        require(Files.exists(source) && Files.size(source) == expectedSize) { "De duurzame artifactkopie heeft een ongeldige grootte." }
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(source).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        require(HexFormat.of().formatHex(digest.digest()) == expectedSha256) { "De duurzame artifactkopie heeft een ongeldige hash." }
        val target = ready(storageKey)
        Files.createDirectories(target.parent)
        runCatching { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            .getOrElse { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING) }
    }

    override fun open(storageKey: String, offset: Long): InputStream {
        require(offset >= 0) { "Ongeldige artifactoffset." }
        return Files.newInputStream(ready(storageKey)).also { input ->
            var remaining = offset
            while (remaining > 0) {
                val skipped = input.skip(remaining)
                if (skipped <= 0) {
                    input.close()
                    throw IllegalArgumentException("Artifactoffset valt buiten het bestand.")
                }
                remaining -= skipped
            }
        }
    }

    override fun readySize(storageKey: String): Long = Files.size(ready(storageKey))

    override fun delete(storageKey: String) {
        Files.deleteIfExists(partial(storageKey))
        Files.deleteIfExists(ready(storageKey))
    }

    private fun partial(storageKey: String): Path = resolve(storageKey).let { it.resolveSibling("${it.fileName}.part") }
    private fun ready(storageKey: String) = resolve(storageKey)
    private fun resolve(storageKey: String): Path = root.resolve(storageKey).normalize().also {
        require(it.startsWith(root)) { "Ongeldige artifactopslagsleutel." }
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
    }
}
