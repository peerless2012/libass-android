#include <gtest/gtest.h>

#include <cstdint>
#include <memory>
#include <vector>

#include "AssGlTest.h"
#include "AssOverlayGl.h"
#include "AssTestImages.h"

namespace {

// Runs once per upload path: true for GLES3's (R8 textures, PBO uploads), false for GLES2's (ALPHA
// textures, client-memory uploads). A GLES3 context runs both.
class AssOverlayGlTest : public AssGlTest, public testing::WithParamInterface<bool> {
protected:
    void SetUp() override {
        AssGlTest::SetUp();
        if (IsSkipped() || HasFatalFailure()) {
            return;
        }
        ASSERT_TRUE(assOverlayGlInit(&gl));
        if (GetParam() && !gl.isGles3) {
            GTEST_SKIP() << "GLES2 context";
        }
        gl.isGles3 = GetParam();
        atlas.maxPageSize = gl.maxTextureSize;
    }

    void TearDown() override {
        frame.reset();
        assOverlayGlRelease(&gl);
        assAtlasFree(&atlas);
    }

    // What AssOverlay.c does with a frame's images: packs them, uploads the pages, then draws them onto
    // a new frameW x frameH video frame. Returns the frame, top row first.
    std::vector<Rgba> draw(ImageList& images, int frameW, int frameH) {
        frame = std::make_unique<VideoFrame>(frameW, frameH);
        assAtlasPack(&atlas, images.head());
        EXPECT_EQ(assOverlayGlUpload(&gl, &atlas), atlas.pageCount);
        EXPECT_TRUE(assOverlayGlSetQuads(&gl, &atlas, frameW, frameH));
        assOverlayGlDraw(&gl, &atlas, frame->fbo(), frameW, frameH);
        EXPECT_EQ(glGetError(), (GLenum) GL_NO_ERROR);
        return frame->read();
    }

    AssOverlayGl gl{};
    AssAtlas atlas{};
    // The last video frame drawn.
    std::unique_ptr<VideoFrame> frame;
};

} // namespace

// Each image lands at its libass position, upright, with its own color and coverage, clipped to the
// frame.
TEST_P(AssOverlayGlTest, DrawsImagesWhereLibassPlacesThem) {
    const Bitmap a = makeBitmap(10, 6, 1);
    const Bitmap b = makeBitmap(7, 9, 2, 12);
    const Bitmap c = makeBitmap(8, 12, 3);
    ImageList images;
    images.add(a, 5, 3, 0xFF000000); // opaque red
    images.add(b, 30, 20, 0x00FF0080); // half transparent green
    images.add(c, -3, 40, 0x2040E010); // past the left and bottom edges

    expectFrame(draw(images, 64, 48), expectedFrame(images.head(), 64, 48), 64);
}

// Placement goes tallest first, but drawing goes in list order: a later image blends over an earlier
// one.
TEST_P(AssOverlayGlTest, BlendsImagesInListOrder) {
    const Bitmap low = makeBitmap(20, 4, 1);
    const Bitmap tall = makeBitmap(6, 20, 2);
    ImageList images;
    images.add(low, 10, 10, 0xFF000060);
    images.add(tall, 15, 5, 0x0000FF60);

    expectFrame(draw(images, 40, 30), expectedFrame(images.head(), 40, 30), 40);
}

// Every coverage at every opacity, first over the background, then over what the first pass drew: the
// GPU must draw exactly what the 8-bit integer math does.
TEST_P(AssOverlayGlTest, BlendsEveryCoverageAtEveryOpacityExactly) {
    const Bitmap ramp = makeBitmap(256, 1, 0); // coverage x at column x
    const Bitmap shiftedRamp = makeBitmap(256, 1, 4); // coverage x + 124
    ImageList images;
    for (uint32_t y = 0; y < 256; y++) {
        images.add(ramp, 0, (int) y, y << 24 | (255 - y) << 16 | (y * 37 & 0xFF) << 8 | y);
    }
    for (uint32_t y = 0; y < 256; y++) {
        // 91 and 53 are odd: each pass still has every transparency once.
        images.add(shiftedRamp, 0, (int) y, (y * 53 & 0xFF) << 24 | (y ^ 0xA5) << 16 | (255 - y) << 8 | (y * 91 & 0xFF));
    }

    expectFrame(draw(images, 256, 256), expectedFrame(images.head(), 256, 256), 256);
}

