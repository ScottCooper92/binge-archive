package com.cooper.binge.companion.archive.resolve

import com.binge.companion.contracts.stream.v1.Resolution

/** What a source's file is: its MIME type, always `video/…`, and a short codec label. */
data class VideoFormat(
    val mimeType: String,
    val codec: String,
) {
    val isH264Mp4: Boolean get() = mimeType == MP4 && codec == H264

    companion object {
        const val MP4 = "video/mp4"
        const val H264 = "H.264"
    }
}

/**
 * Maps an Archive file's `format` (and, where the format does not say, its extension) to a
 * [VideoFormat]. Anything not listed is not a video this app hands on: subtitles, images, audio,
 * metadata and unknown formats all return null, so `mime_type` is only ever a video type.
 */
object VideoFormats {
    private const val MPEG2 = "MPEG-2"

    private val byFormat =
        mapOf(
            "h.264" to VideoFormat(VideoFormat.MP4, VideoFormat.H264),
            "h.264 hd" to VideoFormat(VideoFormat.MP4, VideoFormat.H264),
            "h.264 ia" to VideoFormat(VideoFormat.MP4, VideoFormat.H264),
            "512kb mpeg4" to VideoFormat(VideoFormat.MP4, ""),
            "mpeg4" to VideoFormat(VideoFormat.MP4, ""),
            "matroska" to VideoFormat("video/x-matroska", ""),
            "webm" to VideoFormat("video/webm", ""),
            "ogg video" to VideoFormat("video/ogg", "Theora"),
            "mpeg2" to VideoFormat("video/mpeg", MPEG2),
            "mpeg1" to VideoFormat("video/mpeg", "MPEG-1"),
            "quicktime" to VideoFormat("video/quicktime", ""),
        )

    /** The Archive's format for an h.264 original in any container, which the extension names. */
    private const val H264_ANY_CONTAINER = "h.264 mpeg4"

    /** A container for the formats that name the codec but not the container. */
    private val byExtension =
        mapOf(
            "mp4" to VideoFormat.MP4,
            "m4v" to VideoFormat.MP4,
            "mkv" to "video/x-matroska",
            "m2ts" to "video/mp2t",
            "ts" to "video/mp2t",
            "mov" to "video/quicktime",
            "webm" to "video/webm",
        )

    fun of(
        format: String?,
        fileName: String,
    ): VideoFormat? {
        val key = format?.trim()?.lowercase() ?: return null
        return when (key) {
            H264_ANY_CONTAINER ->
                byExtension[fileName.substringAfterLast('.', "").lowercase()]?.let { VideoFormat(it, VideoFormat.H264) }
            else -> byFormat[key]
        }
    }

    /** The resolution class of a picture [height] lines tall. */
    fun resolutionOf(height: Int): Resolution =
        when {
            height >= UHD_LINES -> Resolution.RESOLUTION_UHD_2160
            height >= FULL_HD_LINES -> Resolution.RESOLUTION_HD_1080
            height >= HD_LINES -> Resolution.RESOLUTION_HD_720
            else -> Resolution.RESOLUTION_SD
        }

    // A little under each nominal height, so a cropped encode (1080x1920 at 1036 lines, say) keeps
    // its class.
    private const val UHD_LINES = 2000
    private const val FULL_HD_LINES = 1000
    private const val HD_LINES = 700
}
