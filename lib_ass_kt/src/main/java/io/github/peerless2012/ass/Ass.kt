package io.github.peerless2012.ass

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/05 14:15
 * @Version V1.0
 * @Description
 */
class Ass {

    companion object {

        init {
            System.loadLibrary("asskt")
        }

        @JvmStatic
        external fun nativeAssInit(): Long

        @JvmStatic
        external fun nativeAssAddFont(ptr: Long, name: String, buffer: ByteArray)

        @JvmStatic
        external fun nativeAssClearFont(ptr: Long)

        @JvmStatic
        external fun nativeAssDeinit(ptr: Long)

    }

    /** Single lock for all libass calls on this library instance. */
    val lock = ReentrantLock()

    private var nativeAss: Long = nativeAssInit()

    @Volatile
    var released = false
        private set

    public fun createTrack(): AssTrack {
        return lock.withLock {
            if (released || nativeAss == 0L) throw IllegalStateException("Ass already released")
            AssTrack(nativeAss, lock)
        }
    }

    public fun createRender(): AssRender {
        return lock.withLock {
            if (released || nativeAss == 0L) throw IllegalStateException("Ass already released")
            AssRender(nativeAss, lock)
        }
    }

    public fun addFont(name: String, buffer: ByteArray) {
        lock.withLock {
            if (released || nativeAss == 0L) return
            nativeAssAddFont(nativeAss, name, buffer)
        }
    }

    public fun clearFont() {
        lock.withLock {
            if (released || nativeAss == 0L) return
            nativeAssClearFont(nativeAss)
        }
    }

    fun release() {
        lock.withLock {
            if (released) return
            released = true
            if (nativeAss != 0L) {
                nativeAssDeinit(nativeAss)
                nativeAss = 0
            }
        }
    }

    protected fun finalize() {
        release()
    }

}
