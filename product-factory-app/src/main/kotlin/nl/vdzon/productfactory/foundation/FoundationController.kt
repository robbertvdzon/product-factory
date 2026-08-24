package nl.vdzon.productfactory.foundation

import nl.vdzon.productfactory.api.foundation.FoundationState
import nl.vdzon.productfactory.api.foundation.FoundationStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/foundation")
class FoundationController {
    @GetMapping
    fun getFoundation(): FoundationStatus = FoundationStatus(
        application = "Product Factory",
        state = FoundationState.READY,
        message = "De technische fundering is actief; functionele processen volgen later.",
    )
}
