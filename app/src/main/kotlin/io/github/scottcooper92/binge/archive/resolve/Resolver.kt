package io.github.scottcooper92.binge.archive.resolve

import com.binge.companion.contracts.stream.v1.StreamSource
import com.binge.companion.contracts.v1.EpisodeRef
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import io.github.scottcooper92.binge.archive.index.IndexRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

/**
 * What a `Resolve` comes to: `stream.proto`'s four answers. The Service maps each to the contract:
 *
 * - [Sources]: OK. The flow may complete empty: a known title with no licit source.
 * - [NotFound]: `NOT_FOUND`, a title not in the catalogue. For a film, that is one neither the
 *   index nor any network route can place on the Archive. A valid series is always this: v1
 *   carries no series.
 * - [NotAFilm]: `INVALID_ARGUMENT`, a movie id the index knows is not a film.
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

/**
 * Resolves a title to [StreamSource]s. The index answers first: a known non-film is [ResolveOutcome.NotAFilm].
 * Then [ItemFinder] looks for Archive items, for a film the index knows and for one it does not, since a
 * network route can place a film the index has not linked. A film no route places is
 * [ResolveOutcome.NotFound]; otherwise [ItemReader] and [SourceMapper] turn the items into sources.
 */
class Resolver(
    private val index: IndexRepository,
    private val finder: ItemFinder,
    private val reader: ItemReader,
    private val mapper: SourceMapper,
    private val coverage: CoverageLog,
) {
    suspend fun resolve(
        media: MediaId,
        episode: EpisodeRef?,
    ): ResolveOutcome {
        val invalidReason = invalid(media, episode)
        if (invalidReason != null) return ResolveOutcome.Invalid(invalidReason)
        if (media.mediaType == MediaType.MEDIA_TYPE_TV) return ResolveOutcome.NotFound
        val hit = index.lookup(media)
        if (hit != null && !hit.isFilm) return ResolveOutcome.NotAFilm
        val tmdbId = media.tmdbId
        val found = finder.find(tmdbId)
        val inIndex = hit != null
        return when {
            found.candidates.isNotEmpty() -> {
                if (!inIndex) coverage.miss(CoverageMiss(tmdbId, found.route, CoverageMiss.Reason.NOT_IN_INDEX))
                ResolveOutcome.Sources(sources(tmdbId, found))
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

    private fun sources(
        tmdbId: Int,
        found: Found,
    ): Flow<StreamSource> =
        flow {
            val items = reader.read(found)
            val sources = mapper.map(items, found.facts)
            when {
                items.isEmpty() -> coverage.miss(CoverageMiss(tmdbId, found.route, CoverageMiss.Reason.NO_LICIT_ITEM))
                sources.isEmpty() -> coverage.miss(CoverageMiss(tmdbId, found.route, CoverageMiss.Reason.NO_PLAYABLE_FILE))
            }
            sources.asFlow().collect { emit(it) }
        }

    /** The contract's unusable requests, as a reason, or null for a usable one. */
    private fun invalid(
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
}
