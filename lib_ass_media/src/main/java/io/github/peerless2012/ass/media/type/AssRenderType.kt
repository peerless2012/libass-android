package io.github.peerless2012.ass.media.type

/**
 * ASS render type
 */
enum class AssRenderType {

    /**
     * Use SubtitleView render.
     */
    LEGACY,

    /**
     * Use Effect(Powered by canvas)
     */
    CANVAS,

    /**
     * Use Effect(Powered by OpenGL)
     */
    OPEN_GL,

    /**
     * Use Widget overlay.
     */
    OVERLAY
}