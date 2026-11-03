package com.cooper.binge.companion.archive.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.binge.companion.sdk.readBrandColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.pow
import org.junit.Assert.assertNotNull as junitAssertNotNull

private const val WHITE = 0xFFFFFFFF.toInt()
private const val DARK_SURFACE = 0xFF121212.toInt()
private const val NON_TEXT_CONTRAST = 3.0

/**
 * The brand colour the Service declares reads back through the SDK's own reader, as the host reads it, and each variant
 * clears the host's 3:1 non-text contrast against the surface it is drawn on. A tint under that is not used, and the
 * host falls back to its own, so a shade changed below it would silently stop applying.
 */
@RunWith(RobolectricTestRunner::class)
class BrandColourTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun metaData(): Bundle =
        context.packageManager
            .queryIntentServices(Intent("com.binge.companion.STREAM").setPackage(context.packageName), PackageManager.GET_META_DATA)
            .single()
            .serviceInfo.metaData

    private fun brand(dark: Boolean): Int? = metaData().readBrandColor(dark) { context.getColor(it) }

    @Test
    fun `a light theme reads the plain colour and a dark theme reads the dark one`() {
        assertEquals(0xFF059669.toInt(), brand(dark = false))
        assertEquals(0xFF34D399.toInt(), brand(dark = true))
    }

    @Test
    fun `the plain colour clears 3 to 1 against white`() {
        assertTrue(contrast(assertNotNull(brand(dark = false)), WHITE) >= NON_TEXT_CONTRAST)
    }

    @Test
    fun `the dark colour clears 3 to 1 against a dark surface`() {
        assertTrue(contrast(assertNotNull(brand(dark = true)), DARK_SURFACE) >= NON_TEXT_CONTRAST)
    }

    private fun assertNotNull(value: Int?): Int {
        junitAssertNotNull("no brand colour was declared", value)
        return value ?: 0
    }

    private fun contrast(
        a: Int,
        b: Int,
    ): Double {
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
