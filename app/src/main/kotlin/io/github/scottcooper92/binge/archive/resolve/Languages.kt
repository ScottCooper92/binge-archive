package io.github.scottcooper92.binge.archive.resolve

/**
 * The Archive's `language` field, free text in practice ("English", "eng"), as a BCP 47 tag for a
 * subtitle track. Unknown is `und`, BCP 47's own word for it.
 */
internal object Languages {
    private const val UNDETERMINED = "und"

    private val tags =
        mapOf(
            "en" to listOf("english", "eng", "en"),
            "fr" to listOf("french", "fre", "fra", "fr"),
            "de" to listOf("german", "ger", "deu", "de"),
            "es" to listOf("spanish", "spa", "es"),
            "it" to listOf("italian", "ita", "it"),
            "pt" to listOf("portuguese", "por", "pt"),
            "ru" to listOf("russian", "rus", "ru"),
            "ja" to listOf("japanese", "jpn", "ja"),
            "sv" to listOf("swedish", "swe", "sv"),
            "nl" to listOf("dutch", "dut", "nld", "nl"),
        ).flatMap { (tag, names) -> names.map { it to tag } }.toMap()

    fun bcp47(language: String?): String = tagOrNull(language) ?: UNDETERMINED

    /** The tag for [language], a name, a two or three letter code, or null where this table does not know it. */
    fun tagOrNull(language: String?): String? = tags[language?.trim()?.lowercase()]
}
