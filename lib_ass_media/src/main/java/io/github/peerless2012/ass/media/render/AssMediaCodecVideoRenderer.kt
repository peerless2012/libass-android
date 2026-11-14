package io.github.peerless2012.ass.media.render

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import java.nio.ByteBuffer
import java.util.TreeSet

/**
 * A `MediaCodecVideoRenderer` that drops late frames for
 * [io.github.peerless2012.ass.media.type.AssRenderType.EFFECTS_ATLAS].
 *
 * Decides right before drawing a frame if we will render it.
 * It drops the frame if its display time is already over (`pts + duration < now`),
 * unless no frame was shown for 100ms (to avoid freezing).
 * This renderer applies the same rule right before a frame enters
 * the video effects, where the subtitles are rendered.
 *
 * It replaces both of media3's own drops method, which doesn't work well
 * when a videoEffects takes too much time to render:
 * - After the effects, media3 drops frames that turned out late. By then the frame's subtitles are
 *   rendered, so dropping it saves no time and only throws the work away.
 * - Before the effects, media3 drops frames *predicted* to be late, from how late the frames it
 *   showed were. Those are a couple of frames behind, and dropped frames don't count, so when libass
 *   is slower than the frame rate it keeps predicting late and drops up to 2 frames out of 3, when
 *   about 1 out of 10 would do.
 *
 * Very late frames are still dropped up to the next keyframe (`shouldDropBuffersToKeyframe`).
 *
 * `buildWithAssSupport` uses it when no `renderersFactory` is passed. An app with its own
 * `DefaultRenderersFactory` settings can build it from the same settings by overriding
 * `buildVideoRenderers`; otherwise media3's dropping is used:
 * ```
 * override fun buildVideoRenderers(context: Context, extensionRendererMode: Int,
 *     mediaCodecSelector: MediaCodecSelector, enableDecoderFallback: Boolean, eventHandler: Handler,
 *     eventListener: VideoRendererEventListener, allowedVideoJoiningTimeMs: Long, out: ArrayList<Renderer>) {
 *     out.add(AssMediaCodecVideoRenderer(MediaCodecVideoRenderer.Builder(context)
 *         .setCodecAdapterFactory(getCodecAdapterFactory())
 *         .setMediaCodecSelector(mediaCodecSelector)
 *         .setEnableDecoderFallback(enableDecoderFallback)
 *         .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
 *         .setEventHandler(eventHandler)
 *         .setEventListener(eventListener)
 *         .setMaxDroppedFramesToNotify(MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)))
 *         // ...and the other settings the app gives its factory.
 * }
 * ```
 *
 * @param builder Its `experimentalSetLateThresholdToDropDecoderInputUs` is replaced: this renderer
 * replaces media3's prediction-based drop.
 */
