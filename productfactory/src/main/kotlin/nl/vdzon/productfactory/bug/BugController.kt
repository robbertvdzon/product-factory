package nl.vdzon.productfactory.bug

import nl.vdzon.productfactory.bug.api.BugCatalog
import nl.vdzon.productfactory.contracts.BugView
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class UpdateBugRequest(val priority: String? = null, val status: String? = null)

@RestController
@RequestMapping("/api/products/{slug}/bugs")
class BugController(private val bugs: BugCatalog) {
    @GetMapping fun list(@PathVariable slug: String): List<BugView> = bugs.list(slug)
    @PutMapping("/{id}")
    fun update(@PathVariable slug: String, @PathVariable id: Long, @RequestBody request: UpdateBugRequest): BugView =
        bugs.updateManually(slug, id, request.priority, request.status)
}