// One draw per page, each with its own texture, in list order: the images overlap across pages.
TEST_P(AssOverlayGlTest, DrawsEveryPage) {
    std::vector<Bitmap> bitmaps;
    for (int i = 0; i < 9; i++) {
        bitmaps.push_back(makeBitmap(8, 8, i));
    }
    ImageList images;
    for (int i = 0; i < 9; i++) {
        images.add(bitmaps[i], i * 3, i * 2, 0x10E03040u + (uint32_t) i * 0x18000000u);
    }
    atlas.maxPageSize = 16; // four 8x8 pieces per page

    const std::vector<Rgba> drawn = draw(images, 40, 30);
    EXPECT_EQ(atlas.pageCount, 3);
    expectFrame(drawn, expectedFrame(images.head(), 40, 30), 40);
}

// A repeat samples its copy's texels, with its own position and color.
TEST_P(AssOverlayGlTest, DrawsRepeatsFromTheirCopy) {
    const Bitmap a = makeBitmap(8, 8, 1);
    const Bitmap b = makeBitmap(8, 8, 2);
    ImageList images;
    images.add(a, 2, 2, 0xFF000000);
    images.add(b, 12, 2, 0x00FF0000);
    images.add(a, 22, 2, 0x0000FF40);

    const std::vector<Rgba> drawn = draw(images, 32, 12);
    ASSERT_EQ(atlas.pieceCount, 3);
    EXPECT_EQ(atlas.pieces[2].sameAs, 0);
    expectFrame(drawn, expectedFrame(images.head(), 32, 12), 32);
}

// A page that fits its texture reuses it, and one that doesn't gets a larger one. Either way the
// quads sample the texture as it is, whatever an earlier frame left in it.
TEST_P(AssOverlayGlTest, ReusesAndGrowsTextures) {
    const Bitmap first = makeBitmap(10, 10, 1);
    ImageList firstImages;
    firstImages.add(first, 4, 4, 0xFFFFFF00);
    expectFrame(draw(firstImages, 320, 20), expectedFrame(firstImages.head(), 320, 20), 320);
    const GLuint texture = gl.textures[0].id;
    EXPECT_EQ(gl.textures[0].capW, 256);
    EXPECT_EQ(gl.textures[0].capH, 256);

    const Bitmap smaller = makeBitmap(20, 8, 2);
    ImageList smallerImages;
    smallerImages.add(smaller, 100, 6, 0x80FF4020);
    expectFrame(draw(smallerImages, 320, 20), expectedFrame(smallerImages.head(), 320, 20), 320);
    EXPECT_EQ(gl.textures[0].id, texture);
    EXPECT_EQ(gl.textures[0].capW, 256);
    EXPECT_EQ(gl.textures[0].capH, 256);

    const Bitmap wider = makeBitmap(300, 4, 3);
    ImageList widerImages;
    widerImages.add(wider, 10, 8, 0x4080FF00);
    expectFrame(draw(widerImages, 320, 20), expectedFrame(widerImages.head(), 320, 20), 320);
    EXPECT_EQ(gl.textures[0].capW, 512); // 300 plus 1/8, rounded up to 256
    EXPECT_EQ(gl.textures[0].capH, 256);
}

// A frame with fewer pages keeps the spare textures, and a later frame with more pages reuses them,
// whatever an earlier frame left in them.
TEST_P(AssOverlayGlTest, ReusesSpareTextures) {
    std::vector<Bitmap> bitmaps;
    for (int i = 0; i < 19; i++) {
        bitmaps.push_back(makeBitmap(8, 8, i));
    }
    ImageList threePages;
    for (int i = 0; i < 9; i++) {
        threePages.add(bitmaps[i], i * 3, i * 2, 0x10E03040u + (uint32_t) i * 0x18000000u);
    }
    ImageList onePage;
    onePage.add(bitmaps[9], 20, 10, 0xFF804000);
    ImageList otherThreePages;
    for (int i = 0; i < 9; i++) {
        otherThreePages.add(bitmaps[10 + i], 30 - i * 3, i * 2, 0x40E0A020u + (uint32_t) i * 0x00180000u);
    }
    atlas.maxPageSize = 16; // four 8x8 pieces per page

    expectFrame(draw(threePages, 40, 30), expectedFrame(threePages.head(), 40, 30), 40);
    ASSERT_EQ(atlas.pageCount, 3);
    const std::vector<GLuint> textures{gl.textures[0].id, gl.textures[1].id, gl.textures[2].id};

    expectFrame(draw(onePage, 40, 30), expectedFrame(onePage.head(), 40, 30), 40);
    EXPECT_EQ(atlas.pageCount, 1);

    expectFrame(draw(otherThreePages, 40, 30), expectedFrame(otherThreePages.head(), 40, 30), 40);
    EXPECT_EQ(atlas.pageCount, 3);
    EXPECT_EQ((std::vector<GLuint>{gl.textures[0].id, gl.textures[1].id, gl.textures[2].id}), textures);
}

