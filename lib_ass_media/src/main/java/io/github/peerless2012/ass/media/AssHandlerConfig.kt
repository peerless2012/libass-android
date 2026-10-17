package io.github.peerless2012.ass.media

data class AssHandlerConfig(
    val glyphSize: Int = 10000,
    val cacheSize: Int = 128,

    /**
     * Maximum number of pixels (width * height) for subtitle rendering.
     *
     * When the target frame size exceeds this limit, the render size will be
     * proportionally downscaled while maintaining the aspect ratio.
     *
     * This reduces CPU and memory usage on high-resolution displays (e.g., 4K TVs)
     * at the cost of slightly lower subtitle sharpness.
     *
     * Examples:
     * - 1920 * 1080 = 2_073_600 (limit to 1080p)
     * - 2560 * 1440 = 3_686_400 (limit to 1440p)
     * - 0 = no limit, render at full frame size (default)
     *
     * Only applies to OVERLAY and EFFECTS render types, except EFFECTS_ATLAS, which
     * always renders at video size. CUES mode is not affected.
     */
    val maxRenderPixels: Int = 0
)
