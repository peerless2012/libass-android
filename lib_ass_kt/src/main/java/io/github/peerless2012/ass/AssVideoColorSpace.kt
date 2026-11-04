package io.github.peerless2012.ass

/**
 * The YCbCr matrix and range a video is converted to RGB with.
 *
 * TV is limited range (16-235), PC is full range (0-255).
 * Note: This file must be in sync with AssVideoColorSpace values in AssColor.h.
 */
enum class AssVideoColorSpace {

    BT601_TV,

    BT601_PC,

    BT709_TV,

    BT709_PC,

    BT2020_TV,

    BT2020_PC

}
