package nl.vdzon.productfactory.foundation

import nl.vdzon.productfactory.api.foundation.BuildIdentity
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/version")
class VersionController(
    private val buildIdentityService: BuildIdentityService,
) {
    @GetMapping
    fun version(): ResponseEntity<BuildIdentity> = ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(buildIdentityService.identity)
}
