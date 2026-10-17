package io.github.scottcooper92.binge.archive.resolve

/**
 * The licence an item is listed under. [SourceStrings] turns it into the name a source's label shows.
 * Which kinds may be returned is the user's [LicenceLevel]; see CLAUDE.md > The one rule.
 */
data class Licence(
    val kind: Kind,
    /**
     * For a Creative Commons licence with terms, the version and any jurisdiction: "4.0" or "3.0 US".
     * For [Kind.RESTRICTED] and [Kind.UNKNOWN], the item's own licence text, whitespace collapsed and
     * control and format characters removed.
     */
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

        /** The item's licence text reserves the rights: "All rights reserved", "©". Only [LicenceLevel.ANY] admits it. */
        RESTRICTED,

        /** Licence text or a URL this app does not recognise. Only [LicenceLevel.ANY] admits it. */
        UNKNOWN,
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
    CREATIVE_COMMONS(
        Licence.Kind.entries
            .filter { it.code.isNotEmpty() }
            .toSet() + PUBLIC_DOMAIN_KINDS,
    ),

    /**
     * Every item, whatever its licence: none stated, unrecognised or restrictive. Chosen only through a
     * confirm. Restricted and dark items still stay out: that is the Archive's access control, not a licence.
     */
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

    /**
     * What an item's licence field says, whatever the level; [LicenceLevel.admits] decides whether it
     * may be returned. A missing or blank field is [Licence.Kind.NONE_STATED]. A Creative Commons URL
     * is matched whole, after only cosmetic differences are removed: the scheme, `www.`, a trailing
     * slash, and a `deed.xx` or `legalcode` page. Text that reserves the rights is
     * [Licence.Kind.RESTRICTED]; anything else is [Licence.Kind.UNKNOWN]. Both keep the text.
     */
    fun identify(licenseUrl: String?): Licence {
        if (licenseUrl.isNullOrBlank()) return Licence(Licence.Kind.NONE_STATED)
        val recognised = normalise(licenseUrl)?.let(::recognise)
        if (recognised != null) return recognised
        // Free text from the uploader reaches the host: no control or bidi-override characters.
        val text =
            licenseUrl
                .replace(whitespace, " ")
                .filterNot { Character.getType(it) in invisible }
                .trim()
        return Licence(if (restrictive.containsMatchIn(text)) Licence.Kind.RESTRICTED else Licence.Kind.UNKNOWN, text)
    }

    private fun recognise(path: String): Licence? {
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

    private val whitespace = Regex("\\s+")

    /** Character types that draw nothing or reorder text, once whitespace is a plain space: controls and format marks. */
    private val invisible = setOf(Character.FORMAT.toInt(), Character.CONTROL.toInt())

    /** Words that reserve the rights, as the Archive's free-text licence fields put it. */
    private val restrictive = Regex("all rights reserved|©|\\(c\\)\\s*\\d{4}|copyright\\s+(?:\\(c\\)\\s*)?\\d{4}", RegexOption.IGNORE_CASE)

    private const val HOST = "creativecommons.org/"
}
