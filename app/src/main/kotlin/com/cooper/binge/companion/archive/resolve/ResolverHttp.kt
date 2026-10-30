package com.cooper.binge.companion.archive.resolve

import com.cooper.binge.companion.archive.index.IndexFetcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
) {
    /**
     * Running out of [sparqlBudgetMillis] is an [IOException], like any other Wikidata failure. The budget is
     * OkHttp's call timeout, which spans connecting, waiting and reading the body, and runs on the wall clock.
     */
    suspend fun sparql(query: String): String =
        body(
            Request
                .Builder()
                .url(wikidataEndpoint)
                .header("Accept", "text/tab-separated-values")
                .post(FormBody.Builder().add("query", query).build())
                .build(),
            callTimeoutMillis = sparqlBudgetMillis,
        )

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

        /** About 1.5 s: the ranking search's budget, so a slow answer costs the first read little. */
        const val RANKING_BUDGET_MILLIS = 1_500L
        const val ARCHIVE_METADATA = "https://archive.org/metadata"
        const val ARCHIVE_DOWNLOAD = "https://archive.org/download"
    }
}
