#ifndef LIBASS_ANDROID_ASS_COLOR_H
#define LIBASS_ANDROID_ASS_COLOR_H

#include <stdbool.h>
#include <stdint.h>

#include "ass/ass.h"

#ifdef __cplusplus
extern "C" {
#endif

// The YCbCr matrix and range the video is converted to RGB with.
// Note: This file must be in sync with AssVideoColorSpace values in AssVideoColorSpace.kt.
typedef enum {
    ASS_VIDEO_BT601_TV,
    ASS_VIDEO_BT601_PC,
    ASS_VIDEO_BT709_TV,
    ASS_VIDEO_BT709_PC,
    ASS_VIDEO_BT2020_TV,
    ASS_VIDEO_BT2020_PC,
} AssVideoColorSpace;

typedef struct {
    double kr, kb; // luma coefficients of red and blue; green's is 1 - kr - kb
    bool fullRange; // PC (0-255) rather than TV (16-235, chroma 16-240)
} AssYcbcrSpace;

typedef struct {
    bool enabled;
    AssYcbcrSpace header; // the subtitle color space
    AssYcbcrSpace video; // the video color space
} AssColorMangle;

/**
 * \brief Sets up the mangling for a track drawn on a video.
 *
 * A missing or unparsable header means TV.601. "None" and an unknown video color space leave colors
 * untouched.
 * \param header the track's "YCbCr Matrix" header (ASS_Track.YCbCrMatrix)
 * \param video the video's matrix and range
 * \return the mangling, disabled if colors are drawn as they are
 */
AssColorMangle assColorMangleInit(ASS_YCbCrMatrix header, AssVideoColorSpace video);

/**
 * \brief Mangles a libass color like VSFilter.
 * \param mangle mangling from assColorMangleInit
 * \param color libass color, 0xRRGGBBTT
 * \return the color to draw, transparency unchanged
 * \note This method do what is described here: https://github.com/libass/libass/blob/5b44c4d690254a7bd8de2e048fa23820036b671b/libass/ass_types.h#L157-L262
 */
uint32_t assColorMangle(const AssColorMangle* mangle, uint32_t color);

#ifdef __cplusplus
}
#endif

#endif // LIBASS_ANDROID_ASS_COLOR_H
