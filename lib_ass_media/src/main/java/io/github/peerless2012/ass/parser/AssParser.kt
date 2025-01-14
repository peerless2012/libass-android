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
            var result = lines.joinToString(separator = "\n")
//            result = """
//                [Script Info]
//                ;SrtEdit 6.3.2012.1001
//                ;Copyright(C) 2005-2012 Yuan Weiguo
//
//                Title: 太阳之火
//                Original Script: 太阳之火
//                Original Translation:
//                Original Timing:
//                Original Editing:
//                Script Updated By:
//                Update Details:
//                ScriptType: v4.00+
//                Collisions: Normal
//                PlayResX: 384
//                PlayResY: 288
//                Timer: 100.0000
//                Synch Point: 1
//                WrapStyle: 0
//                ScaledBorderAndShadow: no
//
//                [V4+ Styles]
//                Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
//                Style: 01,SimSun,32,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,0,2,2,10,10,10,1
//                Style: 简体中文,STKaiti,20,&H00E0E0E0,&HF0000000,&H000D0500,&H00000000,0,0,0,0,100,100,0,0,1,0,1,2,0,0,8,1
//                Style: Default,KaiTi,18,&H00E0E0E0,&HF0000000,&H000D0500,&H00000000,0,0,0,0,100,100,0,0,1,0,1,2,0,0,8,1
//
//                [Events]
//                Format: Format: Start, End, ReadOrder, Layer, Style, Name, MarginL, MarginR, MarginV, Effect, Text
//
//            """.trimIndent()
            track.readBuffer(result.toByteArray())
            render.setFrameSize(1920, 1080)
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
                Log.i("AssParser", "tex : " + tex)
                val cue = Cue.Builder()
                    .setBitmap(tex.bitmap)
                    .setPosition(0.5f)
                    .setPositionAnchor(Cue.ANCHOR_TYPE_MIDDLE)
                    .setLine(0.9f, Cue.LINE_TYPE_FRACTION)
                    .setLineAnchor(Cue.ANCHOR_TYPE_MIDDLE)
                    .setSize(tex.bitmap.width * 2 / 1920f)
                    .setBitmapHeight(tex.bitmap.height * 2 / 1080f)
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