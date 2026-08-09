package nl.vdzon.productfactory.product

import nl.vdzon.productfactory.product.api.ProductCatalog
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
class ProjectsYamlReconcilerTest(@Autowired private val catalog: ProductCatalog) {
    // De context is al gestart voordat deze test draait, dus ApplicationReadyEvent (en daarmee de
    // reconciler) is al afgevuurd met de echte productfactory/src/main/resources/projects.yaml.
    @Test
    fun `startup applies the fixed repository and acceptance url from projects yaml`() {
        val product = catalog.requireProduct("hkh-autopilot")
        assertEquals("robbertvdzon/hkh-autopilot", product.targetRepositoryName)
        assertEquals("https://hkh-autopilot-acceptance.vdzonsoftware.nl", product.acceptanceUrl)
    }

    @Test
    fun `reconcileFixedFields rejects an invalid repository name`() {
        assertFailsWith<IllegalArgumentException> {
            catalog.reconcileFixedFields("hkh-autopilot", "niet een geldige repo naam met spaties", null, null)
        }
    }

    @Test
    fun `reconcileFixedFields rejects an invalid admin url`() {
        assertFailsWith<IllegalArgumentException> {
            catalog.reconcileFixedFields("hkh-autopilot", null, null, "niet-een-url")
        }
    }

    @Test
    fun `reconcileFixedFields sets and keeps the admin url`() {
        val updated = catalog.reconcileFixedFields("hkh-autopilot", null, null, "https://hkh-autopilot.vdzonsoftware.nl/admin")
        assertEquals("https://hkh-autopilot.vdzonsoftware.nl/admin", updated.adminUrl)

        val kept = catalog.reconcileFixedFields("hkh-autopilot", null, null, null)
        assertEquals("https://hkh-autopilot.vdzonsoftware.nl/admin", kept.adminUrl)
    }

    @Test
    fun `reconcileFixedFields keeps the current value when a field is not supplied`() {
        val before = catalog.requireProduct("hkh-autopilot")
        val after = catalog.reconcileFixedFields("hkh-autopilot", null, null, null)
        assertEquals(before.targetRepositoryName, after.targetRepositoryName)
        assertEquals(before.acceptanceUrl, after.acceptanceUrl)
        assertEquals(before.adminUrl, after.adminUrl)
    }
}
