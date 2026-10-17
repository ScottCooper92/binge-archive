package io.github.scottcooper92.binge.archive.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.scottcooper92.binge.archive.index.IndexRefresher
import io.github.scottcooper92.binge.archive.index.IndexRepository
import io.github.scottcooper92.binge.archive.index.RefreshLogEntity
import io.github.scottcooper92.binge.archive.reports.DeviceRecords
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The status screen's one state stream: the index's size and freshness, a pending refresh, and this
 * device's recently resolved titles and reports.
 */
class StatusViewModel(
    private val index: IndexRepository,
    private val records: DeviceRecords,
    private val refresher: IndexRefresher,
) : ViewModel() {
    private val attempts = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StatusUiState> =
        attempts
            .flatMapLatest { readyStates() }
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

    private fun readyStates() =
        combine(
            combine(index.filmCount(), index.lastSuccess(), index.latestRefresh(), ::IndexFacts),
            refresher.refreshing,
            records.recent(),
            records.reported(),
        ) { facts, refreshing, recent, reported ->
            StatusUiState.Ready(
                filmCount = facts.filmCount,
                lastRefreshedAt = facts.lastSuccessAt,
                lastRefreshFailed = facts.latestFailed,
                refreshing = refreshing,
                recent = recent.map { RecentTitle(it.tmdbId, it.title, it.identifier, it.resolvedAt) },
                reported = reported,
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

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
