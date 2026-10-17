package io.github.scottcooper92.binge.archive.resolve

import com.binge.companion.contracts.stream.v1.Resolution
import com.binge.companion.contracts.stream.v1.SourceQuality
import com.binge.companion.contracts.stream.v1.StreamSource
import com.binge.companion.contracts.stream.v1.SubtitleTrack
import com.binge.companion.contracts.v1.PlaybackSource
import com.google.protobuf.Timestamp
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

/**
 * Stage 3: turns licensed items' files into [StreamSource]s.
 *
 * - Only video files, by [VideoFormats]; every `mime_type` is a video type.
 * - When the film's runtime is known, a file under half of it is dropped: a reel of a split upload,
 *   a trailer or an extra is not the film. Without a runtime there is nothing to check against.
 * - One source per resolution class per item. Among encodes of the same class, h.264 in MP4 wins,
 *   then any MP4 (the Archive's `512Kb MPEG4` does not name its codec), then the taller picture, the
 *   longer file and the larger one.
 * - Each source's label names the film and its licence, so the host can show the licence on the row.
 * - SubRip and WebVTT files become subtitle tracks, at most ten per source.
 * - An item's sources go by height. The order of items, holding back an item this device reported
 *   and the cap of [MAX_SOURCES] are the [Resolver]'s, which streams sources item by item.
 */
class SourceMapper(
    private val strings: SourceStrings,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** When sources issued now expire: one resolve stamps all of its sources with the same time. */
    fun expiresAt(): Timestamp = Timestamp.newBuilder().setSeconds(TimeUnit.MILLISECONDS.toSeconds(clock()) + EXPIRY_SECONDS).build()

    /** One item's sources, by height, for a resolve that streams item by item. */
    fun sources(
        licensed: LicensedItem,
        facts: TitleFacts,
        expiresAt: Timestamp,
    ): List<StreamSource> {
        val item = licensed.item
        val minimumLength = facts.runtimeSeconds?.let { it * MINIMUM_SHARE_OF_RUNTIME }
        val encodes =
            item.files.mapNotNull { file ->
                val format = VideoFormats.of(file.format, file.name) ?: return@mapNotNull null
                val height = file.height ?: return@mapNotNull null
                if (minimumLength != null && (file.lengthSeconds ?: 0.0) < minimumLength) return@mapNotNull null
                Encode(file, format, height)
            }
        val best =
            encodes
                .groupBy { VideoFormats.resolutionOf(it.height) }
                .mapValues { (_, group) -> group.maxWith(PREFERENCE) }
        val label = strings.label(facts.title ?: item.title ?: item.identifier, facts.year, licensed.licence, MAX_LABEL)
        val subtitles = subtitles(item)
        return best.entries
            .sortedByDescending { it.value.height }
            .map { (resolution, encode) -> source(item, encode, resolution, label, subtitles, expiresAt) }
    }

    private fun source(
        item: ArchiveItem,
        encode: Encode,
        resolution: Resolution,
        label: String,
        subtitles: List<SubtitleTrack>,
        expiresAt: Timestamp,
    ): StreamSource =
        StreamSource
            .newBuilder()
            .setId("${item.identifier}/${encode.file.name}")
            .setSource(
                PlaybackSource
                    .newBuilder()
                    .setUrl(downloadUrl(item.identifier, encode.file.name))
                    .setExpiresAt(expiresAt)
                    .setMimeType(encode.format.mimeType),
            ).setQuality(
                SourceQuality
                    .newBuilder()
                    .setResolution(resolution)
                    .setVideoCodec(encode.format.codec)
                    .setSizeBytes(encode.file.sizeBytes),
            ).setLabel(label)
            .addAllSubtitles(subtitles)
            .build()

    private fun subtitles(item: ArchiveItem): List<SubtitleTrack> {
        val language = Languages.bcp47(item.language)
        return item.files
            .filter { it.format.equals(SUBRIP, ignoreCase = true) || it.format.equals(WEBVTT, ignoreCase = true) }
            // The same captions are often uploaded once per encode; one copy is enough.
            .distinctBy { it.sizeBytes to it.name.substringAfterLast('.') }
            .take(MAX_SUBTITLES)
            .map { file ->
                SubtitleTrack
                    .newBuilder()
                    .setUrl(downloadUrl(item.identifier, file.name))
                    .setLanguage(language)
                    .setLabel(if (file.name.contains(".asr.")) strings.automaticCaptions else "")
                    .build()
            }
    }

    private data class Encode(
        val file: ArchiveFile,
        val format: VideoFormat,
        val height: Int,
    )

    companion object {
        private val PREFERENCE =
            compareBy<Encode>(
                { it.format.isH264Mp4 },
                { it.format.mimeType == VideoFormat.MP4 },
                { it.height },
                { it.file.lengthSeconds ?: 0.0 },
                { it.file.sizeBytes },
            )

        /** Nominal: an Archive download URL does not expire, but the contract needs a bound. */
        const val EXPIRY_SECONDS = 24 * 60 * 60L
        const val MAX_SOURCES = 50
        const val MAX_SUBTITLES = 10
        const val MAX_LABEL = 120
        private const val MINIMUM_SHARE_OF_RUNTIME = 0.5
        private const val SUBRIP = "SubRip"
        private const val WEBVTT = "Web Video Text Tracks"

        fun downloadUrl(
            identifier: String,
            fileName: String,
        ): String =
            ResolverHttp.ARCHIVE_DOWNLOAD
                .toHttpUrl()
                .newBuilder()
                .addPathSegment(identifier)
                .addPathSegments(fileName)
                .build()
                .toString()
    }
}
