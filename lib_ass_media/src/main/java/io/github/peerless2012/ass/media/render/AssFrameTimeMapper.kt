package io.github.peerless2012.ass.media.render

import androidx.media3.common.C

/**
 * Maps the presentation time a video effect receives back to the media time of the frame, which is
 * the time base of the ASS events.
 *
 * With video effects, `MediaCodecVideoRenderer` shifts each buffer timestamp (renderer time, which
 * is media time + stream offset) by `-startPositionUs` before the frame reaches the effects.
 * `startPositionUs` is the start position of the first stream the renderer is enabled with after
 * it is created or reset, and it keeps that value across seeks, disable/enable cycles and playlist
 * transitions. This class mirrors that from the calls [AssVideoRendererWrapper] sees, and keeps
 * every stream's offset so that a frame is mapped with the offset of the stream it belongs to.
 *
 * The renderer calls come from the playback thread and [toMediaTimeUs] from the GL thread.
 */
internal class AssFrameTimeMapper {

    private class Stream(val startPositionUs: Long, val offsetUs: Long)

    /** The renderer time that media3 subtracts from every frame timestamp. */
    private var baseUs = C.TIME_UNSET

    /** In renderer-time order. Each stream ends where the next one starts. */
    private val streams = ArrayDeque<Stream>()

    @Synchronized
    fun onEnabled(startPositionUs: Long, offsetUs: Long) {
        if (baseUs == C.TIME_UNSET) {
            baseUs = startPositionUs
        }
        streams.clear()
        streams.addLast(Stream(startPositionUs, offsetUs))
    }

    @Synchronized
    fun onStreamReplaced(startPositionUs: Long, offsetUs: Long) {
        streams.addLast(Stream(startPositionUs, offsetUs))
    }

    /** Like `MediaCodecRenderer`, only the last stream is output after a position reset. */
    @Synchronized
    fun onPositionReset() {
        while (streams.size > 1) {
            streams.removeFirst()
        }
    }

    @Synchronized
    fun onReset() {
        baseUs = C.TIME_UNSET
        streams.clear()
    }

    /**
     * Returns the media time of the frame with the given effect presentation time, or
     * [C.TIME_UNSET] if the video renderer hasn't been enabled.
     */
    @Synchronized
    fun toMediaTimeUs(framePresentationTimeUs: Long): Long {
        if (baseUs == C.TIME_UNSET || streams.isEmpty()) {
            return C.TIME_UNSET
        }
        val rendererTimeUs = framePresentationTimeUs + baseUs
        // Frames arrive in order, so the streams that ended before this frame aren't needed anymore.
        while (streams.size > 1 && streams[1].startPositionUs <= rendererTimeUs) {
            streams.removeFirst()
        }
        return rendererTimeUs - streams.first().offsetUs
    }
}
