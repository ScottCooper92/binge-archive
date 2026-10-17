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
    /** Takes the version and any jurisdiction: "CC BY %1$s". */
    private val ccBy: String,
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
            Licence.Kind.CC_BY -> ccBy.format(Locale.ROOT, licence.detail)
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
        fun from(context: Context): SourceStrings =
            SourceStrings(
                publicDomainMark = context.getString(R.string.licence_public_domain),
                publicDomainUs = context.getString(R.string.licence_public_domain_us),
                cc0 = context.getString(R.string.licence_cc0),
                ccBy = context.getString(R.string.licence_cc_by),
                labelWithYear = context.getString(R.string.source_label_with_year),
                labelWithoutYear = context.getString(R.string.source_label_without_year),
                automaticCaptions = context.getString(R.string.subtitle_automatic),
            )
    }
}
