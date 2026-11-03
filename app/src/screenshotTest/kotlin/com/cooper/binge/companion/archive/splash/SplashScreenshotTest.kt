package com.cooper.binge.companion.archive.splash

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.preview.PHONE_PORTRAIT_SPEC
import com.binge.designsystem.tv.preview.TV_PREVIEW_SPEC
import com.cooper.binge.companion.archive.R

private val PhoneGround = Color(0xFF0F1F1A)
private val TvGround = Color.Black
private val SplashIconSize = 288.dp

/**
 * The launch splash's two ends. The platform draws the splash itself, so it cannot be captured; these frames render the same
 * animated vector through Compose at its first state (the arc at nothing) and its last (the arc at its resting length, which
 * is the launcher icon, and is also all a device before Android 12 shows). The mid-sweep is not captured here, and
 * `SplashResourcesTest` pins the duration, the interpolator and that the sweep ends on the icon's own value.
 */
class SplashScreenshotTest {
    @PreviewTest
    @Preview(name = "phone-start", device = PHONE_PORTRAIT_SPEC)
    @Composable
    fun PhoneStart() = SplashFrame(atEnd = false, ground = PhoneGround)

    @PreviewTest
    @Preview(name = "phone-end", device = PHONE_PORTRAIT_SPEC)
    @Composable
    fun PhoneEnd() = SplashFrame(atEnd = true, ground = PhoneGround)

    @PreviewTest
    @Preview(name = "tv-start", device = TV_PREVIEW_SPEC)
    @Composable
    fun TvStart() = SplashFrame(atEnd = false, ground = TvGround)

    @PreviewTest
    @Preview(name = "tv-end", device = TV_PREVIEW_SPEC)
    @Composable
    fun TvEnd() = SplashFrame(atEnd = true, ground = TvGround)
}

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
private fun SplashFrame(
    atEnd: Boolean,
    ground: Color,
) {
    val image = AnimatedImageVector.animatedVectorResource(R.drawable.avd_launcher_ring)
    Box(Modifier.fillMaxSize().background(ground), contentAlignment = Alignment.Center) {
        Image(rememberAnimatedVectorPainter(image, atEnd), contentDescription = null, modifier = Modifier.size(SplashIconSize))
    }
}
