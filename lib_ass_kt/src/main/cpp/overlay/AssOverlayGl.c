#include "AssOverlayGl.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "AssOverlayLog.h"

// Page texture capacities are rounded up to a multiple of this.
#define TEXTURE_GROW_STEP 256
// Each piece is a quad made of two triangles.
#define VERTICES_PER_PIECE 6
// glGetError returns one error flag per call, and an implementation can have several.
#define MAX_GL_ERRORS 8

// One draw call covers many pieces, so their colors travel as a vertex attribute. The output is
// premultiplied by opacity and coverage, for a premultiplied "over" blend onto the video frame.
static const char* kVertexShader =
    "attribute vec2 a_Position;\n"
    "attribute vec2 a_TexCoord;\n"
    "attribute vec4 a_Color;\n"
    "varying vec2 v_TexCoord;\n"
    "varying vec4 v_Color;\n"
    "void main() {\n"
    "    gl_Position = vec4(a_Position, 0.0, 1.0);\n"
    "    v_TexCoord = a_TexCoord;\n"
    "    v_Color = a_Color;\n"
    "}\n";

// The output is in whole 8-bit steps, alpha and color each rounded once, so the GPU's conversion to
// the frame's 8 bits is exact. The blend's only rounding is then dst * (255 - alpha) / 255, which is
// never halfway between two steps (255 is odd): every GPU that rounds to nearest writes the same byte.
static const char* kFragmentShader =
    // highp: mediump is fp16 on most mobile GPUs. It can't hold the 16-bit products below, nor address
    // single texels of a large page (UVs in [0.5, 1) of a 4096 page step by 2 texels): pieces would
    // sample their neighbors. Samplers default to lowp, which may not tell two 8-bit values apart.
    "#ifdef GL_FRAGMENT_PRECISION_HIGH\n"
    "precision highp float;\n"
    "precision highp sampler2D;\n"
    "#else\n"
    "precision mediump float;\n"
    "precision mediump sampler2D;\n"
    "#endif\n"
    "uniform sampler2D u_Texture;\n"
    "varying vec2 v_TexCoord;\n"
    "varying vec4 v_Color;\n"
    // Back to the 8-bit integer that x was made from.
    "vec4 bytes(vec4 x) {\n"
    "    return floor(x * 255.0 + 0.5);\n"
    "}\n"
    "void main() {\n"
    "    vec4 color = bytes(v_Color);\n"
    "    float coverage = bytes(texture2D(u_Texture, v_TexCoord)).a;\n"
    "    float a = floor(color.a * coverage / 255.0 + 0.5);\n"
    "    gl_FragColor = vec4(floor(color.rgb * a / 255.0 + 0.5), a) / 255.0;\n"
    "}\n";

/**
 * \brief Reads and clears the GL errors raised since the last call, logging each.
 *
 * The overlay handles its own errors: left pending, they'd make media3's next GlUtil.checkGlError()
 * throw and fail playback.
 * \param what what the errors come from, for the log
 * \return true if there was none
 */
static bool clearGlErrors(const char* what) {
    bool none = true;
    for (int i = 0; i < MAX_GL_ERRORS; i++) {
        const GLenum error = glGetError();
        if (error == GL_NO_ERROR) {
            break;
        }
        LOGE("%s: GL error 0x%x", what, error);
        none = false;
    }
    return none;
}

/**
 * \brief Compiles a shader, logging the compile error if any.
 * \param type GL_VERTEX_SHADER or GL_FRAGMENT_SHADER
 * \param source GLSL source
 * \return the shader, or 0 on failure
 */
static GLuint compileShader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    if (shader == 0) {
        LOGE("glCreateShader(0x%x) failed: GL error 0x%x", type, glGetError());
        return 0;
    }

    glShaderSource(shader, 1, &source, NULL);
    glCompileShader(shader);
    GLint status = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &status);
    if (status != GL_TRUE) {
        char log[512];
        glGetShaderInfoLog(shader, sizeof(log), NULL, log);
        LOGE("shader compile error: %s", log);
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

/**
 * \brief Compiles both shaders and links them into a program, logging the link error if any.
 * \param vertexSource vertex shader GLSL source
 * \param fragmentSource fragment shader GLSL source
 * \return the program, or 0 on failure
 */
static GLuint linkProgram(const char* vertexSource, const char* fragmentSource) {
    GLuint vertexShader = compileShader(GL_VERTEX_SHADER, vertexSource);
    GLuint fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource);
    if (vertexShader == 0 || fragmentShader == 0) {
        if (vertexShader != 0) {
            glDeleteShader(vertexShader);
        }
        if (fragmentShader != 0) {
            glDeleteShader(fragmentShader);
        }
        return 0;
    }

    GLuint program = glCreateProgram();
    glAttachShader(program, vertexShader);
    glAttachShader(program, fragmentShader);
    glLinkProgram(program);
    GLint status = GL_FALSE;
    glGetProgramiv(program, GL_LINK_STATUS, &status);
    glDeleteShader(vertexShader);
    glDeleteShader(fragmentShader);
    if (status != GL_TRUE) {
        char log[512];
        glGetProgramInfoLog(program, sizeof(log), NULL, log);
        LOGE("program link error: %s", log);
        glDeleteProgram(program);
        return 0;
    }
    return program;
}

