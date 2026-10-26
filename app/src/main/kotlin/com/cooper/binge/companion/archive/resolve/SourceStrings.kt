package com.cooper.binge.companion.archive.resolve

import android.content.Context
import com.cooper.binge.companion.archive.R
import java.util.Locale

/**
 * The text a source shows the host, resolved once from `strings.xml`. The pieces are plain strings
 * so the mapping stays testable without a [Context].
 */
class SourceStrings(
    private val publicDomainMark: String,
    private val publicDomainUs: String,
    private val cc0: String,
    /** Takes the licence's code and its version with any jurisdiction: "CC %1$s %2$s". */
    private val creativeCommons: String,
    private val noneStated: String,
    /** Takes the title and the year: "%1$s (%2$d)". */
    private val labelWithYear: String,
    val automaticCaptions: String,
) {
    /**
     * The licence as the host shows it, in `StreamSource.licence_label`: a recognised licence's short
     * name, "No licence stated", or the item's own words, which no translation can name. At most
     * [MAX_LICENCE_LABEL] characters.
     */
    fun licenceName(licence: Licence): String =
        when (licence.kind) {
            Licence.Kind.PUBLIC_DOMAIN_MARK -> publicDomainMark
            Licence.Kind.PUBLIC_DOMAIN_US -> publicDomainUs
            Licence.Kind.CC0 -> cc0
            Licence.Kind.NONE_STATED -> noneStated
            Licence.Kind.RESTRICTED, Licence.Kind.UNKNOWN -> licence.detail
            else -> creativeCommons.format(Locale.ROOT, licence.kind.code, licence.detail)
        }.shortened(MAX_LICENCE_LABEL)

    /** The source's label: "Night of the Living Dead (1968)", the title shortened to fit [max] characters. */
    fun label(
        title: String,
        year: Int?,
        max: Int,
    ): String {
        fun of(title: String) = if (year == null) title else labelWithYear.format(Locale.ROOT, title, year)
        val full = of(title)
        if (full.length <= max) return full
        return of(title.shortened(max - of("").length))
    }

    companion object {
        /** The host's cap on `licence_label`. */
        const val MAX_LICENCE_LABEL = 40

        fun from(context: Context): SourceStrings =
            SourceStrings(
                publicDomainMark = context.getString(R.string.licence_public_domain),
                publicDomainUs = context.getString(R.string.licence_public_domain_us),
                cc0 = context.getString(R.string.licence_cc0),
                creativeCommons = context.getString(R.string.licence_creative_commons),
                noneStated = context.getString(R.string.licence_none_stated),
                labelWithYear = context.getString(R.string.source_label_with_year),
                automaticCaptions = context.getString(R.string.subtitle_automatic),
            )
    }
}

/**
 * This text in at most [max] characters, ending in "…" when it is cut. It never cuts a character in
 * two: a surrogate pair stays whole.
 */
internal fun String.shortened(max: Int): String {
    if (length <= max) return this
    var end = max - 1
    if (end > 0 && Character.isHighSurrogate(this[end - 1])) end--
    return take(end).trimEnd() + "…"
}
