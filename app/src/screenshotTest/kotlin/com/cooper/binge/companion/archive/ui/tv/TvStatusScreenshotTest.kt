package com.cooper.binge.companion.archive.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.archive.preview.ArchiveTvFontScalePreviews
import com.cooper.binge.companion.archive.preview.ArchiveTvScreenPreviews
import com.cooper.binge.companion.archive.preview.ArchiveTvSpanishScreenPreviews
import com.cooper.binge.companion.archive.ui.status.PREVIEW_NOW
import com.cooper.binge.companion.archive.ui.status.StatusActions
import com.cooper.binge.companion.archive.ui.status.StatusUiState
import com.cooper.binge.companion.archive.ui.status.previewAnyLicence
import com.cooper.binge.companion.archive.ui.status.previewEmpty
import com.cooper.binge.companion.archive.ui.status.previewFailedRefreshing
import com.cooper.binge.companion.archive.ui.status.previewReady
import com.cooper.binge.companion.archive.ui.status.previewRefreshing
import com.cooper.binge.companion.archive.ui.status.previewSourcesChanged
import com.binge.designsystem.R as DesR

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
    fun refreshing() = TvStatusScreen(previewRefreshing, StatusActions(), now = PREVIEW_NOW)

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
    fun sourcesChanged() = TvStatusScreen(previewSourcesChanged, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveTvSpanishScreenPreviews
    @Composable
    fun sourcesChangedSpanish() = TvStatusScreen(previewSourcesChanged, StatusActions(), now = PREVIEW_NOW)

    @PreviewTest
    @ArchiveTvFontScalePreviews
    @Composable
    fun sourcesFontScale() = TvStatusScreen(previewSourcesChanged, StatusActions(), now = PREVIEW_NOW)

    /** The Sources section alone, so every row shows whatever the window's height. */
    @PreviewTest
    @ArchiveTvScreenPreviews
    @ArchiveTvFontScalePreviews
    @Composable
    fun sourcesSection() = SectionFrame { TvSourceSettings(previewSourcesChanged, StatusActions()) }

    /** The same section scrolled to its end: minimum quality and subtitles. */
    @PreviewTest
    @ArchiveTvScreenPreviews
    @ArchiveTvFontScalePreviews
    @Composable
    fun sourcesSectionEnd() = SectionFrame(atEnd = true) { TvSourceSettings(previewSourcesChanged, StatusActions()) }

    @PreviewTest
    @ArchiveTvSpanishScreenPreviews
    @Composable
    fun sourcesSectionEndSpanish() = SectionFrame(atEnd = true) { TvSourceSettings(previewSourcesChanged, StatusActions()) }

    @PreviewTest
    @ArchiveTvSpanishScreenPreviews
    @Composable
    fun sourcesSectionSpanish() = SectionFrame { TvSourceSettings(previewSourcesChanged, StatusActions()) }

    /** "Any licence" saved: the licence section's warning. */
    @PreviewTest
    @ArchiveTvScreenPreviews
    @Composable
    fun anyLicence() = SectionFrame { TvSourceSettings(previewAnyLicence, StatusActions()) }

    @PreviewTest
    @ArchiveTvSpanishScreenPreviews
    @Composable
    fun anyLicenceSpanish() = SectionFrame { TvSourceSettings(previewAnyLicence, StatusActions()) }

    @PreviewTest
    @ArchiveTvFontScalePreviews
    @Composable
    fun anyLicenceFontScale() = SectionFrame { TvSourceSettings(previewAnyLicence, StatusActions()) }

    /** The confirm as it first shows: focus on Cancel, the safe answer. */
    @PreviewTest
    @ArchiveTvScreenPreviews
    @Composable
    fun anyLicenceConfirm() = ConfirmFrame { TvAnyLicenceConfirm(StatusActions()) }

    @PreviewTest
    @ArchiveTvSpanishScreenPreviews
    @Composable
    fun anyLicenceConfirmSpanish() = ConfirmFrame { TvAnyLicenceConfirm(StatusActions()) }

    @PreviewTest
    @ArchiveTvFontScalePreviews
    @Composable
    fun anyLicenceConfirmFontScale() = ConfirmFrame { TvAnyLicenceConfirm(StatusActions()) }

    @PreviewTest
    @ArchiveTvScreenPreviews
    @Composable
    fun licences() = TvLicenceScreen()

    @PreviewTest
    @ArchiveTvSpanishScreenPreviews
    @Composable
    fun licencesSpanish() = TvLicenceScreen()
}

/** The confirm's card centred in the window, as its dialog shows it. */
@Composable
private fun ConfirmFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** A section of the page, scrolling, from its top or, [atEnd], its bottom: the window shows one screenful. */
@Composable
private fun SectionFrame(
    atEnd: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .verticalScroll(
                    rememberScrollState(if (atEnd) Int.MAX_VALUE else 0),
                ).padding(dimensionResource(DesR.dimen.padding_l)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) { content() }
}
