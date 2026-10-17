package io.github.scottcooper92.binge.archive.ui.status

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.archive.preview.ArchiveFontScalePreviews
import io.github.scottcooper92.binge.archive.preview.ArchiveScreenPreviews
import io.github.scottcooper92.binge.archive.preview.ArchiveScreenStatePreview
import io.github.scottcooper92.binge.archive.preview.ArchiveSpanishPreviews

/** The phone's status screen: its layout across the matrix, then each state on the phone cell alone. */
class StatusScreenshotTest {
    @PreviewTest
    @ArchiveScreenPreviews
    @Composable
    fun ready() = StatusScreen(previewReady, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveFontScalePreviews
    @Composable
    fun readyFontScale() = StatusScreen(previewReady, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveSpanishPreviews
    @Composable
    fun readySpanish() = StatusScreen(previewReady, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveScreenStatePreview
    @Composable
    fun empty() = StatusScreen(previewEmpty, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveScreenStatePreview
    @Composable
    fun failedAndRefreshing() = StatusScreen(previewFailedRefreshing, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveScreenStatePreview
    @Composable
    fun loading() = StatusScreen(StatusUiState.Loading, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveScreenStatePreview
    @Composable
    fun error() = StatusScreen(StatusUiState.Error, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveScreenStatePreview
    @Composable
    fun licences() = LicenceScreen(onBack = {})

    @PreviewTest
    @ArchiveSpanishPreviews
    @Composable
    fun licencesSpanish() = LicenceScreen(onBack = {})
}
