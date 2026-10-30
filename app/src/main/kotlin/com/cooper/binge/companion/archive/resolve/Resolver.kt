package com.cooper.binge.companion.archive.resolve

import android.util.Log
import com.binge.companion.contracts.stream.v1.StreamSource
import com.binge.companion.contracts.v1.EpisodeRef
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.archive.index.IndexHit
import com.cooper.binge.companion.archive.index.IndexRepository
import com.cooper.binge.companion.archive.settings.SourceSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * What a `Resolve` comes to: `stream.proto`'s four answers. The Service maps each to the contract:
 *
 * - [Sources]: OK. The flow may complete empty: a known title with no licit source.
 * - [NotFound]: `NOT_FOUND`, a title not in the catalogue. For a film, that is one neither the
 *   index nor any network route can place on the Archive. A valid series is always this: v1
 *   carries no series.
 * - [NotAFilm]: `INVALID_ARGUMENT`, a movie id the index knows is a known non-film kind (a series,
 *   an episode, a literary work…). Anything else Wikidata calls it goes on to the routes.
 * - [Invalid]: `INVALID_ARGUMENT`, a request the contract calls unusable.
 *
 * A network failure is an `IOException`, out of [Resolver.resolve] or out of the flow, which the
 * Service maps to `UNAVAILABLE`.
 */
sealed interface ResolveOutcome {
    data class Sources(
        val sources: Flow<StreamSource>,
    ) : ResolveOutcome

    data object NotFound : ResolveOutcome

    data object NotAFilm : ResolveOutcome

    data class Invalid(
        val reason: String,
    ) : ResolveOutcome
}

/** What the Service resolves through. [Resolver] is the one implementation; the Service's tests stub it. */
fun interface TitleResolver {
    suspend fun resolve(
        media: MediaId,
        episode: EpisodeRef?,
    ): ResolveOutcome
}

/**
 * Resolves a title to [StreamSource]s. The index answers first: a known non-film is [ResolveOutcome.NotAFilm].
 * Then [ItemFinder] looks for Archive items, for a film the index knows and for one it does not, since a
 * network route can place a film the index has not linked. When Wikidata fails, the finder falls back
 * on the index's own Archive ids for the film, and the fallback is logged as a [CoverageMiss]. A film no route places is
 * [ResolveOutcome.NotFound]; otherwise [ItemReader] and [SourceMapper] turn the items into sources.
 *
 * The user's [SourceSettings] are read once, before the routes, and hold for the whole resolve. They
 * never change the answer's kind: a known film they filter down to nothing is still OK and empty.
 */
