package io.github.peerless2012.ass.media.parser

import androidx.media3.common.Format
import androidx.media3.common.text.Cue
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import io.github.peerless2012.ass.ASSTrack
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.type.AssRenderType

/**
 * Ass full subtitle parser.
 */
@UnstableApi
class AssFullSubtitleParser(
    assHandler: AssHandler,
    track: ASSTrack
) : AssSubtitleParser(assHandler, track) {
    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>
    ) {
        super.parse(data, offset, length, outputOptions, output)
        track.readBuffer(data, offset, length)
        if (assHandler.renderType == AssRenderType.LEGACY) {
            val events = track.getEvents()
            events?.forEach {event ->
                val cues = mutableListOf<Cue>()
                val frames = assHandler.render?.renderFrame(event.start, false)
                frames?.images?.let { texts ->
                    texts.forEach { tex ->
                        val cue = Cue.Builder()
                            .setBitmap(tex.bitmap)
                            .setPosition(tex.x / assHandler.surfaceSize.width.toFloat())
                            .setPositionAnchor(Cue.ANCHOR_TYPE_START)
                            .setLine(tex.y / assHandler.surfaceSize.height.toFloat(), Cue.LINE_TYPE_FRACTION)
                            .setLineAnchor(Cue.ANCHOR_TYPE_START)
                            .setSize(tex.bitmap.width / assHandler.surfaceSize.width.toFloat())
                            .setBitmapHeight(tex.bitmap.height / assHandler.surfaceSize.height.toFloat())
                            .build()
                        cues.add(cue)
                    }
                    val cwt = CuesWithTiming(cues, event.start * 1000, event.duration * 1000)
                    output.accept(cwt)
                }
            }
        }
    }

    override fun getCueReplacementBehavior(): Int {
        return Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE
    }
}