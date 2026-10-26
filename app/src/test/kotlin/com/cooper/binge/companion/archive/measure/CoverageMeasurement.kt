package com.cooper.binge.companion.archive.measure

import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.archive.BuildConfig
import com.cooper.binge.companion.archive.index.ArchiveHttp
import com.cooper.binge.companion.archive.index.IndexFetcher
import com.cooper.binge.companion.archive.index.IndexRepository
import com.cooper.binge.companion.archive.index.IndexSnapshot
import com.cooper.binge.companion.archive.index.inMemoryIndex
import com.cooper.binge.companion.archive.index.movie
import com.cooper.binge.companion.archive.resolve.CoverageMiss
import com.cooper.binge.companion.archive.resolve.ItemFinder
import com.cooper.binge.companion.archive.resolve.ItemReader
import com.cooper.binge.companion.archive.resolve.ResolveOutcome
import com.cooper.binge.companion.archive.resolve.Resolver
import com.cooper.binge.companion.archive.resolve.ResolverHttp
import com.cooper.binge.companion.archive.resolve.SourceMapper
import com.cooper.binge.companion.archive.resolve.SourceStrings
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.EventListener
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * binge-companions#148's measurement, against live Wikidata and the Archive. Skipped unless asked for:
 *
 *     ./gradlew :app:testDebugUnitTest --tests '*CoverageMeasurement*' --rerun -Parchive.measure=<dir>
 *
 * `<dir>` holds one `<list>.txt` per list, a TMDB movie id first on each line. The index is fetched
 * live first, as the weekly refresh does, and its cost is recorded. Then each id is resolved through the
 * real [Resolver], one at a time, and `<dir>/results.tsv` gets one row per id: the answer, the route,
 * the time, the requests it took and any HTTP error codes. A title-route resolve also records what the
 * title search returned before the runtime check, for the check's precision.
 */
@RunWith(RobolectricTestRunner::class)
class CoverageMeasurement {
    private val dir = System.getProperty("archive.measure")?.takeIf { it.isNotBlank() }?.let(::File)

    private val requests = AtomicInteger()
    private val bytes = AtomicLong()
    private val errors = mutableListOf<Int>()

    private val client =
        ArchiveHttp
            .client(BuildConfig.VERSION_NAME)
            .newBuilder()
            .addInterceptor { chain ->
                requests.incrementAndGet()
                chain.proceed(chain.request()).also { if (!it.isSuccessful) synchronized(errors) { errors += it.code } }
            }.eventListener(
                object : EventListener() {
                    override fun responseBodyEnd(
                        call: Call,
                        byteCount: Long,
                    ) {
                        bytes.addAndGet(byteCount)
                    }
                },
            ).build()

    @Test
    fun measure() {
        assumeTrue("set -Parchive.measure=<dir> to run", dir != null)
        val dir = dir!!
        val database = inMemoryIndex()
        // The weekly refresh, as the worker runs it, up to three times: a timeout is a finding too.
        val attempts = StringBuilder()
        var snapshot: IndexSnapshot? = null
        repeat(REFRESH_ATTEMPTS) {
            if (snapshot != null) return@repeat
            bytes.set(0)
            requests.set(0)
            synchronized(errors) { errors.clear() }
            val started = System.nanoTime()
            val outcome =
                try {
                    snapshot = IndexFetcher(client).fetch()
                    "ok links=${snapshot!!.links.size} archive_items=${snapshot!!.archiveItems.size}"
                } catch (e: IOException) {
                    "failed ${e.javaClass.simpleName}: ${e.message}"
                }
            attempts.append(
                "refresh_ms=${(System.nanoTime() - started) / NANOS_PER_MS} bytes=${bytes.get()} requests=${requests.get()} " +
                    "errors=$errors $outcome\n",
            )
            File(dir, "refresh.txt").writeText(attempts.toString())
        }
        val fetched = checkNotNull(snapshot) { "no refresh succeeded: $attempts" }
        val index = IndexRepository(database.indexDao(), seed = { fetched })
        val http = ResolverHttp(client)
        val misses = mutableListOf<CoverageMiss>()
        val resolver =
            Resolver(
                index,
                ItemFinder(http),
                ItemReader(http),
                SourceMapper(SourceStrings.from(ApplicationProvider.getApplicationContext())),
                coverage = { synchronized(misses) { misses += it } },
            )
        File(dir, "results.tsv").printWriter().use { out ->
            out.println(HEADER.joinToString("\t"))
            dir.listFiles { f -> f.extension == "txt" && f.name != "refresh.txt" }!!.sortedBy { it.name }.forEach { list ->
                list.readLines().mapNotNull { it.substringBefore('\t').trim().toIntOrNull() }.distinct().forEach { tmdbId ->
                    out.println(runBlocking { row(list.nameWithoutExtension, tmdbId, index, http, resolver, misses) }.joinToString("\t"))
                    out.flush()
                }
            }
        }
        database.close()
    }

