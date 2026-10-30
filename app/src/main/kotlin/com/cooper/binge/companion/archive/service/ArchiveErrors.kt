package com.cooper.binge.companion.archive.service

import com.binge.companion.sdk.withUserMessage
import com.cooper.binge.companion.archive.resolve.ResolveOutcome
import com.cooper.binge.companion.archive.resolve.ResolveTimeoutException
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.serialization.SerializationException
import java.io.IOException

/**
 * Every status this companion answers with, in one place, as `stream.proto` defines them:
 *
 * - A, sources: OK, after the sources.
 * - B, a known film with no licit copy: OK and empty.
 * - C, not in the catalogue, and a valid series: `NOT_FOUND`, with a sentence naming the Archive.
 *   It is an answer, not a failure.
 * - D, a bad request: `INVALID_ARGUMENT`, including a movie id that is not a film and a `source_id`
 *   this companion never issued.
 * - Wikidata or the Archive unreachable or unreadable: `UNAVAILABLE`, transient, with a fixed
 *   description. Never an exception's message, which can carry a URL or a response body.
 */
class ArchiveErrors(
    private val messages: UserMessages,
) {
    /** The status for an outcome that is not [ResolveOutcome.Sources]. */
    fun of(outcome: ResolveOutcome): StatusException =
        when (outcome) {
            is ResolveOutcome.NotFound ->
                Status.NOT_FOUND
                    .withDescription("Not in this companion's catalogue")
                    .withUserMessage(reason = REASON_NOT_ON_ARCHIVE, message = messages.notOnArchive(), locale = messages.locale())
            is ResolveOutcome.NotAFilm -> invalid("This TMDB movie id is not a film")
            is ResolveOutcome.Invalid -> invalid(outcome.reason)
            is ResolveOutcome.Sources -> error("Sources is not a failure")
        }

    fun invalid(reason: String): StatusException = StatusException(Status.INVALID_ARGUMENT.withDescription(reason))

    /**
     * The status for a failure while resolving, or null when it is not one this companion maps and
     * should travel on as it is: a [StatusException] already, or a cancellation.
     */
    fun ofFailure(failure: Throwable): StatusException? =
        when {
            failure.causedByTimeout() ->
                Status.UNAVAILABLE
                    .withDescription(TOOK_TOO_LONG)
                    .withUserMessage(reason = REASON_TOOK_TOO_LONG, message = messages.tookTooLong(), locale = messages.locale())
            failure is IOException -> StatusException(Status.UNAVAILABLE.withDescription(UNREACHABLE))
            failure is SerializationException -> StatusException(Status.UNAVAILABLE.withDescription(UNREADABLE))
            else -> null
        }

    /** A reader or finder wraps what stopped it in a new [IOException], so the budget's timeout may be a few causes down. */
    private fun Throwable.causedByTimeout(): Boolean =
        generateSequence(this) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .any { it is ResolveTimeoutException }

    companion object {
        private const val MAX_CAUSE_DEPTH = 6
        const val REASON_NOT_ON_ARCHIVE = "NOT_ON_ARCHIVE"
        const val REASON_TOOK_TOO_LONG = "LOOKUP_TOOK_TOO_LONG"
        const val TOOK_TOO_LONG = "The lookup ran past this companion's own time limit"
        const val UNREACHABLE = "Wikidata or the Internet Archive could not be reached"
        const val UNREADABLE = "Wikidata or the Internet Archive answered with something unreadable"
    }
}

/** The sentences the host may show the user, in the device's language. */
interface UserMessages {
    fun notOnArchive(): String

    /** Said when a lookup ran past the companion's own time limit, so the host can tell it from an unreachable server. */
    fun tookTooLong(): String

    /** The device's locale as a BCP 47 tag, which the contract asks a user message to carry. */
    fun locale(): String
}
