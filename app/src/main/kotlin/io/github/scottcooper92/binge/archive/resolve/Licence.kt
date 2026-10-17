package io.github.scottcooper92.binge.archive.resolve

/**
 * An open licence this app may return an item under. [SourceStrings] turns it into the name a
 * source's label shows.
 *
 * The allowlist is exactly four licences: the Public Domain Mark, the Creative Commons
 * public-domain URL, CC0 and CC BY. Nothing else is returned, and there is no setting that changes
 * that. See CLAUDE.md > The one rule.
 */
data class Licence(
    val kind: Kind,
    /** For [Kind.CC_BY], the version and any jurisdiction: "4.0" or "3.0 US". */
    val detail: String = "",
) {
    enum class Kind { PUBLIC_DOMAIN_MARK, PUBLIC_DOMAIN_US, CC0, CC_BY }
}

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
            publicDomainMark.matches(path) -> Licence(Licence.Kind.PUBLIC_DOMAIN_MARK)
            publicDomainUs.matches(path) -> Licence(Licence.Kind.PUBLIC_DOMAIN_US)
            cc0.matches(path) -> Licence(Licence.Kind.CC0)
            ccByMatch != null -> ccBy(ccByMatch)
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

    private fun ccBy(match: MatchResult): Licence {
        val (version, jurisdiction) = match.destructured
        val detail = if (jurisdiction.isEmpty()) version else "$version ${jurisdiction.uppercase()}"
        return Licence(Licence.Kind.CC_BY, detail)
    }

    private const val HOST = "creativecommons.org/"
}
