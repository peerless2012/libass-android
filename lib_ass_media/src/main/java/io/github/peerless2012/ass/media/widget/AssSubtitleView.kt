package io.github.peerless2012.ass.media.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.View
import io.github.peerless2012.ass.AssFrame
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.executor.AssExecutor
import io.github.peerless2012.ass.media.type.AssRenderType

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 5/26/25 8:58 PM
 * @Version V1.0
 * @Description
 */
class AssSubtitleView: View {

    private val paint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
    }

    private val assHandler: AssHandler

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
            it.bitmap.prepareToDraw()
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (assHandler.renderType != AssRenderType.OVERLAY) {
            return
        }
        assHandler.render?.setFrameSize(w, h)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (assHandler.renderType != AssRenderType.OVERLAY) {
            return
        }
        assHandler.render?.let {
            assExecutor = AssExecutor(it)
        }
        assHandler.renderCallback = {
            if (it == null) {
                assExecutor?.shutdown()
                assExecutor = null
            } else {
                assExecutor = AssExecutor(it)
            }
        }
        assHandler.videoTimeCallback = { presentationTimeUs ->
            assExecutor?.asyncRenderFrame(presentationTimeUs, assRenderCallback)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        assFrame?.images?.let { frames ->
            frames.forEach { frame ->
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

    override fun onDetachedFromWindow() {
        assHandler.renderCallback = null
        assHandler.videoTimeCallback = null
        assExecutor?.shutdown()
        assExecutor = null
        super.onDetachedFromWindow()
    }
}