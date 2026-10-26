package com.cooper.binge.companion.archive.resolve

import android.util.Log

/**
 * A gap in coverage, and where it showed. Logged for binge-companions#148's measurement. [route] is
 * the route that found candidates, or null when none did.
 */
data class CoverageMiss(
    val tmdbId: Int,
    val route: Route?,
    val reason: Reason,
) {
    enum class Reason {
        /** Neither the index nor any route knows the film: the resolve was NOT_FOUND. */
        NOT_IN_CATALOGUE,

        /** The index did not know the film, and [route] found it: a gap in the index, not a miss. */
        NOT_IN_INDEX,

        /** The index knows the film, and no route found any Archive item for it. */
        NO_ITEMS,

        /** Items were found, and none passed the licence and access checks. */
        NO_LICIT_ITEM,

        /** Licit items were found, with no playable video file. */
        NO_PLAYABLE_FILE,

        /** Wikidata failed, and [route]'s candidates are the index's Archive ids instead. */
        WIKIDATA_FAILED,

        /**
         * The resolve ended with nothing, and the user's settings, narrower than the default, removed
         * something the default would have kept: the title route, an item's licence, or a source.
         */
        FILTERED_BY_SETTINGS,
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
