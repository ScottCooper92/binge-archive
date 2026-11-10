package com.cooper.binge.companion.archive.service

import com.binge.companion.contracts.stream.v1.ListServedTitlesRequest
import java.util.Base64

/** What `ListServedTitles` pages by, read from a request: a refusal is a message for `INVALID_ARGUMENT`. */
internal class ServedTitlesQuery(
    val region: String,
    val allowed: Set<String>,
    val includeUnrated: Boolean,
    val pageSize: Int,
    val after: Int,
) {
    companion object {
        /** The page size for a `page_size` of 0. */
        const val DEFAULT_PAGE_SIZE = 100

        /** The contract's cap: at most this many titles, whatever is asked. */
        const val MAX_PAGE_SIZE = 200

        private const val VERSION = "st1"

        /** A token is its version, the last TMDB id served and the fingerprint. */
        private const val TOKEN_FIELDS = 3
        private const val HEX = 16
        private val REGION = Regex("[A-Za-z]{2}")

        /** The query [request] asks for, or the reason it is refused. */
        fun of(request: ListServedTitlesRequest): Result<ServedTitlesQuery> {
            if (!REGION.matches(request.region)) return Result.failure(Refused("region must be two letters, was \"${request.region}\""))
            if (request.pageSize < 0) return Result.failure(Refused("page_size must not be negative, was ${request.pageSize}"))
            val region = request.region.uppercase()
            val allowed = request.allowedCertificationsList.toSet()
            val after =
                if (request.pageToken.isEmpty()) {
                    0
                } else {
                    afterOf(request, region, allowed) ?: return Result.failure(Refused("page_token was not issued for this request"))
                }
            val size = (if (request.pageSize == 0) DEFAULT_PAGE_SIZE else request.pageSize).coerceAtMost(MAX_PAGE_SIZE)
            return Result.success(ServedTitlesQuery(region, allowed, request.includeUnrated, size, after))
        }

        /** The token for the page that follows a page ending at TMDB id [last], bound to [request]'s filter and page size. */
        fun tokenAfter(
            request: ListServedTitlesRequest,
            last: Int,
        ): String {
            val region = request.region.uppercase()
            val fingerprint = fingerprint(region, request.allowedCertificationsList.toSet(), request.includeUnrated, request.pageSize)
            return Base64.getUrlEncoder().withoutPadding().encodeToString("$VERSION:$last:$fingerprint".toByteArray())
        }

        /** The id a token stands for, or null when this app did not issue it for this filter and page size. */
        private fun afterOf(
            request: ListServedTitlesRequest,
            region: String,
            allowed: Set<String>,
        ): Int? {
            val text = attemptDecode(request.pageToken) ?: return null
            val parts = text.split(':')
            if (parts.size != TOKEN_FIELDS || parts[0] != VERSION) return null
            val after = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            return after.takeIf { parts[2] == fingerprint(region, allowed, request.includeUnrated, request.pageSize) }
        }

        private fun attemptDecode(token: String): String? =
            try {
                String(Base64.getUrlDecoder().decode(token))
            } catch (_: IllegalArgumentException) {
                null
            }

        /** A small stable digest of what a token is valid with; the host never reads it. */
        private fun fingerprint(
            region: String,
            allowed: Set<String>,
            includeUnrated: Boolean,
            pageSize: Int,
        ): String =
            listOf(region, allowed.sorted().joinToString("\u0000"), includeUnrated, pageSize).joinToString("|").hashCode().toString(HEX)
    }

    /** Why a request was refused. */
    class Refused(
        message: String,
    ) : Exception(message)
}
