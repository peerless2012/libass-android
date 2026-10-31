package io.github.peerless2012.ass.media.render

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlShaderProgram
import io.github.peerless2012.ass.AssRender
import io.github.peerless2012.ass.media.AssHandler
import java.util.concurrent.Executor

/**
 * Draws ASS subtitles in place onto each video frame's own texture and forwards that same texture
 * downstream.
 *
 * Implements [GlShaderProgram] directly instead of extending `BaseGlShaderProgram`, which always
 * renders into a new, cleared output texture: every frame would first need a full-frame copy of the
 * video before the subtitles could be drawn on top. Drawing in place avoids that copy, so a frame
 * without visible subtitles costs no GPU work at all, and it saves a full-size output texture.
 *
 * Subtitles are rendered synchronously on the GL thread by [AssRender.drawOverlayFrame], so every
 * frame is accurate, but a pathologically slow subtitle frame stalls video delivery.
 */
@OptIn(UnstableApi::class)
class AssGlShaderProgram(
    private val handler: AssHandler,
    private val render: AssRender
) : GlShaderProgram {

    private var inputListener: GlShaderProgram.InputListener = object : GlShaderProgram.InputListener {}
    private var outputListener: GlShaderProgram.OutputListener = object : GlShaderProgram.OutputListener {}
    private var errorListener = GlShaderProgram.ErrorListener {}
    private var errorExecutor = Executor { it.run() }

    /** True while downstream holds the frame we forwarded. We accept one frame at a time, so this
     * mirrors `BaseGlShaderProgram.setInputListener`, which only signals readiness for free output
     * textures: a new input listener mustn't be told we're ready while a frame is still held. */
    private var frameInFlight = false

    /** Only populated when upstream doesn't already hand us an FBO for a given texture (fboId
     * unset) — keyed by texId so a texture media3's pool reuses doesn't leak a new FBO every time. */
    private val fallbackFbos = HashMap<Int, Int>()

    /** The native overlay, created by the first draw. Its GL objects belong to this program's GL
     * context, so it's this program's alone, never shared through [render]. */
    private var overlay = 0L

    override fun setInputListener(listener: GlShaderProgram.InputListener) {
        inputListener = listener
        if (!frameInFlight) {
            listener.onReadyToAcceptInputFrame()
        }
    }

    override fun setOutputListener(listener: GlShaderProgram.OutputListener) {
        outputListener = listener
    }

    override fun setErrorListener(executor: Executor, listener: GlShaderProgram.ErrorListener) {
        errorExecutor = executor
        errorListener = listener
    }

    override fun queueInputFrame(
        glObjectsProvider: GlObjectsProvider,
        inputTexture: GlTextureInfo,
        presentationTimeUs: Long
    ) {
        try {
            val fbo = if (inputTexture.fboId != C.INDEX_UNSET) {
                inputTexture.fboId
            } else {
                fallbackFbos.getOrPut(inputTexture.texId) {
                    GlUtil.createFboForTexture(inputTexture.texId)
                }
            }
            // presentationTimeUs isn't media time, see AssFrameTimeMapper. Without a mapping (the player
            // wasn't built with AssRenderersFactory), fall back to it like the other effect overlays do.
            val mediaTimeUs = handler.frameTimeMapper.toMediaTimeUs(presentationTimeUs)
                .takeIf { it != C.TIME_UNSET } ?: presentationTimeUs
            val startNs = System.nanoTime()
            overlay = render.drawOverlayFrame(
                overlay,
                fbo,
                inputTexture.width,
                inputTexture.height,
                mediaTimeUs / 1000
            )
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
            if (elapsedMs >= SLOW_FRAME_LOG_THRESHOLD_MS) {
                Log.w(TAG, "drawOverlayFrame took ${elapsedMs}ms for presentationTimeUs=$presentationTimeUs")
            }
        } catch (e: Exception) {
            errorExecutor.execute {
                errorListener.onError(VideoFrameProcessingException.from(e, presentationTimeUs))
            }
        }
        frameInFlight = true
        outputListener.onOutputFrameAvailable(inputTexture, presentationTimeUs)
    }

    override fun releaseOutputFrame(outputTexture: GlTextureInfo) {
        frameInFlight = false
        inputListener.onInputFrameProcessed(outputTexture)
        inputListener.onReadyToAcceptInputFrame()
    }

    override fun signalEndOfCurrentInputStream() {
        outputListener.onCurrentOutputStreamEnded()
    }

    override fun flush() {
        frameInFlight = false 
        inputListener.onFlush()
        inputListener.onReadyToAcceptInputFrame()
    }

    override fun release() {
        fallbackFbos.values.forEach { GlUtil.deleteFbo(it) }
        fallbackFbos.clear()
        render.releaseOverlay(overlay)
        overlay = 0L
    }

    private companion object {
        private const val TAG = "AssGlShaderProgram"
        private const val SLOW_FRAME_LOG_THRESHOLD_MS = 8
    }
}
