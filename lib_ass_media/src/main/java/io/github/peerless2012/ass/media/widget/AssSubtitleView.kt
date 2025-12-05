package io.github.peerless2012.ass.media.widget
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.type.AssRenderType

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 5/26/25 8:58 PM
 * @Version V1.0
 * @Description
 */
class AssSubtitleView: FrameLayout {

    private val assHandler: AssHandler

    private var assSubtitleRender: AssSubtitleRender? = null

    constructor(context: Context, assHandler: AssHandler) : this(context, null, assHandler)

    constructor(context: Context, attrs: AttributeSet?, assHandler: AssHandler) : this(
        context,
        attrs,
        0,
        assHandler
    )

    @OptIn(UnstableApi::class)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, assHandler: AssHandler) :
            super(context, attrs, defStyleAttr) {
        this.assHandler = assHandler
        val view = when (assHandler.renderType) {
            AssRenderType.OVERLAY_CANVAS -> {
                AssSubtitleCanvasView(context, attrs, defStyleAttr, assHandler)
            }
            AssRenderType.OVERLAY_OPEN_GL -> {
                AssSubtitleTextureView(context, attrs, defStyleAttr, assHandler)
            }
            else -> {
                null
            }
        }
        view?.let {
            assSubtitleRender = it
            val params = LayoutParams(MarginLayoutParams.MATCH_PARENT,
                MarginLayoutParams.MATCH_PARENT)
            addView(it, params)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        assHandler.videoTimeCallback = { presentationTimeUs ->
            assSubtitleRender?.requestRender(presentationTimeUs)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        assHandler.videoTimeCallback = null
    }

}