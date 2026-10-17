package io.github.scottcooper92.binge.archive.resolve

import io.github.scottcooper92.binge.archive.index.ArchiveHttp
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.io.Closeable

internal fun resolveFixture(name: String): String =
    checkNotNull(object {}.javaClass.getResource("/fixtures/resolve/$name")) { "No fixture $name" }.readText()

/**
 * Wikidata and the Archive on a MockWebServer, answering from the fixtures: trimmed copies of the
 * real responses. [sparql] and [search] pick a body for a request; metadata is served by file name.
 */
internal class FakeArchive(
    var sparql: (String) -> String = { error("No SPARQL answer set") },
    var search: (String) -> String = { error("No search answer set") },
) : Closeable {
    val server = MockWebServer()
    val searches = mutableListOf<String>()
    val metadataStatus = mutableMapOf<String, Int>()

    /** When set, every search answers with this status instead of a body. */
    var searchStatus: Int? = null
    val metadataBodies = mutableMapOf<String, String>()

    /** Holds an item's metadata answer until its latch is released, for the streaming test. */
    val metadataGates = mutableMapOf<String, java.util.concurrent.CountDownLatch>()

    /** When set, Wikidata answers every query with this status and no body: a failed query. */
    var sparqlStatus: Int? = null

    /** Holds every SPARQL answer back this long, for the cancellation test. */
    var sparqlDelaySeconds = 0L

    init {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    return when {
                        path == "/sparql" && sparqlStatus != null -> MockResponse(code = checkNotNull(sparqlStatus))
                        path == "/sparql" ->
                            MockResponse
                                .Builder()
                                .body(sparql(request.body?.utf8().orEmpty()))
                                .headersDelay(sparqlDelaySeconds, java.util.concurrent.TimeUnit.SECONDS)
                                .build()
                        path == "/search" -> {
                            val query = request.url.queryParameter("q").orEmpty()
                            searches += query
                            searchStatus?.let { MockResponse(code = it) } ?: MockResponse(body = search(query))
                        }
                        path.startsWith("/metadata/") -> metadata(request.url.pathSegments.last())
                        else -> MockResponse(code = 404)
                    }
                }
            }
        server.start()
    }

    private fun metadata(identifier: String): MockResponse {
        metadataGates[identifier]?.await(GATE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        metadataStatus[identifier]?.let { return MockResponse(code = it) }
        metadataBodies[identifier]?.let { return MockResponse(body = it) }
        val body = javaClass.getResource("/fixtures/resolve/metadata-$identifier.json")?.readText() ?: "{}"
        return MockResponse(body = body)
    }

    fun http(): ResolverHttp =
        ResolverHttp(
            client = ArchiveHttp.client("test"),
            wikidataEndpoint = server.url("/sparql"),
            archiveSearch = server.url("/search"),
            archiveMetadata = server.url("/metadata"),
        )

    override fun close() {
        metadataGates.values.forEach { it.countDown() }
        server.close()
    }

    private companion object {
        const val GATE_TIMEOUT_SECONDS = 30L
    }
}

internal fun searchResponse(vararg docs: Pair<String, Long>): String =
    docs.joinToString(",", prefix = """{"response":{"numFound":${docs.size},"docs":[""", postfix = "]}}") { (id, downloads) ->
        """{"identifier":"$id","downloads":$downloads}"""
    }
