package nl.vdzon.productfactory.config

import java.nio.file.Files
import java.nio.file.Path

object EnvironmentFiles {
    private val validKey = Regex("PF_[A-Z0-9_]+")
    private val fileOrder = listOf("properties.default.env", "properties.env", "secrets.env")

    fun load(
        directory: Path,
        processEnvironment: Map<String, String> = System.getenv(),
    ): Map<String, String> {
        val merged = linkedMapOf<String, String>()
        fileOrder.forEach { filename ->
            val file = directory.resolve(filename)
            if (Files.isRegularFile(file)) {
                parse(file).forEach { (key, value) -> merged[key] = value }
            }
        }
        processEnvironment
            .filterKeys { it.startsWith("PF_") }
            .forEach { (key, value) ->
                require(validKey.matches(key)) { "Ongeldige Product Factory-configuratiesleutel: $key" }
                merged[key] = value
            }
        return merged.toMap()
    }

    private fun parse(file: Path): Map<String, String> = buildMap {
        Files.readAllLines(file).forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val separator = line.indexOf('=')
            require(separator > 0) { "Ongeldige configuratieregel in ${file.fileName}:${index + 1}" }
            val key = line.substring(0, separator).trim()
            require(validKey.matches(key)) { "Ongeldige Product Factory-configuratiesleutel in ${file.fileName}:${index + 1}" }
            put(key, line.substring(separator + 1))
        }
    }
}
