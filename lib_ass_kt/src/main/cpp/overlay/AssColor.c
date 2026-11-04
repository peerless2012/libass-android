#include "AssColor.h"

#include "AssOverlayLog.h"

#define BT601_KR 0.299
#define BT601_KB 0.114
#define BT709_KR 0.2126
#define BT709_KB 0.0722
#define SMPTE240M_KR 0.212
#define SMPTE240M_KB 0.087
#define FCC_KR 0.30
#define FCC_KB 0.11
#define BT2020_KR 0.2627
#define BT2020_KB 0.0593

typedef struct {
    uint8_t r, g, b;
} Rgb;

typedef struct {
    double y, cb, cr;
} Ycbcr;

/**
 * \brief Rounds a value to the nearest byte, clamping it to [0, 255].
 * \param color color to round
 * \return the color rounded + clamped
 */
static uint8_t roundColor(double color) {
    if (color <= 0.0) {
        return 0;
    }
    if (color >= 255.0) {
        return 255;
    }
    return (uint8_t) (color + 0.5);
}

/**
 * \brief Converts an 8-bit RGB color to YCbCr, unrounded.
 * \param space YCbCr matrix and range to convert to
 * \param rgb color to convert
 * \return the YCbCr color
 */
static Ycbcr rgbToYcbcr(const AssYcbcrSpace* space, Rgb rgb) {
    const double kg = 1.0 - space->kr - space->kb;
    const double r = rgb.r / 255.0;
    const double g = rgb.g / 255.0;
    const double b = rgb.b / 255.0;

    // y_prime is [0, 1]
    // pb/pr are [-0.5, 0.5]
    const double y_prime = space->kr * r + kg * g + space->kb * b;
    const double pb = (b - y_prime) / (2.0 * (1.0 - space->kb));
    const double pr = (r - y_prime) / (2.0 * (1.0 - space->kr));

    if (space->fullRange) {
        // y is [0, 255]
        // cb/cr are [0.5, 255.5] (but, anything above 255.5 is clipped to 255)
        const double cb = 128.0 + 255.0 * pb;
        const double cr = 128.0 + 255.0 * pr;
        return (Ycbcr) {255.0 * y_prime, cb > 255.0 ? 255.0 : cb, cr > 255.0 ? 255.0 : cr};
    }

    // y is [16, 235]
    // cb/cr are [16, 240]
    return (Ycbcr) {16.0 + 219.0 * y_prime, 128.0 + 224.0 * pb, 128.0 + 224.0 * pr};
}

/**
 * \brief Converts a YCbCr color to 8-bit RGB.
 * \param space YCbCr matrix and range to convert from
 * \param ycbcr color to convert
 * \return the RGB color
 */
static Rgb ycbcrToRgb(const AssYcbcrSpace* space, Ycbcr ycbcr) {
    double y_prime, pb, pr;
    if (space->fullRange) {
        // y is [0, 255] -> y_prime is [0, 1]
        // cb/cr are [0.5, 255] -> pb/pr are [-0.5, 0.5]
        y_prime = ycbcr.y / 255.0;
        pb = (ycbcr.cb - 128.0) / 255.0;
        pr = (ycbcr.cr - 128.0) / 255.0;
    } else {
        // y is [16, 235] -> y_prime is [0, 1]
        // cb/cr are [16, 240] -> pb/pr are [-0.5, 0.5]
        y_prime = (ycbcr.y - 16.0) / 219.0;
        pb = (ycbcr.cb - 128.0) / 224.0;
        pr = (ycbcr.cr - 128.0) / 224.0;
    }

    const double kg = 1.0 - space->kr - space->kb;
    const double r = y_prime + pr * (1.0 - space->kr) * 2.0;
    const double b = y_prime + pb * (1.0 - space->kb) * 2.0;
    const double g = (y_prime - space->kr * r - space->kb * b) / kg;

    return (Rgb) {roundColor(255.0 * r), roundColor(255.0 * g), roundColor(255.0 * b)};
}

/**
 * \brief Looks up the YCbCr space a "YCbCr Matrix" header names.
 * \param header the track's header
 * \param space receives the YCbCr space
 * \return false if colors aren't to be mangled: "None" or a header unknown to us
 */
