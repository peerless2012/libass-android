package io.github.peerless2012.ass.render

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.BaseRenderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.text.TextOutput

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/05 22:37
 * @Version V1.0
 * @Description
 */
@UnstableApi
class AssTextRender(private val output: TextOutput) : BaseRenderer(C.TRACK_TYPE_TEXT) {

    override fun getName(): String {
        return "ASS"
    }

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
    }

    override fun isReady(): Boolean {
        return true
    }

    override fun isEnded(): Boolean {
        return false
    }

    override fun supportsFormat(format: Format): Int {
        return if (MimeTypes.TEXT_SSA == format.sampleMimeType) {
            RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE)
        } else {
            RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        }
    }
}