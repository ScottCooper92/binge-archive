package com.cooper.binge.companion.archive.service

import com.binge.companion.contracts.stream.v1.Capability
import com.binge.companion.contracts.stream.v1.ForgetProgressRequest
import com.binge.companion.contracts.stream.v1.ForgetProgressResponse
import com.binge.companion.contracts.stream.v1.HandshakeRequest
import com.binge.companion.contracts.stream.v1.HandshakeResponse
import com.binge.companion.contracts.stream.v1.ListContinueWatchingRequest
import com.binge.companion.contracts.stream.v1.ListContinueWatchingResponse
import com.binge.companion.contracts.stream.v1.ProbeRequest
import com.binge.companion.contracts.stream.v1.ProbeResponse
import com.binge.companion.contracts.stream.v1.ReportPlaybackRequest
import com.binge.companion.contracts.stream.v1.ReportPlaybackResponse
import com.binge.companion.contracts.stream.v1.ReportSourceRequest
import com.binge.companion.contracts.stream.v1.ReportSourceResponse
import com.binge.companion.contracts.stream.v1.ResolveRequest
import com.binge.companion.contracts.stream.v1.ResolveResponse
import com.binge.companion.contracts.stream.v1.SourceProblem
import com.binge.companion.contracts.stream.v1.StreamServiceGrpcKt
import com.binge.companion.contracts.v1.EpisodeRef
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.sdk.ContinueWatchingPageToken
import com.binge.companion.sdk.MAX_LIST_PAGE_SIZE
import com.binge.companion.sdk.asResume
import com.binge.companion.sdk.effectivePageSize
import com.binge.companion.sdk.requireDeclared
import com.binge.companion.sdk.requireValid
import com.cooper.binge.companion.archive.index.IndexRepository
import com.cooper.binge.companion.archive.reports.DeviceRecords
import com.cooper.binge.companion.archive.reports.PlaybackProgressStore
import com.cooper.binge.companion.archive.resolve.ResolveOutcome
import com.cooper.binge.companion.archive.resolve.TitleResolver
import com.cooper.binge.companion.archive.resolve.unusableRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.withContext

/**
 * STREAM v1, served from this app.
 *
 * - `Handshake` always succeeds: there is no sign-in. It declares Probe, subtitles, source
 *   reports and watch progress, names the Internet Archive as the provider, and ignores `HostInfo`.
 * - `Resolve` is the resolver's answer, mapped by [ArchiveErrors]. Its first message carries the
 *   title's resume point, when the device holds one.
 * - `Probe` reads the index and nothing else: it never touches the network.
 * - `ReportSource` records the report on the device and pushes the item down for 30 days.
 * - `ReportPlayback`, `ListContinueWatching` and `ForgetProgress` keep the user's watch progress
 *   on the device ([PlaybackProgressStore]), by the SDK's rules for it.
 *
 * Each call stands alone; nothing is kept per connection.
 */
