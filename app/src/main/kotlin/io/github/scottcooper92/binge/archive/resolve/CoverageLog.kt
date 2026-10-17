package io.github.scottcooper92.binge.archive.resolve

import android.util.Log

/**
 * A resolve that found nothing, and where it stopped. Logged for binge-companions#148's coverage
 * measurement. [route] is the route that found candidates, or null when none did or the index did
 * not know the title.
 */
data class CoverageMiss(
    val tmdbId: Int,
    val route: Route?,
    val reason: Reason,
) {
    enum class Reason {
        /** The index does not know the TMDB id. */
        NOT_IN_INDEX,

        /** No route found any Archive item. */
        NO_ITEMS,

        /** Items were found, and none passed the licence and access checks. */
        NO_LICIT_ITEM,

        /** Licit items were found, with no playable video file. */
        NO_PLAYABLE_FILE,
    }
}

/** Where coverage misses go. Only ever the device: this is never sent anywhere. */
fun interface CoverageLog {
    fun miss(miss: CoverageMiss)
}

/** Writes each miss to logcat, on the device, under the `Coverage` tag. */
object LogcatCoverageLog : CoverageLog {
    override fun miss(miss: CoverageMiss) {
        Log.i(
            "Coverage",
            "miss tmdb=${miss.tmdbId} route=${miss.route?.name?.lowercase() ?: "none"} reason=${miss.reason.name.lowercase()}",
        )
    }
}