bool assOverlayGlInit(AssOverlayGl* gl) {
    glGetIntegerv(GL_MAX_TEXTURE_SIZE, &gl->maxTextureSize);
    // GLES guarantees GL_VERSION starts with "OpenGL ES <major>.<minor>".
    const char* version = (const char*) glGetString(GL_VERSION);
    int major = 0;
    if (version != NULL) {
        sscanf(version, "OpenGL ES %d", &major);
    }
    gl->isGles3 = major >= 3;
    LOGI("GL_VERSION=%s, max texture size %d, %s path",
        version != NULL ? version : "?", gl->maxTextureSize, gl->isGles3 ? "GLES3 PBO/R8" : "GLES2");

    gl->program = linkProgram(kVertexShader, kFragmentShader);
    if (gl->program == 0) {
        LOGE("linkProgram failed");
        return false;
    }

    gl->aPosition = glGetAttribLocation(gl->program, "a_Position");
    gl->aTexCoord = glGetAttribLocation(gl->program, "a_TexCoord");
    gl->aColor = glGetAttribLocation(gl->program, "a_Color");
    // Page textures are always bound to unit 0.
    glUseProgram(gl->program);
    glUniform1i(glGetUniformLocation(gl->program, "u_Texture"), 0);
    glUseProgram(0);
    glGenBuffers(1, &gl->vbo);
    return clearGlErrors("init");
}

void assOverlayGlRelease(AssOverlayGl* gl) {
    for (int i = 0; i < gl->textureCap; i++) {
        if (gl->textures[i].id != 0) {
            glDeleteTextures(1, &gl->textures[i].id);
        }
    }
    if (gl->program != 0) {
        glDeleteProgram(gl->program);
    }
    if (gl->vbo != 0) {
        glDeleteBuffers(1, &gl->vbo);
    }
    if (gl->pbo != 0) {
        glDeleteBuffers(1, &gl->pbo);
    }
    free(gl->textures);
    free(gl->staging);
    free(gl->vertices);
}

// --- Page textures -------------------------------------------------------------------------------

/**
 * \brief Makes room for page textures. Capacity at least doubles.
 *
 * New entries have no GL texture yet.
 * \param gl GL state to grow
 * \param count how many textures must fit
 * \return false on OOM
 */
static bool ensureTextures(AssOverlayGl* gl, int count) {
    if (count <= gl->textureCap) {
        return true;
    }
    int newCap = gl->textureCap > 0 ? gl->textureCap * 2 : 2;
    if (newCap < count) {
        newCap = count;
    }
    AssOverlayTexture* textures = (AssOverlayTexture*) realloc(gl->textures, (size_t) newCap * sizeof(AssOverlayTexture));
    if (textures == NULL) {
        return false;
    }
    memset(textures + gl->textureCap, 0, (size_t) (newCap - gl->textureCap) * sizeof(AssOverlayTexture));
    gl->textures = textures;
    gl->textureCap = newCap;
    return true;
}

/**
 * \brief Rounds value up to a multiple of step.
 * \param value value to round, non-negative
 * \param step multiple to round to, positive
 * \return the smallest multiple of step that is at least value
 */
static int roundUp(int value, int step) {
    return (value + step - 1) / step * step;
}

/**
 * \brief Binds the texture for `page`, (re)allocating it first if it's missing or too small.
 * \param gl GL state, for the max texture size and the texture format
 * \param texture texture to bind, its id and capacity updated if it is (re)allocated
 * \param page page the texture must hold
 * \return false if the texture can't be allocated, which leaves it deleted
 */
