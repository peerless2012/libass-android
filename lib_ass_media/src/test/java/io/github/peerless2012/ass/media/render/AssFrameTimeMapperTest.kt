package io.github.peerless2012.ass.media.render

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Each test feeds the mapper the renderer calls ExoPlayer makes, builds the timestamp media3 gives
 * the effect for a frame (buffer time - startPositionUs of the first stream since reset), and
 * checks that the frame's media time comes back.
 */
class AssFrameTimeMapperTest {

    private val mapper = AssFrameTimeMapper()

    @Test
    fun unsetBeforeEnabled() {
        assertEquals(C.TIME_UNSET, mapper.toMediaTimeUs(0))
    }

    @Test
    fun playbackStartedAtZero() {
        mapper.onEnabled(startPositionUs = OFFSET, offsetUs = OFFSET)

        assertEquals(5 * S, mapper.toMediaTimeUs(effectTimeUs(5 * S, OFFSET, base = OFFSET)))
    }

    @Test
    fun playbackStartedLater() {
        val base = OFFSET + 600 * S
        mapper.onEnabled(startPositionUs = base, offsetUs = OFFSET)

        assertEquals(605 * S, mapper.toMediaTimeUs(effectTimeUs(605 * S, OFFSET, base)))
    }

    @Test
    fun seekInsideTheItemKeepsTheMapping() {
        val base = OFFSET + 600 * S
        mapper.onEnabled(startPositionUs = base, offsetUs = OFFSET)
        mapper.onPositionReset()

        assertEquals(5 * S, mapper.toMediaTimeUs(effectTimeUs(5 * S, OFFSET, base)))
    }

    @Test
    fun reEnableWithoutResetKeepsTheFirstStartPosition() {
        // Seek to another item: the renderer is disabled and enabled again, but not reset.
        val base = OFFSET + 600 * S
        mapper.onEnabled(startPositionUs = base, offsetUs = OFFSET)
        mapper.onEnabled(startPositionUs = OFFSET, offsetUs = OFFSET)

        assertEquals(3 * S, mapper.toMediaTimeUs(effectTimeUs(3 * S, OFFSET, base)))
    }

    @Test
    fun playlistTransition() {
        val firstDuration = 1200 * S
        val secondOffset = OFFSET + firstDuration
        mapper.onEnabled(startPositionUs = OFFSET, offsetUs = OFFSET)
        // The next item's stream is handed over before the current item's last frames are processed.
        mapper.onStreamReplaced(startPositionUs = secondOffset, offsetUs = secondOffset)

        assertEquals(1199 * S, mapper.toMediaTimeUs(effectTimeUs(1199 * S, OFFSET, base = OFFSET)))
        assertEquals(5 * S, mapper.toMediaTimeUs(effectTimeUs(5 * S, secondOffset, base = OFFSET)))
    }

    @Test
    fun seekAfterPlaylistTransitionUsesTheLastStream() {
        val firstDuration = 1200 * S
        val secondOffset = OFFSET + firstDuration
        mapper.onEnabled(startPositionUs = OFFSET, offsetUs = OFFSET)
        mapper.onStreamReplaced(startPositionUs = secondOffset, offsetUs = secondOffset)
        mapper.onPositionReset()

        assertEquals(0L, mapper.toMediaTimeUs(effectTimeUs(0, secondOffset, base = OFFSET)))
    }

    @Test
    fun resetTakesTheNextStartPosition() {
        mapper.onEnabled(startPositionUs = OFFSET + 600 * S, offsetUs = OFFSET)
        mapper.onReset()
        assertEquals(C.TIME_UNSET, mapper.toMediaTimeUs(0))

        val base = OFFSET + 30 * S
        mapper.onEnabled(startPositionUs = base, offsetUs = OFFSET)

        assertEquals(40 * S, mapper.toMediaTimeUs(effectTimeUs(40 * S, OFFSET, base)))
    }

    /** What media3 1.11 hands to video effects for a frame at [mediaTimeUs]. */
    private fun effectTimeUs(mediaTimeUs: Long, offsetUs: Long, base: Long): Long {
        return mediaTimeUs + offsetUs - base
    }

    private companion object {
        /** MediaPeriodQueue.INITIAL_RENDERER_POSITION_OFFSET_US */
        const val OFFSET = 1_000_000_000_000L
        const val S = 1_000_000L
    }
}
