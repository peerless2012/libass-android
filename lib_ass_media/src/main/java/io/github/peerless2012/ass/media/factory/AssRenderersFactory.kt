package io.github.peerless2012.ass.media.factory

import android.os.Handler
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.render.AssRenderer
import io.github.peerless2012.ass.media.render.AssVideoRendererWrapper
import io.github.peerless2012.ass.media.type.AssRenderType

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 5/25/25 10:47 PM
 * @Version V1.0
 * @Description
 */
@UnstableApi
class AssRenderersFactory(private val assHandler: AssHandler, private val renderersFactory: RenderersFactory): RenderersFactory {
    override fun createRenderers(
        eventHandler: Handler,
        videoRendererEventListener: VideoRendererEventListener,
        audioRendererEventListener: AudioRendererEventListener,
        textRendererOutput: TextOutput,
        metadataRendererOutput: MetadataOutput
    ): Array<Renderer> {
        val renderers = renderersFactory.createRenderers(eventHandler, videoRendererEventListener, audioRendererEventListener, textRendererOutput, metadataRendererOutput).toMutableList()
        if (assHandler.renderType == AssRenderType.EFFECTS_ATLAS) {
            // Only MediaCodecVideoRenderer supports video effects.
            val index = renderers.indexOfFirst { it is MediaCodecVideoRenderer }
            if (index >= 0) {
                renderers[index] = AssVideoRendererWrapper(renderers[index], assHandler.frameTimeMapper)
            } else {
                Log.e("AssRenderersFactory", "No MediaCodecVideoRenderer found, EFFECTS_ATLAS subtitles will be drawn at the wrong time or not at all")
            }
        }
        return (renderers + AssRenderer(assHandler)).toTypedArray()
    }

    override fun createSecondaryRenderer(
        renderer: Renderer,
        eventHandler: Handler,
        videoRendererEventListener: VideoRendererEventListener,
        audioRendererEventListener: AudioRendererEventListener,
        textRendererOutput: TextOutput,
        metadataRendererOutput: MetadataOutput
    ): Renderer? {
        // A pre-warming video renderer would send frames whose timestamps the frame time mapper doesn't track.
        if (renderer is AssVideoRendererWrapper) return null
        return renderersFactory.createSecondaryRenderer(
            renderer,
            eventHandler,
            videoRendererEventListener,
            audioRendererEventListener,
            textRendererOutput,
            metadataRendererOutput
        )
    }
}