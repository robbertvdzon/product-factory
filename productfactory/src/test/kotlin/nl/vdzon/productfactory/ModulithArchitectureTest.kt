package nl.vdzon.productfactory

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModulithArchitectureTest {
    @Test fun `module boundaries are valid`() {
        ApplicationModules.of(ProductFactoryApplication::class.java).verify()
    }

    @Test fun `every business module is explicit and fail closed`() {
        val root = Path.of("src/main/kotlin/nl/vdzon/productfactory")
        val modules = listOf("product", "iteration", "research", "opportunity", "ux", "decision", "story", "monitoring", "humanaction", "agentruntime", "knowledge", "workspace", "dashboard", "config", "support", "web", "preview")
        modules.forEach { module ->
            val text = root.resolve(module).resolve("package-info.java").readText()
            assertTrue(text.contains("@org.springframework.modulith.ApplicationModule"), "$module mist modulemetadata")
            assertTrue(text.contains("allowedDependencies ="), "$module heeft geen expliciete dependencygrens")
            assertFalse(text.contains("*"), "$module gebruikt een wildcard")
        }
        Files.walk(root).use { paths ->
            assertFalse(paths.filter { it.fileName.toString() == "package-info.java" }.anyMatch { it.readText().contains("allowedDependencies = {\"*\"}") })
        }
    }
}