@UnstableApi
class AssMediaCodecVideoRenderer(
    builder: MediaCodecVideoRenderer.Builder
) : MediaCodecVideoRenderer(disablePredictedLateDrop(builder)) {

    /**
     * The presentation times of the buffers queued to the codec and not output yet.
     */
    private val queuedPresentationTimesUs = TreeSet<Long>()

    /** The presentation time of the last output buffer seen, to estimate the frame duration. */
    private var lastBufferPresentationTimeUs = C.TIME_UNSET

    /**
     * The gap between the last two output buffers, or [C.TIME_UNSET] if unknown. It's the duration
     * of the previous frame, used only when the next frame isn't queued yet.
     */
    private var estimatedFrameDurationUs = C.TIME_UNSET

    /** The presentation time of the last frame warned about using [estimatedFrameDurationUs]. */
    private var lastWarnedPresentationTimeUs = C.TIME_UNSET

    /** [android.os.SystemClock.elapsedRealtime] when a frame was last passed on, in microseconds. */
    private var lastPassedRealtimeUs = C.TIME_UNSET

    override fun processOutputBuffer(
        positionUs: Long,
        elapsedRealtimeUs: Long,
        codec: MediaCodecAdapter?,
        buffer: ByteBuffer?,
        bufferIndex: Int,
        bufferFlags: Int,
        sampleCount: Int,
        bufferPresentationTimeUs: Long,
        isDecodeOnlyBuffer: Boolean,
        isLastBuffer: Boolean,
        format: Format
    ): Boolean {
        updateEstimatedFrameDuration(bufferPresentationTimeUs)
        val frameDurationUs = frameDurationUs(bufferPresentationTimeUs, isLastBuffer)
        if (codec != null && shouldDropLateFrame(positionUs, elapsedRealtimeUs, bufferPresentationTimeUs,
                frameDurationUs, isDecodeOnlyBuffer, isLastBuffer)) {
            dropOutputBuffer(codec, bufferIndex, bufferPresentationTimeUs - outputStreamOffsetUs)
            return true
        }
        val processed = super.processOutputBuffer(positionUs, elapsedRealtimeUs, codec, buffer, bufferIndex,
            bufferFlags, sampleCount, bufferPresentationTimeUs, isDecodeOnlyBuffer, isLastBuffer, format)
        if (processed && !isDecodeOnlyBuffer) {
            lastPassedRealtimeUs = clock.elapsedRealtime() * 1000
        }
        return processed
    }

    override fun onQueueInputBuffer(buffer: DecoderInputBuffer) {
        super.onQueueInputBuffer(buffer)
        queuedPresentationTimesUs.add(buffer.timeUs)
    }

    override fun resetCodecStateForFlush() {
        super.resetCodecStateForFlush()
        queuedPresentationTimesUs.clear()
    }

    /**
     * The duration of the frame output at [bufferPresentationTimeUs]: the gap to the next queued
     * frame, or the estimate if it isn't queued yet. [C.TIME_UNSET] if unknown.
     */
    private fun frameDurationUs(bufferPresentationTimeUs: Long, isLastBuffer: Boolean): Long {
        // Output is in presentation order: the buffers up to this one are output, or were dropped
        // by the decoder.
        queuedPresentationTimesUs.headSet(bufferPresentationTimeUs, true).clear()
        val nextPresentationTimeUs = queuedPresentationTimesUs.firstOrNull()
        if (nextPresentationTimeUs == null) {
            // The last frame has no next one, so we must estimate its duration.
            if (!isLastBuffer && bufferPresentationTimeUs != lastWarnedPresentationTimeUs) {
                lastWarnedPresentationTimeUs = bufferPresentationTimeUs
                Log.w(TAG, "Next frame not queued for the frame at ${bufferPresentationTimeUs}us, " +
                    "using the estimated duration ${estimatedFrameDurationUs}us")
            }
            return estimatedFrameDurationUs
        }
        return nextPresentationTimeUs - bufferPresentationTimeUs
    }

    /**
     * The same buffer comes back until it's processed, and seeks go back in time: only an increase is
     * a frame duration.
     */
    private fun updateEstimatedFrameDuration(bufferPresentationTimeUs: Long) {
        val last = lastBufferPresentationTimeUs
        if (last != C.TIME_UNSET && bufferPresentationTimeUs > last
            && bufferPresentationTimeUs - last <= MAX_FRAME_DURATION_US) {
            // A gap over MAX_FRAME_DURATION_US is a discontinuity (next playlist item, timestamp jump,
            // frames lost by the decoder), not a frame duration. If we would not protect ourselves from that,
            // it would stop the next frame from being dropped.
            // A real frame that long (5 fps) is skipped too, but it only affects this estimate,
            // not the exact duration from the queued frames.
            estimatedFrameDurationUs = bufferPresentationTimeUs - last
        }
        if (last == C.TIME_UNSET || bufferPresentationTimeUs != last) {
            lastBufferPresentationTimeUs = bufferPresentationTimeUs
        }
    }

    private fun shouldDropLateFrame(
        positionUs: Long,
        elapsedRealtimeUs: Long,
        bufferPresentationTimeUs: Long,
        durationUs: Long,
        isDecodeOnlyBuffer: Boolean,
        isLastBuffer: Boolean
    ): Boolean {
        val speed = playbackSpeed
        if (state != Renderer.STATE_STARTED || isDecodeOnlyBuffer || isLastBuffer
            || durationUs == C.TIME_UNSET || speed <= 0f) {
            return false
        }
        val nowUs = clock.elapsedRealtime() * 1000
        if (lastPassedRealtimeUs == C.TIME_UNSET || nowUs - lastPassedRealtimeUs > MAX_TIME_WITHOUT_FRAME_US) {
            // Degrade to ~10fps rather than freeze.
            return false
        }
        val earlyUs = ((bufferPresentationTimeUs - positionUs) / speed.toDouble()).toLong() -
            (nowUs - elapsedRealtimeUs)
        return earlyUs < -(durationUs / speed.toDouble()).toLong()
    }

    override fun onPositionReset(positionUs: Long, joining: Boolean, sampleStreamIsResetToKeyFrame: Boolean) {
        super.onPositionReset(positionUs, joining, sampleStreamIsResetToKeyFrame)
        lastBufferPresentationTimeUs = C.TIME_UNSET
        lastPassedRealtimeUs = C.TIME_UNSET
    }

    override fun shouldDropOutputBuffer(earlyUs: Long, elapsedRealtimeUs: Long, isLastBuffer: Boolean): Boolean {
        return false
    }

    companion object {
        private const val TAG = "AssMediaCodecVideoRenderer"

        /** Don't drop a frame if none was shown for 100ms. */
        private const val MAX_TIME_WITHOUT_FRAME_US = 100_000L

        /** A large gap between two frames is considered as a discontinuity, not a frame duration. */
        private const val MAX_FRAME_DURATION_US = 200_000L

        /**
         * Disables media3's drops of frames predicted to be late (before decoding and before the
         * effects): processOutputBuffer drops late frames instead.
         */
        @OptIn(ExperimentalApi::class)
        private fun disablePredictedLateDrop(builder: MediaCodecVideoRenderer.Builder): MediaCodecVideoRenderer.Builder =
            builder.experimentalSetLateThresholdToDropDecoderInputUs(C.TIME_UNSET)
    }
}
