package io.github.peerless2012.ass

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/05 14:18
 * @Version V1.0
 * @Description
 */
class AssRender(private val ctx: AssContext, nativeAss: Long) {

    companion object {

        @JvmStatic
        external fun nativeAssRenderInit(ctx: Long, ass: Long): Long

        @JvmStatic
        external fun nativeAssRenderSetFontScale(ctx: Long, render: Long, scale: Float)

        @JvmStatic
        external fun nativeAssRenderSetCacheLimit(ctx: Long, render: Long, glyphMax: Int, bitmapMaxSize: Int)

        @JvmStatic
        external fun nativeAssRenderSetStorageSize(ctx: Long, render: Long, width: Int, height: Int)

        @JvmStatic
        external fun nativeAssRenderSetFrameSize(ctx: Long, render: Long, width: Int, height: Int)

        @JvmStatic
        external fun nativeAssRenderFrame(ctx: Long, render: Long, track: Long, time: Long, type: Int): AssFrame?

        @JvmStatic
        external fun nativeAssRenderDeinit(ctx: Long, render: Long)
    }

    private val nativeRender: Long = nativeAssRenderInit(ctx.nativeCtx, nativeAss)

    private var track: AssTrack? = null

    public fun setTrack(track: AssTrack?) {
        this.track = track
    }

    public fun setFontScale(scale: Float) {
        nativeAssRenderSetFontScale(ctx.nativeCtx, nativeRender, scale)
    }

    public fun setCacheLimit(glyphMax: Int, bitmapMaxSize: Int) {
        nativeAssRenderSetCacheLimit(ctx.nativeCtx, nativeRender, glyphMax, bitmapMaxSize)
    }

    public fun setStorageSize(width: Int, height: Int) {
        nativeAssRenderSetStorageSize(ctx.nativeCtx, nativeRender, width, height)
    }

    public fun setFrameSize(width: Int, height: Int) {
        nativeAssRenderSetFrameSize(ctx.nativeCtx, nativeRender, width, height)
    }

    public fun renderFrame(time: Long, type: AssTexType): AssFrame? {
        return track?.let { nativeAssRenderFrame(ctx.nativeCtx, nativeRender, it.nativeAssTrack, time, type.ordinal) }
    }

    protected fun finalize() {
        nativeAssRenderDeinit(ctx.nativeCtx, nativeRender)
    }

}