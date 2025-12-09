package io.github.peerless2012.ass

import android.graphics.Bitmap

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/08 22:11
 * @Version V1.0
 * @Description
 */
data class AssTex(val x: Int, val y: Int, val w: Int, val h: Int, val color: Int, val bitmap: Bitmap? = null, val tex: Int = 0)