package io.github.peerless2012.ass.media.parser

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.common.ROLE_FLAG_EXTERNAL_SUBTITLES
import io.github.peerless2012.ass.media.type.AssRenderType

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/07 01:36
 * @Version V1.0
 * @Description
 */
@UnstableApi
class AssSubtitleParserFactory(private val assHandler: AssHandler): SubtitleParser.Factory {

    private val defaultSubtitleParserFactory = DefaultSubtitleParserFactory()

    override fun supportsFormat(format: Format): Boolean {
        return defaultSubtitleParserFactory.supportsFormat(format)
    }

    override fun getCueReplacementBehavior(format: Format): Int {
        return defaultSubtitleParserFactory.getCueReplacementBehavior(format)
    }

    override fun create(format: Format): SubtitleParser {
        return if (format.sampleMimeType == MimeTypes.TEXT_SSA) {
            val externalSubtitles = (format.roleFlags and ROLE_FLAG_EXTERNAL_SUBTITLES) > 0
            val track = assHandler.createTrack(format)
            if (externalSubtitles) {
                AssFullSubtitleParser(assHandler.renderType == AssRenderType.LEGACY,
                    assHandler,
                    track)
            } else {
                if (assHandler.renderType != AssRenderType.LEGACY) {
                    AssNoOpSubtitleParser()
                } else {
                    AssSegmentSubtitleParser(assHandler, track)
                }
            }
        } else {
            defaultSubtitleParserFactory.create(format)
        }
    }

}
