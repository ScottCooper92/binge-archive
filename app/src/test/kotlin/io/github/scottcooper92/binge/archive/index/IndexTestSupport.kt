package io.github.scottcooper92.binge.archive.index

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType

internal fun inMemoryIndex(): IndexDatabase =
    Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), IndexDatabase::class.java)
        .allowMainThreadQueries()
        .build()

internal fun movie(tmdbId: Int): MediaId =
    MediaId
        .newBuilder()
        .setMediaType(MediaType.MEDIA_TYPE_MOVIE)
        .setTmdbId(tmdbId)
        .build()

internal fun series(tmdbId: Int): MediaId =
    MediaId
        .newBuilder()
        .setMediaType(MediaType.MEDIA_TYPE_TV)
        .setTmdbId(tmdbId)
        .build()

/** A small snapshot shaped like the real one: the two films the issue names, a series and a non-film. */
internal val sampleSnapshot =
    IndexSnapshot(
        links =
            listOf(
                WikidataLink(LinkedMediaType.MOVIE, 653, "Nosferatu_DVD_quality", isFilm = true),
                WikidataLink(LinkedMediaType.MOVIE, 653, "PhantasmagoriaTheater-Nosferatu1922909", isFilm = true),
                WikidataLink(LinkedMediaType.MOVIE, 10331, "Night.Of.The.Living.Dead_1080p", isFilm = true),
                WikidataLink(LinkedMediaType.TV, 894, "The_Prisoner", isFilm = false),
                WikidataLink(LinkedMediaType.MOVIE, 34372, "Execution_of_Mary_1895", isFilm = false),
            ),
        archiveItems =
            listOf(
                ArchiveImdbItem("silent-aelita-queen-of-mars", "tt0014646"),
                ArchiveImdbItem("night_of_the_living_dead_imdb", "tt0063350"),
                ArchiveImdbItem("unmatched_item", "tt9999999"),
            ),
        imdbLinks =
            listOf(
                ImdbTmdbLink("tt0014646", 2973, isFilm = true),
                ImdbTmdbLink("tt0063350", 10331, isFilm = true),
            ),
    )
