#include <gtest/gtest.h>

#include <cstdarg>
#include <cstdio>
#include <string>

#include "AssGlTest.h"
#include "AssOverlay.h"

namespace {

// Two drawings, in script coordinates that are the frame's pixels at 64x48, each drawn as an outline
// and a fill image: a triangle shown from 1s to 2s, and a half transparent square shown from 1s to 3s
// that moves 32 pixels right over its first second.
constexpr const char* kScript = R"([Script Info]
ScriptType: v4.00+
PlayResX: 64
PlayResY: 48
ScaledBorderAndShadow: yes

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,sans-serif,20,&H000000FF,&H000000FF,&H00FF0000,&H00000000,0,0,0,0,100,100,0,0,1,1,0,7,0,0,0,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,{\pos(10,8)\p1}m 0 0 l 20 0 0 12{\p0}
Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,{\move(4,30,36,30,0,1000)\1a&H80&\p1}m 0 0 l 8 0 8 8 0 8{\p0}
)";

// A scene of what tracks are made of, in script coordinates that are the frame's pixels at 320x180:
// lines that fade or come and go, a sign shown twice at once (libass hands over the same bitmap for
// both), a drawing that turns and grows to the frame's width, past the first page texture's size, a
// blurred and clipped one that moves, and from 3s on a line that stays still.
constexpr const char* kScene = R"([Script Info]
ScriptType: v4.00+
PlayResX: 320
PlayResY: 180
ScaledBorderAndShadow: yes
WrapStyle: 2

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,sans-serif,24,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,2,1,2,8,8,8,1
Style: Sign,sans-serif,16,&H0000FFFF,&H000000FF,&H00400000,&H00000000,1,0,0,0,100,100,0,0,1,1,0,7,0,0,0,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:00.00,0:00:01.50,Default,,0,0,0,,{\fad(300,300)}Hello there
Dialogue: 0,0:00:01.20,0:00:03.00,Default,,0,0,0,,Two lines\Nat once
Dialogue: 0,0:00:00.50,0:00:02.50,Sign,,0,0,0,,{\pos(20,20)}Sign
Dialogue: 0,0:00:00.50,0:00:02.50,Sign,,0,0,0,,{\pos(200,20)}Sign
Dialogue: 1,0:00:00.00,0:00:03.00,Sign,,0,0,0,,{\pos(160,90)\an5\t(0,3000,\fscx2500\fscy2500\frz30\1c&HFF00FF&)\p1}m 0 0 l 12 0 12 8 0 8{\p0}
Dialogue: 2,0:00:00.00,0:00:03.00,Sign,,0,0,0,,{\move(0,120,280,120)\blur2\clip(40,100,240,160)\p1}m 0 0 l 40 0 20 32{\p0}
Dialogue: 0,0:00:02.50,0:00:04.00,Default,,0,0,0,,The end
)";

// libass's warnings and errors, as its default callback would print them; not its chatter.
void onLibassMessage(int level, const char* format, va_list args, void*) {
    if (level > 2) {
        return;
    }
    fputs("libass: ", stderr);
    vfprintf(stderr, format, args);
    fputc('\n', stderr);
}

// Where the subtitles are expected, but libass rendered none, it most likely found no font.
constexpr const char* kNoImages = "libass rendered no images: no font?";

// What a draw showed.
struct Drawn {
    // ass_render_frame's detect_change: 0 if nothing changed since the previous draw, 1 if only
    // positions did, 2 if the content did
    int changed;
    // how many images libass rendered
    int images;
};

// Draws a track with assOverlayDraw, as AssRender.kt does for each video frame.
class AssOverlayTest : public AssGlTest {
protected:
    void SetUp() override {
        AssGlTest::SetUp();
        if (IsSkipped() || HasFatalFailure()) {
            return;
        }
        library = ass_library_init();
        ASSERT_NE(library, nullptr);
        ass_set_message_cb(library, onLibassMessage, nullptr);
        renderer = newRenderer();
        mirror = newRenderer();
        readTrack(kScript);
    }

    void TearDown() override {
        assOverlayRelease(overlay); // its GL objects, while the context is current
        if (track != nullptr) {
            ass_free_track(track);
        }
        if (mirror != nullptr) {
            ass_renderer_done(mirror);
        }
        if (renderer != nullptr) {
            ass_renderer_done(renderer);
        }
        if (library != nullptr) {
            ass_library_done(library);
        }
    }

    // Replaces the track, before the first draw.
    void readTrack(const char* script) {
        if (track != nullptr) {
            ass_free_track(track);
        }
        std::string text = script;
        track = ass_read_memory(library, text.data(), text.size(), nullptr);
        ASSERT_NE(track, nullptr);
    }

    // Draws the track at timeMs onto a new frameW x frameH video frame, and expects the frame to show
    // what libass renders for it.
    Drawn draw(long long timeMs, int frameW = 64, int frameH = 48) {
        const VideoFrame frame(frameW, frameH);
        overlay = assOverlayDraw(overlay, renderer, track, frame.fbo(), frameW, frameH, timeMs);
        EXPECT_NE(overlay, nullptr);
        EXPECT_EQ(glGetError(), (GLenum) GL_NO_ERROR);

        // The mirror gets the very calls the overlay makes to its renderer, so it renders the same
        // images, and reports the same change.
        if (frameW != mirrorW || frameH != mirrorH) {
            ass_set_frame_size(mirror, frameW, frameH);
            mirrorW = frameW;
            mirrorH = frameH;
        }
        Drawn drawn{-1, 0};
        const ASS_Image* images = ass_render_frame(mirror, track, timeMs, &drawn.changed);
        for (const ASS_Image* img = images; img != nullptr; img = img->next) {
            drawn.images++;
        }
        expectFrame(frame.read(), expectedFrame(images, frameW, frameH), frameW);
        return drawn;
    }