class Resolver(
    private val index: IndexRepository,
    private val finder: ItemFinder,
    private val reader: ItemReader,
    private val mapper: SourceMapper,
    private val coverage: CoverageLog,
    private val deranked: suspend () -> Set<String> = { emptySet() },
    /** Told of each resolve that found sources: the TMDB id, the title and the first source's Archive item. */
    private val recordResolved: suspend (tmdbId: Int, title: String, identifier: String) -> Unit = { _, _, _ -> },
    /** The user's settings now. A failure to read them is an `IOException`, so the resolve fails. */
    private val settings: suspend () -> SourceSettings = { SourceSettings.DEFAULT },
    private val timings: PhaseTimings = PhaseTimings.NONE,
    /** How long a whole resolve may take, from its start, before it ends with a [ResolveTimeoutException]. */
    private val budgetMillis: Long = RESOLVE_BUDGET_MILLIS,
    /** False in the app: a first `Resolve` does not spend its deadline waiting for the seed (see [IndexRepository.lookupWithoutWaiting]). */
    private val waitForSeed: Boolean = true,
    /** What a debug build writes about each resolve; [ResolveLog.NONE] in release. */
    private val log: ResolveLog = ResolveLog.NONE,
) : TitleResolver {
    override suspend fun resolve(
        media: MediaId,
        episode: EpisodeRef?,
    ): ResolveOutcome {
        val invalidReason = unusableRequest(media, episode)
        if (invalidReason != null) return ResolveOutcome.Invalid(invalidReason)
        if (media.mediaType == MediaType.MEDIA_TYPE_TV) return ResolveOutcome.NotFound
        val started = timings.mark()
        val begun = System.nanoTime()
        val hit = timings.time("index_lookup") { lookupIndex(media) }
        if (hit != null && !hit.isFilm) return ResolveOutcome.NotAFilm
        val tmdbId = media.tmdbId
        val settings = timings.time("settings_read") { settings() }
        log.line { ResolveEvents.start(tmdbId, settings.licences.name, settings.titleMatches) }
        val found = findLogged(tmdbId, hit, settings, begun)
        log.line { ResolveEvents.candidates(found.route, found.candidates.map { it.identifier }, found.fromIndex) }
        val inIndex = hit != null
        val outcome =
            when {
                found.candidates.isNotEmpty() -> {
                    if (found.fromIndex) coverage.miss(CoverageMiss(tmdbId, found.route, CoverageMiss.Reason.WIKIDATA_FAILED))
                    if (!inIndex) coverage.miss(CoverageMiss(tmdbId, found.route, CoverageMiss.Reason.NOT_IN_INDEX))
                    ResolveOutcome.Sources(sources(tmdbId, found, settings, started, begun))
                }
                found.titleSkipped -> {
                    coverage.miss(CoverageMiss(tmdbId, Route.TITLE, CoverageMiss.Reason.FILTERED_BY_SETTINGS))
                    if (inIndex) ResolveOutcome.Sources(emptyFlow()) else ResolveOutcome.NotFound
                }
                inIndex -> {
                    coverage.miss(CoverageMiss(tmdbId, route = null, reason = CoverageMiss.Reason.NO_ITEMS))
                    ResolveOutcome.Sources(emptyFlow())
                }
                else -> {
                    coverage.miss(CoverageMiss(tmdbId, route = null, reason = CoverageMiss.Reason.NOT_IN_CATALOGUE))
                    ResolveOutcome.NotFound
                }
            }
        logAnswer(outcome, found, begun)
        return outcome
    }

    private suspend fun lookupIndex(media: MediaId): IndexHit? = if (waitForSeed) index.lookup(media) else index.lookupWithoutWaiting(media)

    /** [ItemFinder.find], with a failure written to the debug log before it goes on to the host. */
    private suspend fun findLogged(
        tmdbId: Int,
        hit: IndexHit?,
        settings: SourceSettings,
        begun: Long,
    ): Found =
        try {
            withContext(deadline(begun)) {
                timings.time("find_items") { finder.find(tmdbId, hit, titleMatches = settings.titleMatches) }
            }
        } catch (e: IOException) {
            log.line { ResolveEvents.failed(e, elapsedSince(begun)) }
            throw e
        }

    /** The budget as a deadline every HTTP call of this resolve reads, so a call in flight is cancelled by the clock, not by the dispatcher. */
    private fun deadline(begun: Long): ResolveDeadline = ResolveDeadline(begun + budgetMillis * NANOS_PER_MILLI)

    /** The answer, unless a flow of sources will write it when it completes. */
    private fun logAnswer(
        outcome: ResolveOutcome,
        found: Found,
        begun: Long,
    ) {
        if (outcome !is ResolveOutcome.Sources || found.candidates.isEmpty()) {
            log.line { ResolveEvents.answer(outcome.logWord(), elapsedSince(begun)) }
        }
    }

    private fun logSettingsDrops(
        identifier: String,
        all: Int,
        kept: Int,
    ) {
        if (all == 0) log.line { ResolveEvents.dropped(identifier, "no_video_file") }
        if (kept < all) log.line { ResolveEvents.dropped(identifier, "settings_dropped_${all - kept}_of_$all") }
    }

    private fun logInterrupted(
        cause: Throwable?,
        begun: Long,
    ) {
        when (cause) {
            null -> Unit
            is CancellationException ->
                log.line {
                    ResolveEvents.answer(
                        "interrupted by ${cause.javaClass.simpleName}",
                        elapsedSince(begun),
                    )
                }
            else -> log.line { ResolveEvents.failed(cause, elapsedSince(begun)) }
        }
    }

    /**
     * Each item's sources as soon as the item is read, so a deadline keeps what was found before it. Items
     * come in download order; one this device reported is held back and sent after every other. At most
     * [SourceMapper.MAX_SOURCES] in all, after the user's [settings] have dropped what they exclude.
     */
    private fun sources(
        tmdbId: Int,
        found: Found,
        settings: SourceSettings,
        started: Long,
        begun: Long,
    ): Flow<StreamSource> =
        flow {
            val derankedIds = attempt { deranked() }.onFailure { bookkeepingFailed("deranking", it) }.getOrDefault(emptySet())
            val expiresAt = mapper.expiresAt()
            val heldBack = mutableListOf<LicensedItem>()
            var items = 0
            var sent = 0
            var filtered = false

            suspend fun send(licensed: LicensedItem) {
                val all = mapper.sources(licensed, found.facts, expiresAt)
                val kept = mapper.kept(all, settings)
                logSettingsDrops(licensed.item.identifier, all.size, kept.size)
                if (kept.size < all.size) filtered = true
                for (source in kept) {
                    if (sent == SourceMapper.MAX_SOURCES) return
                    if (sent == 0) {
                        timings.since("first_source", started)
                        attempt {
                            recordResolved(
                                tmdbId,
                                found.facts.title ?: licensed.item.title ?: licensed.item.identifier,
                                licensed.item.identifier,
                            )
                        }.onFailure { bookkeepingFailed("recording the title", it) }
                    }
                    emit(source)
                    sent++
                }
            }

            reader
                .readEach(
                    found,
                    settings.licences,
                    onFiltered = { filtered = true },
                ).flowOn(deadline(begun))
                .collect { licensed ->
                    items++
                    if (licensed.item.identifier in derankedIds) heldBack += licensed else send(licensed)
                }
            heldBack.forEach { send(it) }
            log.line { ResolveEvents.answer("sources=$sent items=$items", elapsedSince(begun)) }
            when {
                sent == 0 && filtered -> coverage.miss(CoverageMiss(tmdbId, found.route, CoverageMiss.Reason.FILTERED_BY_SETTINGS))
                items == 0 -> coverage.miss(CoverageMiss(tmdbId, found.route, CoverageMiss.Reason.NO_LICIT_ITEM))
                sent == 0 -> coverage.miss(CoverageMiss(tmdbId, found.route, CoverageMiss.Reason.NO_PLAYABLE_FILE))
            }
        }.onCompletion { cause -> logInterrupted(cause, begun) }
}

