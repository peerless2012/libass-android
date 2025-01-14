package io.github.peerless2012.ass.decoder

import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.SimpleSubtitleDecoder
import androidx.media3.extractor.text.Subtitle
import io.github.peerless2012.ass.text.AssSubtitle

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/05 22:35
 * @Version V1.0
 * @Description
 */
@UnstableApi
class AssSubtitleDecoder: SimpleSubtitleDecoder("ASS") {

    override fun decode(data: ByteArray, length: Int, reset: Boolean): Subtitle {
        return AssSubtitle()
    }

}