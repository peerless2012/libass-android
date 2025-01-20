package io.github.peerless2012.ass.factory

import android.annotation.SuppressLint
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.text.SubtitleDecoderFactory
import androidx.media3.extractor.text.SubtitleDecoder
import io.github.peerless2012.ass.AssKeeper
import io.github.peerless2012.ass.decoder.AssSubtitleDecoder

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/05 23:12
 * @Version V1.0
 * @Description
 */
@SuppressLint("UnsafeOptInUsageError")
class AssDecoderFactory(private val assKeeper: AssKeeper) : SubtitleDecoderFactory {

    override fun supportsFormat(format: Format): Boolean {
        return format.sampleMimeType == MimeTypes.TEXT_SSA
    }

    override fun createDecoder(format: Format): SubtitleDecoder {
        return AssSubtitleDecoder(assKeeper, format.initializationData)
    }

}