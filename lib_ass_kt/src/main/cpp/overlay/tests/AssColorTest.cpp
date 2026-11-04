#include <gtest/gtest.h>

#include <cstdint>

#include "AssColor.h"

namespace {

constexpr uint32_t rgb(uint32_t r, uint32_t g, uint32_t b) {
    return r << 24 | g << 16 | b << 8;
}

uint32_t mangle(ASS_YCbCrMatrix header, AssVideoColorSpace video, uint32_t color) {
    const AssColorMangle mangle = assColorMangleInit(header, video);
    return assColorMangle(&mangle, color);
}

} // namespace

TEST(AssColorTest, TV601ToTV709) {
    EXPECT_EQ(mangle(YCBCR_BT601_TV, ASS_VIDEO_BT709_TV, rgb(150, 100, 80)), rgb(155, 104, 78));
}

TEST(AssColorTest, TV601ToPC709) {
    EXPECT_EQ(mangle(YCBCR_BT601_TV, ASS_VIDEO_BT709_PC, rgb(150, 100, 80)), rgb(150, 105, 83));
}

TEST(AssColorTest, TV709ToTV601) {
    EXPECT_EQ(mangle(YCBCR_BT709_TV, ASS_VIDEO_BT601_TV, rgb(150, 100, 80)), rgb(146, 96, 81));
}

TEST(AssColorTest, TV709ToPC601) {
    EXPECT_EQ(mangle(YCBCR_BT709_TV, ASS_VIDEO_BT601_PC, rgb(150, 100, 80)), rgb(142, 98, 85));
}

TEST(AssColorTest, TVFCCToTV709) {
    EXPECT_EQ(mangle(YCBCR_FCC_TV, ASS_VIDEO_BT709_TV, rgb(150, 100, 80)), rgb(155, 104, 79));
}

TEST(AssColorTest, TVFCCToPC709) {
    EXPECT_EQ(mangle(YCBCR_FCC_TV, ASS_VIDEO_BT709_PC, rgb(150, 100, 80)), rgb(150, 105, 83));
}

TEST(AssColorTest, TV240MToTV709) {
    EXPECT_EQ(mangle(YCBCR_SMPTE240M_TV, ASS_VIDEO_BT709_TV, rgb(150, 100, 80)), rgb(150, 100, 80));
}

TEST(AssColorTest, TV240MToPC709) {
    EXPECT_EQ(mangle(YCBCR_SMPTE240M_TV, ASS_VIDEO_BT709_PC, rgb(150, 100, 80)), rgb(146, 101, 84));
}

TEST(AssColorTest, None) {
    EXPECT_EQ(mangle(YCBCR_NONE, ASS_VIDEO_BT709_TV, rgb(150, 100, 80)), rgb(150, 100, 80));
}

TEST(AssColorTest, NoYcbcrMatrix) {
    EXPECT_EQ(mangle(YCBCR_DEFAULT, ASS_VIDEO_BT709_TV, rgb(150, 100, 80)), rgb(155, 104, 78));
}
