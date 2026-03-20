package io.github.peerless2012.ass

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/05 14:18
 * @Version V1.0
 * @Description
 */
class AssRender(nativeAss: Long) {

    companion object {

        @JvmStatic
        external fun nativeAssRenderInit(ass: Long): Long

        @JvmStatic
        external fun nativeAssRenderSetFontScale(render: Long, scale: Float)

        @JvmStatic
        external fun nativeAssRenderSetCacheLimit(render: Long, glyphMax: Int, bitmapMaxSize: Int)

        @JvmStatic
        external fun nativeAssRenderSetStorageSize(render: Long, width: Int, height: Int)

        @JvmStatic
        external fun nativeAssRenderSetFrameSize(render: Long, width: Int, height: Int)

        @JvmStatic
        external fun nativeAssRenderFrame(render: Long, track: Long, time: Long, type: Int): AssFrame?

        @JvmStatic
        external fun nativeAssRenderDeinit(render: Long)
    }

    val lock = ReentrantLock()

    private var nativeRender: Long = nativeAssRenderInit(nativeAss)

    @Volatile
    var released = false
        private set

    private var track: AssTrack? = null

    public fun setTrack(track: AssTrack?) {
        lock.withLock {
            this.track = track
        }
    }

    public fun setFontScale(scale: Float) {
        if (released || nativeRender == 0L) return
        nativeAssRenderSetFontScale(nativeRender, scale)
    }

    public fun setCacheLimit(glyphMax: Int, bitmapMaxSize: Int) {
        if (released || nativeRender == 0L) return
        nativeAssRenderSetCacheLimit(nativeRender, glyphMax, bitmapMaxSize)
    }

    public fun setStorageSize(width: Int, height: Int) {
        if (released || nativeRender == 0L) return
        nativeAssRenderSetStorageSize(nativeRender, width, height)
    }

    public fun setFrameSize(width: Int, height: Int) {
        if (released || nativeRender == 0L) return
        nativeAssRenderSetFrameSize(nativeRender, width, height)
    }

    public fun renderFrame(time: Long, type: AssTexType): AssFrame? {
        lock.withLock {
            if (released || nativeRender == 0L) return null
            val t = track ?: return null
            if (t.released || t.nativeAssTrack == 0L) return null
            return nativeAssRenderFrame(nativeRender, t.nativeAssTrack, time, type.ordinal)
        }
    }

    fun release() {
        lock.withLock {
            if (released) return
            released = true
            track = null
            if (nativeRender != 0L) {
                nativeAssRenderDeinit(nativeRender)
                nativeRender = 0
            }
        }
    }

    protected fun finalize() {
        release()
    }

}
