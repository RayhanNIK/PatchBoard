package com.example.patchboard.profile

import com.example.patchboard.model.Column
import com.example.patchboard.model.LocationTag
import com.example.patchboard.model.Stage

/** One person's numbers, computed from the whole board (never from the filtered view). */
data class ProfileStats(
    val reportsFiled: Int,
    val fixed: Int,                 // my reports that reached "Fixed"
    val inProgress: Int,
    val open: Int,                  // my reports still in "Reported"
    val commentsWritten: Int,
    val upvotesReceived: Int,       // total upvotes on my reports
    val upvotesGiven: Int,          // reports I have upvoted
    val topLocation: LocationTag?,  // where I report most
    val recentFixed: List<String>,  // titles of my latest fixed reports (newest first, max 3)
) {
    /** Share of my reports that got fixed, 0..100. */
    val fixRatePercent: Int get() = if (reportsFiled == 0) 0 else fixed * 100 / reportsFiled

    companion object {
        fun compute(columns: List<Column>, userId: String?): ProfileStats {
            if (userId == null) return ProfileStats(0, 0, 0, 0, 0, 0, 0, null, emptyList())

            var filed = 0; var fixed = 0; var progress = 0; var open = 0
            var comments = 0; var received = 0; var given = 0
            val byLocation = HashMap<LocationTag, Int>()
            val fixedCards = ArrayList<Pair<Long, String>>()

            for (col in columns) for (card in col.cards) {
                comments += card.comments.count { it.authorId == userId }
                if (userId in card.upvotedBy) given++
                if (card.authorId != userId) continue

                filed++
                received += card.upvotes
                byLocation[card.location] = (byLocation[card.location] ?: 0) + 1
                when (col.stage) {
                    Stage.FIXED -> { fixed++; fixedCards += (card.resolvedAt ?: 0L) to card.title }
                    Stage.IN_PROGRESS -> progress++
                    Stage.REPORTED -> open++
                }
            }

            val top = byLocation.entries
                .sortedWith(compareByDescending<Map.Entry<LocationTag, Int>> { it.value }.thenBy { it.key.ordinal })
                .firstOrNull()?.key

            return ProfileStats(
                reportsFiled = filed, fixed = fixed, inProgress = progress, open = open,
                commentsWritten = comments, upvotesReceived = received, upvotesGiven = given,
                topLocation = top,
                recentFixed = fixedCards.sortedByDescending { it.first }.take(3).map { it.second },
            )
        }
    }
}
