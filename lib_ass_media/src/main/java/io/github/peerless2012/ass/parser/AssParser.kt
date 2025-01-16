package io.github.peerless2012.ass.parser

import android.annotation.SuppressLint
import android.util.Log
import androidx.media3.common.Format
import androidx.media3.common.text.Cue
import androidx.media3.common.util.Consumer
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import io.github.peerless2012.ass.kt.Ass

val ass = Ass()

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

    private val track = ass.createTrack()

    private val renderWidth = 1920;

    private val renderHeight = 1080;

    private val render = ass.createRender().also {
        it.setTrack(track)
    }

    constructor(): this(null)

    constructor(initializationData: List<ByteArray>?) {
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
            track.readBuffer(result.toByteArray())
            render.setFontScale(2.0f)
            render.setStorageSize(1920, 1080)
            render.setFrameSize(renderWidth, renderHeight)
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
        track.readBuffer(newText.toByteArray())
        val events = track.getEvents()
        val cues= mutableListOf<Cue>()
        val startTimesUs: List<Long> = ArrayList()
        Log.i("AssParser", string)
        events?.forEach {event ->
            Log.i("AssParser", "event : " + event)
            val texs = render.readFrames(event.start)
            texs?.forEach { tex ->
                Log.i("AssParser", "tex : x = " + tex.x + ", y = " + tex.y + ", width = " + tex.bitmap.width + ", height = " + tex.bitmap.height)
                val cue = Cue.Builder()
                    .setBitmap(tex.bitmap)
                    .setPosition(tex.x / 1920f)
                    .setPositionAnchor(Cue.ANCHOR_TYPE_START)
                    .setLine(tex.y / 1080f, Cue.LINE_TYPE_FRACTION)
                    .setLineAnchor(Cue.ANCHOR_TYPE_START)
                    .setSize(tex.bitmap.width / 1920f)
                    .setBitmapHeight(tex.bitmap.height / 1080f)
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