package io.github.peerless2012.ass.kt

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/05 14:18
 * @Version V1.0
 * @Description
 */
class ASSTrack(private val ass: Long) {

    companion object {

        @JvmStatic
        external fun nativeAssTrackInit(track: Long): Long

        @JvmStatic
        external fun nativeAssTrackGetWidth(track: Long): Int

        @JvmStatic
        external fun nativeAssTrackGetHeight(track: Long): Int

        @JvmStatic
        external fun nativeAssTrackGetEvents(track: Long): Array<ASSEvent>?

        @JvmStatic
        external fun nativeAssTrackClearEvents(track: Long)

        @JvmStatic
        external fun nativeAssTrackReadBuffer(track: Long, byteArray: ByteArray, offset: Int, length: Int)

        @JvmStatic
        external fun nativeAssTrackReadChunk(track: Long, start: Long, duration: Long, byteArray: ByteArray, offset: Int, length: Int)

        @JvmStatic
        external fun nativeAssTrackDeinit(track: Long)
    }

    public val nativeAssTrack = nativeAssTrackInit(ass)

    public fun getWidth(): Int {
        return nativeAssTrackGetWidth(nativeAssTrack)
    }

    public fun getHeight(): Int {
        return nativeAssTrackGetHeight(nativeAssTrack)
    }

    public fun getEvents(): Array<ASSEvent>? {
        return nativeAssTrackGetEvents(nativeAssTrack)
    }

    public fun clearEvent() {
        nativeAssTrackClearEvents(nativeAssTrack)
    }

    public fun readBuffer(array: ByteArray, offset: Int = 0, length : Int = array.size) {
        nativeAssTrackReadBuffer(nativeAssTrack, array, offset, length)
    }

    public fun readChunk(start: Long, duration: Long, array: ByteArray, offset: Int = 0, length: Int = array.size) {
        nativeAssTrackReadChunk(nativeAssTrack, start, duration, array, offset, length)
    }

    protected fun finalize() {
        nativeAssTrackDeinit(nativeAssTrack)
    }

}