// A page texture that can't be allocated isn't counted as uploaded, and its GL error doesn't reach
// media3, whose next GlUtil.checkGlError() would fail playback. Going past GL_MAX_TEXTURE_SIZE fails
// like running out of GPU memory does. The next frame allocates the texture again.
TEST_P(AssOverlayGlTest, HandlesAPageTextureThatCantBeAllocated) {
    const int maxTextureSize = gl.maxTextureSize;
    gl.maxTextureSize = maxTextureSize * 2;
    atlas.maxPageSize = gl.maxTextureSize;
    const Bitmap tooWide = makeBitmap(maxTextureSize + 1, 1, 1);
    ImageList tooWideImages;
    tooWideImages.add(tooWide);
    assAtlasPack(&atlas, tooWideImages.head());
    ASSERT_EQ(atlas.pageCount, 1);

    EXPECT_EQ(assOverlayGlUpload(&gl, &atlas), 0);
    EXPECT_EQ(glGetError(), (GLenum) GL_NO_ERROR);
    EXPECT_EQ(gl.textures[0].id, 0u);

    gl.maxTextureSize = maxTextureSize;
    atlas.maxPageSize = maxTextureSize;
    const Bitmap a = makeBitmap(8, 8, 2);
    ImageList images;
    images.add(a, 2, 2, 0xFF000000);
    expectFrame(draw(images, 16, 16), expectedFrame(images.head(), 16, 16), 16);
}

// media3's own shader programs run right after the overlay, in the same context.
TEST_P(AssOverlayGlTest, LeavesTheGlStateMedia3Expects) {
    const Bitmap a = makeBitmap(8, 8, 1);
    ImageList images;
    images.add(a, 2, 2, 0xFF000000);
    GLuint previousFbo;
    glGenFramebuffers(1, &previousFbo);
    GLint suiteAlignment;
    glGetIntegerv(GL_UNPACK_ALIGNMENT, &suiteAlignment);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 8);

    draw(images, 16, 16);
    glBindFramebuffer(GL_FRAMEBUFFER, previousFbo);
    assOverlayGlDraw(&gl, &atlas, frame->fbo(), 16, 16);

    GLint value = -1;
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &value);
    EXPECT_EQ(value, (GLint) previousFbo);
    EXPECT_FALSE(glIsEnabled(GL_BLEND));
    glGetIntegerv(GL_CURRENT_PROGRAM, &value);
    EXPECT_EQ(value, 0);
    glGetIntegerv(GL_ARRAY_BUFFER_BINDING, &value);
    EXPECT_EQ(value, 0);
    glGetIntegerv(GL_TEXTURE_BINDING_2D, &value);
    EXPECT_EQ(value, 0);
    for (const GLint attrib : {gl.aPosition, gl.aTexCoord, gl.aColor}) {
        glGetVertexAttribiv((GLuint) attrib, GL_VERTEX_ATTRIB_ARRAY_ENABLED, &value);
        EXPECT_EQ(value, GL_FALSE) << "attribute " << attrib;
    }
    if (GetParam()) {
        // Left bound, it would turn media3's client-memory uploads into PBO offsets.
        glGetIntegerv(GL_PIXEL_UNPACK_BUFFER_BINDING, &value);
        EXPECT_EQ(value, 0);
    }
    glGetIntegerv(GL_UNPACK_ALIGNMENT, &value);
    EXPECT_EQ(value, 8);
    EXPECT_EQ(glGetError(), (GLenum) GL_NO_ERROR);

    glPixelStorei(GL_UNPACK_ALIGNMENT, suiteAlignment);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glDeleteFramebuffers(1, &previousFbo);
}

INSTANTIATE_TEST_SUITE_P(, AssOverlayGlTest, testing::Bool(), [](const testing::TestParamInfo<bool>& info) {
    return info.param ? "Gles3" : "Gles2";
});
