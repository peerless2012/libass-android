package io.github.peerless2012.ass.media.render

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.NoSampleRenderer
import io.github.peerless2012.ass.media.AssHandler

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 5/25/25 10:19 PM
 * @Version V1.0
 * @Description
 */
@OptIn(UnstableApi::class)
class AssRenderer(private val assHandler: AssHandler): NoSampleRenderer() {

    override fun getName(): String {
        return "AssRenderer"
    }

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        assHandler.videoTime = positionUs - 1000000000000L
    }

}