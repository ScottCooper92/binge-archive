package io.github.scottcooper92.binge.archive.index

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The one HTTP client: Wikidata and the Archive, for the index and the resolver alike.
 *
 * Fixed timeouts, 10 s to connect and 20 s to read, so a slow host fails a lookup rather than
 * holding it. Wikimedia asks every client to name itself and a way to reach its author, so every
 * request carries this app's User-Agent. Nothing else about the user or the device is sent.
 */
object ArchiveHttp {
    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 20L

    fun userAgent(version: String): String = "binge-archive/$version (https://github.com/ScottCooper92/binge-archive)"

    fun client(version: String): OkHttpClient {
        val userAgent = userAgent(version)
        return OkHttpClient
            .Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain
                        .request()
                        .newBuilder()
                        .header("User-Agent", userAgent)
                        .build(),
                )
            }.build()
    }
}
