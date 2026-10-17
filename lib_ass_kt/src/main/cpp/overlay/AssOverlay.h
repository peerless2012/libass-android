#ifndef LIBASS_ANDROID_ASS_OVERLAY_H
#define LIBASS_ANDROID_ASS_OVERLAY_H

#include "ass/ass.h"
#include "AssOverlayGl.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    AssOverlayGl gl;
    AssAtlas atlas;
    // The video frame's pixel size: libass's frame size and the GL viewport.
    int frameW, frameH;
} AssOverlay;

/**
 * \brief Renders `track` at `timeMs` and draws it onto a video frame.
 *
 * The first call creates the overlay, and later calls reuse it. Nothing is drawn if render or
 * track is NULL.
 * \param overlay NULL on the first call, then whatever the previous call returned
 * \param render libass renderer
 * \param track track to render
 * \param fbo GL framebuffer wrapping the video frame's texture
 * \param frameWidth video frame width, in pixels
 * \param frameHeight video frame height, in pixels
 * \param timeMs time to render the track at, in milliseconds
 * \return the overlay to pass to the next call, or NULL if it can't be created
 */
AssOverlay* assOverlayDraw(AssOverlay* overlay, ASS_Renderer* render, ASS_Track* track,
    unsigned int fbo, int frameWidth, int frameHeight, long long timeMs);

/**
 * \brief Frees the overlay and its GL objects.
 * \param overlay overlay to free, or NULL
 */
void assOverlayRelease(AssOverlay* overlay);

#ifdef __cplusplus
}
#endif

#endif // LIBASS_ANDROID_ASS_OVERLAY_H