static bool bindPageTexture(const AssOverlayGl* gl, AssOverlayTexture* texture, const AssAtlasPage* page) {
    if (texture->id != 0 && page->usedW <= texture->capW && page->usedH <= texture->capH) {
        glBindTexture(GL_TEXTURE_2D, texture->id);
        return true;
    }
    // A dimension that overflows gets 1/8 headroom, so a page creeping up by a few rows doesn't
    // reallocate a multi-MB texture every time.
    int capW = texture->capW >= page->usedW ? texture->capW : page->usedW + page->usedW / 8;
    int capH = texture->capH >= page->usedH ? texture->capH : page->usedH + page->usedH / 8;
    capW = roundUp(capW, TEXTURE_GROW_STEP);
    capH = roundUp(capH, TEXTURE_GROW_STEP);
    if (capW > gl->maxTextureSize) {
        capW = gl->maxTextureSize;
    }
    if (capH > gl->maxTextureSize) {
        capH = gl->maxTextureSize;
    }
    if (texture->id != 0) {
        glDeleteTextures(1, &texture->id);
    }
    glGenTextures(1, &texture->id);
    glBindTexture(GL_TEXTURE_2D, texture->id);
    // GLES2 needs CLAMP_TO_EDGE for non-power-of-two textures, which otherwise sample as black.
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    // Quads are drawn 1:1, so every fragment samples a texel center: GL_NEAREST returns exactly that
    // texel, never one of a neighboring piece.
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    if (gl->isGles3) {
        // R8 is native everywhere, while several drivers emulate ALPHA by converting it on the CPU at
        // every upload. The swizzle keeps the shader's `.a` read working.
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_A, GL_RED);
        glTexStorage2D(GL_TEXTURE_2D, 1, GL_R8, capW, capH);
    } else {
        glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, capW, capH, 0, GL_ALPHA, GL_UNSIGNED_BYTE, NULL);
    }
    if (!clearGlErrors("page texture allocation")) {
        glDeleteTextures(1, &texture->id);
        *texture = (AssOverlayTexture) {0};
        return false;
    }
    texture->capW = capW;
    texture->capH = capH;
    return true;
}

// --- Uploads -------------------------------------------------------------------------------------

/**
 * \brief The pixel format of page uploads, matching the textures' internal format.
 * \param gl GL state
 * \return GL_RED on GLES3, GL_ALPHA on GLES2
 */
static GLenum textureFormat(const AssOverlayGl* gl) {
    return gl->isGles3 ? GL_RED : GL_ALPHA;
}

/**
 * \brief Makes the staging buffer at least `bytes` long, with 1/8 headroom.
 * \param gl GL state holding the staging buffer
 * \param bytes how many bytes must fit
 * \return false on OOM
 */
static bool ensureStaging(AssOverlayGl* gl, size_t bytes) {
    if (bytes <= gl->stagingCap) {
        return true;
    }
    size_t newCap = bytes + bytes / 8;
    unsigned char* staging = (unsigned char*) realloc(gl->staging, newCap);
    if (staging == NULL) {
        return false;
    }
    gl->staging = staging;
    gl->stagingCap = newCap;
    return true;
}

/**
 * \brief Uploads a page into the bound texture through the PBO. GLES3 only.
 *
 * Writes the page's bitmaps straight into the mapped PBO - the one unavoidable CPU copy - and lets
 * the driver move them into the texture GPU-side, asynchronously.
 * \param gl GL state holding the PBO
 * \param atlas atlas holding the page's pieces
 * \param page page to upload, its texture bound
 * \return false if the PBO can't be allocated or mapped, or the upload fails
 */
static bool uploadThroughPbo(AssOverlayGl* gl, const AssAtlas* atlas, const AssAtlasPage* page) {
    const size_t bytes = (size_t) page->usedW * page->usedH;
    if (gl->pbo == 0) {
        glGenBuffers(1, &gl->pbo);
    }
    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, gl->pbo);
    // Sized to the packed area rather than the texture's capacity, with 1/8 headroom: the first use of
    // new storage is the expensive part.
    if (gl->pboSize < bytes) {
        gl->pboSize = bytes + bytes / 8;
        glBufferData(GL_PIXEL_UNPACK_BUFFER, (GLsizeiptr) gl->pboSize, NULL, GL_STREAM_DRAW);
        if (!clearGlErrors("PBO allocation")) {
            gl->pboSize = 0;
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            return false;
        }
    }
    // Synchronized: if the previous upload from the buffer hasn't run yet, the driver waits for it.
    unsigned char* mapped = (unsigned char*) glMapBufferRange(GL_PIXEL_UNPACK_BUFFER, 0, (GLsizeiptr) bytes,
        GL_MAP_WRITE_BIT);
    bool uploaded = false;
    if (mapped != NULL) {
        assAtlasFillPage(atlas, page, mapped);
        if (glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER)) {
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, page->usedW, page->usedH, textureFormat(gl), GL_UNSIGNED_BYTE, (const void*) 0);
            uploaded = true;
        }
    }
    // Unbound before any client-memory upload, ours or media3's.
    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
    // A failed map raises an error too.
    const bool noError = clearGlErrors("PBO upload");
    return uploaded && noError;
}

