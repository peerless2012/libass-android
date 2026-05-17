package io.github.peerless2012.ass

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/05 14:15
 * @Version V1.0
 * @Description
 */
class Ass {

    private val ctx = AssContext()

    companion object {

        @JvmStatic
        external fun nativeAssInit(ctx: Long): Long

        @JvmStatic
        external fun nativeAssAddFont(ctx: Long, ptr: Long, name: String, buffer: ByteArray)

        @JvmStatic
        external fun nativeAssClearFont(ctx: Long, ptr: Long)

        @JvmStatic
        external fun nativeAssDeinit(ctx: Long, ptr: Long)

    }

    private val nativeAss: Long = nativeAssInit(ctx.nativeCtx)

    public fun createTrack(): AssTrack {
        return AssTrack(ctx, nativeAss)
    }

    public fun createRender(): AssRender {
        return AssRender(ctx, nativeAss)
    }

    public fun addFont(name: String, buffer: ByteArray) {
        nativeAssAddFont(ctx.nativeCtx, nativeAss, name, buffer)
    }

    public fun clearFont() {
        nativeAssClearFont(ctx.nativeCtx, nativeAss)
    }

    protected fun finalize() {
        nativeAssDeinit(ctx.nativeCtx, nativeAss)
        ctx.destroy()
    }

}