package io.github.peerless2012.ass.text

import android.annotation.SuppressLint
import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.Subtitle

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/05 23:16
 * @Version V1.0
 * @Description
 */
@SuppressLint("UnsafeOptInUsageError")
class AssSubtitle : Subtitle {
    override fun getNextEventTimeIndex(timeUs: Long): Int {
        TODO("Not yet implemented")
    }

    override fun getEventTimeCount(): Int {
        TODO("Not yet implemented")
    }

    override fun getEventTime(index: Int): Long {
        TODO("Not yet implemented")
    }

    override fun getCues(timeUs: Long): MutableList<Cue> {
        TODO("Not yet implemented")
    }
}