package io.github.peerless2012.ass

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

    private var nativeAss: Long = nativeAssInit()

    @Volatile
    var released = false
        private set

    public fun createTrack(): AssTrack {
        if (released || nativeAss == 0L) throw IllegalStateException("Ass already released")
        return AssTrack(nativeAss)
    }

    public fun createRender(): AssRender {
        if (released || nativeAss == 0L) throw IllegalStateException("Ass already released")
        return AssRender(nativeAss)
    }

    public fun addFont(name: String, buffer: ByteArray) {
        if (released || nativeAss == 0L) return
        nativeAssAddFont(nativeAss, name, buffer)
    }

    public fun clearFont() {
        if (released || nativeAss == 0L) return
        nativeAssClearFont(nativeAss)
    }

    fun release() {
        if (released) return
        released = true
        if (nativeAss != 0L) {
            nativeAssDeinit(nativeAss)
            nativeAss = 0
        }
    }

    protected fun finalize() {
        release()
    }

}
