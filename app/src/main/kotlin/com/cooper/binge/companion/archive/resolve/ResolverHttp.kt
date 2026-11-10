package com.cooper.binge.companion.archive.resolve

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.archive.index.IndexFetcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.TimeSource

/** A response with a failure status. An [IOException], as every failed call is, that also names the status for the debug log. */
class HttpStatusException(
    val code: Int,
    host: String,
) : IOException("HTTP $code from $host")

/**
 * The resolver's three calls: a Wikidata query, an Archive search and an Archive item's metadata.
 *
 * Every call is cancellable: when the host cancels a `Resolve`, the coroutine is cancelled and the
 * HTTP call with it, so no work outlives the request.
 */
class ResolverHttp(
    private val client: OkHttpClient,
    private val wikidataEndpoint: HttpUrl = IndexFetcher.WIKIDATA_SPARQL.toHttpUrl(),
    private val archiveSearch: HttpUrl = IndexFetcher.ARCHIVE_SEARCH.toHttpUrl(),
    private val archiveMetadata: HttpUrl = ARCHIVE_METADATA.toHttpUrl(),
    /** Where a response body is read: the read blocks, so never the caller's thread. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** The Wikidata query's own budget: well inside the host's 10 s deadline, so the index fallback has time to run. */
    private val sparqlBudgetMillis: Long = SPARQL_BUDGET_MILLIS,
    /** The budget of the search that only orders candidates: its answer is never worth holding the first read for. */
    val rankingBudgetMillis: Long = RANKING_BUDGET_MILLIS,
    /** How long the Wikidata query may go unanswered before an identical second one is sent beside it. */
    private val hedgeDelayMillis: Long = HEDGE_DELAY_MILLIS,
) {
    /**
     * The Wikidata query, hedged. A stalled request is the usual way it fails (an immediate retry of the same query answers in
     * well under a second), so when the first has not answered after [hedgeDelayMillis], or it fails with a 5xx, an identical
     * second is sent beside it. The first success wins and the other is cancelled. Both share one budget, [sparqlBudgetMillis]
     * from the start: the second gets what is left. Both failing is the first one's failure, so the answer to the host is the
     * same as it was before the hedge.
     */
    suspend fun sparql(query: String): String {
        val request =
            Request
                .Builder()
                .url(wikidataEndpoint)
                .header("Accept", "text/tab-separated-values")
                .post(FormBody.Builder().add("query", query).build())
                .build()
        return coroutineScope {
            val started = TimeSource.Monotonic.markNow()
            val first = async { attempt { body(request, callTimeoutMillis = sparqlBudgetMillis) } }
            val early = withTimeoutOrNull(hedgeDelayMillis) { first.await() }
            when {
                early?.isSuccess == true -> early.getOrThrow()
                early != null && !early.isServerFailure() -> early.getOrThrow()
                else -> {
                    val second =
                        async {
                            attempt {
                                val left = sparqlBudgetMillis - started.elapsedNow().inWholeMilliseconds
                                // A call timeout of 0 means none, so a spent budget must not reach the call.
                                if (left <= 0) throw IOException("The Wikidata budget is spent")
                                body(request, callTimeoutMillis = left)
                            }
                        }
                    firstSuccess(first, second)
                }
            }
        }
    }

    /** True for a failure that carries a 5xx status: the host answered, and is worth a second try. */
    private fun Result<String>.isServerFailure(): Boolean =
        (exceptionOrNull() as? HttpStatusException)?.code?.let { it >= SERVER_ERROR } == true

    /** The first of [first] and [second] to succeed, with the other cancelled. If both fail, [first]'s failure. */
    private suspend fun firstSuccess(
        first: Deferred<Result<String>>,
        second: Deferred<Result<String>>,
    ): String {
        val (done, other) =
            select<Pair<Result<String>, Deferred<Result<String>>>> {
                first.onAwait { it to second }
                second.onAwait { it to first }
            }
        done.getOrNull()?.let {
            other.cancel()
            return it
        }
        val rest = other.await()
        rest.getOrNull()?.let { return it }
        throw checkNotNull(first.await().exceptionOrNull())
    }

    /**
     * An advancedsearch query, best-downloaded first, returning identifier and downloads. [callTimeoutMillis]
     * bounds the whole call like [sparql]'s budget does; running out of it is an [IOException].
     */
    suspend fun search(
        query: String,
        rows: Int,
        callTimeoutMillis: Long? = null,
    ): String {
        val url =
            archiveSearch
                .newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("fl[]", "identifier")
                .addQueryParameter("fl[]", "downloads")
                .addQueryParameter("sort[]", "downloads desc")
                .addQueryParameter("rows", rows.toString())
                .addQueryParameter("output", "json")
                .build()
        return body(Request.Builder().url(url).build(), callTimeoutMillis)
    }

    suspend fun metadata(identifier: String): String =
        body(Request.Builder().url(archiveMetadata.newBuilder().addPathSegment(identifier).build()).build())

    private suspend fun body(
        request: Request,
        callTimeoutMillis: Long? = null,
    ): String {
        val deadline = currentCoroutineContext()[ResolveDeadline]
        val call = client.newCall(request)
        val timeout = effectiveTimeout(callTimeoutMillis, deadline)
        if (timeout != null) call.timeout().timeout(timeout, TimeUnit.MILLISECONDS)
        try {
            return read(call, request)
        } catch (e: IOException) {
            throw e.orBudgetTimeout(deadline)
        }
    }

    private suspend fun read(
        call: Call,
        request: Request,
    ): String =
        call.await().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code, request.url.host)
            withContext(io) { response.body.string() }
        }

    /** The tighter of the call's own budget and what is left of the resolve's; a spent resolve budget fails the call at once. */
    private fun effectiveTimeout(
        callTimeoutMillis: Long?,
        deadline: ResolveDeadline?,
    ): Long? {
        val remaining = deadline?.remainingMillis()
        if (remaining != null && remaining <= 0) throw ResolveTimeoutException()
        return listOfNotNull(callTimeoutMillis, remaining).minOrNull()
    }

    /** A call that ran out of the resolve's budget is the budget's doing, whatever OkHttp called it. */
    private fun IOException.orBudgetTimeout(deadline: ResolveDeadline?): IOException =
        if (deadline != null && deadline.remainingMillis() <= 0) ResolveTimeoutException(this) else this

    private suspend fun Call.await(): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(
                object : Callback {
                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        continuation.resume(response) { _, value, _ -> value.close() }
                    }

                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        continuation.resumeWithException(e)
                    }
                },
            )
        }

    companion object {
        /** About 4 s: the resolver's Wikidata budget. The weekly refresh keeps the shared client's 20 s read timeout. */
        const val SPARQL_BUDGET_MILLIS = 4_000L

        /** About 1.2 s: several times the Wikidata query's healthy p99, so a second query is sent only to a stalled one. */
        const val HEDGE_DELAY_MILLIS = 1_200L
        private const val SERVER_ERROR = 500

        /** About 1.5 s: the ranking search's budget, so a slow answer costs the first read little. */
        const val RANKING_BUDGET_MILLIS = 1_500L
        const val ARCHIVE_METADATA = "https://archive.org/metadata"
        const val ARCHIVE_DOWNLOAD = "https://archive.org/download"
    }
}
