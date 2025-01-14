package io.github.peerless2012.ass.kt

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
        external fun nativeAssDeinit(ptr: Long)

    }

    private val nativeAss: Long = nativeAssInit()

    public fun createTrack(): ASSTrack {
        return ASSTrack(nativeAss)
    }

    public fun createRender(): ASSRender {
        return ASSRender(nativeAss)
    }

    protected fun finalize() {
        nativeAssDeinit(nativeAss)
    }

}