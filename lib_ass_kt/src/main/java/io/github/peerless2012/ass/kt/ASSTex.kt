package io.github.peerless2012.ass.kt

import android.graphics.Bitmap

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/08 22:11
 * @Version V1.0
 * @Description
 */
data class ASSTex(val x: Int, val y: Int, val bitmap: Bitmap)

data class ASSTexAlpha(val x: Int, val y: Int, val alpha: Bitmap, val color: Int)
