package io.github.scottcooper92.binge.archive.ui.status

/** A fixed "now" for every frame, so relative dates never drift: 17 October 2026, 09:00 UTC. */
const val PREVIEW_NOW = 1_792_227_600_000L

private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR

val previewReady =
    StatusUiState.Ready(
        filmCount = 6_284,
        lastRefreshedAt = PREVIEW_NOW - 2 * DAY,
        lastRefreshFailed = false,
        refreshing = false,
        recent =
            listOf(
                RecentTitle(10331, "Night of the Living Dead", "Night.Of.The.Living.Dead_1080p", PREVIEW_NOW - 3 * HOUR),
                RecentTitle(653, "Nosferatu", "PhantasmagoriaTheater-Nosferatu1922909", PREVIEW_NOW - DAY),
                RecentTitle(3082, "Modern Times", "modern_times_1936", PREVIEW_NOW - 4 * DAY),
            ),
        reported = listOf("Nosferatu_DVD_quality"),
    )

val previewEmpty =
    StatusUiState.Ready(
        filmCount = 0,
        lastRefreshedAt = null,
        lastRefreshFailed = false,
        refreshing = false,
        recent = emptyList(),
        reported = emptyList(),
    )

val previewFailedRefreshing = previewReady.copy(lastRefreshFailed = true, refreshing = true, recent = emptyList(), reported = emptyList())
