package io.github.peerless2012.ass.media.executor

import io.github.peerless2012.ass.ASSFrame
import io.github.peerless2012.ass.ASSRender
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Executor to render.
 */
class AssExecutor(private val render: ASSRender) {

    private val assFrameNotChange = ASSFrame(null, 0)

    private val executor = Executors.newSingleThreadExecutor()

    private val executorService = ExecutorCompletionService<ASSFrame?>(executor)

    private var lastFrame: ASSFrame? = null

    private var executorBusy = false

    public fun renderFrame(presentationTimeUs: Long): ASSFrame? {
        var assFrame: ASSFrame? = null
        if (executorBusy) {
            // render thread is busy
            assFrame = lastFrame
            if (assFrame == null) {
                // no new frame, keep last content
                assFrame = assFrameNotChange
            }
        } else {
            // submit render task
            val future = executorService.submit{
                executorBusy = true
                lastFrame = render.renderFrame(presentationTimeUs / 1000, true)
                executorBusy = false
                lastFrame
            }
            try {
                assFrame = future.get(8, TimeUnit.MILLISECONDS)
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

    public fun shutdown() {
        executor.shutdown()
    }

}