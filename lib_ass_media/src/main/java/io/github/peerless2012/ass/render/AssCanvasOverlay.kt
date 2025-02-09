package io.github.peerless2012.ass.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.annotation.OptIn
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import io.github.peerless2012.ass.kt.ASSRender


@OptIn(UnstableApi::class)
class AssCanvasOverlay(private val renderer: ASSRender) : CanvasOverlay(true) {

    private val paint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
    }

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        renderer.setFrameSize(videoSize.width, videoSize.height)
    }

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        val result = renderer.renderFrame(presentationTimeUs / 1000, true)
        if (result == null || result.changed != 0) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }
        result?.images?.forEach { frame ->
            val r = frame.color shr 24 and 0xFF
            val g = frame.color shr 16 and 0xFF
            val b = frame.color shr 8 and 0xFF
            val a = 0xFF - frame.color and 0xFF
            val color = (a shl 24) or (r shl 16) or (g shl 8) or b

            paint.color = color
            canvas.drawBitmap(frame.bitmap, frame.x.toFloat(), frame.y.toFloat(), paint)
        }
    }
}