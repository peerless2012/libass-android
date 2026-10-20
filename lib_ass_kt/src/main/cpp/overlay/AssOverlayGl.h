#ifndef LIBASS_ANDROID_ASS_OVERLAY_GL_H
#define LIBASS_ANDROID_ASS_OVERLAY_GL_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "AssAtlas.h"
#include "GLES3/gl3.h"

#ifdef __cplusplus
extern "C" {
#endif

// The texture holding one atlas page. Its capacity only grows, so it is rarely reallocated.
typedef struct {
    GLuint id;
    int capW, capH;
} AssOverlayTexture;

typedef struct {
    float x, y; // clip space
    float u, v; // texture coordinates
    uint8_t r, g, b, a; // normalized to [0, 1] by the attribute pointer
} AssOverlayVertex;

// The GL side of the overlay: shader program, page textures, uploads and quads. GL thread only, with
// the context current.
typedef struct {
    int maxTextureSize;
    bool isGles3; // R8 textures and PBO uploads; GLES2 gets ALPHA textures and client-memory uploads

    GLuint program;
    GLint aPosition, aTexCoord, aColor;

    AssOverlayTexture* textures; // textures[i] holds atlas page i; spare ones are kept for reuse
    int textureCap;

    // The GLES3 upload buffer. Grow-only and never orphaned: on ANGLE, orphaning a multi-MB buffer
    // allocates and page-faults new storage on every upload (10-18 ms). Instead it's mapped
    // synchronized, which only waits if its previous upload hasn't run yet, and that upload has
    // normally long run by the next changed frame: one buffer is enough.
    GLuint pbo;
    size_t pboSize;
    // For when there is no PBO (GLES2) or it can't be mapped.
    unsigned char* staging;
    size_t stagingCap;

    GLuint vbo;
    int vboCap; // in vertices
    AssOverlayVertex* vertices; // a quad (6 vertices) per atlas piece
    int vertexCap;
} AssOverlayGl;

/**
 * \brief Builds the shader program and reads the context's limits.
 *
 * Whether it succeeds or not, assOverlayGlRelease frees what it created.
 * \param gl GL state to set up, zero-initialized
 * \return false if the shader program can't be built or a GL call fails
 */
bool assOverlayGlInit(AssOverlayGl* gl);

/**
 * \brief Frees the GL objects and arrays. The AssOverlayGl struct itself belongs to the caller.
 * \param gl GL state to free, passed to assOverlayGlInit before
 */
void assOverlayGlRelease(AssOverlayGl* gl);

/**
 * \brief Uploads every atlas page into its texture.
 *
 * Page i goes into textures[i], which is (re)allocated first if it's missing or too small.
 * \param gl GL state holding the textures
 * \param atlas atlas whose pages to upload
 * \return how many pages were uploaded, from the first on: fewer than the atlas has only when CPU or
 * GPU memory runs out
 */
int assOverlayGlUpload(AssOverlayGl* gl, const AssAtlas* atlas);

/**
 * \brief Rebuilds the quads that draw the atlas pieces onto a frameW x frameH frame.
 *
 * Texture coordinates are relative to the page textures' sizes, so the pages must be uploaded first.
 * \param gl GL state holding the vertex buffer
 * \param atlas atlas whose pieces to draw
 * \param frameW video frame width, in pixels
 * \param frameH video frame height, in pixels
 * \return false on OOM, or if the vertex buffer can't be filled: the quads can't be drawn then
 */
bool assOverlayGlSetQuads(AssOverlayGl* gl, const AssAtlas* atlas, int frameW, int frameH);

/**
 * \brief Draws the quads onto `fbo`, one call per page, then restores the GL state media3 expects.
 *
 * Errors the draw raises are logged and cleared, so they don't fail playback.
 * \param gl GL state holding the program, textures and quads
 * \param atlas atlas the quads were built from
 * \param fbo GL framebuffer wrapping the video frame's texture
 * \param frameW video frame width, in pixels
 * \param frameH video frame height, in pixels
 */
void assOverlayGlDraw(const AssOverlayGl* gl, const AssAtlas* atlas, GLuint fbo, int frameW, int frameH);

#ifdef __cplusplus
}
#endif

#endif // LIBASS_ANDROID_ASS_OVERLAY_GL_H
