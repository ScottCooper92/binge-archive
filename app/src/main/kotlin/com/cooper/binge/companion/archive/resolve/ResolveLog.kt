package com.cooper.binge.companion.archive.resolve

import android.util.Log

/**
 * Where a debug build writes what one `Resolve` did, so a failure like a lookup that ran past the host's
 * deadline can be read from logcat. Release builds are given [NONE], so nothing is logged and nothing is
 * built (see [resolveLogFor]): a line is a lambda, so it is not even assembled. Only the device ever sees these lines.
 */
fun interface ResolveLog {
    fun line(message: () -> String)

    companion object {
        /** Logs nothing: what a release build and a resolver built without logging hold. */
        val NONE = ResolveLog { }
    }
}

/** Writes each line to logcat under the `ArchiveResolve` tag. */
object LogcatResolveLog : ResolveLog {
    const val TAG = "ArchiveResolve"

    override fun line(message: () -> String) {
        Log.d(TAG, message())
    }
}

/** The log a build gets: logcat for a debug build, and [ResolveLog.NONE] for a release one. */
fun resolveLogFor(debug: Boolean): ResolveLog = if (debug) LogcatResolveLog else ResolveLog.NONE

/**
 * The lines a `Resolve` writes, as text. Each field is a number, an Archive identifier, a route or reason word, an HTTP
 * status or an exception's class name: never a title, never user text, never an exception's message.
 */
object ResolveEvents {
    fun start(
        tmdbId: Int,
        level: String,
        titleMatches: Boolean,
    ) = "resolve start tmdb=$tmdbId level=$level titleMatches=$titleMatches"

    fun candidates(
        route: Route?,
        identifiers: List<String>,
        fromIndex: Boolean,
    ) = "candidates route=${route?.name?.lowercase() ?: "none"} count=${identifiers.size} fromIndex=$fromIndex ids=$identifiers"

    fun dropped(
        identifier: String,
        why: String,
    ) = "dropped id=$identifier why=$why"

    fun read(
        identifier: String,
        failure: Throwable?,
    ) = "read id=$identifier " + (failure?.let { "failed ${failureWord(it)}" } ?: "ok")

    fun answer(
        outcome: String,
        elapsedMillis: Long,
    ) = "answer $outcome after ${elapsedMillis}ms"

    fun failed(
        failure: Throwable,
        elapsedMillis: Long,
    ) = answer("failed ${failureWord(failure)}", elapsedMillis)

    /** An HTTP failure's status, else the exception's class name: a message can carry a URL. */
    private fun failureWord(failure: Throwable): String =
        (failure as? HttpStatusException)?.let { "http=${it.code}" } ?: "io=${failure.javaClass.simpleName}"
}
