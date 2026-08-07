package nl.vdzon.productfactory.autonomy.api

interface StoryDeliveryPort {
    fun deliverCandidate(productSlug: String, candidateId: Long)
}
