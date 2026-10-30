package com.cooper.binge.companion.archive.service

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.binge.companion.contracts.stream.v1.Capability
import com.binge.companion.contracts.stream.v1.ContinueWatchingEntry
import com.binge.companion.contracts.stream.v1.ForgetProgressRequest
import com.binge.companion.contracts.stream.v1.HandshakeRequest
import com.binge.companion.contracts.stream.v1.ListContinueWatchingRequest
import com.binge.companion.contracts.stream.v1.ReportPlaybackRequest
import com.binge.companion.contracts.stream.v1.ResolveRequest
import com.binge.companion.contracts.stream.v1.StreamServiceGrpcKt
import com.binge.companion.contracts.stream.v1.StreamSource
import com.binge.companion.contracts.v1.EpisodeRef
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.contracts.v1.PlaybackEvent
import com.cooper.binge.companion.archive.index.IndexDatabase
import com.cooper.binge.companion.archive.index.IndexRepository
import com.cooper.binge.companion.archive.index.inMemoryIndex
import com.cooper.binge.companion.archive.index.movie
import com.cooper.binge.companion.archive.index.sampleSnapshot
import com.cooper.binge.companion.archive.index.series
import com.cooper.binge.companion.archive.reports.DeviceRecords
import com.cooper.binge.companion.archive.reports.DeviceRecordsDatabase
import com.cooper.binge.companion.archive.reports.PlaybackProgressStore
import com.cooper.binge.companion.archive.resolve.ResolveOutcome
import com.cooper.binge.companion.archive.resolve.TitleResolver
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executors

/** CAPABILITY_PLAYBACK_PROGRESS over an in-process channel, with the resolver stubbed and the store real. */
@RunWith(RobolectricTestRunner::class)
class ArchiveStreamServiceProgressTest {
    private val indexDatabase: IndexDatabase = inMemoryIndex()
    private val recordsDatabase: DeviceRecordsDatabase =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeviceRecordsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private var now = 1_000L
    private var outcome: suspend (MediaId, EpisodeRef?) -> ResolveOutcome = { _, _ -> error("No outcome set") }
    private val messages =
        object : UserMessages {
            override fun notOnArchive() = "Not on the Internet Archive"

            override fun tookTooLong() = "Took too long"

            override fun locale() = "en-GB"
        }
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private val name = InProcessServerBuilder.generateName()
    private val server: Server =
        InProcessServerBuilder
            .forName(name)
            .directExecutor()
            .addService(
                ArchiveStreamService(
                    resolver = TitleResolver { media, episode -> outcome(media, episode) },
                    index = IndexRepository(indexDatabase.indexDao(), seed = { sampleSnapshot }),
                    records = DeviceRecords(recordsDatabase.dao(), clock = { now }),
                    progress = PlaybackProgressStore(recordsDatabase, clock = { now }),
                    versionName = "1.2.3",
                    errors = ArchiveErrors(messages),
                    io = ioExecutor.asCoroutineDispatcher(),
                ),
            ).build()
            .start()
    private val channel: ManagedChannel = InProcessChannelBuilder.forName(name).directExecutor().build()
    private val stub = StreamServiceGrpcKt.StreamServiceCoroutineStub(channel)

    @After
    fun close() {
        channel.shutdownNow()
        server.shutdownNow()
        indexDatabase.close()
        recordsDatabase.close()
        ioExecutor.shutdownNow()
    }

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

    private fun list(
        size: Int = 0,
        token: String = "",
    ) = ListContinueWatchingRequest
        .newBuilder()
        .setPageSize(size)
        .setPageToken(token)
        .build()

    private fun forget(
        media: MediaId,
        episode: EpisodeRef? = null,
    ) = ForgetProgressRequest
        .newBuilder()
        .setMedia(media)
        .apply { episode?.let(::setEpisode) }
        .build()

    private fun resolveRequest(
        media: MediaId,
        episode: EpisodeRef? = null,
    ) = ResolveRequest
        .newBuilder()
        .setMedia(media)
        .apply { episode?.let(::setEpisode) }
        .build()

    private fun source(id: String) = StreamSource.newBuilder().setId(id).build()

    private suspend fun entries(): List<ContinueWatchingEntry> = stub.listContinueWatching(list()).entriesList

