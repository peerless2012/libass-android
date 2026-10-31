package io.github.peerless2012.ass.media.render

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import io.github.peerless2012.ass.AssRender
import io.github.peerless2012.ass.media.AssHandler

@OptIn(UnstableApi::class)
class AssGlEffect(
    private val handler: AssHandler,
    private val render: AssRender
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        if (useHdr) {
            Log.w(TAG, "HDR is not supported: subtitles are blended as 8-bit sRGB and colors will be wrong")
        }
        return AssGlShaderProgram(handler, render)
    }

    companion object {
        private const val TAG = "AssGlEffect"
    }
}
