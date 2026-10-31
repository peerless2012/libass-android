#ifndef LIBASS_ANDROID_ASS_GL_TEST_H
#define LIBASS_ANDROID_ASS_GL_TEST_H

#include <gtest/gtest.h>

#include <cstdint>
#include <ostream>
#include <vector>

#include "GLES3/gl3.h"
#include "ass/ass.h"

// What the tests that draw share: a headless GLES context, the video frame they draw onto, and what
// the overlay must draw, composited on the CPU.

struct Rgba {
    uint8_t r, g, b, a;
};

std::ostream& operator<<(std::ostream& os, const Rgba& c);

// The video frame before the subtitles go on it.
constexpr Rgba kBackground{40, 80, 120, 255};

// What the overlay must draw: each image in list order, its coverage times its opacity blended "over"
// what is under it, in 8-bit integers as the shader computes them. Top row first, as libass's rows.
std::vector<Rgba> expectedFrame(const ASS_Image* images, int frameW, int frameH);

// Expects every pixel to be exactly the expected one.
void expectFrame(const std::vector<Rgba>& actual, const std::vector<Rgba>& expected, int frameW);

// The video frame: an RGBA texture of kBackground wrapped in an FBO, as media3 hands it over.
class VideoFrame {
public:
    VideoFrame(int width, int height);
    ~VideoFrame();
    VideoFrame(const VideoFrame&) = delete;
    VideoFrame& operator=(const VideoFrame&) = delete;

    GLuint fbo() const {
        return fbo_;
    }

    // The frame as it is now, top row first.
    std::vector<Rgba> read() const;

private:
    int width_, height_;
    GLuint texture_ = 0;
    GLuint fbo_ = 0;
};

// A test that draws, on a headless GLES context shared by its test suite: on
// EGL_MESA_platform_surfaceless when the EGL has it, as it needs no display server, else on the
// default display. Each test then runs on whatever GPU that picks; LIBGL_ALWAYS_SOFTWARE=1 picks
// Mesa's llvmpipe.
//
// Without a context, the tests are skipped, or fail when ASS_OVERLAY_REQUIRE_GL is set: in CI, a
// missing context is a broken setup, not a machine without a GPU.
class AssGlTest : public testing::Test {
protected:
    static void SetUpTestSuite();
    static void TearDownTestSuite();
    // Derived fixtures call it first, and return if it skipped or failed the test.
    void SetUp() override;
};

#endif // LIBASS_ANDROID_ASS_GL_TEST_H
