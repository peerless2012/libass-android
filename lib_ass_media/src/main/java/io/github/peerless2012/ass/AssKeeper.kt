package io.github.peerless2012.ass

import android.util.Log
import androidx.media3.common.Player.Listener
import androidx.media3.common.VideoSize
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import io.github.peerless2012.ass.kt.ASSRender
import io.github.peerless2012.ass.kt.Ass

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/17 08:20
 * @Version V1.0
 * @Description
 */
@UnstableApi
class AssKeeper : Listener {

    val ass = Ass()

    val track = ass.createTrack()

    val render: ASSRender = ass.createRender().also {
        it.setTrack(track)
    }

    private var _videoSize = Size(0, 0)

    private var _surfaceSize = Size(0, 0)

    private var videoSizeCallback: ((Size) -> Unit)? = null

    private var surfaceSizeCallback: ((Size) -> Unit)? = null

    val videoSize: Size
        get() = _videoSize

    val surfaceSize: Size
        get() = _surfaceSize

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        super.onVideoSizeChanged(videoSize)
        Log.i("AssKeeper", "onVideoSizeChanged: width = ${videoSize.width}, height = ${videoSize.height}")
        if (_videoSize.width == videoSize.width && _videoSize.height == videoSize.height) return
        _videoSize = Size(videoSize.width, videoSize.height)
        videoSizeCallback?.invoke(_videoSize)
    }

    override fun onSurfaceSizeChanged(width: Int, height: Int) {
        super.onSurfaceSizeChanged(width, height)
        Log.i("AssKeeper", "onSurfaceSizeChanged: width = $width, height = $height")
        if (_surfaceSize.width == width && _surfaceSize.height == height) return
        _surfaceSize = Size(width, height)
        surfaceSizeCallback?.invoke(_surfaceSize)
    }

    public fun onVideoSizeChanged(callback: (Size) -> Unit) {
        this.videoSizeCallback = callback
    }

    public fun onSurfaceSizeChanged(callback: (Size) -> Unit) {
        this.surfaceSizeCallback = callback
    }

}