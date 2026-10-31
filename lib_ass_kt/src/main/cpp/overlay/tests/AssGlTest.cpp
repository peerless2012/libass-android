#include "AssGlTest.h"

#include <EGL/egl.h>
#include <EGL/eglext.h>

#include <cstdlib>
#include <cstring>
#include <string>

// GPU drivers and the libraries they pull in, such as libdbus, leave allocations behind at exit, and so
// does fontconfig 2.15 when libass has it parse its config files. LeakSanitizer reads this when the
// build has ASan.
extern "C" const char* __lsan_default_suppressions() {
    return "leak:libdbus-1.so\n"
           "leak:libEGL_mesa.so\n"
           "leak:libEGL_nvidia.so\n"
           "leak:libfontconfig.so\n"
           "leak:libgallium\n"
           "leak:libLLVM\n"
           "leak:libnvidia-\n";
}

std::ostream& operator<<(std::ostream& os, const Rgba& c) {
    return os << "(" << (int) c.r << ", " << (int) c.g << ", " << (int) c.b << ", " << (int) c.a << ")";
}

std::vector<Rgba> expectedFrame(const ASS_Image* images, int frameW, int frameH) {
    // Round to nearest
    const auto divide255 = [](int x) {
        return (x + 127) / 255;
    };
    std::vector<Rgba> frame((size_t) frameW * frameH, kBackground);
    for (const ASS_Image* img = images; img != nullptr; img = img->next) {
        const int r = (int) (img->color >> 24 & 0xFF);
        const int g = (int) (img->color >> 16 & 0xFF);
        const int b = (int) (img->color >> 8 & 0xFF);
        const int opacity = 255 - (int) (img->color & 0xFF);
        for (int y = 0; y < img->h; y++) {
            for (int x = 0; x < img->w; x++) {
                const int fx = img->dst_x + x;
                const int fy = img->dst_y + y;
                if (fx < 0 || fx >= frameW || fy < 0 || fy >= frameH) {
                    continue;
                }
                const int a = divide255(opacity * img->bitmap[(size_t) y * img->stride + x]);
                Rgba& px = frame[(size_t) fy * frameW + fx];
                const auto over = [&](int src, uint8_t dst) {
                    return (uint8_t) (divide255(src * a) + divide255(dst * (255 - a)));
                };
                px = {over(r, px.r), over(g, px.g), over(b, px.b), over(255, px.a)};
            }
        }
    }
    return frame;
}

void expectFrame(const std::vector<Rgba>& actual, const std::vector<Rgba>& expected, int frameW) {
    ASSERT_EQ(actual.size(), expected.size());
    int mismatches = 0;
    for (size_t i = 0; i < actual.size(); i++) {
        const Rgba& p = actual[i];
        const Rgba& q = expected[i];
        if (p.r == q.r && p.g == q.g && p.b == q.b && p.a == q.a) {
            continue;
        }
        if (++mismatches <= 5) {
            ADD_FAILURE() << "pixel (" << i % frameW << ", " << i / frameW << ") is " << p << " instead of " << q;
        }
    }
    EXPECT_EQ(mismatches, 0) << "mismatching pixels";
}

VideoFrame::VideoFrame(int width, int height) : width_(width), height_(height) {
    glGenTextures(1, &texture_);
    glBindTexture(GL_TEXTURE_2D, texture_);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glBindTexture(GL_TEXTURE_2D, 0);
    glGenFramebuffers(1, &fbo_);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture_, 0);
    EXPECT_EQ(glCheckFramebufferStatus(GL_FRAMEBUFFER), (GLenum) GL_FRAMEBUFFER_COMPLETE);
    glClearColor(kBackground.r / 255.0f, kBackground.g / 255.0f, kBackground.b / 255.0f, kBackground.a / 255.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

VideoFrame::~VideoFrame() {
    glDeleteFramebuffers(1, &fbo_);
    glDeleteTextures(1, &texture_);
}

// GL's rows go up, so the frame's top row is the last one glReadPixels returns.
std::vector<Rgba> VideoFrame::read() const {
    std::vector<Rgba> rows((size_t) width_ * height_);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo_);
    glReadPixels(0, 0, width_, height_, GL_RGBA, GL_UNSIGNED_BYTE, rows.data());
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    std::vector<Rgba> frame((size_t) width_ * height_);
    for (int y = 0; y < height_; y++) {
        memcpy(&frame[(size_t) y * width_], &rows[(size_t) (height_ - 1 - y) * width_], (size_t) width_ * sizeof(Rgba));
    }
    return frame;
}

namespace {

EGLDisplay display = EGL_NO_DISPLAY;
EGLContext context = EGL_NO_CONTEXT;
std::string noContextReason;

void createContext() {
    const char* clientExtensions = eglQueryString(EGL_NO_DISPLAY, EGL_EXTENSIONS);
    if (clientExtensions != nullptr && strstr(clientExtensions, "EGL_MESA_platform_surfaceless") != nullptr) {
        display = eglGetPlatformDisplay(EGL_PLATFORM_SURFACELESS_MESA, EGL_DEFAULT_DISPLAY, nullptr);
    }
    if (display == EGL_NO_DISPLAY) {
        display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    }
    if (display == EGL_NO_DISPLAY || !eglInitialize(display, nullptr, nullptr)) {
        noContextReason = "no EGL display";
        return;
    }
    // The tests draw into their own FBO: the context needs no surface.
    if (strstr(eglQueryString(display, EGL_EXTENSIONS), "EGL_KHR_surfaceless_context") == nullptr) {
        noContextReason = "no EGL_KHR_surfaceless_context";
        return;
    }

    eglBindAPI(EGL_OPENGL_ES_API);
    const EGLint configAttribs[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_SURFACE_TYPE, 0, EGL_NONE};
    EGLConfig config;
    EGLint configCount = 0;
    if (!eglChooseConfig(display, configAttribs, &config, 1, &configCount) || configCount == 0) {
        noContextReason = "no GLES EGL config";
        return;
    }
    // GLES3 if there is one: it runs both upload paths.
    for (const EGLint version : {3, 2}) {
        const EGLint contextAttribs[] = {EGL_CONTEXT_CLIENT_VERSION, version, EGL_NONE};
        context = eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttribs);
        if (context != EGL_NO_CONTEXT) {
            break;
        }
    }
    if (context == EGL_NO_CONTEXT || !eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, context)) {
        noContextReason = "no GLES context";
        context = EGL_NO_CONTEXT;
    }
}

} // namespace

void AssGlTest::SetUpTestSuite() {
    createContext();
}

void AssGlTest::TearDownTestSuite() {
    if (display == EGL_NO_DISPLAY) {
        return;
    }
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    if (context != EGL_NO_CONTEXT) {
        eglDestroyContext(display, context);
    }
    eglTerminate(display);
    context = EGL_NO_CONTEXT;
    display = EGL_NO_DISPLAY;
}

void AssGlTest::SetUp() {
    if (context != EGL_NO_CONTEXT) {
        return;
    }
    if (getenv("ASS_OVERLAY_REQUIRE_GL") != nullptr) {
        FAIL() << noContextReason << ", and ASS_OVERLAY_REQUIRE_GL is set";
    }
    GTEST_SKIP() << noContextReason;
}
