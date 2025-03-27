package io.github.peerless2012.ass.media.parser

import androidx.media3.common.util.UnstableApi
import io.github.peerless2012.ass.AssTrack
import io.github.peerless2012.ass.media.AssHandler

/**
 * Ass full subtitle parser.
 */
@UnstableApi
class AssFullSubtitleParser(
    assHandler: AssHandler,
    track: AssTrack
) : AssSubtitleParser(assHandler, track, false) {

    override fun onParse(data: ByteArray, offset: Int, length: Int) {
        track.readBuffer(data, offset, length)
    }

}