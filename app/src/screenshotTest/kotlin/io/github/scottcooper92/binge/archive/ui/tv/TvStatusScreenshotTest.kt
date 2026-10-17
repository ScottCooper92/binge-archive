package io.github.scottcooper92.binge.archive.ui.tv

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.archive.preview.ArchiveTvScreenPreviews
import io.github.scottcooper92.binge.archive.preview.ArchiveTvSpanishScreenPreviews
import io.github.scottcooper92.binge.archive.ui.status.PREVIEW_NOW
import io.github.scottcooper92.binge.archive.ui.status.StatusActions
import io.github.scottcooper92.binge.archive.ui.status.StatusUiState
import io.github.scottcooper92.binge.archive.ui.status.previewEmpty
import io.github.scottcooper92.binge.archive.ui.status.previewFailedRefreshing
import io.github.scottcooper92.binge.archive.ui.status.previewReady

/** The television's status screen in each state, and its licence page. */
class TvStatusScreenshotTest {
    @PreviewTest
    @ArchiveTvScreenPreviews
    @Composable
    fun ready() = TvStatusScreen(previewReady, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveTvSpanishScreenPreviews
    @Composable
    fun readySpanish() = TvStatusScreen(previewReady, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveTvScreenPreviews
    @Composable
    fun empty() = TvStatusScreen(previewEmpty, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveTvScreenPreviews
    @Composable
    fun failedAndRefreshing() = TvStatusScreen(previewFailedRefreshing, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveTvScreenPreviews
    @Composable
    fun loading() = TvStatusScreen(StatusUiState.Loading, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveTvScreenPreviews
    @Composable
    fun error() = TvStatusScreen(StatusUiState.Error, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveTvScreenPreviews
    @Composable
    fun licences() = TvLicenceScreen()
}
