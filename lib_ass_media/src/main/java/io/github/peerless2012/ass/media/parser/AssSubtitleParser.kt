package io.github.peerless2012.ass.media.parser

import androidx.media3.common.Format
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.text.Cue
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.AssTrack
import io.github.peerless2012.ass.media.type.AssRenderType

@UnstableApi
abstract class AssSubtitleParser(
    protected val assHandler: AssHandler,
    protected val track: AssTrack,
    private val segment: Boolean
): SubtitleParser {

    private val fadPattern = """\\fad\((\d+),(\d+)\)""".toRegex()

    final override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>
    ) {
        onParse(data, offset, length)
        if (assHandler.renderType != AssRenderType.LEGACY) {
            return
        }
        val events = track.getEvents()
        events?.forEach {event ->

            // Note
            // When use fade effect, the default start time will display nothing.
            // So we find the fade in and out time, pass the content real display time to ass.
            // And we also change the start time and duration.
            val fadeMatch = fadPattern.find(event.text)
            var fadeIn = 0
            var fadeOut = 0
            fadeMatch?.destructured?.let { (newFadeIn, newFadeOut) ->
                fadeIn = newFadeIn.trim().toInt()
                fadeOut = newFadeOut.trim().toInt()
            }

            val cues = mutableListOf<Cue>()
            val frames = assHandler.render?.renderFrame(event.start + fadeIn, false)
            frames?.images?.let { texts ->
                texts.forEach { tex ->
                    val cueBuilder = Cue.Builder()
                    // For users use stable media3
                    if (MediaLibraryInfo.VERSION_INT > 1_008_000_0_00) {
                        cueBuilder.setZIndex(event.layer)
                    }
                    val cue = cueBuilder
                        .setBitmap(tex.bitmap)
                        .setPosition(tex.x / assHandler.videoSize.width.toFloat())
                        .setPositionAnchor(Cue.ANCHOR_TYPE_START)
                        .setLine(tex.y / assHandler.videoSize.height.toFloat(), Cue.LINE_TYPE_FRACTION)
                        .setLineAnchor(Cue.ANCHOR_TYPE_START)
                        .setSize(tex.bitmap.width / assHandler.videoSize.width.toFloat())
                        .setBitmapHeight(tex.bitmap.height / assHandler.videoSize.height.toFloat())
                        .build()
                    cues.add(cue)
                }
                val cwt = CuesWithTiming(cues, (event.start + fadeIn) * 1000
                    , (event.duration - fadeIn - fadeOut) * 1000)
                output.accept(cwt)
            }
        }
        if (segment) {
            track.clearEvent()
        }
    }

    abstract fun onParse(data: ByteArray, offset: Int, length: Int)

    final override fun getCueReplacementBehavior(): Int {
        return Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE
    }

}