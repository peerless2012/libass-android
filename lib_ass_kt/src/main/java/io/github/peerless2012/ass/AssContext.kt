package io.github.peerless2012.ass

class AssContext {

    companion object {
        init {
            System.loadLibrary("asskt")
        }

        @JvmStatic
        private external fun nativeAssContextCreate(): Long

        @JvmStatic
        private external fun nativeAssContextDestroy(ctx: Long)
    }

    val nativeCtx: Long = nativeAssContextCreate()

    fun destroy() {
        nativeAssContextDestroy(nativeCtx)
    }

}