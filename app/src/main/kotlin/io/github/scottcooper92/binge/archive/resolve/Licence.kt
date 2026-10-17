package io.github.scottcooper92.binge.archive.resolve

/**
 * An open licence this app may return an item under, with the name a source's label shows.
 *
 * The allowlist is exactly four licences: the Public Domain Mark, the Creative Commons
 * public-domain URL, CC0 and CC BY. Nothing else is returned, and there is no setting that changes
 * that. See CLAUDE.md > The one rule.
 */
data class Licence(
    val label: String,
)

object LicenceAllowlist {
    private val publicDomainMark = Regex("publicdomain/mark/1\\.0")
    private val publicDomainUs = Regex("licenses/publicdomain")
    private val cc0 = Regex("publicdomain/zero/1\\.0")

    /** `by` and nothing after it: not `by-sa`, `by-nc` or `by-nd`. An optional jurisdiction port. */
    private val ccBy = Regex("licenses/by/(\\d\\.\\d)(?:/([a-z]{2}))?")

    /**
     * The licence a Creative Commons URL names, or null when it is not on the allowlist. The URL is
     * matched whole, after only cosmetic differences are removed: the scheme, `www.`, a trailing
     * slash, and a `deed.xx` or `legalcode` page. Anything else, a missing URL included, is null.
     */
    fun match(licenseUrl: String?): Licence? {
        val path = normalise(licenseUrl ?: return null) ?: return null
        val ccByMatch = ccBy.matchEntire(path)
        return when {
            publicDomainMark.matches(path) -> Licence("Public domain")
            publicDomainUs.matches(path) -> Licence("Public domain (US)")
            cc0.matches(path) -> Licence("CC0")
            ccByMatch != null -> Licence(ccByLabel(ccByMatch))
            else -> null
        }
    }

    private fun normalise(url: String): String? {
        val withoutScheme =
            url
                .trim()
                .lowercase()
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
        if (!withoutScheme.startsWith(HOST)) return null
        return withoutScheme
            .removePrefix(HOST)
            .substringBefore('?')
            .substringBefore('#')
            .trimEnd('/')
            .removeSuffix("/legalcode")
            .replace(Regex("/deed\\.[a-z-]+$"), "")
    }

    private fun ccByLabel(match: MatchResult): String {
        val (version, jurisdiction) = match.destructured
        return if (jurisdiction.isEmpty()) "CC BY $version" else "CC BY $version ${jurisdiction.uppercase()}"
    }

    private const val HOST = "creativecommons.org/"
}