private const val TAG = "ArchiveResolver"

/** Deranking and the recent-titles list are the device's own: when they fail, the sources still go out. */
private fun bookkeepingFailed(
    what: String,
    cause: Throwable,
) {
    Log.w(TAG, "Skipped $what: the device records failed", cause)
}

/**
 * The contract's unusable requests, as a reason, or null for a usable one. Shared by `Resolve` and
 * `Probe`, which refuse the same requests.
 */
fun unusableRequest(
    media: MediaId,
    episode: EpisodeRef?,
): String? =
    when {
        media.tmdbId <= 0 -> "tmdb_id must be positive"
        media.mediaType == MediaType.MEDIA_TYPE_MOVIE && episode != null -> "a movie takes no episode"
        media.mediaType == MediaType.MEDIA_TYPE_TV && episode == null -> "a series needs an episode"
        media.mediaType == MediaType.MEDIA_TYPE_TV && episode != null && (episode.seasonNumber < 0 || episode.episodeNumber < 1) ->
            "no such episode"
        media.mediaType != MediaType.MEDIA_TYPE_MOVIE && media.mediaType != MediaType.MEDIA_TYPE_TV -> "unknown media type"
        else -> null
    }

private fun elapsedSince(begun: Long): Long = (System.nanoTime() - begun) / NANOS_PER_MILLI

private const val NANOS_PER_MILLI = 1_000_000L

/** The outcome as the debug log words it. A [ResolveOutcome.Sources] with no candidates is an empty, finished answer. */
private fun ResolveOutcome.logWord(): String =
    when (this) {
        is ResolveOutcome.Sources -> "sources=0 (nothing to read)"
        ResolveOutcome.NotFound -> "not_found"
        ResolveOutcome.NotAFilm -> "not_a_film"
        is ResolveOutcome.Invalid -> "invalid"
    }

/**
 * The resolve ran past its budget, which sits inside the host's 10 s deadline so the answer is ours rather than a deadline.
 * An [IOException], so the Service answers `UNAVAILABLE`; [ArchiveErrors] words it as taking too long, also when it is the
 * cause of the `IOException` a reader or finder raises after its calls ran out of time.
 */
class ResolveTimeoutException(
    cause: Throwable? = null,
) : IOException("The resolve ran past its budget", cause)

/**
 * When a resolve must be over, on the monotonic clock, carried in the coroutine context so every HTTP call it makes reads
 * it ([ResolverHttp]). A call's own timeout is the OkHttp clock, not a coroutine timer, so it runs in real time whichever
 * dispatcher the caller is on, and cancels the call in flight.
 */
class ResolveDeadline(
    val atNanos: Long,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ResolveDeadline>

    /** What is left, in milliseconds. Zero or less means the budget is spent. */
    fun remainingMillis(now: Long = System.nanoTime()): Long = (atNanos - now) / NANOS_PER_MILLI_DEADLINE
}

private const val NANOS_PER_MILLI_DEADLINE = 1_000_000L

/** About 8 s: well inside the host's 10 s deadline, so a slow lookup is told apart from a call the host gave up on. */
const val RESOLVE_BUDGET_MILLIS = 8_000L
