package io.github.peerless2012.ass

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/05 14:18
 * @Version V1.0
 * @Description
 */
class AssTrack(private val ctx: AssContext, private val ass: Long) {

    companion object {

        @JvmStatic
        external fun nativeAssTrackInit(ctx: Long, track: Long): Long

        @JvmStatic
        external fun nativeAssTrackGetWidth(ctx: Long, track: Long): Int

        @JvmStatic
        external fun nativeAssTrackGetHeight(ctx: Long, track: Long): Int

        @JvmStatic
        external fun nativeAssTrackGetEvents(ctx: Long, track: Long): Array<AssEvent>?

        @JvmStatic
        external fun nativeAssTrackClearEvents(ctx: Long, track: Long)

        @JvmStatic
        external fun nativeAssTrackReadBuffer(ctx: Long, track: Long, byteArray: ByteArray, offset: Int, length: Int)

        @JvmStatic
        external fun nativeAssTrackReadChunk(ctx: Long, track: Long, start: Long, duration: Long, byteArray: ByteArray, offset: Int, length: Int)

        @JvmStatic
        external fun nativeAssTrackDeinit(ctx: Long, track: Long)
    }

    public val nativeAssTrack = nativeAssTrackInit(ctx.nativeCtx, ass)

    public fun getWidth(): Int {
        return nativeAssTrackGetWidth(ctx.nativeCtx, nativeAssTrack)
    }

    public fun getHeight(): Int {
        return nativeAssTrackGetHeight(ctx.nativeCtx, nativeAssTrack)
    }

    public fun getEvents(): Array<AssEvent>? {
        return nativeAssTrackGetEvents(ctx.nativeCtx, nativeAssTrack)
    }

    public fun clearEvent() {
        nativeAssTrackClearEvents(ctx.nativeCtx, nativeAssTrack)
    }

    public fun readBuffer(array: ByteArray, offset: Int = 0, length: Int = array.size) {
        nativeAssTrackReadBuffer(ctx.nativeCtx, nativeAssTrack, array, offset, length)
    }

    public fun readChunk(start: Long, duration: Long, array: ByteArray, offset: Int = 0, length: Int = array.size) {
        nativeAssTrackReadChunk(ctx.nativeCtx, nativeAssTrack, start, duration, array, offset, length)
    }

    protected fun finalize() {
        nativeAssTrackDeinit(ctx.nativeCtx, nativeAssTrack)
    }

}