package io.github.peerless2012.ass.factory

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import io.github.peerless2012.ass.AssKeeper
import io.github.peerless2012.ass.parser.AssParser

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/07 01:36
 * @Version V1.0
 * @Description
 */
@UnstableApi
class AssSubtitleParserFactory(private val assKeeper: AssKeeper): SubtitleParser.Factory {

    private val defaultSubtitleParserFactory = DefaultSubtitleParserFactory()

    override fun supportsFormat(format: Format): Boolean {
        return defaultSubtitleParserFactory.supportsFormat(format)
    }

    override fun getCueReplacementBehavior(format: Format): Int {
        return defaultSubtitleParserFactory.getCueReplacementBehavior(format)
    }

    override fun create(format: Format): SubtitleParser {
        return if (format.sampleMimeType == MimeTypes.TEXT_SSA) {
            AssParser(assKeeper, format.initializationData)
        } else {
            defaultSubtitleParserFactory.create(format)
        }
    }

}