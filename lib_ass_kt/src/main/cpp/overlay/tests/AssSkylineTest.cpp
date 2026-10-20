#include <gtest/gtest.h>

#include <algorithm>
#include <optional>
#include <random>
#include <utility>
#include <vector>

#include "AssSkyline.h"

namespace {

using Point = std::pair<int, int>;

class AssSkylineTest : public testing::Test {
protected:
    void TearDown() override {
        assSkylineFree(&skyline);
    }

    void reset(int count, int width, int maxHeight) {
        assSkylineFree(&skyline);
        skyline = {};
        ASSERT_TRUE(assSkylineReserve(&skyline, count));
        assSkylineReset(&skyline, width, maxHeight);
    }

    std::optional<Point> add(int w, int h) {
        int x, y;
        if (!assSkylineAdd(&skyline, w, h, &x, &y)) {
            return std::nullopt;
        }
        return Point(x, y);
    }

    // A rectangle that fits nowhere leaves x and y as they were. RandomRectangles checks it leaves
    // the skyline as it was too, through the rectangles placed after it.
    void expectRejected(int w, int h) {
        int x = -1, y = -1;
        EXPECT_FALSE(assSkylineAdd(&skyline, w, h, &x, &y)) << w << "x" << h;
        EXPECT_EQ(Point(x, y), Point(-1, -1));
    }

    AssSkyline skyline{};
};

} // namespace

// The example from AssSkyline.h.
TEST_F(AssSkylineTest, HeaderExample) {
    reset(4, 10, 100);

    EXPECT_EQ(add(4, 5), Point(0, 0)); // A
    EXPECT_EQ(add(3, 4), Point(4, 0)); // B
    EXPECT_EQ(add(5, 2), Point(4, 4)); // C
    EXPECT_EQ(add(2, 2), Point(0, 5)); // D
}

TEST_F(AssSkylineTest, RejectsTooWide) {
    reset(1, 10, 100);

    expectRejected(11, 1);
}

TEST_F(AssSkylineTest, RejectsTooTall) {
    reset(2, 10, 100);

    ASSERT_EQ(add(10, 60), Point(0, 0));
    expectRejected(1, 41);
}

TEST_F(AssSkylineTest, FillsRemainingHeight) {
    reset(2, 10, 100);

    ASSERT_EQ(add(10, 60), Point(0, 0));
    EXPECT_EQ(add(1, 40), Point(0, 60));
}

// Of two spots as high, the leftmost wins.
//      0123456789
//   0  AAABBBCCCC
//   1  AAABBBCCCC
//   2  DDDBBB....
//   3  ...BBB....
//   4  ...BBB....
TEST_F(AssSkylineTest, TiesGoLeftmost) {
    reset(4, 10, 5);

    ASSERT_EQ(add(3, 2), Point(0, 0));
    ASSERT_EQ(add(3, 5), Point(3, 0));
    ASSERT_EQ(add(4, 2), Point(6, 0));
    EXPECT_EQ(add(3, 1), Point(0, 2));
}

// A reset forgets what was packed, area size included, and keeps the room reserved.
TEST_F(AssSkylineTest, ResetStartsOver) {
    reset(2, 10, 100);
    ASSERT_EQ(add(10, 60), Point(0, 0));

    assSkylineReset(&skyline, 5, 10);
    expectRejected(6, 1);
    EXPECT_EQ(add(5, 10), Point(0, 0));
    expectRejected(1, 1);
}

// Rectangles each one column wide and one row shorter than the one before, with a column left free
// after them, leave a staircase: the most steps as many rectangles can. Room for exactly that many
// must still hold them; taking more only shows under ASan.
TEST_F(AssSkylineTest, ReservedRoomSuffices) {
    const int count = 64;
    reset(count, count + 1, count);

    for (int i = 0; i < count; i++) {
        EXPECT_EQ(add(1, count - i), Point(i, 0));
    }
}

// Random rectangles into random areas, checked against a model of the area: the bottom of what is
// packed over each column. Each rectangle must go at the highest spot it fits, then the leftmost -
// trying every column - or be rejected.
TEST_F(AssSkylineTest, RandomRectangles) {
    std::mt19937 rng(20261014);
    // rng() % n rather than std::uniform_int_distribution, whose algorithm varies between standard
    // libraries: a seed makes the same data everywhere.
    const auto random = [&rng](int min, int max) { return min + (int) (rng() % (uint32_t) (max - min + 1)); };
    for (int round = 0; round < 2000; round++) {
        SCOPED_TRACE(testing::Message() << "round " << round);
        const int width = random(1, 48);
        const int maxHeight = random(1, 48);
        const int count = random(1, 40);
        reset(count, width, maxHeight);
        std::vector<int> bottom((size_t) width, 0);

        for (int k = 0; k < count; k++) {
            // Mostly small, at times too wide.
            const int w = random(1, random(1, width + 1));
            const int h = random(1, random(1, maxHeight / 2 + 1));
            int bestX = -1;
            int bestY = maxHeight - h + 1;
            for (int x = 0; x + w <= width; x++) {
                const int y = *std::max_element(bottom.begin() + x, bottom.begin() + x + w);
                if (y < bestY) {
                    bestX = x;
                    bestY = y;
                }
            }

            if (bestX < 0) {
                expectRejected(w, h);
            } else {
                EXPECT_EQ(add(w, h), Point(bestX, bestY)) << w << "x" << h;
                std::fill(bottom.begin() + bestX, bottom.begin() + bestX + w, bestY + h);
            }
            if (HasFailure()) {
                return;
            }
        }
    }
}