/**
 * \brief Copies a page's bitmaps into the staging buffer and uploads them from there into the bound
 * texture.
 * \param gl GL state holding the staging buffer
 * \param atlas atlas holding the page's pieces
 * \param page page to upload, its texture bound
 * \return false on OOM, or if the upload fails
 */
static bool uploadFromStaging(AssOverlayGl* gl, const AssAtlas* atlas, const AssAtlasPage* page) {
    if (!ensureStaging(gl, (size_t) page->usedW * page->usedH)) {
        return false;
    }
    assAtlasFillPage(atlas, page, gl->staging);
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, page->usedW, page->usedH, textureFormat(gl), GL_UNSIGNED_BYTE, gl->staging);
    return clearGlErrors("page upload");
}

int assOverlayGlUpload(AssOverlayGl* gl, const AssAtlas* atlas) {
    if (!ensureTextures(gl, atlas->pageCount)) {
        return 0;
    }
    // Page rows are usedW bytes, unpadded. The previous alignment is restored for media3's own uploads.
    GLint previousAlignment;
    glGetIntegerv(GL_UNPACK_ALIGNMENT, &previousAlignment);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    int pageIdx = 0;
    for (; pageIdx < atlas->pageCount; pageIdx++) {
        const AssAtlasPage* page = &atlas->pages[pageIdx];
        const bool uploaded = bindPageTexture(gl, &gl->textures[pageIdx], page)
            && ((gl->isGles3 && uploadThroughPbo(gl, atlas, page)) || uploadFromStaging(gl, atlas, page));
        if (!uploaded) {
            break; // out of CPU or GPU memory
        }
    }
    glPixelStorei(GL_UNPACK_ALIGNMENT, previousAlignment);
    glBindTexture(GL_TEXTURE_2D, 0);
    return pageIdx;
}

// --- Quads ---------------------------------------------------------------------------------------

/**
 * \brief Splits a libass color into its channels, turning transparency into alpha.
 *
 * libass packs a color as 0xRRGGBBTT, TT being transparency (0 = opaque) rather than alpha.
 * \param color libass color
 * \param r receives the red channel
 * \param g receives the green channel
 * \param b receives the blue channel
 * \param a receives the alpha channel (0xFF = opaque)
 */
static void unpackColor(uint32_t color, uint8_t* r, uint8_t* g, uint8_t* b, uint8_t* a) {
    *r = (uint8_t) (color >> 24);
    *g = (uint8_t) (color >> 16);
    *b = (uint8_t) (color >> 8);
    *a = (uint8_t) (0xFF - (color & 0xFF));
}

/**
 * \brief Makes room for vertices. Capacity at least doubles.
 * \param gl GL state to grow
 * \param count how many vertices must fit
 * \return false on OOM
 */
static bool ensureVertices(AssOverlayGl* gl, int count) {
    if (count <= gl->vertexCap) {
        return true;
    }
    int newCap = gl->vertexCap > 0 ? gl->vertexCap * 2 : 64 * VERTICES_PER_PIECE;
    if (newCap < count) {
        newCap = count;
    }
    AssOverlayVertex* vertices = (AssOverlayVertex*) realloc(gl->vertices, (size_t) newCap * sizeof(AssOverlayVertex));
    if (vertices == NULL) {
        return false;
    }
    gl->vertices = vertices;
    gl->vertexCap = newCap;
    return true;
}