    private suspend fun row(
        list: String,
        tmdbId: Int,
        index: IndexRepository,
        http: ResolverHttp,
        resolver: Resolver,
        misses: MutableList<CoverageMiss>,
    ): List<Any?> {
        misses.clear()
        synchronized(errors) { errors.clear() }
        val before = requests.get()
        val inIndex = index.lookup(movie(tmdbId)) != null
        val started = System.nanoTime()
        var sources = 0
        var firstMs: Long? = null
        val answer =
            try {
                when (val outcome = resolver.resolve(movie(tmdbId), null)) {
                    is ResolveOutcome.Sources -> {
                        val all =
                            outcome.sources
                                .onEach { if (firstMs == null) firstMs = (System.nanoTime() - started) / NANOS_PER_MS }
                                .toList()
                        sources = all.size
                        if (all.isEmpty()) "empty" else "sources"
                    }
                    ResolveOutcome.NotFound -> "not_found"
                    ResolveOutcome.NotAFilm -> "not_a_film"
                    is ResolveOutcome.Invalid -> "invalid"
                }
            } catch (e: IOException) {
                "unavailable:${e.message.orEmpty().replace(Regex("\\s+"), " ")}"
            }
        val totalMs = (System.nanoTime() - started) / NANOS_PER_MS
        val used = requests.get() - before
        val codes = synchronized(errors) { errors.joinToString(",") }
        val missReasons = misses.joinToString(",") { it.reason.name.lowercase() }
        // Outside the timed resolve: the route and facts (one more find, which can differ from the timed
        // one only if this pass fails) and the raw title search, for the report.
        val found = runCatchingIo { ItemFinder(http).find(tmdbId) }
        val route = found?.route
        val facts = found?.facts
        val titleSearch =
            if (facts?.title != null && facts.runtimeSeconds != null && facts.archiveIds.isEmpty()) {
                runCatchingIo {
                    ItemFinder
                        .parseSearch(http.search(ItemFinder.titleQuery(facts.title, facts.year), rows = ItemFinder.MAX_CANDIDATES))
                        .joinToString(",") { it.identifier }
                }
            } else {
                null
            }
        return listOf(
            list,
            tmdbId,
            answer,
            route?.name?.lowercase(),
            inIndex,
            sources,
            firstMs,
            totalMs,
            used,
            codes,
            missReasons,
            facts?.title,
            facts?.year,
            facts?.runtimeSeconds?.toInt(),
            facts?.imdbId,
            facts?.archiveIds?.size,
            titleSearch,
        )
    }

    private inline fun <T> runCatchingIo(block: () -> T): T? =
        try {
            block()
        } catch (e: IOException) {
            null
        }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val REFRESH_ATTEMPTS = 3
        val HEADER =
            listOf(
                "list",
                "tmdb",
                "answer",
                "route",
                "in_index",
                "sources",
                "first_ms",
                "total_ms",
                "requests",
                "http_errors",
                "misses",
                "title",
                "year",
                "runtime_s",
                "imdb",
                "wikidata_archive_ids",
                "title_search",
            )
    }
}
