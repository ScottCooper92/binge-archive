package com.cooper.binge.companion.archive.reports

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.binge.companion.contracts.stream.v1.ReportPlaybackRequest
import com.binge.companion.contracts.v1.EpisodeRef
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.contracts.v1.PlaybackEvent
import com.cooper.binge.companion.archive.index.movie
import com.cooper.binge.companion.archive.index.series
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class PlaybackProgressDaoTest {
    private val database =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeviceRecordsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val dao = database.progressDao()
    private var now = 1_000L
    private val store = PlaybackProgressStore(database, clock = { now })

    @After
    fun close() {
        database.close()
    }

    private fun row(
        tmdbId: Int,
        lastPlayed: Long,
        mediaType: MediaType = MediaType.MEDIA_TYPE_MOVIE,
        season: Int = PlaybackProgressEntity.NO_EPISODE,
        episode: Int = PlaybackProgressEntity.NO_EPISODE,
        position: Long = 60_000,
    ) = PlaybackProgressEntity(
        mediaType = mediaType.number,
        tmdbId = tmdbId,
        season = season,
        episode = episode,
        positionMillis = position,
        runtimeMillis = 600_000,
        lastPlayed = lastPlayed,
        completed = false,
    )

    private fun episodeRow(
        tmdbId: Int,
        season: Int,
        episode: Int,
        lastPlayed: Long,
    ) = row(tmdbId, lastPlayed, MediaType.MEDIA_TYPE_TV, season, episode)

    private fun ref(
        season: Int,
        episode: Int,
    ): EpisodeRef =
        EpisodeRef
            .newBuilder()
            .setSeasonNumber(season)
            .setEpisodeNumber(episode)
            .build()

    private fun report(
        media: MediaId,
        episode: EpisodeRef? = null,
        event: PlaybackEvent = PlaybackEvent.PLAYBACK_EVENT_PROGRESS,
        position: Long = 60_000,
        runtime: Long = 600_000,
    ) = ReportPlaybackRequest
        .newBuilder()
        .setMedia(media)
        .apply { episode?.let(::setEpisode) }
        .setEvent(event)
        .setPositionMillis(position)
        .setRuntimeMillis(runtime)
        .build()

    @Test
    fun `an upserted row reads back by its key and is replaced by the next`() =
        runTest {
            dao.upsert(row(653, lastPlayed = 1))
            dao.upsert(row(653, lastPlayed = 2, position = 90_000))

            val held = dao.get(MediaType.MEDIA_TYPE_MOVIE.number, 653, -1, -1)
            assertEquals(90_000L, held?.positionMillis)
            assertEquals(2L, held?.lastPlayed)
            assertNull(dao.get(MediaType.MEDIA_TYPE_MOVIE.number, 10331, -1, -1))
        }

    @Test
    fun `a series lists once, as its most recently played episode`() =
        runTest {
            dao.upsert(episodeRow(894, season = 1, episode = 3, lastPlayed = 10))
            dao.upsert(episodeRow(894, season = 1, episode = 1, lastPlayed = 30))
            dao.upsert(episodeRow(894, season = 2, episode = 1, lastPlayed = 20))

            val listed = dao.inProgress(limit = 10, offset = 0)

            assertEquals(1, listed.size)
            assertEquals(1 to 1, listed.single().season to listed.single().episode)
        }

    @Test
    fun `episodes played at the same moment list the later one`() =
        runTest {
            dao.upsert(episodeRow(894, season = 1, episode = 2, lastPlayed = 10))
            dao.upsert(episodeRow(894, season = 1, episode = 4, lastPlayed = 10))
            dao.upsert(episodeRow(894, season = 0, episode = 9, lastPlayed = 10))

            assertEquals(listOf(1 to 4), dao.inProgress(10, 0).map { it.season to it.episode })
        }

    @Test
    fun `titles list most recently played first, ties by key, and page without overlap`() =
        runTest {
            dao.upsert(row(3, lastPlayed = 50))
            dao.upsert(row(1, lastPlayed = 50))
            dao.upsert(row(2, lastPlayed = 70))
            dao.upsert(episodeRow(1, season = 1, episode = 1, lastPlayed = 60))
            dao.upsert(row(4, lastPlayed = 10))

            val all = dao.inProgress(limit = 10, offset = 0).map { it.mediaType to it.tmdbId }
            assertEquals(listOf(1 to 2, 2 to 1, 1 to 1, 1 to 3, 1 to 4), all)
            val paged = (0 until 5 step 2).flatMap { dao.inProgress(limit = 2, offset = it) }.map { it.mediaType to it.tmdbId }
            assertEquals(all, paged)
        }

    @Test
    fun `forgetting an episode keeps the rest of its series, forgetting the series drops it all`() =
        runTest {
            store.report(report(series(894), ref(1, 1)))
            store.report(report(series(894), ref(1, 2)))
            store.report(report(movie(894)))

            store.forget(series(894), ref(1, 2))
            assertNull(store.get(series(894), ref(1, 2)))
            assertTrue(store.get(series(894), ref(1, 1)) != null)

            store.forget(series(894), null)
            assertNull(store.get(series(894), ref(1, 1)))
            assertTrue("a movie with the same id is another title", store.get(movie(894), null) != null)

            store.forget(movie(894), null)
            assertNull(store.get(movie(894), null))
            assertEquals(emptyList<Any>(), store.continueWatching(10, 0))
        }

    @Test
    fun `a series' specials are season 0, never a movie's key`() =
        runTest {
            store.report(report(series(894), ref(0, 1), position = 1_000))
            store.report(report(movie(894), position = 2_000))

            assertEquals(1_000L, store.get(series(894), ref(0, 1))?.positionMillis)
            assertEquals(2_000L, store.get(movie(894), null)?.positionMillis)
            val entries = store.continueWatching(10, 0)
            assertEquals(2, entries.size)
            val special = entries.single { it.media.mediaType == MediaType.MEDIA_TYPE_TV }
            assertEquals(0, special.episode.seasonNumber)
            assertTrue(special.hasEpisode())
            assertFalse(entries.single { it.media.mediaType == MediaType.MEDIA_TYPE_MOVIE }.hasEpisode())
        }

    @Test
    fun `a stored report reads back as the contract's progress, stamped by the clock`() =
        runTest {
            now = 1_234_567L
            store.report(report(movie(653), position = 42_000, runtime = 0))

            val held = store.get(movie(653), null)
            assertEquals(42_000L, held?.positionMillis)
            assertEquals(0L, held?.runtimeMillis)
            assertEquals(1_234L, held?.lastPlayed?.seconds)
            assertEquals(567_000_000, held?.lastPlayed?.nanos)
            assertFalse(held!!.completed)
        }

    @Test
    fun `the count is titles, not rows, and clearing forgets every one`() =
        runTest {
            assertEquals(0, store.titleCount().first())
            dao.upsert(row(653, lastPlayed = 1))
            dao.upsert(row(894, lastPlayed = 1))
            dao.upsert(episodeRow(894, season = 1, episode = 1, lastPlayed = 1))
            dao.upsert(episodeRow(894, season = 1, episode = 2, lastPlayed = 2))

            assertEquals("a movie, a movie with the same id as a series, and the series once", 3, store.titleCount().first())

            store.clear()
            assertEquals(0, store.titleCount().first())
            assertEquals(emptyList<Any>(), store.continueWatching(10, 0))
        }

    @Test
    fun `a title not played for 90 days is forgotten, one played 89 days ago is kept`() =
        runTest {
            now = days(200)
            dao.upsert(row(653, lastPlayed = now - days(89)))
            dao.upsert(row(10331, lastPlayed = now - days(91)))
            dao.upsert(row(3082, lastPlayed = now - days(90)))

            store.pruneUnplayed()

            assertTrue(store.get(movie(653), null) != null)
            assertNull(store.get(movie(10331), null))
            assertTrue("exactly 90 days is still inside the window", store.get(movie(3082), null) != null)
        }

    @Test
    fun `a series is judged by its latest episode, so one in progress keeps every episode`() =
        runTest {
            now = days(200)
            dao.upsert(episodeRow(894, season = 1, episode = 1, lastPlayed = now - days(150)))
            dao.upsert(episodeRow(894, season = 1, episode = 2, lastPlayed = now - days(10)))
            dao.upsert(episodeRow(1399, season = 1, episode = 1, lastPlayed = now - days(120)))
            dao.upsert(episodeRow(1399, season = 1, episode = 2, lastPlayed = now - days(91)))

            store.pruneUnplayed()

            assertTrue("an old episode of a series still in progress stays", store.get(series(894), ref(1, 1)) != null)
            assertTrue(store.get(series(894), ref(1, 2)) != null)
            assertNull(store.get(series(1399), ref(1, 1)))
            assertNull(store.get(series(1399), ref(1, 2)))
            assertEquals(1, store.titleCount().first())
        }

    private fun days(count: Long): Long = TimeUnit.DAYS.toMillis(count)
}
