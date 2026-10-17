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
 * What a `Resolve` comes to. The Service maps each to the contract's answer:
 *
 * - [Sources]: OK. The flow may complete empty, which is a film the index knows with no licit copy.
 * - [NotFound]: `NOT_FOUND`, a TMDB id the index does not know.
 * - [NotAFilm]: `INVALID_ARGUMENT`, an id the index knows that is not a film.
 * - [Invalid]: `INVALID_ARGUMENT`, a request the contract calls unusable.
 *
 * A network failure while the sources are found is an `IOException` out of the flow, which the
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
 * Resolves a title to [StreamSource]s: the index decides whether the title is one this app can
 * place at all, then [ItemFinder], [ItemReader] and [SourceMapper] run in turn. Movies only in v1: a
 * series request is validated and answers no sources.
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
        invalid(media, episode)?.let { return ResolveOutcome.Invalid(it) }
        val hit = index.lookup(media)
        if (hit == null) {
            coverage.miss(CoverageMiss(media.tmdbId, route = null, reason = CoverageMiss.Reason.NOT_IN_INDEX))
            return ResolveOutcome.NotFound
        }
        return when {
            media.mediaType == MediaType.MEDIA_TYPE_TV -> ResolveOutcome.Sources(emptyFlow())
            !hit.isFilm -> ResolveOutcome.NotAFilm
            else -> ResolveOutcome.Sources(sources(media.tmdbId))
        }
    }

    private fun sources(tmdbId: Int): Flow<StreamSource> =
        flow {
            val found = finder.find(tmdbId)
            val items = reader.read(found)
            val sources = mapper.map(items, found.facts)
            when {
                found.candidates.isEmpty() -> coverage.miss(CoverageMiss(tmdbId, found.route, CoverageMiss.Reason.NO_ITEMS))
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
