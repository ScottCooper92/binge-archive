package io.github.scottcooper92.binge.archive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import io.github.scottcooper92.binge.archive.theme.ArchiveTheme
import io.github.scottcooper92.binge.archive.theme.ArchiveTvTheme
import io.github.scottcooper92.binge.archive.ui.HomeScreen
import io.github.scottcooper92.binge.archive.ui.tv.TvHomeScreen
import io.github.scottcooper92.binge.archive.ui.tv.isTelevision

/** The app's own UI: the phone surface under the design system's theme, or the TV one on a television. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            if (LocalConfiguration.current.isTelevision()) {
                ArchiveTvTheme { TvHomeScreen() }
            } else {
                ArchiveTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        HomeScreen()
                    }
                }
            }
        }
    }
}
