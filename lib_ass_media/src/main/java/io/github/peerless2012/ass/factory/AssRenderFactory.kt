package io.github.peerless2012.ass.factory

import android.content.Context
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import io.github.peerless2012.ass.AssKeeper
import java.util.ArrayList

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/05 23:01
 * @Version V1.0
 * @Description
 */
@UnstableApi
class AssRenderFactory(context: Context, private val assKeeper: AssKeeper) : DefaultRenderersFactory(context) {

    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>
    ) {
        super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, out)
        out.add(0, TextRenderer(output, outputLooper, AssDecoderFactory(assKeeper)))
    }

}