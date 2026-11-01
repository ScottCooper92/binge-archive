package com.cooper.binge.companion.archive.service

import com.binge.companion.contracts.stream.v1.Capability
import com.binge.companion.contracts.stream.v1.HandshakeRequest
import com.binge.companion.contracts.stream.v1.ProbeRequest
import com.binge.companion.contracts.stream.v1.ReportSourceRequest
import com.binge.companion.contracts.stream.v1.ResolveRequest
import com.binge.companion.contracts.stream.v1.StreamServiceGrpcKt
import com.binge.companion.contracts.stream.v1.StreamSource
import com.binge.companion.contracts.v1.EpisodeRef
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.sdk.requireDeclared
import com.binge.companion.sdk.userMessageOf
import com.cooper.binge.companion.archive.index.IndexDatabase
import com.cooper.binge.companion.archive.index.IndexRepository
import com.cooper.binge.companion.archive.index.inMemoryIndex
import com.cooper.binge.companion.archive.index.movie
import com.cooper.binge.companion.archive.index.sampleSnapshot
import com.cooper.binge.companion.archive.index.series
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/** The whole contract over an in-process channel, with the resolver stubbed and the index real. */
@RunWith(RobolectricTestRunner::class)
class ArchiveStreamServiceTest {
    private val indexDatabase: IndexDatabase = inMemoryIndex()
    private val reportDatabase: DeviceRecordsDatabase =
        androidx.room.Room
            .inMemoryDatabaseBuilder(
                androidx.test.core.app.ApplicationProvider
                    .getApplicationContext(),
                DeviceRecordsDatabase::class.java,
            ).allowMainThreadQueries()
            .build()
    private var outcome: suspend (MediaId, EpisodeRef?) -> ResolveOutcome = { _, _ -> error("No outcome set") }
    private val messages =
        object : UserMessages {
            override fun notOnArchive() = "Not on the Internet Archive"

            override fun tookTooLong() = "Took too long"

            override fun locale() = "en-GB"
        }

    /** A named thread, so a test can see the Service's work leave the calling thread. */
    private val ioExecutor =
        java.util.concurrent.Executors
            .newSingleThreadExecutor { Thread(it, "archive-io") }