static bool headerSpace(ASS_YCbCrMatrix header, AssYcbcrSpace* space) {
    switch (header) {
        case YCBCR_DEFAULT: // header missing: VSFilter always used TV.601
        case YCBCR_UNKNOWN: // header libass couldn't parse: TV.601 too
        case YCBCR_BT601_TV: *space = (AssYcbcrSpace) {BT601_KR, BT601_KB, false}; return true;
        case YCBCR_BT601_PC: *space = (AssYcbcrSpace) {BT601_KR, BT601_KB, true}; return true;
        case YCBCR_BT709_TV: *space = (AssYcbcrSpace) {BT709_KR, BT709_KB, false}; return true;
        case YCBCR_BT709_PC: *space = (AssYcbcrSpace) {BT709_KR, BT709_KB, true}; return true;
        case YCBCR_SMPTE240M_TV: *space = (AssYcbcrSpace) {SMPTE240M_KR, SMPTE240M_KB, false}; return true;
        case YCBCR_SMPTE240M_PC: *space = (AssYcbcrSpace) {SMPTE240M_KR, SMPTE240M_KB, true}; return true;
        case YCBCR_FCC_TV: *space = (AssYcbcrSpace) {FCC_KR, FCC_KB, false}; return true;
        case YCBCR_FCC_PC: *space = (AssYcbcrSpace) {FCC_KR, FCC_KB, true}; return true;
        default: return false; // YCBCR_NONE or newer
    }
}

/**
 * \brief Looks up the YCbCr space of the video.
 * \param video the video's matrix and range
 * \param space receives the YCbCr space
 * \return false if video isn't an AssVideoColorSpace
 */
static bool videoSpace(AssVideoColorSpace video, AssYcbcrSpace* space) {
    switch (video) {
        case ASS_VIDEO_BT601_TV: *space = (AssYcbcrSpace) {BT601_KR, BT601_KB, false}; return true;
        case ASS_VIDEO_BT601_PC: *space = (AssYcbcrSpace) {BT601_KR, BT601_KB, true}; return true;
        case ASS_VIDEO_BT709_TV: *space = (AssYcbcrSpace) {BT709_KR, BT709_KB, false}; return true;
        case ASS_VIDEO_BT709_PC: *space = (AssYcbcrSpace) {BT709_KR, BT709_KB, true}; return true;
        case ASS_VIDEO_BT2020_TV: *space = (AssYcbcrSpace) {BT2020_KR, BT2020_KB, false}; return true;
        case ASS_VIDEO_BT2020_PC: *space = (AssYcbcrSpace) {BT2020_KR, BT2020_KB, true}; return true;
        default: return false;
    }
}

AssColorMangle assColorMangleInit(ASS_YCbCrMatrix header, AssVideoColorSpace video) {
    AssColorMangle mangle = {0};
    LOGI("Color mangle init: header %d, video %d", (int) header, (int) video);
    if (!headerSpace(header, &mangle.header)) {
        LOGI("Header %d means no mangling, subtitle colors are drawn unmangled", (int) header);
        return mangle;
    }
    if (!videoSpace(video, &mangle.video)) {
        LOGE("Unknown video color space %d, subtitle colors are drawn unmangled", (int) video);
        return mangle;
    }

    // Converted even when the header matches the video: PC range's Cb/Cr clipping at 255 can still
    // change a color.
    mangle.enabled = true;
    LOGI("Color mangle enabled: header kr=%.4f kb=%.4f %s, video kr=%.4f kb=%.4f %s",
        mangle.header.kr, mangle.header.kb, mangle.header.fullRange ? "PC" : "TV",
        mangle.video.kr, mangle.video.kb, mangle.video.fullRange ? "PC" : "TV");
    return mangle;
}

uint32_t assColorMangle(const AssColorMangle* mangle, uint32_t color) {
    if (!mangle->enabled) {
        return color;
    }

    const Rgb rgb = {(uint8_t) (color >> 24), (uint8_t) (color >> 16), (uint8_t) (color >> 8)};
    const Rgb mangled = ycbcrToRgb(&mangle->video, rgbToYcbcr(&mangle->header, rgb));
    return (uint32_t) mangled.r << 24 | (uint32_t) mangled.g << 16 | (uint32_t) mangled.b << 8 | (color & 0xFF);
}
