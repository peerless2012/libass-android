#ifndef LIBASS_ANDROID_ASS_TEST_IMAGES_H
#define LIBASS_ANDROID_ASS_TEST_IMAGES_H

#include <cstddef>
#include <cstdint>
#include <deque>
#include <vector>

#include "ass/ass.h"

// Stand-ins for libass's output, shared by the tests.

// A w x h bitmap with rows stride bytes apart. Each texel's value depends on seed and its position,
// so a piece copied from the wrong place or with the wrong stride doesn't match.
struct Bitmap {
    int w, h, stride;
    std::vector<unsigned char> data;
};

inline Bitmap makeBitmap(int w, int h, int seed, int stride = 0) {
    Bitmap bitmap{w, h, stride > 0 ? stride : w, {}};
    // Padding past w is never sampled, so it must never reach a page.
    bitmap.data.assign((size_t) bitmap.stride * h, 0xEE);
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            bitmap.data[(size_t) y * bitmap.stride + x] = (unsigned char) (seed * 31 + y * w + x);
        }
    }
    return bitmap;
}

// A libass image list, as ass_render_frame returns it.
class ImageList {
public:
    void add(const Bitmap& bitmap, int dstX = 0, int dstY = 0, uint32_t color = 0) {
        addRaw(bitmap.w, bitmap.h, bitmap.stride, bitmap.data.data(), dstX, dstY, color);
    }

    void addRaw(int w, int h, int stride, const unsigned char* bitmap, int dstX = 0, int dstY = 0, uint32_t color = 0) {
        ASS_Image img{};
        img.w = w;
        img.h = h;
        img.stride = stride;
        img.bitmap = const_cast<unsigned char*>(bitmap);
        img.color = color;
        img.dst_x = dstX;
        img.dst_y = dstY;
        images_.push_back(img);
    }

    const ASS_Image* head() {
        for (size_t i = 0; i < images_.size(); i++) {
            images_[i].next = i + 1 < images_.size() ? &images_[i + 1] : nullptr;
        }
        return images_.empty() ? nullptr : &images_[0];
    }

    const std::deque<ASS_Image>& images() const {
        return images_;
    }

private:
    // A deque, so that adding an image never moves the earlier ones.
    std::deque<ASS_Image> images_;
};

#endif // LIBASS_ANDROID_ASS_TEST_IMAGES_H
