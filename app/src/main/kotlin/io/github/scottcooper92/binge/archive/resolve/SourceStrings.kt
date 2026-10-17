package io.github.scottcooper92.binge.archive.resolve

import android.content.Context
import io.github.scottcooper92.binge.archive.R
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
    /** Takes the title, the year and the licence name. */
    private val labelWithYear: String,
    /** Takes the title and the licence name. */
    private val labelWithoutYear: String,
    val automaticCaptions: String,
) {
    fun licenceName(licence: Licence): String =
        when (licence.kind) {
            Licence.Kind.PUBLIC_DOMAIN_MARK -> publicDomainMark
            Licence.Kind.PUBLIC_DOMAIN_US -> publicDomainUs
            Licence.Kind.CC0 -> cc0
            Licence.Kind.NONE_STATED -> noneStated
            // The item's own words, which no translation can name: shortened, so the label keeps room for the title.
            Licence.Kind.RESTRICTED, Licence.Kind.UNKNOWN ->
                if (licence.detail.length <= MAX_OWN_TEXT) licence.detail else licence.detail.take(MAX_OWN_TEXT - 1).trimEnd() + "…"
            else -> creativeCommons.format(Locale.ROOT, licence.kind.code, licence.detail)
        }

    /** "Night of the Living Dead (1968) · Public domain (US)". */
    fun label(
        title: String,
        year: Int?,
        licence: Licence,
    ): String {
        val name = licenceName(licence)
        return if (year == null) labelWithoutYear.format(Locale.ROOT, title, name) else labelWithYear.format(Locale.ROOT, title, year, name)
    }

    /** [label] with the title shortened so the whole fits in [max] characters. */
    fun label(
        title: String,
        year: Int?,
        licence: Licence,
        max: Int,
    ): String {
        val full = label(title, year, licence)
        if (full.length <= max) return full
        val room = max - label("", year, licence).length
        return label(title.take(room - 1).trimEnd() + "…", year, licence)
    }

    companion object {
        /** The most of an item's own licence text a label shows, ellipsis included. */
        const val MAX_OWN_TEXT = 40

        fun from(context: Context): SourceStrings =
            SourceStrings(
                publicDomainMark = context.getString(R.string.licence_public_domain),
                publicDomainUs = context.getString(R.string.licence_public_domain_us),
                cc0 = context.getString(R.string.licence_cc0),
                creativeCommons = context.getString(R.string.licence_creative_commons),
                noneStated = context.getString(R.string.licence_none_stated),
                labelWithYear = context.getString(R.string.source_label_with_year),
                labelWithoutYear = context.getString(R.string.source_label_without_year),
                automaticCaptions = context.getString(R.string.subtitle_automatic),
            )
    }
}
