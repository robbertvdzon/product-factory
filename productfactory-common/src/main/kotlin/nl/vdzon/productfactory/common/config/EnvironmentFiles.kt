package nl.vdzon.productfactory.common.config

import java.nio.file.Files
import java.nio.file.Path

object EnvironmentFiles {
    private val keyPattern = Regex("[A-Z][A-Z0-9_]*")

    fun load(configDirectory: Path, processEnvironment: Map<String, String> = System.getenv()): Map<String, Any> {
        val merged = linkedMapOf<String, String>()
        listOf("properties.default.env", "properties.env", "secrets.env").forEach { name ->
            val path = configDirectory.resolve(name)
            if (Files.exists(path)) merged.putAll(parse(path))
        }
        processEnvironment.filterKeys { it.startsWith("PF_") }.forEach(merged::put)
        return merged
    }

    fun parse(path: Path): Map<String, String> = Files.readAllLines(path).mapIndexedNotNull { index, raw ->
        val line = raw.trim()
        if (line.isBlank() || line.startsWith("#")) return@mapIndexedNotNull null
        val separator = line.indexOf('=')
        require(separator > 0) { "Ongeldige configuratieregel ${path.fileName}:${index + 1}" }
        val key = line.substring(0, separator).trim()
        require(keyPattern.matches(key)) { "Ongeldige configuratiesleutel ${path.fileName}:${index + 1}" }
        key to line.substring(separator + 1).trim().removeSurrounding("\"").removeSurrounding("'")
    }.toMap()

    fun locate(start: Path = Path.of(System.getProperty("user.dir"))): Path {
        val explicit = System.getenv("PF_CONFIG_DIR")?.takeIf(String::isNotBlank)?.let(Path::of)
        if (explicit != null) return explicit.toAbsolutePath().normalize()
        return generateSequence(start.toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.exists(it.resolve("properties.default.env")) }
            ?: start.toAbsolutePath().normalize()
    }
}
