package io.github.scottcooper92.binge.archive.preview

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.binge.designsystem.preview.FOLDABLE_PORTRAIT_SPEC
import com.binge.designsystem.preview.PHONE_LANDSCAPE_SPEC
import com.binge.designsystem.preview.PHONE_PORTRAIT_SPEC
import com.binge.designsystem.preview.SOURCE_LOCALE
import com.binge.designsystem.preview.ScreenshotThemeWrapper
import com.binge.designsystem.theme.BingeBrand
import com.binge.designsystem.tv.preview.TV_PREVIEW_SPEC
import com.binge.designsystem.tv.preview.TvScreenshotThemeOnBlackWrapper
import io.github.scottcooper92.binge.archive.theme.ArchiveBrand

/*
 * This app's multipreviews: the design system's cells, wearing [ArchiveBrand] instead of Binge's.
 *
 * A multipreview binds its theme wrapper by class, so an app with its own brand subclasses the
 * wrappers and declares its own annotations from the design system's public specs and
 * [SOURCE_LOCALE]. A cell here is the shared cell's window, theme and locale.
 */

/** The phone harness theme, wearing [ArchiveBrand]. */
class ArchiveScreenshotThemeWrapper : ScreenshotThemeWrapper() {
    override val brand: BingeBrand = ArchiveBrand
}

/** The TV harness painting the window's black, for a screen root. */
class ArchiveTvScreenshotThemeOnBlackWrapper : TvScreenshotThemeOnBlackWrapper() {
    override val brand: BingeBrand = ArchiveBrand
}

/** Phone, phone-light, phone-land and foldable. */
@PreviewWrapper(ArchiveScreenshotThemeWrapper::class)
@Preview(name = "phone", device = PHONE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
@Preview(name = "phone-light", device = PHONE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_NO, locale = SOURCE_LOCALE)
@Preview(name = "phone-land", device = PHONE_LANDSCAPE_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
@Preview(name = "foldable", device = FOLDABLE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class ArchiveScreenPreviews

/** A phone window at font scales 1.5 and 2.0; the 1.0 cell is the screen's own frame. */
@PreviewWrapper(ArchiveScreenshotThemeWrapper::class)
@Preview(name = "font15", device = PHONE_PORTRAIT_SPEC, fontScale = 1.5f, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
@Preview(name = "font20", device = PHONE_PORTRAIT_SPEC, fontScale = 2f, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class ArchiveFontScalePreviews

/** A phone window at font scales 1.3 and 2.0, for a section that must not crop at either. */
@PreviewWrapper(ArchiveScreenshotThemeWrapper::class)
@Preview(name = "font13", device = PHONE_PORTRAIT_SPEC, fontScale = 1.3f, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
@Preview(name = "font20", device = PHONE_PORTRAIT_SPEC, fontScale = 2f, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class ArchiveSettingsFontScalePreviews

/** A television screen root at font scales 1.3 and 2.0. */
@PreviewWrapper(ArchiveTvScreenshotThemeOnBlackWrapper::class)
@Preview(name = "tv-font13", device = TV_PREVIEW_SPEC, fontScale = 1.3f, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
@Preview(name = "tv-font20", device = TV_PREVIEW_SPEC, fontScale = 2f, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class ArchiveTvFontScalePreviews

/** The phone cell alone, for a state of a screen whose layout frame has the matrix. */
@PreviewWrapper(ArchiveScreenshotThemeWrapper::class)
@Preview(name = "phone", device = PHONE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class ArchiveScreenStatePreview

/** One phone cell in Spanish, whose copy runs longer than English. */
@PreviewWrapper(ArchiveScreenshotThemeWrapper::class)
@Preview(name = "es", device = PHONE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = "es")
annotation class ArchiveSpanishPreviews

/** A television screen root in Spanish. */
@PreviewWrapper(ArchiveTvScreenshotThemeOnBlackWrapper::class)
@Preview(name = "tv-es", device = TV_PREVIEW_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = "es")
annotation class ArchiveTvSpanishScreenPreviews

/** A television screen root, with the window's black behind it. */
@PreviewWrapper(ArchiveTvScreenshotThemeOnBlackWrapper::class)
@Preview(name = "tv", device = TV_PREVIEW_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class ArchiveTvScreenPreviews
