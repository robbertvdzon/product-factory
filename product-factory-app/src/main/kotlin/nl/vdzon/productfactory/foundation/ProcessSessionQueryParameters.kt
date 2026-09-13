package nl.vdzon.productfactory.foundation

import nl.vdzon.productfactory.api.shared.ProcessSessionFilter
import nl.vdzon.productfactory.api.shared.ProcessSessionStatus
import nl.vdzon.productfactory.api.shared.ProductId
import java.time.Instant

/** Bovengrens voor `limit` op lijst-endpoints. */
const val MAX_LIST_LIMIT = 500

/** Bouwt het sessiefilter voor de HTTP-lijstendpoints; zonder optionele parameters blijft het de volledige lijst. */
fun processSessionFilter(
    productId: String,
    statuses: Set<ProcessSessionStatus>?,
    limit: Int?,
    before: Instant?,
    excludeNoOps: Boolean?,
) = ProcessSessionFilter(
    productId = ProductId(productId),
    statuses = statuses.orEmpty(),
    limit = limit?.coerceIn(1, MAX_LIST_LIMIT),
    before = before,
    excludeNoOps = excludeNoOps ?: false,
)
