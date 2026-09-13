package nl.vdzon.productfactory.progress

import nl.vdzon.productfactory.api.shared.EpicId
import nl.vdzon.productfactory.api.shared.ProductId
import org.springframework.web.bind.annotation.*

/**
 * Samengestelde leesmodellen voor de frontend. Autorisatie loopt via ProductAuthorizationInterceptor:
 * /api/products/{productId}/... en /api/epics/{epicId}/... worden daar al naar het product herleid.
 */
@RestController
@RequestMapping("/api")
class ProgressController(
    private val liveOverview: ProductLiveOverviewService,
    private val epicProgress: EpicProgressService,
) {
    @GetMapping("/products/{productId}/live")
    fun live(@PathVariable productId: String) = liveOverview.overview(ProductId(productId))

    @GetMapping("/epics/{epicId}/progress")
    fun progress(@PathVariable epicId: String) = epicProgress.progress(EpicId(epicId))
}
