package io.github.peerless2012.ass.media.executor

import io.github.peerless2012.ass.AssFrame
import io.github.peerless2012.ass.AssTexType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssExecutorTest {

    @Test
    fun shutdownPreventsInFlightRenderCallback() {
        val renderStarted = CountDownLatch(1)
        val continueRender = CountDownLatch(1)
        val callbackCalled = AtomicBoolean(false)
        val workerExecutor = Executors.newSingleThreadExecutor()
        val assExecutor = AssExecutor(
            renderFrame = { _, _ ->
                renderStarted.countDown()
                var canContinue = false
                while (!canContinue) {
                    try {
                        canContinue = continueRender.await(1, TimeUnit.SECONDS)
                    } catch (ignored: InterruptedException) {
                        // Simulate native rendering that cannot be interrupted.
                    }
                }
                AssFrame(null, 1)
            },
            executor = workerExecutor
        )

        assExecutor.asyncRenderFrame(0, AssTexType.BITMAP_ALPHA) {
            callbackCalled.set(true)
        }
        try {
            assertTrue(renderStarted.await(1, TimeUnit.SECONDS))
        } finally {
            assExecutor.shutdown()
            continueRender.countDown()
        }

        assertTrue(workerExecutor.awaitTermination(1, TimeUnit.SECONDS))
        assertFalse(callbackCalled.get())
    }
}
