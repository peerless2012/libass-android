package io.github.peerless2012.ass.media.parser

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
object AssHeaderParser {

    private const val ASS_EVENTS = "[Events]\n" +
            "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"

    /**
     * Fix some ass header with error end.
     * https://github.com/jellyfin/jellyfin-ffmpeg/issues/506
     */
    private fun fixAssHeaderIfNeed(buffer: ByteArray/*, extra: ByteArray*/): ByteArray {
        return if (buffer[buffer.size -1] != 0.toByte()) {
            // validate ass header
            buffer
        } else {
            // remote the last null character and append the events tag
            (String(buffer, 0, buffer.size - 1) + "\n" + ASS_EVENTS).toByteArray()
        }
    }

    /**
     * Parses the headers from the initialization data of the given [format]. The behavior of this
     * method depends on the value of [useOriginalHeaders]:
     *
     * - If [useOriginalHeaders] is true, the original headers are returned without modification.
     * - If [useOriginalHeaders] is false, the headers are adjusted to ensure compatibility with
     *   ExoPlayer's native subtitle renderer.
     *
     * ExoPlayer's subtitle renderer modifies the "Format:" line in the headers to include the line
     * number for duplicate checking. When using it, this method overrides the original "Format:"
     * line with the updated version provided by ExoPlayer.
     *,
     * When the effects overlay is used, duplication checks are handled by libass, so the original
     * headers are preserved.
     */
    fun parse(format: Format, useOriginalHeaders: Boolean): ByteArray {
        if (useOriginalHeaders) {
            return fixAssHeaderIfNeed(format.initializationData[1])
        }

        val header1 = format.initializationData[0].decodeToString()
        assert(header1.startsWith("Format:"))

        val header2 = fixAssHeaderIfNeed(format.initializationData[1]).decodeToString()

        val lines = header2.lines().toMutableList()
        val index = lines.indexOfFirst {
            it.startsWith("[Events]")
        }
        if (index >= 0 && lines[index + 1].startsWith("Format:")) {
            lines[index + 1] = header1
        }
        val result = lines.joinToString(separator = "\n")
        return result.toByteArray()
    }
}
