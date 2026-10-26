package com.cooper.binge.companion.archive.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.binge.designsystem.theme.BingeBrand
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.binge.designsystem.theme.DarkColorScheme
import com.binge.designsystem.theme.LightColorScheme
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.binge.designsystem.tv.theme.toTvColorScheme
import androidx.tv.material3.ColorScheme as TvColorScheme

private val Emerald100 = Color(0xFFD1FAE5)
private val Emerald200 = Color(0xFFA7F3D0)
private val Emerald300 = Color(0xFF6EE7B7)
private val Emerald400 = Color(0xFF34D399)
private val EmeraldBrand500 = Color(0xFF10B981)
private val Emerald700 = Color(0xFF047857)
private val Emerald900 = Color(0xFF064E3B)
private val EmeraldInk = Color(0xFF022C22)

/** Tertiary accent: violet, a warm-cool contrast to emerald that stays clear of the amber Binge wears. */
private val Violet100 = Color(0xFFEDE9FE)
private val Violet200 = Color(0xFFDDD6FE)
private val Violet400 = Color(0xFFA78BFA)
private val Violet600 = Color(0xFF7C3AED)
private val Violet900 = Color(0xFF4C1D95)
private val VioletInk = Color(0xFF2E1065)

val ArchiveDarkColorScheme: ColorScheme =
    DarkColorScheme.copy(
        primary = Emerald400,
        onPrimary = EmeraldInk,
        primaryContainer = Emerald900,
        onPrimaryContainer = Emerald200,
        inversePrimary = EmeraldBrand500,
        secondary = Emerald300,
        onSecondary = EmeraldInk,
        secondaryContainer = Emerald900,
        onSecondaryContainer = Emerald200,
        tertiary = Violet400,
        onTertiary = VioletInk,
        tertiaryContainer = Violet900,
        onTertiaryContainer = Violet200,
        primaryFixed = Emerald200,
        primaryFixedDim = Emerald400,
        onPrimaryFixed = EmeraldInk,
        onPrimaryFixedVariant = Emerald900,
    )

val ArchiveLightColorScheme: ColorScheme =
    LightColorScheme.copy(
        primary = Emerald700,
        onPrimary = Color.White,
        primaryContainer = Emerald100,
        onPrimaryContainer = EmeraldInk,
        inversePrimary = EmeraldBrand500,
        secondary = Emerald700,
        onSecondary = Color.White,
        secondaryContainer = Emerald100,
        onSecondaryContainer = EmeraldInk,
        tertiary = Violet600,
        onTertiary = Color.White,
        tertiaryContainer = Violet100,
        onTertiaryContainer = VioletInk,
        primaryFixed = Emerald200,
        primaryFixedDim = Emerald400,
        onPrimaryFixed = EmeraldInk,
        onPrimaryFixedVariant = Emerald900,
    )

/**
 * The design system's brand slot, with this app's emerald in place of Binge's amber.
 *
 * The accent is the icon's emerald. Only the brand family changes, as in binge-seerr's theme. The
 * neutral surfaces and `error` are shared meaning, so they stay the design system's. The fixed roles
 * are set too, because the design system paints its big fills on them (filled buttons, switches,
 * selected chips); left unset they would stay amber.
 */
val ArchiveBrand = BingeBrand(light = ArchiveLightColorScheme, dark = ArchiveDarkColorScheme)

/** [ArchiveDarkColorScheme] projected for the TV surface, which is dark-only like [BingeTvTheme]. */
val ArchiveTvColorScheme: TvColorScheme = ArchiveDarkColorScheme.toTvColorScheme()

/** [BingeExpressiveTheme] wearing [ArchiveBrand]. */
@Composable
fun ArchiveTheme(content: @Composable () -> Unit) {
    BingeExpressiveTheme(brand = ArchiveBrand, content = content)
}

/** [BingeTvTheme] wearing [ArchiveTvColorScheme], the TV counterpart of [ArchiveTheme]. */
@Composable
fun ArchiveTvTheme(content: @Composable () -> Unit) {
    BingeTvTheme(colorScheme = ArchiveTvColorScheme, content = content)
}
