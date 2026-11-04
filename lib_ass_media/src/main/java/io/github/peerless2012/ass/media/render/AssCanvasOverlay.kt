package io.github.peerless2012.ass.media.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.annotation.OptIn
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import io.github.peerless2012.ass.AssFrame
import io.github.peerless2012.ass.AssRender
import io.github.peerless2012.ass.AssTexType
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.executor.AssExecutor

@OptIn(UnstableApi::class)
class AssCanvasOverlay(private val handler: AssHandler, private val render: AssRender) : CanvasOverlay(true) {

    private val paint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
    }

    private lateinit var executor: AssExecutor

    private var texDirty = true

    private var renderSize = Size.ZERO

    private var videoSize = Size.ZERO

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        this.videoSize = videoSize
        renderSize = handler.computeRenderSize(videoSize.width, videoSize.height)
        executor = AssExecutor(render)
        render.setFrameSize(renderSize.width, renderSize.height)
    }

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        val timeUs = if (handler.videoTime >= 0) {
            handler.videoTime
        } else {
            presentationTimeUs
        }
        val assFrame: AssFrame? = executor.renderFrame(timeUs, AssTexType.BITMAP_ALPHA, handler.videoColorSpace)

        if (assFrame != null && assFrame.changed == 0) {
            return
        }

        if (assFrame == null && !texDirty) {
            return
        }

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        texDirty = false

        assFrame?.images?.let { frames ->
            texDirty = true
            val scaleX = videoSize.width.toFloat() / renderSize.width
            val scaleY = videoSize.height.toFloat() / renderSize.height
            frames.forEach { frame ->
                frame.bitmap?.let { bitmap ->
                    val r = frame.color shr 24 and 0xFF
                    val g = frame.color shr 16 and 0xFF
                    val b = frame.color shr 8 and 0xFF
                    val a = 0xFF - frame.color and 0xFF
                    val color = (a shl 24) or (r shl 16) or (g shl 8) or b

                    paint.color = color
                    if (scaleX == 1f && scaleY == 1f) {
                        canvas.drawBitmap(bitmap, frame.x.toFloat(), frame.y.toFloat(), paint)
                    } else {
                        val dst = android.graphics.RectF(
                            frame.x * scaleX,
                            frame.y * scaleY,
                            (frame.x + bitmap.width) * scaleX,
                            (frame.y + bitmap.height) * scaleY
                        )
                        canvas.drawBitmap(bitmap, null, dst, paint)
                    }
                }
            }
        }
    }

    override fun release() {
        executor.shutdown()
        super.release()
    }

}