    private fun code(block: suspend () -> Unit): Status.Code =
        assertThrows(StatusException::class.java) { runBlocking { block() } }.status.code

    @Test
    fun `the handshake declares watch progress`() =
        runBlocking {
            assertTrue(Capability.CAPABILITY_PLAYBACK_PROGRESS in stub.handshake(HandshakeRequest.getDefaultInstance()).capabilitiesList)
        }

    @Test
    fun `a report the contract calls bad is INVALID_ARGUMENT, and stores nothing`() =
        runBlocking {
            val unspecified = MediaId.newBuilder().setTmdbId(653).build()
            val bad =
                listOf(
                    report(unspecified),
                    report(movie(0)),
                    report(movie(653), ref(1, 1)),
                    report(series(894)),
                    report(series(894), ref(-1, 1)),
                    report(series(894), ref(-1, -1)),
                    report(series(894), ref(1, 0)),
                    report(movie(653), event = PlaybackEvent.PLAYBACK_EVENT_UNSPECIFIED),
                    report(movie(653)).toBuilder().setEventValue(99).build(),
                    report(movie(653), position = -1),
                    report(movie(653), runtime = -1),
                )
            bad.forEach { request -> assertEquals(request.toString(), Status.Code.INVALID_ARGUMENT, code { stub.reportPlayback(request) }) }
            assertEquals(emptyList<ContinueWatchingEntry>(), entries())
        }

    @Test
    fun `a movie in progress lists with its position, and finishing it takes it off`() =
        runBlocking {
            stub.reportPlayback(report(movie(653), position = 90_000))

            val entry = entries().single()
            assertEquals(movie(653), entry.media)
            assertFalse(entry.hasEpisode())
            assertEquals(90_000L, entry.progress.positionMillis)
            assertEquals(1L, entry.progress.lastPlayed.seconds)

            stub.reportPlayback(report(movie(653), event = PlaybackEvent.PLAYBACK_EVENT_FINISHED, position = 590_000))
            assertEquals(emptyList<ContinueWatchingEntry>(), entries())
        }

    @Test
    fun `a finished episode lists as completed at its runtime, until a later report clears it`() =
        runBlocking {
            stub.reportPlayback(report(series(894), ref(1, 2), event = PlaybackEvent.PLAYBACK_EVENT_FINISHED, position = 590_000))

            val finished = entries().single()
            assertEquals(ref(1, 2), finished.episode)
            assertTrue(finished.progress.completed)
            assertEquals(600_000L, finished.progress.positionMillis)

            stub.reportPlayback(report(series(894), ref(1, 2), position = 5_000))
            val rewatched = entries().single()
            assertFalse(rewatched.progress.completed)
            assertEquals(5_000L, rewatched.progress.positionMillis)
        }

    @Test
    fun `a position of 0 stores nothing`() =
        runBlocking {
            stub.reportPlayback(report(movie(653), event = PlaybackEvent.PLAYBACK_EVENT_STARTED, position = 0))

            assertEquals(emptyList<ContinueWatchingEntry>(), entries())
        }

    @Test
    fun `pages follow their tokens to the end, newest first`() =
        runBlocking {
            (1..5).forEach { id ->
                now = id * 1_000L
                stub.reportPlayback(report(movie(id)))
            }

            val first = stub.listContinueWatching(list(size = 2))
            val second = stub.listContinueWatching(list(size = 2, token = first.nextPageToken))
            val last = stub.listContinueWatching(list(size = 2, token = second.nextPageToken))

            assertEquals(listOf(5, 4, 3, 2, 1), (first.entriesList + second.entriesList + last.entriesList).map { it.media.tmdbId })
            assertTrue(first.nextPageToken.isNotEmpty())
            assertEquals("", last.nextPageToken)
        }

    @Test
    fun `a full last page has no next token`() =
        runBlocking {
            stub.reportPlayback(report(movie(1)))
            stub.reportPlayback(report(movie(2)))

            assertEquals("", stub.listContinueWatching(list(size = 2)).nextPageToken)
        }

