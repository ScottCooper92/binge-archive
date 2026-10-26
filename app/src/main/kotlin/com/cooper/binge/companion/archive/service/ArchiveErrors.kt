package com.cooper.binge.companion.archive.service

import com.binge.companion.sdk.withUserMessage
import com.cooper.binge.companion.archive.resolve.ResolveOutcome
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
        when (failure) {
            is IOException -> StatusException(Status.UNAVAILABLE.withDescription(UNREACHABLE))
            is SerializationException -> StatusException(Status.UNAVAILABLE.withDescription(UNREADABLE))
            else -> null
        }

    companion object {
        const val REASON_NOT_ON_ARCHIVE = "NOT_ON_ARCHIVE"
        const val UNREACHABLE = "Wikidata or the Internet Archive could not be reached"
        const val UNREADABLE = "Wikidata or the Internet Archive answered with something unreadable"
    }
}

/** The sentences the host may show the user, in the device's language. */
interface UserMessages {
    fun notOnArchive(): String

    /** The device's locale as a BCP 47 tag, which the contract asks a user message to carry. */
    fun locale(): String
}
