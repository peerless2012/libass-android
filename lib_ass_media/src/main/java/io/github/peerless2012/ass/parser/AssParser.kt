package io.github.peerless2012.ass.parser

import android.annotation.SuppressLint
import android.util.Log
import androidx.media3.common.Format
import androidx.media3.common.text.Cue
import androidx.media3.common.util.Consumer
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import io.github.peerless2012.ass.AssKeeper



/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/07 01:24
 * @Version V1.0
 * @Description
 */
@SuppressLint("UnsafeOptInUsageError")
class AssParser: SubtitleParser {

    private val timestampPattern = "(\\d+:\\d{2}:\\d{2}):(\\d{2})".toRegex()

    private val haveInitializationData: Boolean

    private val assKeeper: AssKeeper

    constructor(assKeeper: AssKeeper): this(assKeeper, null)

    constructor(assKeeper: AssKeeper, initializationData: List<ByteArray>?) {
        this.assKeeper = assKeeper
        if (!initializationData.isNullOrEmpty()) {
            haveInitializationData = true
            val format = String(initializationData[0], Charsets.UTF_8)
            assert(format.startsWith("Format:"))
            val header = String(initializationData[1], Charsets.UTF_8)
            val lines = header.lines().toMutableList()
            val index = lines.indexOfFirst {
                it.startsWith("[Events]")
            }
            if (index >= 0 && lines[index+1].startsWith("Format:")) {
                lines[index+1] = format
            }
            val result = lines.joinToString(separator = "\n")
            assKeeper.track.readBuffer(result.toByteArray())
        } else {
            haveInitializationData = false
        }
    }

    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>
    ) {
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
        assKeeper.track.readBuffer(newText.toByteArray())
        val events = assKeeper.track.getEvents()
        val cues= mutableListOf<Cue>()
        val startTimesUs: List<Long> = ArrayList()
        Log.i("AssParser", string)
        events?.forEach {event ->
            Log.i("AssParser", "event : " + event)
            val texs = assKeeper.render.readFrames(event.start)
            texs?.forEach { tex ->
                Log.i("AssParser", "tex : x = " + tex.x + ", y = " + tex.y + ", width = " + tex.bitmap.width + ", height = " + tex.bitmap.height)
                val cue = Cue.Builder()
                    .setBitmap(tex.bitmap)
                    .setPosition(tex.x / assKeeper.width.toFloat())
                    .setPositionAnchor(Cue.ANCHOR_TYPE_START)
                    .setLine(tex.y / assKeeper.height.toFloat(), Cue.LINE_TYPE_FRACTION)
                    .setLineAnchor(Cue.ANCHOR_TYPE_START)
                    .setSize(tex.bitmap.width / assKeeper.width.toFloat())
                    .setBitmapHeight(tex.bitmap.height / assKeeper.height.toFloat())
                    .build()
                cues.add(cue)
            }
            if (cues.size > 0) {
                val cwt = CuesWithTiming(cues, event.start * 1000, event.duration * 1000)
                output.accept(cwt)
            }
        }
        assKeeper.track.clearEvent()
    }

    override fun getCueReplacementBehavior(): Int {
        return Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE
    }
}