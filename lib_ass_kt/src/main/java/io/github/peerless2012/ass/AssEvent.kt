package io.github.peerless2012.ass

/**
 * @Author peerless2012
 * @Email peerless2012@126.com
 * @DateTime 2025/Jan/07 23:18
 * @Version V1.0
 * @Description
 */
data class AssEvent(
    val start: Long,
    val duration: Long,
    val order: Int,
    val layer: Int,
    val style: Int,
    val name: String,
    val marginLeft: Int,
    val marginRight: Int,
    val marginVertical: Int,
    val effect: String,
    val text: String
)