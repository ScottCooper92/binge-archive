package io.github.scottcooper92.binge.archive.ui.status

import io.github.scottcooper92.binge.archive.resolve.LicenceLevel
import io.github.scottcooper92.binge.archive.settings.MinimumQuality
import io.github.scottcooper92.binge.archive.settings.SourceSettings

/** A title this device resolved for a host, as the status screen lists it. */
data class RecentTitle(
    val tmdbId: Int,
    val title: String,
    val identifier: String,
    val resolvedAt: Long,
)

/** The status screen's state: one of three arms, never a set of flags. */
sealed interface StatusUiState {
    data object Loading : StatusUiState

    data class Ready(
        /** Films the index can place. */
        val filmCount: Int,
        /** When the index was last replaced, by the seed or a refresh; null before either. */
        val lastRefreshedAt: Long?,
        /** Whether the most recent refresh failed; the previous index is still in use. */
        val lastRefreshFailed: Boolean,
        /** Whether a "Refresh now" is waiting for a network or running. */
        val refreshing: Boolean,
        val recent: List<RecentTitle>,
        /** Archive items this device reported, pushed down for 30 days. */
        val reported: List<String>,
        /** The user's Sources settings, as saved. */
        val sources: SourceSettings = SourceSettings.DEFAULT,
        /** Whether "Any, including no licence" was picked and waits on its confirm. Nothing is saved until then. */
        val confirmingAnyLicence: Boolean = false,
    ) : StatusUiState

    /** The device's own records could not be read. */
    data object Error : StatusUiState
}

/** What the status screen can ask for. */
data class StatusActions(
    val onRefreshNow: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenLicences: () -> Unit = {},
    /** Opens the Sources section in the detail pane, on a window wide enough for two. */
    val onOpenSources: () -> Unit = {},
    val onOpenUrl: (String) -> Unit = {},
    val onClearRecent: () -> Unit = {},
    val onClearReported: () -> Unit = {},
    val onTitleMatches: (Boolean) -> Unit = {},
    /** A licence level picked. [LicenceLevel.ANY] asks first: see [StatusUiState.Ready.confirmingAnyLicence]. */
    val onLicences: (LicenceLevel) -> Unit = {},
    val onConfirmAnyLicence: () -> Unit = {},
    val onCancelAnyLicence: () -> Unit = {},
    val onMinimumQuality: (MinimumQuality) -> Unit = {},
    val onSubtitlesInMyLanguage: (Boolean) -> Unit = {},
)

/** The pages a viewer of an Archive item or a site opens. */
object StatusLinks {
    const val ARCHIVE = "https://archive.org"
    const val WIKIDATA = "https://www.wikidata.org"

    fun item(identifier: String): String = "https://archive.org/details/$identifier"
}
