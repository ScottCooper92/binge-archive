package io.github.scottcooper92.binge.archive.resolve

import android.util.Log
import com.binge.companion.contracts.stream.v1.StreamSource
import com.binge.companion.contracts.v1.EpisodeRef
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.sdk.attempt
import io.github.scottcooper92.binge.archive.index.IndexRepository
import io.github.scottcooper92.binge.archive.settings.SourceSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

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
) : TitleResolver {
    override suspend fun resolve(
        media: MediaId,
        episode: EpisodeRef?,
    ): ResolveOutcome {
        val invalidReason = unusableRequest(media, episode)
        if (invalidReason != null) return ResolveOutcome.Invalid(invalidReason)
        if (media.mediaType == MediaType.MEDIA_TYPE_TV) return ResolveOutcome.NotFound
        val hit = index.lookup(media)
        if (hit != null && !hit.isFilm) return ResolveOutcome.NotAFilm
        val tmdbId = media.tmdbId
        val settings = settings()
        val found = finder.find(tmdbId, hit, titleMatches = settings.titleMatches)
        val inIndex = hit != null
        return when {
            found.candidates.isNotEmpty() -> {
                if (found.fromIndex) coverage.miss(CoverageMiss(tmdbId, found.route, CoverageMiss.Reason.WIKIDATA_FAILED))
                if (!inIndex) coverage.miss(CoverageMiss(tmdbId, found.route, CoverageMiss.Reason.NOT_IN_INDEX))
                ResolveOutcome.Sources(sources(tmdbId, found, settings))
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
                if (kept.size < all.size) filtered = true
                for (source in kept) {
                    if (sent == SourceMapper.MAX_SOURCES) return
                    if (sent == 0) {
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

            reader.readEach(found, settings.licences, onFiltered = { filtered = true }).collect { licensed ->
                items++
                if (licensed.item.identifier in derankedIds) heldBack += licensed else send(licensed)
            }
            heldBack.forEach { send(it) }
            when {
                sent == 0 && filtered -> coverage.miss(CoverageMiss(tmdbId, found.route, CoverageMiss.Reason.FILTERED_BY_SETTINGS))
                items == 0 -> coverage.miss(CoverageMiss(tmdbId, found.route, CoverageMiss.Reason.NO_LICIT_ITEM))
                sent == 0 -> coverage.miss(CoverageMiss(tmdbId, found.route, CoverageMiss.Reason.NO_PLAYABLE_FILE))
            }
        }
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
