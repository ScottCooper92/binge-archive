package com.cooper.binge.companion.archive.index

import java.io.BufferedReader
import java.io.IOException

/**
 * The seed index shipped in the APK's assets, so a fresh install can answer before its first
 * refresh. The build writes it from a live [IndexSnapshot]; the app reads it back into the same type.
 *
 * Plain tab-separated lines after a version header, one record per line:
 *
 *     L <movie|tv> <tmdb id> <archive id> <1|0 may be a film>
 *     A <archive id> <imdb id>
 *     I <imdb id> <tmdb id> <1|0 may be a film>
 *
 * v2 changed what the flag means, not the layout: 0 is now a known non-film kind, where in v1 it
 * was anything not a subclass of film. A v1 seed is refused rather than read with the old meaning.
 */
object SeedFormat {
    const val ASSET_PATH = "index/seed.tsv"
    private const val HEADER = "# binge-archive index seed v2"

    fun write(
        snapshot: IndexSnapshot,
        out: Appendable,
    ) {
        out.append(HEADER).append('\n')
        snapshot.links.forEach { out.append(row("L", it.mediaType.name.lowercase(), it.tmdbId, it.archiveId, it.isFilm.flag())) }
        snapshot.archiveItems.forEach { out.append(row("A", it.archiveId, it.imdbId)) }
        snapshot.imdbLinks.forEach { out.append(row("I", it.imdbId, it.tmdbId, it.isFilm.flag())) }
    }

    fun read(reader: BufferedReader): IndexSnapshot {
        val lines = reader.lineSequence().iterator()
        if (!lines.hasNext() || lines.next() != HEADER) throw IOException("Not a v2 index seed")
        val links = mutableListOf<WikidataLink>()
        val archiveItems = mutableListOf<ArchiveImdbItem>()
        val imdbLinks = mutableListOf<ImdbTmdbLink>()
        lines.forEach { line ->
            if (line.isBlank()) return@forEach
            val tag = line.substringBefore('\t')
            val cells = line.split('\t').drop(1)
            when (tag) {
                "L" -> links += link(fields(cells, count = 4))
                "A" -> fields(cells, count = 2).let { (archiveId, imdbId) -> archiveItems += ArchiveImdbItem(archiveId, imdbId) }
                "I" ->
                    fields(cells, count = 3).let { (imdbId, tmdbId, film) ->
                        imdbLinks += ImdbTmdbLink(imdbId, tmdbId(tmdbId), film == "1")
                    }
                else -> throw IOException("Unknown seed record: $tag")
            }
        }
        return IndexSnapshot(links = links, archiveItems = archiveItems, imdbLinks = imdbLinks)
    }

    private fun link(cells: List<String>): WikidataLink {
        val type =
            when (val mediaType = cells.first()) {
                "movie" -> LinkedMediaType.MOVIE
                "tv" -> LinkedMediaType.TV
                else -> throw IOException("Unknown media type: $mediaType")
            }
        val (tmdbId, archiveId, film) = cells.drop(1)
        return WikidataLink(type, tmdbId(tmdbId), archiveId, film == "1")
    }

    /** The record's fields after its tag, refused unless there are exactly [count] and none is empty. */
    private fun fields(
        cells: List<String>,
        count: Int,
    ): List<String> {
        if (cells.size != count || cells.any { it.isEmpty() }) throw IOException("Seed record needs $count fields: $cells")
        return cells
    }

    private fun tmdbId(value: String): Int = value.toTmdbId() ?: throw IOException("Bad TMDB id: $value")

    private fun Boolean.flag(): String = if (this) "1" else "0"

    private fun row(vararg cells: Any): String = cells.joinToString("\t", postfix = "\n")
}
