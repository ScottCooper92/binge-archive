package io.github.scottcooper92.binge.archive.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.scottcooper92.binge.archive.index.IndexRefresher
import io.github.scottcooper92.binge.archive.index.IndexRepository
import io.github.scottcooper92.binge.archive.index.RefreshLogEntity
import io.github.scottcooper92.binge.archive.reports.DeviceRecords
import io.github.scottcooper92.binge.archive.resolve.LicenceLevel
import io.github.scottcooper92.binge.archive.settings.MinimumQuality
import io.github.scottcooper92.binge.archive.settings.SourceSettings
import io.github.scottcooper92.binge.archive.settings.SourceSettingsStore
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
 * device's recently resolved titles and reports, and the Sources settings.
 *
 * Each setting is saved as it is made. "Any, whatever the licence" is the exception: picking it only
 * asks, and it is saved when the user confirms.
 */
class StatusViewModel(
    private val index: IndexRepository,
    private val records: DeviceRecords,
    private val refresher: IndexRefresher,
    private val sourceSettings: SourceSettingsStore,
) : ViewModel() {
    private val attempts = MutableStateFlow(0)
    private val confirmingAnyLicence = MutableStateFlow(false)

    // Each attempt starts at Loading, so a retry that fails again visibly lands on Error again
    // rather than being conflated into the Error the flow already holds.
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StatusUiState> =
        attempts
            .flatMapLatest { readyStates().onStart { emit(StatusUiState.Loading) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StatusUiState.Loading)

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
            records.recent(),
            records.reported(),
            combine(sourceSettings.settings, confirmingAnyLicence, ::SourcesFacts),
        ) { facts, refreshing, recent, reported, sources ->
            StatusUiState.Ready(
                filmCount = facts.filmCount,
                lastRefreshedAt = facts.lastSuccessAt,
                lastRefreshFailed = facts.latestFailed,
                refreshing = refreshing,
                recent = recent.map { RecentTitle(it.tmdbId, it.title, it.identifier, it.resolvedAt) },
                reported = reported,
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

    private class SourcesFacts(
        val settings: SourceSettings,
        val confirmingAny: Boolean,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