    private val name = InProcessServerBuilder.generateName()
    private val server: Server =
        InProcessServerBuilder
            .forName(name)
            .directExecutor()
            .addService(
                ArchiveStreamService(
                    resolver = TitleResolver { media, episode -> outcome(media, episode) },
                    index = IndexRepository(indexDatabase.indexDao(), seed = { sampleSnapshot }),
                    progress = PlaybackProgressStore(reportDatabase),
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
        reportDatabase.close()
        ioExecutor.shutdownNow()
    }

    private fun source(id: String) = StreamSource.newBuilder().setId(id).build()

    private fun resolveRequest(
        media: MediaId,
        episode: EpisodeRef? = null,
    ) = ResolveRequest
        .newBuilder()
        .setMedia(media)
        .apply { episode?.let(::setEpisode) }
        .build()

    private fun probeRequest(
        media: MediaId,
        episode: EpisodeRef? = null,
    ) = ProbeRequest
        .newBuilder()
        .setMedia(media)
        .apply { episode?.let(::setEpisode) }
        .build()

    private val episode: EpisodeRef =
        EpisodeRef
            .newBuilder()
            .setSeasonNumber(1)
            .setEpisodeNumber(1)
            .build()

    private fun status(block: suspend () -> Unit): StatusException = assertThrows(StatusException::class.java) { runBlocking { block() } }

    @Test
    fun `the handshake always succeeds, with the fixed capabilities and the Archive's name`() =
        runBlocking {
            val response = stub.handshake(HandshakeRequest.getDefaultInstance())

            assertEquals(
                listOf(
                    Capability.CAPABILITY_PROBE,
                    Capability.CAPABILITY_SUBTITLES,
                    Capability.CAPABILITY_PLAYBACK_PROGRESS,
                ),
                response.capabilitiesList,
            )
            assertEquals("Internet Archive", response.providerName)
            assertEquals("1.2.3", response.companionVersionName)
        }

    @Test
    fun `A, sources are sent then the call completes OK`() =
        runBlocking {
            outcome = { _, _ -> ResolveOutcome.Sources(flowOf(source("a/1.mp4"), source("a/2.mp4"))) }

            assertEquals(listOf("a/1.mp4", "a/2.mp4"), stub.resolve(resolveRequest(movie(10331))).toList().map { it.source.id })
        }

    @Test
    fun `subtitles are sent, and the handshake declares them`() =
        runBlocking {
            val withTrack =
                StreamSource
                    .newBuilder()
                    .setId("a/1.mp4")
                    .addSubtitles(
                        com.binge.companion.contracts.stream.v1.SubtitleTrack
                            .newBuilder()
                            .setUrl("https://archive.org/download/a/1.srt"),
                    ).build()
            outcome = { _, _ -> ResolveOutcome.Sources(flowOf(withTrack)) }

            assertEquals(
                1,
                stub
                    .resolve(resolveRequest(movie(10331)))
                    .toList()
                    .single()
                    .source.subtitlesCount,
            )
            assertTrue(Capability.CAPABILITY_SUBTITLES in stub.handshake(HandshakeRequest.getDefaultInstance()).capabilitiesList)
        }

    @Test
    fun `the resolve runs on the io dispatcher, not the calling thread`() =
        runBlocking {
            var thread = ""
            outcome = { _, _ ->
                thread = Thread.currentThread().name.substringBefore(" @")
                ResolveOutcome.Sources(emptyFlow())
            }

            stub.resolve(resolveRequest(movie(10331))).toList()

            assertEquals("archive-io", thread)
        }

    @Test
    fun `B, a known film with no licit copy completes empty`() =
        runBlocking {
            outcome = { _, _ -> ResolveOutcome.Sources(emptyFlow()) }

            assertEquals(emptyList<Any>(), stub.resolve(resolveRequest(movie(653))).toList())
        }

    @Test
    fun `C, not in the catalogue is NOT_FOUND with a sentence naming the Archive`() {
        outcome = { _, _ -> ResolveOutcome.NotFound }

        val failure = status { stub.resolve(resolveRequest(movie(1))).toList() }

        assertEquals(Status.Code.NOT_FOUND, failure.status.code)
        val message = checkNotNull(userMessageOf(failure.trailers))
        assertEquals("Not on the Internet Archive", message.message)
        assertEquals("en-GB", message.locale)
        assertEquals(ArchiveErrors.REASON_NOT_ON_ARCHIVE, message.reason)
    }

    @Test
    fun `D, a bad request and a non-film are INVALID_ARGUMENT`() {
        outcome = { _, _ -> ResolveOutcome.NotAFilm }
        assertEquals(Status.Code.INVALID_ARGUMENT, status { stub.resolve(resolveRequest(movie(146742))).toList() }.status.code)

        outcome = { _, _ -> ResolveOutcome.Invalid("a movie takes no episode") }
        val failure = status { stub.resolve(resolveRequest(movie(1), episode)).toList() }
        assertEquals(Status.Code.INVALID_ARGUMENT, failure.status.code)
        assertEquals("a movie takes no episode", failure.status.description)
    }

    @Test
    fun `the episode reaches the resolver only when the request carries one`() =
        runBlocking {
            val seen = mutableListOf<EpisodeRef?>()
            outcome = { _, ep ->
                seen += ep
                ResolveOutcome.NotFound
            }

            runCatching { stub.resolve(resolveRequest(series(894), episode)).toList() }
            runCatching { stub.resolve(resolveRequest(movie(1))).toList() }

            assertEquals(listOf(episode, null), seen)
        }

    @Test
    fun `an unreachable Archive is UNAVAILABLE with a fixed description, before or after sources`() =
        runBlocking {
            outcome = { _, _ -> throw IOException("https://archive.org/metadata/x answered 503") }
            val before = status { stub.resolve(resolveRequest(movie(10331))).toList() }
            assertEquals(Status.Code.UNAVAILABLE, before.status.code)
            assertEquals(ArchiveErrors.UNREACHABLE, before.status.description)

            outcome = { _, _ ->
                ResolveOutcome.Sources(
                    flow {
                        emit(source("a/1.mp4"))
                        throw IOException("reset")
                    },
                )
            }
            val received = mutableListOf<String>()
            val after = status { stub.resolve(resolveRequest(movie(10331))).collect { received += it.source.id } }
            assertEquals(listOf("a/1.mp4"), received)
            assertEquals(Status.Code.UNAVAILABLE, after.status.code)
        }

    @Test
    fun `an unreadable answer is UNAVAILABLE without the exception's message`() {
        outcome = { _, _ -> throw SerializationException("secret body {\"x\":1}") }

        val failure = status { stub.resolve(resolveRequest(movie(10331))).toList() }

        assertEquals(Status.Code.UNAVAILABLE, failure.status.code)
        assertEquals(ArchiveErrors.UNREADABLE, failure.status.description)
    }

    @Test
    fun `cancelling the call stops the resolver`() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val stopped = CompletableDeferred<Unit>()
            outcome = { _, _ ->
                ResolveOutcome.Sources(
                    flow {
                        emit(source("a/1.mp4"))
                        started.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            stopped.complete(Unit)
                        }
                    },
                )
            }

