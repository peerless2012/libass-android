package io.github.peerless2012.ass.media.executor

import io.github.peerless2012.ass.AssFrame
import io.github.peerless2012.ass.AssRender
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Executor to render.
 */
class AssExecutor(private val render: AssRender) {

    private val assFrameNotChange = AssFrame(null, 0)

    private val executor = Executors.newSingleThreadExecutor()

    private val executorService = ExecutorCompletionService<AssFrame?>(executor)

    private var lastFrame: AssFrame? = null

    private var executorBusy = false

    public fun renderFrame(presentationTimeUs: Long): AssFrame? {
        var assFrame: AssFrame? = null
        if (executorBusy) {
            // render thread is busy, keep last content
            assFrame = assFrameNotChange
        } else {
            // submit render task
            val future = executorService.submit{
                executorBusy = true
                lastFrame = render.renderFrame(presentationTimeUs / 1000, true)
                executorBusy = false
                lastFrame
            }
            try {
                assFrame = if (lastFrame != null) {
                    lastFrame
                } else {
                    future.get(8, TimeUnit.MILLISECONDS)
                }
            } catch (exception: Exception) {
                // task timeout
                assFrame = lastFrame
                if (assFrame == null) {
                    // keep last content
                    assFrame = assFrameNotChange
                }
            }
        }
        lastFrame = null
        return assFrame
    }

    public fun asyncRenderFrame(presentationTimeUs: Long, callback: (AssFrame?) -> Unit) {
        if (executorBusy) {
            // render thread is busy, keep last content
            callback.invoke(assFrameNotChange)
        } else {
            // submit render task
            executorService.submit{
                executorBusy = true
                lastFrame = render.renderFrame(presentationTimeUs / 1000, true)
                callback.invoke(lastFrame)
                executorBusy = false
                lastFrame
            }
        }
    }

    public fun shutdown() {
        executor.shutdown()
    }

}