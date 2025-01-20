package io.github.peerless2012.ass.decoder

import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.SimpleSubtitleDecoder
import androidx.media3.extractor.text.Subtitle
import io.github.peerless2012.ass.AssKeeper
import io.github.peerless2012.ass.parser.AssParser

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/05 22:35
 * @Version V1.0
 * @Description
 */
@UnstableApi
class AssSubtitleDecoder(assKeeper: AssKeeper, initData: List<ByteArray>): SimpleSubtitleDecoder("AssSubtitleDecoder") {

    private val assParser = AssParser(assKeeper, initData)

    override fun decode(data: ByteArray, length: Int, reset: Boolean): Subtitle {
        if (reset) {
            assParser.reset()
        }
        return assParser.parseToLegacySubtitle(data, 0, length)
    }

}