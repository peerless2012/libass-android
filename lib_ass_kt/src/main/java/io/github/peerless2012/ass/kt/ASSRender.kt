package io.github.peerless2012.ass.kt

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/05 14:18
 * @Version V1.0
 * @Description
 */
class ASSRender(nativeAss: Long) {

    companion object {

        @JvmStatic
        external fun nativeAssRenderInit(ass: Long): Long

        @JvmStatic
        external fun nativeAssRenderSetFontScale(render: Long, scale: Float)

        @JvmStatic
        external fun nativeAssRenderSetStorageSize(render: Long, width: Int, height: Int)

        @JvmStatic
        external fun nativeAssRenderSetFrameSize(render: Long, width: Int, height: Int)

        @JvmStatic
        external fun nativeAssRenderFrame(render: Long, track: Long, time: Long, onlyAlpha: Boolean): ASSFrame?

        @JvmStatic
        external fun nativeAssRenderDeinit(render: Long)
    }

    private val nativeRender: Long = nativeAssRenderInit(nativeAss)

    private var track: ASSTrack? = null

    public fun setTrack(track: ASSTrack?) {
        this.track = track
    }

    public fun setFontScale(scale: Float) {
        nativeAssRenderSetFontScale(nativeRender, scale)
    }

    public fun setStorageSize(width: Int, height: Int) {
        nativeAssRenderSetStorageSize(nativeRender, width, height)
    }

    public fun setFrameSize(width: Int, height: Int) {
        nativeAssRenderSetFrameSize(nativeRender, width, height)
    }

    public fun renderFrame(time: Long, onlyAlpha: Boolean): ASSFrame? {
        return track?.let { nativeAssRenderFrame(nativeRender, it.nativeAssTrack, time, onlyAlpha) }
    }

    protected fun finalize() {
        nativeAssRenderDeinit(nativeRender)
    }

}