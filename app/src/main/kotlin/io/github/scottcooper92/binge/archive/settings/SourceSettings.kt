package io.github.scottcooper92.binge.archive.settings

import com.binge.companion.contracts.stream.v1.Resolution
import io.github.scottcooper92.binge.archive.resolve.LicenceLevel

/**
 * The user's choice of which sources a resolve returns, read at the start of each one. The default is
 * today's behaviour: title matches on, public domain and CC BY, any quality, any subtitles. Each
 * setting only narrows or widens what the resolver finds; none of them skips the licence check.
 */
data class SourceSettings(
    /** Whether `ItemFinder`'s title-and-year route runs. Off leaves only Wikidata- and IMDb-linked items. */
    val titleMatches: Boolean = true,
    val licences: LicenceLevel = LicenceLevel.DEFAULT,
    val minimumQuality: MinimumQuality = MinimumQuality.ANY,
    /** Only sources with a subtitle track in the device's language. */
    val subtitlesInMyLanguage: Boolean = false,
) {
    companion object {
        val DEFAULT = SourceSettings()
    }
}

/** The lowest resolution class a source may have. */
enum class MinimumQuality(
    private val lowest: Resolution,
) {
    ANY(Resolution.RESOLUTION_UNSPECIFIED),
    HD_720(Resolution.RESOLUTION_HD_720),
    HD_1080(Resolution.RESOLUTION_HD_1080),
    ;

    fun admits(resolution: Resolution): Boolean = resolution.number >= lowest.number
}