    ASS_Library* library = nullptr;
    // The overlay's renderer, and one that renders the same frames for the tests to compare with.
    ASS_Renderer* renderer = nullptr;
    ASS_Renderer* mirror = nullptr;
    int mirrorW = 0, mirrorH = 0;
    ASS_Track* track = nullptr;
    AssOverlay* overlay = nullptr;

private:
    ASS_Renderer* newRenderer() {
        ASS_Renderer* r = ass_renderer_init(library);
        EXPECT_NE(r, nullptr);
        if (r != nullptr) {
            ass_set_fonts(r, nullptr, "sans-serif", ASS_FONTPROVIDER_AUTODETECT, nullptr, 0);
        }
        return r;
    }
};

} // namespace

// Each frame shows the subtitles at its time, if any.
TEST_F(AssOverlayTest, DrawsTheTrackAtTheGivenTime) {
    EXPECT_EQ(draw(500).images, 0);
    EXPECT_GT(draw(1000).images, 0) << kNoImages;
    EXPECT_GT(draw(2500).images, 0) << kNoImages;
}

// The first draw creates the overlay, and later ones reuse it.
TEST_F(AssOverlayTest, KeepsItsOverlay) {
    draw(1000);
    const AssOverlay* first = overlay;
    ASSERT_NE(first, nullptr);
    draw(1500);
    EXPECT_EQ(overlay, first);
}

// When libass reports no change, the overlay draws the atlas it already has: each draw is onto a new
// video frame, without the subtitles on it.
TEST_F(AssOverlayTest, RedrawsUnchangedSubtitlesOnEveryFrame) {
    ASSERT_GT(draw(1000).images, 0) << kNoImages;
    for (int i = 0; i < 3; i++) {
        EXPECT_EQ(draw(1000).changed, 0);
    }
}

// When libass reports that only positions changed, the subtitles move.
TEST_F(AssOverlayTest, FollowsMovingSubtitles) {
    ASSERT_GT(draw(1000).images, 0) << kNoImages;
    EXPECT_EQ(draw(1500).changed, 1);
}

// When the subtitles change, the new ones replace them.
TEST_F(AssOverlayTest, FollowsChangingSubtitles) {
    ASSERT_GT(draw(1000).images, 0) << kNoImages;
    const Drawn drawn = draw(2500); // the triangle is gone
    EXPECT_EQ(drawn.changed, 2);
    EXPECT_GT(drawn.images, 0);
}

// Once the events end, libass reports a change to no images: nothing is drawn, then or on the next
// unchanged frame.
TEST_F(AssOverlayTest, ClearsSubtitlesWhenEventsEnd) {
    ASSERT_GT(draw(2500).images, 0) << kNoImages;
    const Drawn ended = draw(3500);
    EXPECT_EQ(ended.changed, 2);
    EXPECT_EQ(ended.images, 0);
    EXPECT_EQ(draw(3600).changed, 0);
}

// A new frame size goes to libass, which renders the subtitles for it, and the quads are laid out
// for it, growing or shrinking.
TEST_F(AssOverlayTest, FollowsFrameSizeChanges) {
    ASSERT_GT(draw(1000, 64, 48).images, 0) << kNoImages;
    EXPECT_EQ(draw(1000, 128, 96).changed, 2);
    EXPECT_EQ(draw(1000, 40, 30).changed, 2);
}

// Without a renderer or a track, the overlay is still created, for the next call, but draws nothing.
TEST_F(AssOverlayTest, DrawsNothingWithoutARendererOrATrack) {
    const VideoFrame frame(64, 48);
    overlay = assOverlayDraw(nullptr, nullptr, track, frame.fbo(), 64, 48, 1000);
    ASSERT_NE(overlay, nullptr);
    EXPECT_EQ(assOverlayDraw(overlay, renderer, nullptr, frame.fbo(), 64, 48, 1000), overlay);
    EXPECT_EQ(glGetError(), (GLenum) GL_NO_ERROR);
    expectFrame(frame.read(), expectedFrame(nullptr, 64, 48), 64);

    // It draws once it has both.
    EXPECT_GT(draw(1000).images, 0) << kNoImages;
}

// The scene drawn frame by frame at 23.976 fps, as a video plays.
TEST_F(AssOverlayTest, PlaysAScene) {
    readTrack(kScene);
    int shown = 0;
    int shownUnchanged = 0;
    for (long long frame = 0;; frame++) {
        const long long timeMs = frame * 1001 / 24;
        if (timeMs > 4200) {
            break;
        }
        SCOPED_TRACE(testing::Message() << "frame " << frame << ", at " << timeMs << " ms");
        const Drawn drawn = draw(timeMs, 320, 180);
        if (drawn.images > 0) {
            shown++;
            if (drawn.changed == 0) {
                shownUnchanged++;
            }
        }
        if (HasFailure()) {
            return;
        }
    }
    EXPECT_GT(shown, 0) << kNoImages;
    EXPECT_GT(shownUnchanged, 0) << "the line from 3s on doesn't stay still";
}

TEST(AssOverlay, ReleaseTakesNull) {
    assOverlayRelease(nullptr);
}
