package io.github.peerless2012.ass.parser

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.text.Cue
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import io.github.peerless2012.ass.AssHandler
import io.github.peerless2012.ass.kt.ASSTrack

@OptIn(UnstableApi::class)
class AssSubtitleParser(
    private val assHandler: AssHandler,
    private val track: ASSTrack,
): SubtitleParser {

    private var surfaceSizeDirty = true

    private val timestampPattern = "(\\d+:\\d{2}:\\d{2}):(\\d{2})".toRegex()

    init {
        updateRenderSize()
        assHandler.onSurfaceSizeChanged {
            surfaceSizeDirty = true
        }
    }

    /**
     * Update frame size
     */
    private fun updateRenderSize() {
        if (surfaceSizeDirty) {
            val surfaceSize = assHandler.surfaceSize
            if (surfaceSize.width > 0 && surfaceSize.height > 0) {
                Log.i("AssParser", "surface size = $surfaceSize")
                assHandler.render?.setFrameSize(surfaceSize.width, surfaceSize.height)
            }
            surfaceSizeDirty = false
        }
    }

    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>
    ) {
        updateRenderSize()

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

        track.readBuffer(newText.toByteArray())
        val events = track.getEvents().orEmpty()
        val cues = mutableListOf<Cue>()
        events.forEach {event ->
            Log.i("AssParser", "event : $event")
            val texs = assHandler.render?.renderFrame(event.start, false)
            texs?.images?.forEach { tex ->
                Log.i("AssParser", "tex : x = " + tex.x + ", y = " + tex.y + ", width = " + tex.bitmap.width + ", height = " + tex.bitmap.height)
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
                val cwt = CuesWithTiming(cues, event.start * 1000, event.duration * 1000)
                output.accept(cwt)
            }
        }
        track.clearEvent()
    }

    override fun getCueReplacementBehavior(): Int {
        return Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE
    }
}