class ArchiveStreamService(
    private val resolver: TitleResolver,
    private val index: IndexRepository,
    private val records: DeviceRecords,
    private val progress: PlaybackProgressStore,
    private val versionName: String,
    private val errors: ArchiveErrors,
    /** Where every call runs: never a Binder thread, since a resolve reads response bodies and the index. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : StreamServiceGrpcKt.StreamServiceCoroutineImplBase() {
    override suspend fun handshake(request: HandshakeRequest): HandshakeResponse =
        HandshakeResponse
            .newBuilder()
            .addAllCapabilities(CAPABILITIES.sortedBy(Capability::getNumber))
            .setProviderName(PROVIDER_NAME)
            .setCompanionVersionName(versionName)
            .build()

    /**
     * The resolve runs on [io]. The sources flow is not moved there with `flowOn`: its buffer would
     * drop a source found just before a failure, and the contract has the host keep every source it
     * received. What blocks inside it, a response body's read, moves to `io` itself in `ResolverHttp`.
     *
     * The resume point is read once, after the resolver has accepted the request, and rides on the
     * first source only. A completed episode has none, and a stream with no sources carries none.
     */
    override fun resolve(request: ResolveRequest): Flow<ResolveResponse> =
        flow {
            val episode = if (request.hasEpisode()) request.episode else null
            val (outcome, resume) =
                withContext(io) {
                    val outcome = resolver.resolve(request.media, episode)
                    val resume = if (outcome is ResolveOutcome.Sources) progress.get(request.media, episode).asResume() else null
                    outcome to resume
                }
            when (outcome) {
                is ResolveOutcome.Sources ->
                    outcome.sources.withIndex().collect { (position, source) ->
                        val response = ResolveResponse.newBuilder().setSource(source)
                        if (position == 0 && resume != null) response.setResume(resume)
                        emit(response.build())
                    }
                else -> throw errors.of(outcome)
            }
        }.catch { failure -> throw errors.ofFailure(failure) ?: failure }

    /**
     * The index's answer: a film it knows may have sources. A request `Resolve` would refuse is
     * refused the same way, and a valid series is `NOT_FOUND`, as `Resolve` answers it.
     */
    override suspend fun probe(request: ProbeRequest): ProbeResponse = withContext(io) { probeOnIo(request) }

    private suspend fun probeOnIo(request: ProbeRequest): ProbeResponse {
        CAPABILITIES.requireDeclared(Capability.CAPABILITY_PROBE)
        val media = request.media
        val refusal =
            unusableRequest(media, if (request.hasEpisode()) request.episode else null)?.let { ResolveOutcome.Invalid(it) }
                ?: ResolveOutcome.NotFound.takeIf { media.mediaType == MediaType.MEDIA_TYPE_TV }
        val hit = if (refusal == null) index.lookup(media) else null
        val failure = refusal ?: ResolveOutcome.NotAFilm.takeIf { hit?.isFilm == false }
        if (failure != null) throw errors.of(failure)
        return ProbeResponse.newBuilder().setMayHaveSources(hit != null).build()
    }

    /**
     * Records a report against the Archive item the source came from. A `source_id` is
     * `<identifier>/<file name>`, the identifier in the Archive's character set; one in another shape
     * was not issued here and is `INVALID_ARGUMENT`. The index is not consulted: `Resolve` also serves
     * items the routes found live, which the index may not hold.
     */
    override suspend fun reportSource(request: ReportSourceRequest): ReportSourceResponse = withContext(io) { reportOnIo(request) }

    private suspend fun reportOnIo(request: ReportSourceRequest): ReportSourceResponse {
        CAPABILITIES.requireDeclared(Capability.CAPABILITY_REPORT_SOURCE)
        val identifier = request.sourceId.substringBefore('/', missingDelimiterValue = "")
        val fileName = request.sourceId.substringAfter('/', missingDelimiterValue = "")
        if (!ARCHIVE_IDENTIFIER.matches(identifier) || fileName.isEmpty()) throw errors.invalid("Not a source id this companion issues")
        val problem =
            when (request.problem) {
                SourceProblem.SOURCE_PROBLEM_UNPLAYABLE,
                SourceProblem.SOURCE_PROBLEM_WRONG_TITLE,
                SourceProblem.SOURCE_PROBLEM_EXPIRED,
                SourceProblem.SOURCE_PROBLEM_OTHER,
                -> request.problem
                // The contract lets an unknown or unspecified problem be read as OTHER.
                else -> SourceProblem.SOURCE_PROBLEM_OTHER
            }
        records.record(identifier, problem.name)
        return ReportSourceResponse.getDefaultInstance()
    }

    /**
     * Applies one report of a sitting to the title's progress, by the SDK's `applyReport`: a finished
     * movie, or a position of 0, holds nothing. The `source_id` is not read: progress is keyed by title.
     */
    override suspend fun reportPlayback(request: ReportPlaybackRequest): ReportPlaybackResponse =
        withContext(io) {
            CAPABILITIES.requireDeclared(Capability.CAPABILITY_PLAYBACK_PROGRESS)
            request.requireValid()
            if (request.hasEpisode()) refuseUnusableEpisode(request.media, request.episode)
            progress.report(request)
            ReportPlaybackResponse.getDefaultInstance()
        }

    /**
     * One page of "Continue watching": one entry per title, most recently played first, ids and
     * progress only. One row more than the page is read, to know whether another page follows.
     */
    override suspend fun listContinueWatching(request: ListContinueWatchingRequest): ListContinueWatchingResponse =
        withContext(io) {
            CAPABILITIES.requireDeclared(Capability.CAPABILITY_PLAYBACK_PROGRESS)
            val size = request.effectivePageSize(max = MAX_LIST_PAGE_SIZE)
            val skip = ContinueWatchingPageToken.skipOf(request)
            val entries = progress.continueWatching(limit = size + 1, offset = skip)
            ListContinueWatchingResponse
                .newBuilder()
                .addAllEntries(entries.take(size))
                .apply { if (entries.size > size) setNextPageToken(ContinueWatchingPageToken.issue(request, skip + size)) }
                .build()
        }

    /** Forgets a movie, one episode, or with no episode a whole series. OK when nothing is held. */
    override suspend fun forgetProgress(request: ForgetProgressRequest): ForgetProgressResponse =
        withContext(io) {
            CAPABILITIES.requireDeclared(Capability.CAPABILITY_PLAYBACK_PROGRESS)
            request.requireValid()
            if (request.hasEpisode()) refuseUnusableEpisode(request.media, request.episode)
            progress.forget(request.media, if (request.hasEpisode()) request.episode else null)
            ForgetProgressResponse.getDefaultInstance()
        }

    /** An episode `Resolve` would refuse is refused here too, so no row is kept for an episode never issued. */
    private fun refuseUnusableEpisode(
        media: MediaId,
        episode: EpisodeRef,
    ) {
        unusableRequest(media, episode)?.let { throw errors.invalid(it) }
    }

    companion object {
        private val ARCHIVE_IDENTIFIER = Regex("[A-Za-z0-9][A-Za-z0-9_.-]*")

        const val PROVIDER_NAME = "Internet Archive"

        /** Fixed: nothing here depends on a user or a session. */
        val CAPABILITIES: Set<Capability> =
            setOf(
                Capability.CAPABILITY_PROBE,
                Capability.CAPABILITY_SUBTITLES,
                Capability.CAPABILITY_REPORT_SOURCE,
                Capability.CAPABILITY_PLAYBACK_PROGRESS,
            )
    }
}
