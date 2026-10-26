package com.cooper.binge.companion.archive

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.archive.theme.ArchiveTheme
import com.cooper.binge.companion.archive.theme.ArchiveTvTheme
import com.cooper.binge.companion.archive.ui.status.PhoneStatusRoot
import com.cooper.binge.companion.archive.ui.status.StatusActions
import com.cooper.binge.companion.archive.ui.status.StatusViewModel
import com.cooper.binge.companion.archive.ui.tv.TvLicenceScreen
import com.cooper.binge.companion.archive.ui.tv.TvStatusScreen
import com.cooper.binge.companion.archive.ui.tv.isTelevision

/**
 * The app's own UI: the status screen, and the licence page it opens. A phone wears the design
 * system's theme; a television gets the TV pages under its TV theme. [PhoneStatusRoot] lays the phone
 * out for the window.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as ArchiveApp).graph
        val factory =
            viewModelFactory {
                initializer { StatusViewModel(graph.index, graph.records, graph.refresher, graph.sourceSettings) }
            }
        setContent {
            val viewModel: StatusViewModel = viewModel(factory = factory)
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            var showingLicences by rememberSaveable { mutableStateOf(false) }
            BackHandler(enabled = showingLicences) { showingLicences = false }
            val actions =
                StatusActions(
                    onRefreshNow = viewModel::refreshNow,
                    onRetry = viewModel::retry,
                    onOpenLicences = { showingLicences = true },
                    onOpenSources = { showingLicences = false },
                    onOpenUrl = ::openUrl,
                    onClearRecent = viewModel::clearRecent,
                    onClearReported = viewModel::clearReported,
                    onTitleMatches = viewModel::setTitleMatches,
                    onLicences = viewModel::chooseLicences,
                    onConfirmAnyLicence = viewModel::confirmAnyLicence,
                    onCancelAnyLicence = viewModel::cancelAnyLicence,
                    onMinimumQuality = viewModel::setMinimumQuality,
                    onSubtitlesInMyLanguage = viewModel::setSubtitlesInMyLanguage,
                )
            if (LocalConfiguration.current.isTelevision()) {
                ArchiveTvTheme {
                    if (showingLicences) TvLicenceScreen() else TvStatusScreen(state, actions)
                }
            } else {
                ArchiveTheme {
                    PhoneSurface {
                        PhoneStatusRoot(state, actions, showingLicences, onCloseLicences = { showingLicences = false })
                    }
                }
            }
        }
    }

    /** Opens [url] in the browser. A device with none, a television usually, keeps the user here. */
    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Log.i(TAG, "No browser for $url", e)
        }
    }

    private companion object {
        const val TAG = "ArchiveMain"
    }
}

@Composable
private fun PhoneSurface(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
}
