package io.github.peerless2012.ass.media.executor

import io.github.peerless2012.ass.AssFrame
import io.github.peerless2012.ass.AssRender
import io.github.peerless2012.ass.AssTexType
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 6/17/25 10:24 PM
 * @Version V1.0
 * @Description
 */
class AssTask(private val render: AssRender) : Runnable {

    private val stateLock = ReentrantLock()

    private var isCancelled = false

    @Volatile
    var executorBusy = false

    var presentationTimeUs: Long = 0

    var type: AssTexType = AssTexType.BITMAP_ALPHA

    var callback: ((AssFrame?) -> Unit)? = null

    internal fun prepare(
        presentationTimeUs: Long,
        type: AssTexType,
        callback: (AssFrame?) -> Unit
    ): Boolean = stateLock.withLock {
        if (isCancelled || executorBusy) return@withLock false

        this.presentationTimeUs = presentationTimeUs
        this.type = type
        this.callback = callback
        executorBusy = true
        true
    }

    internal fun cancel() {
        stateLock.withLock {
            isCancelled = true
            callback = null
        }
    }

    override fun run() {
        val renderArguments = stateLock.withLock {
            if (isCancelled) {
                executorBusy = false
                return
            }
            presentationTimeUs to type
        }
        var result: AssFrame? = null
        try {
            result = render.renderFrame(renderArguments.first / 1000, renderArguments.second)
        } catch (e: Exception) {
            result = null
        } finally {
            stateLock.withLock {
                val renderCallback = callback
                callback = null
                executorBusy = false
                // Keep callback ordering atomic with cancel().
                if (!isCancelled) renderCallback?.invoke(result)
            }
        }
    }
}
