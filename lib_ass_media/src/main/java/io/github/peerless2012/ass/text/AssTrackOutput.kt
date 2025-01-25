package io.github.peerless2012.ass.text

import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.extractor.TrackOutput
import io.github.peerless2012.ass.AssHandler
import java.util.regex.Pattern

/**
 * This class is only used by the overlay renderer. It's needed to get the start time of the subtitles.
 */
@UnstableApi
class AssTrackOutput(
    private val delegate: TrackOutput,
    private val assHandler: AssHandler,
) : TrackOutput {

    private var isAss = false

    private var currentLine = ""

    override fun format(format: Format) {
        if (format.sampleMimeType == MimeTypes.TEXT_SSA || format.codecs == MimeTypes.TEXT_SSA) {
            isAss = true
        }
        delegate.format(format)
    }

    // This method is untested (I don't know if this is actually used for ASS subtitles)
    override fun sampleData(
        input: DataReader,
        length: Int,
        allowEndOfInput: Boolean,
        sampleDataPart: Int
    ): Int {
        if (isAss) {
            val buffer = ByteArray(length)
            input.read(buffer, 0, length)
            currentLine = buffer.decodeToString()
        }
        return delegate.sampleData(input, length, allowEndOfInput, sampleDataPart)
    }

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
        if (isAss) {
            currentLine = data.data.decodeToString(endIndex = length)
        }
        delegate.sampleData(data, length, sampleDataPart)
    }

    override fun sampleMetadata(
        timeUs: Long,
        flags: Int,
        size: Int,
        offset: Int,
        cryptoData: TrackOutput.CryptoData?
    ) {
        if (isAss && timeUs.isValidTs) {
            val (lineType, content) = currentLine.split(' ', limit = 2)
            val (_, rawEnd, remainder) = content.split(',', limit = 3)

            val endUs = parseTimecodeUs(rawEnd)
            if (endUs.isValidTs) {
                val start = timeUs.toAssTime()
                val end = (timeUs + endUs).toAssTime()
                val dialogue = "%s %s,%s,%s".format(lineType, start, end, remainder)
                assHandler.track?.readBuffer(dialogue)
            }
        }
        delegate.sampleMetadata(timeUs, flags, size, offset, cryptoData)
    }

    private fun parseTimecodeUs(timeString: String): Long {
        val matcher = SSA_TIMECODE_PATTERN.matcher(timeString.trim { it <= ' ' })
        if (!matcher.matches()) {
            return C.TIME_UNSET
        }
        var timestampUs =
            Util.castNonNull(matcher.group(1)).toLong() * 60 * 60 * C.MICROS_PER_SECOND
        timestampUs += Util.castNonNull(matcher.group(2)).toLong() * 60 * C.MICROS_PER_SECOND
        timestampUs += Util.castNonNull(matcher.group(3)).toLong() * C.MICROS_PER_SECOND
        timestampUs += Util.castNonNull(matcher.group(4)).toLong() * 10000
        return timestampUs
    }

    private fun Long.toAssTime(): String {
        val total = this / 10_000
        val hours = total / (60 * 60 * 100)
        val minutes = (total / (60 * 100)) % 60
        val seconds = (total / 100) % 60
        val centiseconds = total % 100
        return "%d:%02d:%02d.%02d".format(hours, minutes, seconds, centiseconds)
    }

    private val Long.isValidTs
        get() = this != C.TIME_UNSET

    private companion object {
        val SSA_TIMECODE_PATTERN: Pattern =
            Pattern.compile("""(?:(\d+):)?(\d+):(\d+)[:.](\d+)""")
    }
}