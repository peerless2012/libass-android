package io.github.peerless2012.ass.media.parser

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.AssTrack

@OptIn(UnstableApi::class)
class AssSegmentSubtitleParser(
    assHandler: AssHandler,
    track: AssTrack,
): AssSubtitleParser(assHandler, track, true) {

    private val timestampPattern = "(\\d+:\\d{2}:\\d{2}):(\\d{2})".toRegex()

    override fun onParse(data: ByteArray, offset: Int, length: Int) {
        // Note
        // Exoplayer will trans time from hh:mm:ss.xxx to hh:mm:ss:xxx
        // And lib ass only can parse hh:mm:ss.xxx
        // So we have to replace the time
        val string = String(data, offset, length, Charsets.UTF_8)
        val newText = timestampPattern.replace(string) { matchResult ->
            val timePart = matchResult.groupValues[1]
            val frames = matchResult.groupValues[2]
            "$timePart.$frames"
        }

        track.readBuffer(newText.toByteArray())
    }

}