    @Test
    fun `a garbled or mismatched token, or a negative size, is INVALID_ARGUMENT`() =
        runBlocking {
            (1..3).forEach { stub.reportPlayback(report(movie(it))) }
            val token = stub.listContinueWatching(list(size = 2)).nextPageToken

            assertEquals(Status.Code.INVALID_ARGUMENT, code { stub.listContinueWatching(list(size = 2, token = "garbled")) })
            assertEquals(Status.Code.INVALID_ARGUMENT, code { stub.listContinueWatching(list(size = 3, token = token)) })
            assertEquals(Status.Code.INVALID_ARGUMENT, code { stub.listContinueWatching(list(size = -1)) })
        }

    @Test
    fun `nothing in progress lists empty and OK`() =
        runBlocking {
            val response = stub.listContinueWatching(list())

            assertEquals(0, response.entriesCount)
            assertEquals("", response.nextPageToken)
        }

    @Test
    fun `forgetting is idempotent, takes one episode or a whole series, and refuses an episode on a movie`() =
        runBlocking {
            stub.forgetProgress(forget(movie(653)))
            stub.reportPlayback(report(movie(653)))
            stub.forgetProgress(forget(movie(653)))
            stub.forgetProgress(forget(movie(653)))
            assertEquals(emptyList<ContinueWatchingEntry>(), entries())

            now = 2_000
            stub.reportPlayback(report(series(894), ref(1, 1)))
            now = 3_000
            stub.reportPlayback(report(series(894), ref(1, 2)))
            stub.forgetProgress(forget(series(894), ref(1, 2)))
            assertEquals(ref(1, 1), entries().single().episode)
            stub.forgetProgress(forget(series(894)))
            assertEquals(emptyList<ContinueWatchingEntry>(), entries())

            assertEquals(Status.Code.INVALID_ARGUMENT, code { stub.forgetProgress(forget(movie(653), ref(1, 1))) })
            assertEquals(Status.Code.INVALID_ARGUMENT, code { stub.forgetProgress(forget(movie(-1))) })
            assertEquals(Status.Code.INVALID_ARGUMENT, code { stub.forgetProgress(forget(series(894), ref(-1, 1))) })
        }

    @Test
    fun `the resume point rides on the first source only`() =
        runBlocking {
            stub.reportPlayback(report(movie(653), position = 90_000))
            outcome = { _, _ -> ResolveOutcome.Sources(flowOf(source("a/1.mp4"), source("a/2.mp4"))) }

            val responses = stub.resolve(resolveRequest(movie(653))).toList()

            assertEquals(90_000L, responses[0].resume.positionMillis)
            assertTrue(responses[0].hasResume())
            assertFalse(responses[1].hasResume())
        }

    @Test
    fun `a series episode resumes where it was left, and a completed one does not`() =
        runBlocking {
            outcome = { media, _ ->
                assertEquals(MediaType.MEDIA_TYPE_TV, media.mediaType)
                ResolveOutcome.Sources(flowOf(source("a/1.mp4")))
            }
            stub.reportPlayback(report(series(894), ref(1, 1), position = 30_000))
            stub.reportPlayback(report(series(894), ref(1, 2), event = PlaybackEvent.PLAYBACK_EVENT_FINISHED))

            assertEquals(
                30_000L,
                stub
                    .resolve(resolveRequest(series(894), ref(1, 1)))
                    .toList()
                    .single()
                    .resume.positionMillis,
            )
            assertFalse(
                stub
                    .resolve(resolveRequest(series(894), ref(1, 2)))
                    .toList()
                    .single()
                    .hasResume(),
            )
            assertFalse(
                stub
                    .resolve(resolveRequest(series(894), ref(2, 1)))
                    .toList()
                    .single()
                    .hasResume(),
            )
        }

    @Test
    fun `no progress, or no sources, carries no resume`() =
        runBlocking {
            outcome = { _, _ -> ResolveOutcome.Sources(flowOf(source("a/1.mp4"))) }
            assertFalse(
                stub
                    .resolve(resolveRequest(movie(653)))
                    .toList()
                    .single()
                    .hasResume(),
            )

            stub.reportPlayback(report(movie(653)))
            outcome = { _, _ -> ResolveOutcome.Sources(emptyFlow()) }
            assertEquals(emptyList<Any>(), stub.resolve(resolveRequest(movie(653))).toList())
        }
}