bool assOverlayGlSetQuads(AssOverlayGl* gl, const AssAtlas* atlas, int frameW, int frameH) {
    const int count = atlas->pieceCount * VERTICES_PER_PIECE;
    if (!ensureVertices(gl, count)) {
        return false;
    }
    const float sx = 2.0f / (float) frameW;
    const float sy = 2.0f / (float) frameH;
    AssOverlayVertex* out = gl->vertices;
    for (int pageIdx = 0; pageIdx < atlas->pageCount; pageIdx++) {
        const AssAtlasPage* page = &atlas->pages[pageIdx];
        const float su = 1.0f / (float) gl->textures[pageIdx].capW;
        const float sv = 1.0f / (float) gl->textures[pageIdx].capH;
        for (int i = page->firstPiece; i < page->firstPiece + page->pieceCount; i++) {
            const AssAtlasPiece* p = &atlas->pieces[i];
            // libass's y goes down, clip space's goes up.
            const float x0 = (float) p->dstX * sx - 1.0f;
            const float x1 = (float) (p->dstX + p->w) * sx - 1.0f;
            const float y0 = 1.0f - (float) p->dstY * sy;
            const float y1 = 1.0f - (float) (p->dstY + p->h) * sy;
            // w x h pixels sample w x h texels: each fragment hits a texel center.
            const float u0 = (float) p->atlasX * su;
            const float u1 = (float) (p->atlasX + p->w) * su;
            const float v0 = (float) p->atlasY * sv;
            const float v1 = (float) (p->atlasY + p->h) * sv;
            uint8_t r, g, b, a;
            unpackColor(p->color, &r, &g, &b, &a);
            out[0] = (AssOverlayVertex) {x0, y0, u0, v0, r, g, b, a};
            out[1] = (AssOverlayVertex) {x1, y0, u1, v0, r, g, b, a};
            out[2] = (AssOverlayVertex) {x0, y1, u0, v1, r, g, b, a};
            out[3] = out[1];
            out[4] = (AssOverlayVertex) {x1, y1, u1, v1, r, g, b, a};
            out[5] = out[2];
            out += VERTICES_PER_PIECE;
        }
    }
    glBindBuffer(GL_ARRAY_BUFFER, gl->vbo);
    // Storage is only respecified to grow, and otherwise updated in place rather than orphaned (on
    // ANGLE, orphaning is a new allocation every time). A draw still reading it is safe: the driver
    // keeps glBufferSubData from affecting commands already issued.
    if (gl->vboCap < gl->vertexCap) {
        glBufferData(GL_ARRAY_BUFFER, (GLsizeiptr) ((size_t) gl->vertexCap * sizeof(AssOverlayVertex)), NULL, GL_DYNAMIC_DRAW);
        gl->vboCap = gl->vertexCap;
    }
    glBufferSubData(GL_ARRAY_BUFFER, 0, (GLsizeiptr) ((size_t) count * sizeof(AssOverlayVertex)), gl->vertices);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    if (!clearGlErrors("quad upload")) {
        gl->vboCap = 0; // its storage is respecified next time
        return false;
    }
    return true;
}

// --- Draw ----------------------------------------------------------------------------------------

void assOverlayGlDraw(const AssOverlayGl* gl, const AssAtlas* atlas, GLuint fbo, int frameW, int frameH) {
    GLint previousFbo = 0;
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &previousFbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glViewport(0, 0, frameW, frameH);
    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA); // premultiplied "over"
    glUseProgram(gl->program);
    glBindBuffer(GL_ARRAY_BUFFER, gl->vbo);
    const GLsizei stride = sizeof(AssOverlayVertex);
    glEnableVertexAttribArray((GLuint) gl->aPosition);
    glVertexAttribPointer((GLuint) gl->aPosition, 2, GL_FLOAT, GL_FALSE, stride, (void*) offsetof(AssOverlayVertex, x));
    glEnableVertexAttribArray((GLuint) gl->aTexCoord);
    glVertexAttribPointer((GLuint) gl->aTexCoord, 2, GL_FLOAT, GL_FALSE, stride, (void*) offsetof(AssOverlayVertex, u));
    glEnableVertexAttribArray((GLuint) gl->aColor);
    glVertexAttribPointer((GLuint) gl->aColor, 4, GL_UNSIGNED_BYTE, GL_TRUE, stride, (void*) offsetof(AssOverlayVertex, r));
    glActiveTexture(GL_TEXTURE0);
    for (int i = 0; i < atlas->pageCount; i++) {
        const AssAtlasPage* page = &atlas->pages[i];
        glBindTexture(GL_TEXTURE_2D, gl->textures[i].id);
        glDrawArrays(GL_TRIANGLES, page->firstPiece * VERTICES_PER_PIECE, page->pieceCount * VERTICES_PER_PIECE);
    }

    // Leave the GL state the way media3's own shader programs expect it.
    glDisable(GL_BLEND);
    glDisableVertexAttribArray((GLuint) gl->aPosition);
    glDisableVertexAttribArray((GLuint) gl->aTexCoord);
    glDisableVertexAttribArray((GLuint) gl->aColor);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);
    glUseProgram(0);
    glBindFramebuffer(GL_FRAMEBUFFER, (GLuint) previousFbo);
    clearGlErrors("draw");
}
