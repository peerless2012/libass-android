package io.github.peerless2012.ass.media.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.View
import io.github.peerless2012.ass.AssFrame
import io.github.peerless2012.ass.AssTexType
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.executor.AssExecutor

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 12/02/25 9:58 PM
 * @Version V1.0
 * @Description
 */
class AssSubtitleCanvasView : View, AssSubtitleRender {

    private val assHandler: AssHandler

    private val paint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
    }

    private var assExecutor: AssExecutor? = null

    private var assFrame: AssFrame? = null

    // Use a local param, avoid create each time.
    private val invalidateCallback = Runnable { invalidate() }

    // Use a local param, avoid create each time.
    private val assRenderCallback: (AssFrame?) -> Unit = assRenderCallback@{ assFrame ->
        // Not change
        if (assFrame != null && assFrame.changed == 0) {
            return@assRenderCallback
        }
        // prepare to draw
        assFrame?.images?.forEach {
            it.bitmap?.prepareToDraw()
        }
        this.assFrame = assFrame
        handler.post(invalidateCallback)
    }

    constructor(context: Context, assHandler: AssHandler) : this(context, null, assHandler)

    constructor(context: Context, attrs: AttributeSet?, assHandler: AssHandler) : this(
        context,
        attrs,
        0,
        assHandler
    )

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, assHandler: AssHandler) :
            super(context, attrs, defStyleAttr) {
        setWillNotDraw(false)
        this.assHandler = assHandler
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        assHandler.render?.let {
            assExecutor = AssExecutor(it)
        }
        assHandler.renderCallback = { assRender ->
            assExecutor?.shutdown()
            assExecutor = null
            if (assRender != null) {
                assExecutor = AssExecutor(assRender)
            }
        }

    }

    override fun requestRender(timestampNanos: Long) {
        assExecutor?.asyncRenderFrame(timestampNanos, AssTexType.BITMAP_ALPHA, assRenderCallback)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        assFrame?.images?.let { frames ->
            frames.forEach { frame ->
                frame.bitmap?.let { bitmap ->
                    val r = frame.color shr 24 and 0xFF
                    val g = frame.color shr 16 and 0xFF
                    val b = frame.color shr 8 and 0xFF
                    val a = 0xFF - frame.color and 0xFF
                    val color = (a shl 24) or (r shl 16) or (g shl 8) or b
                    paint.color = color
                    canvas.drawBitmap(bitmap, frame.x.toFloat(), frame.y.toFloat(), paint)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        assHandler.renderCallback = null
        assExecutor?.shutdown()
        assExecutor = null
        super.onDetachedFromWindow()
    }
}