package io.github.peerless2012.ass.media.executor

import io.github.peerless2012.ass.AssFrame
import io.github.peerless2012.ass.AssRender
import io.github.peerless2012.ass.AssTexType
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Executor to render.
 */
class AssExecutor internal constructor(
    private val renderFrame: (Long, AssTexType) -> AssFrame?,
    private val executor: ExecutorService
) {

    constructor(render: AssRender) : this(render::renderFrame, Executors.newSingleThreadExecutor())

    private val assFrameNotChange = AssFrame(null, 0)

    private val executorService = ExecutorCompletionService<AssFrame?>(executor)

    private val lifecycleLock = ReentrantLock()

    private var isShutdown = false

    @Volatile
    private var lastFrame: AssFrame? = null

    private var executorBusy = false

    private val task = AssTask(renderFrame)

    public fun renderFrame(presentationTimeUs: Long, type: AssTexType): AssFrame? {
        var assFrame: AssFrame? = null
        val future = lifecycleLock.withLock {
            if (isShutdown || executorBusy) return@withLock null

            executorBusy = true
            // submit render task
            executorService.submit {
                try {
                    lastFrame = renderFrame(presentationTimeUs / 1000, type)
                    lastFrame
                } finally {
                    lifecycleLock.withLock {
                        executorBusy = false
                    }
                }
            }
        }
        if (future == null) {
            lastFrame = null
            return assFrameNotChange
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
        lastFrame = null
        return assFrame
    }

    public fun asyncRenderFrame(presentationTimeUs: Long, type: AssTexType, callback: (AssFrame?) -> Unit) {
        lifecycleLock.withLock {
            if (isShutdown) return
            if (!task.prepare(presentationTimeUs, type, callback)) {
                // render thread is busy, keep last content
                callback.invoke(assFrameNotChange)
                return
            }
            executor.execute(task)
        }
    }

    public fun shutdown() {
        val shouldShutdown = lifecycleLock.withLock {
            if (isShutdown) return@withLock false
            isShutdown = true
            true
        }
        if (!shouldShutdown) return

        task.cancel()
        executor.shutdownNow()
    }

}
