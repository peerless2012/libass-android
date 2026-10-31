package io.github.peerless2012.ass.media.type

/**
 * ASS render type
 */
enum class AssRenderType {

    /**
     * Use SubtitleView render.
     */
    CUES,

    /**
     * Use Effect(Powered by canvas)
     */
    @Deprecated("Use OVERLAY instead.")
    EFFECTS_CANVAS,

    /**
     * Use Effect(Powered by OpenGL)
     */
    @Deprecated("Use OVERLAY instead.")
    EFFECTS_OPEN_GL,

    /**
     * Use Effect(Powered by a native GLES `GlEffect`/`GlShaderProgram` with a GPU texture atlas).
     *
     * This is the most performant and frame accurate mode.
     * It doesn't support HDR and Dolby Vision.
     */
    EFFECTS_ATLAS,

    /**
     * Use Widget overlay(Powered by Canvas).
     */
    OVERLAY_CANVAS,

    /**
     * Use Widget overlay(Powered by OPEN GL).
     */
    OVERLAY_OPEN_GL,
}