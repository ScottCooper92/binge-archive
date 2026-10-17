package io.github.scottcooper92.binge.archive.resolve

/**
 * The licence an item is listed under. [SourceStrings] turns it into the name a source's label shows.
 * Which kinds may be returned is the user's [LicenceLevel]; see CLAUDE.md > The one rule.
 */
data class Licence(
    val kind: Kind,
    /** For a Creative Commons licence with terms, the version and any jurisdiction: "4.0" or "3.0 US". */
    val detail: String = "",
) {
    enum class Kind(
        /** For a Creative Commons licence with terms, its short code: "BY-SA". Blank for the rest. */
        val code: String = "",
    ) {
        PUBLIC_DOMAIN_MARK,
        PUBLIC_DOMAIN_US,
        CC0,
        CC_BY("BY"),
        CC_BY_SA("BY-SA"),
        CC_BY_NC("BY-NC"),
        CC_BY_ND("BY-ND"),
        CC_BY_NC_SA("BY-NC-SA"),
        CC_BY_NC_ND("BY-NC-ND"),

        /** The item names no licence at all. Only [LicenceLevel.ANY] admits it. */
        NONE_STATED,
    }
}

/**
 * Which licences an item may be returned under: a user setting. The default is [DEFAULT], the
 * openly licensed four, and every wider level is the user's explicit choice.
 */
enum class LicenceLevel(
    private val kinds: Set<Licence.Kind>,
) {
    /** The Public Domain Mark, the Creative Commons public-domain URL and CC0. */
    PUBLIC_DOMAIN(PUBLIC_DOMAIN_KINDS),

    /** Public domain and CC BY: the default. */
    PUBLIC_DOMAIN_AND_CC_BY(PUBLIC_DOMAIN_KINDS + Licence.Kind.CC_BY),

    /** Public domain and every Creative Commons licence. */
    CREATIVE_COMMONS(Licence.Kind.entries.toSet() - Licence.Kind.NONE_STATED),

    /** Everything above, and items that state no licence. Chosen only through a confirm. */
    ANY(Licence.Kind.entries.toSet()),
    ;

    fun admits(kind: Licence.Kind): Boolean = kind in kinds

    companion object {
        val DEFAULT = PUBLIC_DOMAIN_AND_CC_BY
    }
}

private val PUBLIC_DOMAIN_KINDS = setOf(Licence.Kind.PUBLIC_DOMAIN_MARK, Licence.Kind.PUBLIC_DOMAIN_US, Licence.Kind.CC0)

object LicenceAllowlist {
    private val publicDomainMark = Regex("publicdomain/mark/1\\.0")
    private val publicDomainUs = Regex("licenses/publicdomain")
    private val cc0 = Regex("publicdomain/zero/1\\.0")

    /** A Creative Commons licence with terms, its version and an optional jurisdiction port. */
    private val creativeCommons = Regex("licenses/([a-z-]+)/(\\d\\.\\d)(?:/([a-z]{2}))?")

    private val byCode =
        Licence.Kind.entries
            .filter { it.code.isNotEmpty() }
            .associateBy { it.code.lowercase() }

    /** The licence [licenseUrl] names, when [level] admits it; otherwise null. */
    fun match(
        licenseUrl: String?,
        level: LicenceLevel = LicenceLevel.DEFAULT,
    ): Licence? = identify(licenseUrl)?.takeIf { level.admits(it.kind) }

    /**
     * The licence a Creative Commons URL names, whatever the level. A missing or blank URL is
     * [Licence.Kind.NONE_STATED]. The URL is matched whole, after only cosmetic differences are
     * removed: the scheme, `www.`, a trailing slash, and a `deed.xx` or `legalcode` page. Any other
     * URL, or text that is not a Creative Commons URL, is null: no level admits it.
     */
    fun identify(licenseUrl: String?): Licence? {
        if (licenseUrl.isNullOrBlank()) return Licence(Licence.Kind.NONE_STATED)
        val path = normalise(licenseUrl) ?: return null
        val ccMatch = creativeCommons.matchEntire(path)
        return when {
            publicDomainMark.matches(path) -> Licence(Licence.Kind.PUBLIC_DOMAIN_MARK)
            publicDomainUs.matches(path) -> Licence(Licence.Kind.PUBLIC_DOMAIN_US)
            cc0.matches(path) -> Licence(Licence.Kind.CC0)
            ccMatch != null -> creativeCommons(ccMatch)
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

    private fun creativeCommons(match: MatchResult): Licence? {
        val (code, version, jurisdiction) = match.destructured
        val kind = byCode[code] ?: return null
        val detail = if (jurisdiction.isEmpty()) version else "$version ${jurisdiction.uppercase()}"
        return Licence(kind, detail)
    }

    private const val HOST = "creativecommons.org/"
}
