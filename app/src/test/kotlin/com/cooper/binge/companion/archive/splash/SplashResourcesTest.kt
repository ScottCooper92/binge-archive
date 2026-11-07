package com.cooper.binge.companion.archive.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private const val ANDROID = "http://schemas.android.com/apk/res/android"
private const val MAX_ANIMATED_ICON_MILLIS = 1000

/**
 * What a screenshot of the splash cannot pin, read from the resources themselves: the sweep ends on the launcher icon's own arc,
 * stays under the 1000 ms the platform allows an animated icon, and the launch theme hands over to the app's. The platform draws
 * the splash, so the frames in `SplashScreenshotTest` cover only its two ends.
 */
class SplashResourcesTest {
    private val res = File("src/main/res")

    private fun parse(file: File): Element =
        DocumentBuilderFactory
            .newInstance()
            .apply {
                isNamespaceAware = true
            }.newDocumentBuilder()
            .parse(file)
            .documentElement

    private fun Element.all(tag: String): List<Element> =
        (0 until getElementsByTagName(tag).length).map {
            getElementsByTagName(tag).item(it) as Element
        }

    private fun Element.attr(name: String): String = getAttributeNS(ANDROID, name)

    private val avd = parse(File(res, "drawable/avd_launcher_ring.xml"))
    private val foreground = parse(File(res, "drawable/ic_launcher_foreground.xml"))
    private val animator = avd.all("objectAnimator").single()

    @Test
    fun `the animation targets the launcher foreground's arc by name`() {
        assertEquals("@drawable/ic_launcher_foreground", avd.attr("drawable"))
        val target = avd.all("target").single().attr("name")

        assertTrue(foreground.all("path").any { it.attr("name") == target })
        assertEquals("trimPathEnd", animator.attr("propertyName"))
    }

    @Test
    fun `the sweep ends on the launcher icon's resting arc`() {
        val arc = foreground.all("path").single { it.attr("name") == "progressArc" }

        assertEquals(0f, animator.attr("valueFrom").toFloat(), 0f)
        assertEquals(arc.attr("trimPathEnd").toFloat(), animator.attr("valueTo").toFloat(), 0f)
    }

    @Test
    fun `the sweep decelerates and fits the platform's limit for an animated icon`() {
        val duration = animator.attr("duration").toInt()

        assertEquals("@android:interpolator/decelerate_cubic", animator.attr("interpolator"))
        assertTrue("$duration ms is over the platform's limit", duration <= MAX_ANIMATED_ICON_MILLIS)
        assertEquals(duration, splashItem("values", "windowSplashScreenAnimationDuration").toInt())
    }

    @Test
    fun `the launch theme animates the ring and hands over to the app's theme, on a phone and a television`() {
        listOf("values", "values-television").forEach { qualifier ->
            assertEquals("@drawable/avd_launcher_ring", splashItem(qualifier, "windowSplashScreenAnimatedIcon"))
            assertEquals("@style/Theme.Archive", splashItem(qualifier, "postSplashScreenTheme"))
        }
        assertEquals("@android:color/black", splashItem("values-television", "windowSplashScreenBackground"))
    }

    @Test
    fun `the phone's splash ground is the window background, in light and in dark`() {
        assertEquals("@color/window_background", splashItem("values", "windowSplashScreenBackground"))
        val archive = { qualifier: String ->
            parse(File(res, "$qualifier/styles.xml")).all("style").single { it.getAttribute("name") == "Theme.Archive" }
        }
        val windowBackground = archive("values").all("item").single { it.getAttribute("name") == "android:windowBackground" }
        assertEquals("@color/window_background", windowBackground.textContent.trim())
        listOf("values", "values-night").forEach { qualifier ->
            assertEquals(
                1,
                parse(File(res, "$qualifier/colors.xml")).all("color").count { it.getAttribute("name") == "window_background" },
            )
        }
    }

    @Test
    fun `the launcher Activity wears the launch theme`() {
        val manifest = parse(File("src/main/AndroidManifest.xml"))
        val main = manifest.all("activity").single { it.attr("name") == ".MainActivity" }

        assertEquals("@style/Theme.Archive.Splash", main.attr("theme"))
    }

    private fun splashItem(
        qualifier: String,
        name: String,
    ): String {
        val style = parse(File(res, "$qualifier/styles.xml")).all("style").single { it.getAttribute("name") == "Theme.Archive.Splash" }
        val item = style.all("item").singleOrNull { it.getAttribute("name") == name }
        assertNotNull("$qualifier has no $name", item)
        return item?.textContent?.trim().orEmpty()
    }
}
