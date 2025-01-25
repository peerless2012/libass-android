package io.github.peerless2012.ass.parser

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import io.github.peerless2012.ass.AssHandler

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
            val track = assHandler.createTrack(format)
            if (assHandler.useEffectsRenderer) {
                // The effects renderer calls libass directly, so we want to ignore parse events
                NoOpSubtitleParser()
            } else {
                AssSubtitleParser(assHandler, track)
            }
        } else {
            defaultSubtitleParserFactory.create(format)
        }
    }

    private class NoOpSubtitleParser : SubtitleParser {
        override fun parse(
            data: ByteArray,
            offset: Int,
            length: Int,
            outputOptions: SubtitleParser.OutputOptions,
            output: Consumer<CuesWithTiming>
        ) {
        }

        override fun getCueReplacementBehavior(): Int {
            return Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE
        }
    }
}
