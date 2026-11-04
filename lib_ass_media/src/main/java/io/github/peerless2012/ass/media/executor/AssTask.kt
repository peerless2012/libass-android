package io.github.peerless2012.ass.media.executor

import io.github.peerless2012.ass.AssFrame
import io.github.peerless2012.ass.AssRender
import io.github.peerless2012.ass.AssTexType
import io.github.peerless2012.ass.AssVideoColorSpace

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 6/17/25 10:24 PM
 * @Version V1.0
 * @Description
 */
class AssTask(private val render: AssRender) : Runnable {

    var executorBusy = false

    var presentationTimeUs: Long = 0

    var type: AssTexType = AssTexType.BITMAP_ALPHA

    var videoColorSpace: AssVideoColorSpace = AssVideoColorSpace.BT709_TV

    var callback: ((AssFrame?) -> Unit)? = null

    private var lastFrame: AssFrame? = null

    override fun run() {
        executorBusy = true
        var result: AssFrame? = null
        try {
            result = render.renderFrame(presentationTimeUs / 1000, type, videoColorSpace)
            lastFrame = result
        } catch (e: Exception) {
            result = null
        } finally {
            callback?.invoke(result)
            executorBusy = false
            callback = null
        }
    }
}