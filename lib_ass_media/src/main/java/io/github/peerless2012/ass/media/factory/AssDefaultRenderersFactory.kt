package io.github.peerless2012.ass.media.factory

import android.content.Context
import android.os.Handler
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import io.github.peerless2012.ass.media.render.AssMediaCodecVideoRenderer

/**
 * The [DefaultRenderersFactory] `buildWithAssSupport` uses for
 * [io.github.peerless2012.ass.media.type.AssRenderType.EFFECTS_ATLAS] when the app gives none:
 * it builds an [AssMediaCodecVideoRenderer] instead of a `MediaCodecVideoRenderer`.
 *
 * It's internal because it's never configured: the settings `DefaultRenderersFactory` doesn't pass
 * to [buildVideoRenderers] are private, so the renderer is built with their defaults, which are
 * the Builder's defaults too. For the same reason, extension renderers (off by default) are not built.
 */
@UnstableApi
internal class AssDefaultRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        out.add(
            AssMediaCodecVideoRenderer(
                MediaCodecVideoRenderer.Builder(context)
                    .setCodecAdapterFactory(getCodecAdapterFactory())
                    .setMediaCodecSelector(mediaCodecSelector)
                    .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
                    .setEnableDecoderFallback(enableDecoderFallback)
                    .setEventHandler(eventHandler)
                    .setEventListener(eventListener)
                    .setMaxDroppedFramesToNotify(MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY)
            )
        )
    }
}
