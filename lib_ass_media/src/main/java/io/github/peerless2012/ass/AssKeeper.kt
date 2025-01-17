package io.github.peerless2012.ass

import android.util.Log
import androidx.media3.common.Player.Listener
import androidx.media3.common.VideoSize
import io.github.peerless2012.ass.kt.ASSRender
import io.github.peerless2012.ass.kt.Ass

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/17 08:20
 * @Version V1.0
 * @Description
 */
class AssKeeper : Listener {

    private var _width = 0

    private var _height = 0

    val ass = Ass()

    val track = ass.createTrack()

    val render: ASSRender = ass.createRender().also {
        it.setTrack(track)
    }

    val width: Int
        get() = _width

    val height: Int
        get() = _height

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        super.onVideoSizeChanged(videoSize)
        Log.i("AssKeeper", "onVideoSizeChanged: width = ${videoSize.width}, height = ${videoSize.height}")
        render.setStorageSize(videoSize.width, videoSize.height)
    }

    override fun onSurfaceSizeChanged(width: Int, height: Int) {
        super.onSurfaceSizeChanged(width, height)
        _width = width
        _height = height
        Log.i("AssKeeper", "onSurfaceSizeChanged: width = $width, height = $height")
        render.setFrameSize(width, height)
    }

}