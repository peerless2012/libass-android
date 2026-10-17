#include "AssOverlay.h"

#include <stdlib.h>
#include <time.h>

#include "AssAtlas.h"
#include "AssOverlayGl.h"
#include "AssOverlayLog.h"

#define ASS_OVERLAY_SLOW_LOG_MS 10

/**
 * \brief Reads the monotonic clock.
 * \return the time in milliseconds, from an arbitrary start
 */
static long long nowMs(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long long) ts.tv_sec * 1000LL + ts.tv_nsec / 1000000LL;
}

/**
 * \brief Frees the overlay, its GL objects and its atlas.
 * \param overlay overlay to free, or NULL
 */
static void destroyOverlay(AssOverlay* overlay) {
    if (overlay == NULL) {
        return;
    }
    assOverlayGlRelease(&overlay->gl);
    assAtlasFree(&overlay->atlas);
    free(overlay);
}

/**
 * \brief Allocates an overlay and sets up its GL state.
 * \return the new overlay, or NULL on OOM or if the shader program can't be built
 */
static AssOverlay* createOverlay(void) {
    AssOverlay* overlay = (AssOverlay*) calloc(1, sizeof(AssOverlay));
    if (overlay == NULL) {
        LOGE("Failed to allocate the overlay");
        return NULL;
    }
    if (!assOverlayGlInit(&overlay->gl)) {
        destroyOverlay(overlay);
        return NULL;
    }
    overlay->atlas.maxPageSize = overlay->gl.maxTextureSize;
    return overlay;
}

/**
 * \brief Brings the atlas and its quads in line with libass's latest output.
 * \param overlay overlay whose atlas to update
 * \param images libass image list (returned by ass_render_frame)
 * \param changed ass_render_frame's detect_change: 0 if nothing changed since the previous call,
 * 1 if only positions did, 2 if the content did
 */
static void updateAtlas(AssOverlay* overlay, const ASS_Image* images, int changed) {
    if (changed == 0) {
        return;
    }

    AssAtlas* atlas = &overlay->atlas;

    assAtlasPack(atlas, images);
    if (atlas->pieceCount == 0) {
        return;
    }

    const int uploadedPages = assOverlayGlUpload(&overlay->gl, atlas);
    assAtlasTruncate(atlas, uploadedPages); // fewer only on OOM
    if (atlas->pieceCount == 0) {
        return;
    }
    if (!assOverlayGlSetQuads(&overlay->gl, atlas, overlay->frameW, overlay->frameH)) {
        assAtlasTruncate(atlas, 0);
    }
}

AssOverlay* assOverlayDraw(AssOverlay* overlay, ASS_Renderer* render, ASS_Track* track,
    unsigned int fbo, int frameWidth, int frameHeight, long long timeMs) {

    if (overlay == NULL) {
        overlay = createOverlay();
        if (overlay == NULL) {
            return NULL;
        }
    }

    if (render == NULL || track == NULL) {
        LOGE("Cannot draw the overlay without a renderer and a track (render=%p, track=%p)",
            (void*) render, (void*) track);
        return overlay;
    }


    if (frameWidth != overlay->frameW || frameHeight != overlay->frameH) {
        overlay->frameW = frameWidth;
        overlay->frameH = frameHeight;
        ass_set_frame_size(render, frameWidth, frameHeight);
        LOGI("ass_set_frame_size(%d, %d)", frameWidth, frameHeight);
    }

    int changed = 0;
    const long long start = nowMs();
    const ASS_Image* images = ass_render_frame(render, track, timeMs, &changed);
    const long long rendered = nowMs();
    updateAtlas(overlay, images, changed);
    const long long updated = nowMs();

    if (updated - start >= ASS_OVERLAY_SLOW_LOG_MS) {
        LOGW("timeMs=%lld (changed=%d): ass_render_frame %lldms, atlas update %lldms (pieces=%d, pages=%d)",
            timeMs, changed, rendered - start, updated - rendered, overlay->atlas.pieceCount, overlay->atlas.pageCount);
    }

    // The atlas outlives changed == 0 frames, but each call's FBO wraps a new video frame that doesn't
    // have the subtitles on it yet.
    if (overlay->atlas.pieceCount > 0) {
        assOverlayGlDraw(&overlay->gl, &overlay->atlas, (GLuint) fbo, overlay->frameW, overlay->frameH);
    }
    return overlay;
}

void assOverlayRelease(AssOverlay* overlay) {
    destroyOverlay(overlay);
}
