package com.cooper.binge.companion.archive.ui.status

import com.cooper.binge.companion.archive.resolve.LicenceLevel
import com.cooper.binge.companion.archive.settings.MinimumQuality
import com.cooper.binge.companion.archive.settings.SourceSettings

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
        progressTitles = 3,
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

val previewRefreshing = previewReady.copy(refreshing = true)

/** Every Sources setting moved off its default. */
val previewSourcesChanged =
    previewReady.copy(
        sources =
            SourceSettings(
                titleMatches = false,
                licences = LicenceLevel.CREATIVE_COMMONS,
                minimumQuality = MinimumQuality.HD_1080,
                subtitlesInMyLanguage = true,
            ),
    )

/** "Any, including no licence" picked, waiting on its confirm. */
val previewConfirmingAny = previewReady.copy(confirmingAnyLicence = true)

/** "Any licence" saved: the slider on its warning stop. */
val previewAnyLicence = previewReady.copy(sources = SourceSettings(licences = LicenceLevel.ANY))
