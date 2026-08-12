package nl.vdzon.productfactory.roadmap.api

import java.util.PriorityQueue
import kotlin.math.roundToInt

internal data class RoadmapEpicRecord(
    val id: String,
    val sequenceNumber: Int,
    val customerRank: Int,
    val processRank: Int,
    val status: String,
    val dependencyIds: Set<String>,
)

internal data class RankedRoadmapEpic(
    val record: RoadmapEpicRecord,
    val score: Int,
    val roadmapRank: Int,
)

/** Dependencies zijn hard; de 75/25-score kiest alleen tussen epics die al uitvoerbaar zijn. */
internal object RoadmapRanking {
    const val CUSTOMER_WEIGHT = 75
    const val PROCESS_WEIGHT = 25

    fun rank(epics: List<RoadmapEpicRecord>): List<RankedRoadmapEpic> {
        val byId = epics.associateBy { it.id }
        require(epics.all { epic -> epic.dependencyIds.all(byId::containsKey) }) {
            "Een epic verwijst naar een onbekende dependency"
        }

        val scores = epics.associate { epic -> epic.id to score(epic, epics.size) }
        val dependents = epics.associate { it.id to mutableListOf<RoadmapEpicRecord>() }
        val remaining = epics.associate { it.id to it.dependencyIds.size }.toMutableMap()
        epics.forEach { epic ->
            epic.dependencyIds.forEach { dependencyId -> dependents.getValue(dependencyId) += epic }
        }

        val ready = PriorityQueue(
            compareByDescending<RoadmapEpicRecord> { scores.getValue(it.id) }
                .thenBy { it.customerRank }
                .thenBy { it.processRank }
                .thenBy { it.sequenceNumber },
        )
        ready += epics.filter { remaining.getValue(it.id) == 0 }
        val ordered = mutableListOf<RoadmapEpicRecord>()
        while (ready.isNotEmpty()) {
            val next = ready.remove()
            ordered += next
            dependents.getValue(next.id).forEach { dependent ->
                val count = remaining.getValue(dependent.id) - 1
                remaining[dependent.id] = count
                if (count == 0) ready += dependent
            }
        }
        require(ordered.size == epics.size) { "Circulaire epic-afhankelijkheid is niet toegestaan" }
        return ordered.mapIndexed { index, epic ->
            RankedRoadmapEpic(epic, scores.getValue(epic.id), index + 1)
        }
    }

    private fun score(epic: RoadmapEpicRecord, total: Int): Int {
        val customer = rankPoints(epic.customerRank, total)
        val process = rankPoints(epic.processRank, total)
        return ((customer * CUSTOMER_WEIGHT + process * PROCESS_WEIGHT) / 100.0).roundToInt()
    }

    private fun rankPoints(rank: Int, total: Int): Double = when {
        total <= 1 -> 100.0
        else -> (total - rank).coerceAtLeast(0) * 100.0 / (total - 1)
    }
}
