package io.github.peerless2012.ass.media.render

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.exoplayer.ExoPlayer
import io.github.peerless2012.ass.AssRender
import io.github.peerless2012.ass.media.AssHandler

@OptIn(UnstableApi::class)
class AssOverlayManager(
    private val handler: AssHandler,
    private val player: ExoPlayer,
    private val tex: Boolean
) {
    private var currentRenderer : AssRender? = null

    init {
        // ExoPlayer documentation states that this needs to be called before .prepare()
        player.setVideoEffects(listOf())
    }

    fun enable(renderer: AssRender) {
        if (renderer == currentRenderer) return
        this.currentRenderer = renderer
        val overlay = if (tex) {
            AssTexOverlay(handler, renderer)
        } else {
            AssCanvasOverlay(handler, renderer)
        }
        val effect = OverlayEffect(listOf(overlay))
        player.setVideoEffects(listOf(effect))
    }

    fun disable() {
        if (currentRenderer == null) return

        currentRenderer = null
        player.setVideoEffects(listOf())
    }

}