            // Taking the first source and leaving cancels the call, as a host leaving a title page does.
            stub.resolve(resolveRequest(movie(10331))).first()

            withTimeout(5_000) { stopped.await() }
            assertTrue(started.isCompleted)
        }

    @Test
    fun `Probe answers from the index alone`() =
        runBlocking {
            assertTrue(stub.probe(probeRequest(movie(10331))).mayHaveSources)
            assertTrue(stub.probe(probeRequest(movie(2973))).mayHaveSources)
            // Only a "video" on Wikidata, not a known non-film kind, so Probe does not refuse it (#26).
            assertTrue(stub.probe(probeRequest(movie(34372))).mayHaveSources)
            assertFalse(stub.probe(probeRequest(movie(1))).mayHaveSources)
        }

    @Test
    fun `Probe refuses what Resolve refuses, and a series is NOT_FOUND`() {
        assertEquals(Status.Code.INVALID_ARGUMENT, status { stub.probe(probeRequest(movie(146742))) }.status.code)
        assertEquals(Status.Code.INVALID_ARGUMENT, status { stub.probe(probeRequest(movie(0))) }.status.code)
        assertEquals(Status.Code.INVALID_ARGUMENT, status { stub.probe(probeRequest(movie(10331), episode)) }.status.code)
        assertEquals(Status.Code.INVALID_ARGUMENT, status { stub.probe(probeRequest(series(894))) }.status.code)
        val unspecified =
            MediaId
                .newBuilder()
                .setMediaType(MediaType.MEDIA_TYPE_UNSPECIFIED)
                .setTmdbId(1)
                .build()
        assertEquals(Status.Code.INVALID_ARGUMENT, status { stub.probe(probeRequest(unspecified)) }.status.code)
        assertEquals(Status.Code.NOT_FOUND, status { stub.probe(probeRequest(series(894), episode)) }.status.code)
    }

    @Test
    fun `ReportSource is no longer declared, so a call to it is PERMISSION_DENIED`() {
        val failure = status { stub.reportSource(ReportSourceRequest.newBuilder().setSourceId("item/a.mp4").build()) }

        assertEquals(Status.Code.PERMISSION_DENIED, failure.status.code)
    }

    @Test
    fun `an undeclared capability is refused as PERMISSION_DENIED`() {
        val failure =
            assertThrows(
                StatusException::class.java,
            ) { setOf(Capability.CAPABILITY_PROBE).requireDeclared(Capability.CAPABILITY_PLAYBACK_PROGRESS) }

        assertEquals(Status.Code.PERMISSION_DENIED, failure.status.code)
        ArchiveStreamService.CAPABILITIES.requireDeclared(Capability.CAPABILITY_PLAYBACK_PROGRESS)
    }

    @Test
    fun `Sources is never mapped to a status`() {
        assertThrows(IllegalStateException::class.java) { ArchiveErrors(messages).of(ResolveOutcome.Sources(emptyFlow())) }
        assertEquals(null, ArchiveErrors(messages).ofFailure(IllegalStateException("x")))
    }
}
