package com.cooper.binge.companion.archive.service

import com.binge.companion.contracts.stream.v1.Capability
import com.binge.companion.contracts.stream.v1.ListServedTitlesRequest
import com.binge.companion.contracts.stream.v1.StreamServiceGrpcKt
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.sdk.requireDeclared
import com.cooper.binge.companion.archive.index.IndexRepository
import com.cooper.binge.companion.archive.index.certifiedSnapshot
import com.cooper.binge.companion.archive.index.inMemoryIndex
import com.cooper.binge.companion.archive.reports.DeviceRecordsDatabase
import com.cooper.binge.companion.archive.reports.PlaybackProgressStore
import com.cooper.binge.companion.archive.resolve.TitleResolver
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
class ArchiveStreamServiceServedTitlesTest {
    private val indexDatabase = inMemoryIndex()
    private val recordsDatabase =
        androidx.room.Room
            .inMemoryDatabaseBuilder(
                androidx.test.core.app.ApplicationProvider
                    .getApplicationContext(),
                DeviceRecordsDatabase::class.java,
            ).allowMainThreadQueries()
            .build()
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
                    resolver = TitleResolver { _, _ -> error("ListServedTitles never resolves") },
                    index = IndexRepository(indexDatabase.indexDao(), seed = { certifiedSnapshot }),
                    progress = PlaybackProgressStore(recordsDatabase),
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

    private fun request(
        region: String = "US",
        allowed: List<String> = listOf("PG", "PG-13"),
        includeUnrated: Boolean = true,
        pageSize: Int = 0,
        token: String = "",
    ) = ListServedTitlesRequest
        .newBuilder()
        .setRegion(region)
        .addAllAllowedCertifications(allowed)
        .setIncludeUnrated(includeUnrated)
        .setPageSize(pageSize)
        .setPageToken(token)
        .build()

    private fun ids(titles: List<MediaId>) = titles.map { it.tmdbId }

    private fun refused(block: suspend () -> Unit): Status.Code =
        assertThrows(StatusException::class.java) { runBlocking { block() } }.status.code

    @Test
    fun `it lists the Wikidata-linked films the request admits, as movies, with no next page`() =
        runBlocking {
            val response = stub.listServedTitles(request())

            assertEquals(listOf(653, 10331, 34372), ids(response.titlesList))
            assertTrue(response.titlesList.all { it.mediaType == MediaType.MEDIA_TYPE_MOVIE })
            assertEquals("", response.nextPageToken)
        }

    @Test
    fun `the filter is the request's region, certifications and unrated rule`() =
        runBlocking {
            assertEquals(listOf(653), ids(stub.listServedTitles(request(allowed = listOf("PG"), includeUnrated = false)).titlesList))
            assertEquals(
                listOf(10331),
                ids(stub.listServedTitles(request(region = "DE", allowed = listOf("16"), includeUnrated = false)).titlesList),
            )
            assertEquals(emptyList<Int>(), ids(stub.listServedTitles(request(allowed = emptyList(), includeUnrated = false)).titlesList))
            assertEquals(listOf(34372), ids(stub.listServedTitles(request(allowed = emptyList())).titlesList))
        }

    @Test
    fun `a lower-case region is the same region`() =
        runBlocking {
            assertEquals(ids(stub.listServedTitles(request("US")).titlesList), ids(stub.listServedTitles(request("us")).titlesList))
        }

    @Test
    fun `pages are exact, and the next page token ends with the last page`() =
        runBlocking {
            val first = stub.listServedTitles(request(pageSize = 2))
            assertEquals(listOf(653, 10331), ids(first.titlesList))
            assertTrue(first.nextPageToken.isNotEmpty())

            val second = stub.listServedTitles(request(pageSize = 2, token = first.nextPageToken))
            assertEquals(listOf(34372), ids(second.titlesList))
            assertEquals("", second.nextPageToken)
        }

    @Test
    fun `a page that ends exactly at the end of the list has no next page token`() =
        runBlocking {
            val page = stub.listServedTitles(request(pageSize = 3))

            assertEquals(3, page.titlesCount)
            assertEquals("", page.nextPageToken)
        }

    @Test
    fun `a request with a bad region, a negative page size or a foreign token is INVALID_ARGUMENT`() {
        val token = runBlocking { stub.listServedTitles(request(pageSize = 1)).nextPageToken }

        listOf("", "U", "USA", "U1", "United States").forEach { region ->
            assertEquals(Status.Code.INVALID_ARGUMENT, refused { stub.listServedTitles(request(region = region)) })
        }
        assertEquals(Status.Code.INVALID_ARGUMENT, refused { stub.listServedTitles(request(pageSize = -1)) })
        assertEquals(Status.Code.INVALID_ARGUMENT, refused { stub.listServedTitles(request(token = "garbage")) })
        assertEquals(Status.Code.INVALID_ARGUMENT, refused { stub.listServedTitles(request(token = "!!!")) })
        // A token is only valid with the filter and page size it was issued under.
        assertEquals(Status.Code.INVALID_ARGUMENT, refused { stub.listServedTitles(request(pageSize = 1, region = "DE", token = token)) })
        assertEquals(
            Status.Code.INVALID_ARGUMENT,
            refused { stub.listServedTitles(request(pageSize = 1, allowed = listOf("PG"), token = token)) },
        )
        assertEquals(
            Status.Code.INVALID_ARGUMENT,
            refused { stub.listServedTitles(request(pageSize = 1, includeUnrated = false, token = token)) },
        )
        assertEquals(Status.Code.INVALID_ARGUMENT, refused { stub.listServedTitles(request(pageSize = 2, token = token)) })
    }

    @Test
    fun `the page size is the host's up to 200, and 100 when it names none`() {
        fun size(pageSize: Int) = ServedTitlesQuery.of(request(pageSize = pageSize)).getOrThrow().pageSize

        assertEquals(100, size(0))
        assertEquals(7, size(7))
        assertEquals(200, size(200))
        assertEquals(200, size(5_000))
    }

    @Test
    fun `the capability is declared, and PERMISSION_DENIED where it is not`() {
        ArchiveStreamService.CAPABILITIES.requireDeclared(Capability.CAPABILITY_LIST_SERVED_TITLES)
        val failure =
            assertThrows(StatusException::class.java) {
                setOf(Capability.CAPABILITY_PROBE).requireDeclared(Capability.CAPABILITY_LIST_SERVED_TITLES)
            }

        assertEquals(Status.Code.PERMISSION_DENIED, failure.status.code)
    }
}
