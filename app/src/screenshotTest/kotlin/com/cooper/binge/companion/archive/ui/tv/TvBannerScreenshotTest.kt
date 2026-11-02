package com.cooper.binge.companion.archive.ui.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.archive.R

/**
 * The home-screen banner as the launcher draws it: at its own 320x180 and at the smallest size a launcher
 * row shrinks it to, where the name has to stay legible. It is launcher art with no theme, strings or window, so these
 * frames use a plain `@Preview` at the drawable's own size and not the device-and-locale multipreviews screens use.
 */
class TvBannerScreenshotTest {
    @PreviewTest
    @Preview(name = "banner", widthDp = 320, heightDp = 180)
    @Composable
    fun Banner() = Image(painterResource(R.drawable.tv_banner), contentDescription = null)

    @PreviewTest
    @Preview(name = "banner-small", widthDp = 160, heightDp = 90)
    @Composable
    fun BannerSmallest() = Image(painterResource(R.drawable.tv_banner), contentDescription = null, modifier = Modifier.size(160.dp, 90.dp))
}
