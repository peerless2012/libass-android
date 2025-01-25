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
        external fun nativeAssTrackDeinit(track: Long)
    }

    public val nativeAssTrack = nativeAssTrackInit(ass)

    private val hashCache = mutableSetOf<Int>()

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
        hashCache.clear()
        nativeAssTrackClearEvents(nativeAssTrack)
    }

    public fun readBuffer(line: String) {
        val hash = line.hashCode()
        if (hash in hashCache) return

        hashCache.add(hash)
        val array = line.encodeToByteArray()
        nativeAssTrackReadBuffer(nativeAssTrack, array, 0, array.size)
    }

    public fun readBuffer(array: ByteArray, offset: Int = 0, length : Int = array.size) {
        nativeAssTrackReadBuffer(nativeAssTrack, array, offset, length)
    }

    protected fun finalize() {
        nativeAssTrackDeinit(nativeAssTrack)
    }

}