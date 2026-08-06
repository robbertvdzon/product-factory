package nl.vdzon.productfactory.web

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class VersionController(@Value("\${PF_BUILD_SHA:development}") private val buildSha: String) {
    @GetMapping("/api/version") fun version() = mapOf("application" to "product-factory", "version" to buildSha)
}
