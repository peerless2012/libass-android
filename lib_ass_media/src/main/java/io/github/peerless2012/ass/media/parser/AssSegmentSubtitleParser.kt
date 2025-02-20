package io.github.peerless2012.ass.media.parser

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.text.Cue
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.ASSTrack

@OptIn(UnstableApi::class)
class AssSegmentSubtitleParser(
    assHandler: AssHandler,
    track: ASSTrack,
): AssSubtitleParser(assHandler, track) {

    private val timestampPattern = "(\\d+:\\d{2}:\\d{2}):(\\d{2})".toRegex()

    private val fadPattern = """\\fad\((\d+),(\d+)\)""".toRegex()

    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>
    ) {
        super.parse(data, offset, length, outputOptions, output)

        // Note
        // Exoplayer will trans time from hh:mm:ss.xxx to hh:mm:ss:xxx
        // And lib ass only can parse hh:mm:ss.xxx
        // So we have to replace the time
        val string = String(data, offset, length, Charsets.UTF_8)
        val newText = timestampPattern.replace(string) { matchResult ->
            val timePart = matchResult.groupValues[1]
            val frames = matchResult.groupValues[2]
            "$timePart.$frames"
        }

        // Note
        // When use fade effect, the default start time will display nothing.
        // So we find the fade in and out time, pass the content real display time to ass.
        // And we also change the start time and duration.
        val fadeMatch = fadPattern.find(newText)
        var fadeIn = 0
        var fadeOut = 0
        fadeMatch?.destructured?.let { (newFadeIn, newFadeOut) ->
            fadeIn = newFadeIn.trim().toInt()
            fadeOut = newFadeOut.trim().toInt()
        }

        track.readBuffer(newText.toByteArray())
        val events = track.getEvents().orEmpty()
        val cues = mutableListOf<Cue>()
        events.forEach {event ->
            val frames = assHandler.render?.renderFrame(event.start + fadeIn, false)
            frames?.images?.forEach { tex ->
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
            if (cues.size > 0) {
                val cwt = CuesWithTiming(cues, event.start * 1000 + fadeIn * 1000, event.duration * 1000 - (fadeIn + fadeOut) * 1000)
                output.accept(cwt)
            }
        }
        track.clearEvent()
    }

    override fun getCueReplacementBehavior(): Int {
        return Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE
    }
}
