package com.cooper.binge.companion.archive.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.archive.index.IndexRefresher
import com.cooper.binge.companion.archive.index.IndexRepository
import com.cooper.binge.companion.archive.index.RefreshLogEntity
import com.cooper.binge.companion.archive.reports.DeviceRecords
import com.cooper.binge.companion.archive.reports.PlaybackProgressStore
import com.cooper.binge.companion.archive.reports.RecentTitleEntity
import com.cooper.binge.companion.archive.resolve.LicenceLevel
import com.cooper.binge.companion.archive.settings.MinimumQuality
import com.cooper.binge.companion.archive.settings.SourceSettings
import com.cooper.binge.companion.archive.settings.SourceSettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The status screen's one state stream: the index's size and freshness, a pending refresh, this
 * device's recently resolved titles and reports, how many titles hold watch progress, and the
 * Sources settings.
 *
 * Each setting is saved as it is made. "Any, whatever the licence" is the exception: picking it only
 * asks, and it is saved when the user confirms.
 */
class StatusViewModel(
    private val index: IndexRepository,
    private val records: DeviceRecords,
    private val refresher: IndexRefresher,
    private val sourceSettings: SourceSettingsStore,
    private val progress: PlaybackProgressStore,
) : ViewModel() {
    private val attempts = MutableStateFlow(0)
    private val confirmingAnyLicence = MutableStateFlow(false)

    /** The last attempt that showed Loading. Coming back after the stream stopped is not a new attempt. */
    private var loadingShownFor = -1

    // Each new attempt starts at Loading, so a retry that fails again visibly lands on Error again
    // rather than being conflated into the Error the flow already holds. Resuming after the screen was away
    // longer than the stop timeout is not an attempt: it keeps the last state, so nothing flashes and the list
    // keeps its scroll.
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StatusUiState> =
        attempts
            .flatMapLatest { attempt ->
                readyStates().onStart {
                    if (attempt != loadingShownFor) {
                        loadingShownFor = attempt
                        emit(StatusUiState.Loading)
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StatusUiState.Loading)

    fun refreshNow() = refresher.refreshNow()

    fun retry() {
        attempts.value++
    }

    fun clearRecent() {
        viewModelScope.launch { records.clearRecent() }
    }

    fun clearReported() {
        viewModelScope.launch { records.clearReports() }
    }

    fun clearProgress() {
        viewModelScope.launch { progress.clear() }
    }

    fun setTitleMatches(on: Boolean) {
        viewModelScope.launch { sourceSettings.setTitleMatches(on) }
    }

    /** Saves [level], except [LicenceLevel.ANY], which waits on [confirmAnyLicence]. */
    fun chooseLicences(level: LicenceLevel) {
        if (level == LicenceLevel.ANY) {
            confirmingAnyLicence.value = true
        } else {
            viewModelScope.launch { sourceSettings.setLicences(level) }
        }
    }

    fun confirmAnyLicence() {
        if (!confirmingAnyLicence.value) return
        confirmingAnyLicence.value = false
        viewModelScope.launch { sourceSettings.setLicences(LicenceLevel.ANY) }
    }

    fun cancelAnyLicence() {
        confirmingAnyLicence.value = false
    }

    fun setMinimumQuality(quality: MinimumQuality) {
        viewModelScope.launch { sourceSettings.setMinimumQuality(quality) }
    }

    fun setSubtitlesInMyLanguage(on: Boolean) {
        viewModelScope.launch { sourceSettings.setSubtitlesInMyLanguage(on) }
    }

    private fun readyStates() =
        combine(
            combine(index.filmCount(), index.lastSuccess(), index.latestRefresh(), ::IndexFacts),
            refresher.refreshing,
            combine(records.recent(), records.reported(), progress.titleCount(), ::RecordFacts),
            combine(sourceSettings.settings, confirmingAnyLicence, ::SourcesFacts),
        ) { facts, refreshing, records, sources ->
            StatusUiState.Ready(
                filmCount = facts.filmCount,
                lastRefreshedAt = facts.lastSuccessAt,
                lastRefreshFailed = facts.latestFailed,
                refreshing = refreshing,
                recent = records.recent.map { RecentTitle(it.tmdbId, it.title, it.identifier, it.resolvedAt) },
                reported = records.reported,
                progressTitles = records.progressTitles,
                sources = sources.settings,
                confirmingAnyLicence = sources.confirmingAny,
            ) as StatusUiState
        }.catch { emit(StatusUiState.Error) }

    private class IndexFacts(
        val filmCount: Int,
        lastSuccess: RefreshLogEntity?,
        latest: RefreshLogEntity?,
    ) {
        val lastSuccessAt: Long? = lastSuccess?.finishedAt
        val latestFailed: Boolean = latest?.succeeded == false
    }

    private class RecordFacts(
        val recent: List<RecentTitleEntity>,
        val reported: List<String>,
        val progressTitles: Int,
    )

    private class SourcesFacts(
        val settings: SourceSettings,
        val confirmingAny: Boolean,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
