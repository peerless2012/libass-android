#include <gtest/gtest.h>

#include <deque>
#include <ostream>
#include <random>
#include <vector>

#include "AssAtlas.h"
#include "AssTestImages.h"

namespace {

struct Page {
    int firstPiece, pieceCount, usedW, usedH;

    bool operator==(const Page& other) const {
        return firstPiece == other.firstPiece && pieceCount == other.pieceCount && usedW == other.usedW &&
            usedH == other.usedH;
    }
};

std::ostream& operator<<(std::ostream& os, const Page& page) {
    return os << "{first " << page.firstPiece << ", count " << page.pieceCount << ", " << page.usedW << "x"
              << page.usedH << "}";
}

std::vector<Page> pages(const AssAtlas& atlas) {
    std::vector<Page> result;
    for (int k = 0; k < atlas.pageCount; k++) {
        const AssAtlasPage& p = atlas.pages[k];
        result.push_back({p.firstPiece, p.pieceCount, p.usedW, p.usedH});
    }
    return result;
}

std::vector<int> sameAs(const AssAtlas& atlas) {
    std::vector<int> result;
    for (int i = 0; i < atlas.pieceCount; i++) {
        result.push_back(atlas.pieces[i].sameAs);
    }
    return result;
}

bool overlaps(const AssAtlasPiece& a, const AssAtlasPiece& b) {
    return a.atlasX < b.atlasX + b.w && b.atlasX < a.atlasX + a.w && a.atlasY < b.atlasY + b.h &&
        b.atlasY < a.atlasY + a.h;
}

// Checks what holds for any layout: the pages split the pieces into runs in list order, each page
// fits in a max-size texture, the bitmaps stored on a page lie in its used area without
// overlapping, and a repeat on its copy's page sits on that copy.
void expectValidLayout(const AssAtlas& atlas) {
    int nextPiece = 0;
    for (int k = 0; k < atlas.pageCount; k++) {
        const AssAtlasPage& page = atlas.pages[k];
        SCOPED_TRACE(testing::Message() << "page " << k);
        EXPECT_EQ(page.firstPiece, nextPiece);
        EXPECT_GT(page.pieceCount, 0);
        nextPiece = page.firstPiece + page.pieceCount;
        EXPECT_GT(page.usedW, 0);
        EXPECT_GT(page.usedH, 0);
        EXPECT_LE(page.usedW, atlas.maxPageSize);
        EXPECT_LE(page.usedH, atlas.maxPageSize);

        for (int i = page.firstPiece; i < nextPiece; i++) {
            const AssAtlasPiece& p = atlas.pieces[i];
            SCOPED_TRACE(testing::Message() << "piece " << i);
            EXPECT_LT(p.sameAs, i);
            if (p.sameAs >= page.firstPiece) {
                EXPECT_EQ(p.atlasX, atlas.pieces[p.sameAs].atlasX);
                EXPECT_EQ(p.atlasY, atlas.pieces[p.sameAs].atlasY);
                continue;
            }
            EXPECT_GE(p.atlasX, 0);
            EXPECT_GE(p.atlasY, 0);
            EXPECT_LE(p.atlasX + p.w, page.usedW);
            EXPECT_LE(p.atlasY + p.h, page.usedH);
            for (int j = page.firstPiece; j < i; j++) {
                if (atlas.pieces[j].sameAs >= page.firstPiece) {
                    continue;
                }
                EXPECT_FALSE(overlaps(p, atlas.pieces[j])) << "overlaps piece " << j;
            }
        }
    }
    EXPECT_EQ(nextPiece, atlas.pieceCount);
}

// Fills each page and checks that every piece, repeats included, reads back its own bitmap.
void expectPagesHoldBitmaps(const AssAtlas& atlas) {
    for (int k = 0; k < atlas.pageCount; k++) {
        const AssAtlasPage& page = atlas.pages[k];
        std::vector<unsigned char> buf((size_t) page.usedW * page.usedH);
        assAtlasFillPage(&atlas, &page, buf.data());

        for (int i = page.firstPiece; i < page.firstPiece + page.pieceCount; i++) {
            const AssAtlasPiece& p = atlas.pieces[i];
            for (int y = 0; y < p.h; y++) {
                for (int x = 0; x < p.w; x++) {
                    const unsigned char actual = buf[(size_t) (p.atlasY + y) * page.usedW + p.atlasX + x];
                    const unsigned char expected = p.bitmap[(size_t) y * p.stride + x];
                    if (actual != expected) {
                        ADD_FAILURE() << "page " << k << ", piece " << i << ": texel (" << x << ", " << y
                                      << ") is " << (int) actual << " instead of " << (int) expected;
                        return;
                    }
                }
            }
        }
    }
}

// Checks the pieces against the images: every image with something to draw that fits in a page is
// kept, in list order, as it is, and linked to the closest earlier piece showing the very same bitmap.
void expectPieces(const AssAtlas& atlas, const ImageList& images) {
    std::vector<const ASS_Image*> kept;
    for (const ASS_Image& img : images.images()) {
        if (img.w > 0 && img.h > 0 && img.w <= atlas.maxPageSize && img.h <= atlas.maxPageSize) {
            kept.push_back(&img);
        }
    }
    ASSERT_EQ(atlas.pieceCount, (int) kept.size());

    for (int i = 0; i < atlas.pieceCount; i++) {
        const AssAtlasPiece& p = atlas.pieces[i];
        const ASS_Image& img = *kept[(size_t) i];
        SCOPED_TRACE(testing::Message() << "piece " << i);
        EXPECT_EQ(p.dstX, img.dst_x);
        EXPECT_EQ(p.dstY, img.dst_y);
        EXPECT_EQ(p.w, img.w);
        EXPECT_EQ(p.h, img.h);
        EXPECT_EQ(p.color, img.color);
        EXPECT_EQ((const void*) p.bitmap, (const void*) img.bitmap);
        EXPECT_EQ(p.stride, img.stride);

        int sameAs = -1;
        for (int j = i - 1; j >= 0 && sameAs < 0; j--) {
            const ASS_Image& other = *kept[(size_t) j];
            if (other.bitmap == img.bitmap && other.w == img.w && other.h == img.h && other.stride == img.stride) {
                sameAs = j;
            }
        }
        EXPECT_EQ(p.sameAs, sameAs);
    }
}

class AssAtlasTest : public testing::Test {
protected:
    void TearDown() override {
        assAtlasFree(&atlas);
    }

    void pack(ImageList& images, int maxPageSize = 256) {
        atlas.maxPageSize = maxPageSize;
        assAtlasPack(&atlas, images.head());
    }

    AssAtlas atlas{};
};

} // namespace

TEST_F(AssAtlasTest, EmptyList) {
    ImageList images;
    pack(images);

    EXPECT_EQ(atlas.pieceCount, 0);
    EXPECT_EQ(atlas.pageCount, 0);
}

TEST_F(AssAtlasTest, SkipsZeroSizedImages) {
    const Bitmap a = makeBitmap(4, 4, 1);
    ImageList images;
    images.addRaw(0, 4, 0, nullptr);
    images.add(a);
    images.addRaw(4, 0, 4, nullptr);
    pack(images);

    ASSERT_EQ(atlas.pieceCount, 1);
    EXPECT_EQ(atlas.pieces[0].bitmap, a.data.data());
    EXPECT_EQ(pages(atlas), (std::vector<Page>{{0, 1, 4, 4}}));
}

TEST_F(AssAtlasTest, OnlyZeroSizedImages) {
    ImageList images;
    images.addRaw(0, 0, 0, nullptr);
    pack(images);

    EXPECT_EQ(atlas.pieceCount, 0);
    EXPECT_EQ(atlas.pageCount, 0);
}

TEST_F(AssAtlasTest, KeepsImagesInListOrder) {
    const Bitmap a = makeBitmap(3, 5, 1, 8);
    const Bitmap b = makeBitmap(6, 2, 2);
    ImageList images;
    images.add(a, 10, 20, 0x11223344);
    images.add(b, -3, 7, 0xAABBCCDD);
    pack(images);

    ASSERT_EQ(atlas.pieceCount, 2);
    const AssAtlasPiece& pa = atlas.pieces[0];
    EXPECT_EQ(pa.dstX, 10);
    EXPECT_EQ(pa.dstY, 20);
    EXPECT_EQ(pa.w, 3);
    EXPECT_EQ(pa.h, 5);
    EXPECT_EQ(pa.color, 0x11223344u);
    EXPECT_EQ(pa.bitmap, a.data.data());
    EXPECT_EQ(pa.stride, 8);
    const AssAtlasPiece& pb = atlas.pieces[1];
    EXPECT_EQ(pb.dstX, -3);
    EXPECT_EQ(pb.dstY, 7);
    EXPECT_EQ(pb.w, 6);
    EXPECT_EQ(pb.h, 2);
    EXPECT_EQ(pb.color, 0xAABBCCDDu);
    EXPECT_EQ(pb.bitmap, b.data.data());
    EXPECT_EQ(pb.stride, 6);
    expectValidLayout(atlas);
}

TEST_F(AssAtlasTest, DropsImagesLargerThanAPage) {
    const Bitmap tooWide = makeBitmap(17, 1, 1);
    const Bitmap fits = makeBitmap(16, 16, 2);
    const Bitmap tooTall = makeBitmap(1, 17, 3);
    ImageList images;
    images.add(tooWide);
    images.add(fits);
    images.add(tooTall);
    pack(images, 16);

    ASSERT_EQ(atlas.pieceCount, 1);
    EXPECT_EQ(atlas.pieces[0].bitmap, fits.data.data());
    EXPECT_EQ(pages(atlas), (std::vector<Page>{{0, 1, 16, 16}}));
}

// A piece wider than the page its area calls for widens the page, up to a max size that need not be
// a power of two.
TEST_F(AssAtlasTest, WidensPagesForWidePieces) {
    const Bitmap a = makeBitmap(600, 2, 1);
    ImageList images;
    images.add(a);
    pack(images, 1000);

    EXPECT_EQ(pages(atlas), (std::vector<Page>{{0, 1, 600, 2}}));
    expectPagesHoldBitmaps(atlas);
}

// Placement goes tallest first, then widest, while the pieces keep their list order.
TEST_F(AssAtlasTest, PlacesTallestFirst) {
    const Bitmap small = makeBitmap(4, 2, 1);
    const Bitmap tall = makeBitmap(4, 8, 2);
    const Bitmap wide = makeBitmap(8, 2, 3);
    ImageList images;
    images.add(small);
    images.add(tall);
    images.add(wide);
    pack(images);

    ASSERT_EQ(atlas.pieceCount, 3);
    EXPECT_EQ(atlas.pieces[1].atlasX, 0); // tall
    EXPECT_EQ(atlas.pieces[1].atlasY, 0);
    EXPECT_EQ(atlas.pieces[2].atlasX, 4); // wide, beside tall
    EXPECT_EQ(atlas.pieces[2].atlasY, 0);
    EXPECT_EQ(atlas.pieces[0].atlasX, 12); // small, beside wide
    EXPECT_EQ(atlas.pieces[0].atlasY, 0);
    EXPECT_EQ(pages(atlas), (std::vector<Page>{{0, 3, 16, 8}}));
    expectPagesHoldBitmaps(atlas);
}

TEST_F(AssAtlasTest, ReadsBitmapsByStride) {
    const Bitmap a = makeBitmap(3, 2, 1, 8);
    ImageList images;
    images.add(a);
    pack(images);

    ASSERT_EQ(pages(atlas), (std::vector<Page>{{0, 1, 3, 2}}));
    std::vector<unsigned char> buf(3 * 2);
    assAtlasFillPage(&atlas, &atlas.pages[0], buf.data());
    const std::vector<unsigned char> expected{
        a.data[0], a.data[1], a.data[2],
        a.data[8], a.data[9], a.data[10],
    };
    EXPECT_EQ(buf, expected);
}

TEST_F(AssAtlasTest, StoresRepeatsOnce) {
    const Bitmap a = makeBitmap(8, 8, 1);
    const Bitmap b = makeBitmap(8, 8, 2);
    ImageList images;
    images.add(a, 0, 0, 0x11111111);
    images.add(b, 10, 0, 0x22222222);
    images.add(a, 20, 0, 0x33333333);
    pack(images);

    EXPECT_EQ(sameAs(atlas), (std::vector<int>{-1, -1, 0}));
    // A repeat only shares its copy's texels: it is still drawn where and how it is itself.
    EXPECT_EQ(atlas.pieces[2].dstX, 20);
    EXPECT_EQ(atlas.pieces[2].color, 0x33333333u);
    // Only a and b take room.
    EXPECT_EQ(pages(atlas), (std::vector<Page>{{0, 3, 16, 8}}));
    expectValidLayout(atlas);
    expectPagesHoldBitmaps(atlas);
}

// Repeats take no room, so as many as there are fit on the page of their copy.
TEST_F(AssAtlasTest, RepeatsAddNoTexelsToAPage) {
    const Bitmap a = makeBitmap(16, 16, 1);
    ImageList images;
    for (int i = 0; i < 4; i++) {
        images.add(a, i * 20, 0);
    }
    pack(images, 16);

    EXPECT_EQ(sameAs(atlas), (std::vector<int>{-1, 0, 1, 2}));
    EXPECT_EQ(pages(atlas), (std::vector<Page>{{0, 4, 16, 16}}));
    expectValidLayout(atlas);
    expectPagesHoldBitmaps(atlas);
}

TEST_F(AssAtlasTest, RepeatsLinkToTheClosestEarlierPiece) {
    const Bitmap a = makeBitmap(4, 4, 1);
    const Bitmap b = makeBitmap(4, 4, 2);
    ImageList images;
    images.add(a);
    images.add(b);
    images.add(a);
    images.add(b);
    images.add(a);
    pack(images);

    EXPECT_EQ(sameAs(atlas), (std::vector<int>{-1, -1, 0, 1, 2}));
    expectValidLayout(atlas);
    expectPagesHoldBitmaps(atlas);
}

// The same pixels read with another size or stride are another bitmap.
TEST_F(AssAtlasTest, SamePointerWithAnotherShapeIsNotARepeat) {
    const Bitmap a = makeBitmap(8, 8, 1);
    const unsigned char* pixels = a.data.data();
    ImageList images;
    images.addRaw(8, 8, 8, pixels);
    images.addRaw(4, 8, 8, pixels);
    images.addRaw(8, 4, 8, pixels);
    images.addRaw(8, 4, 16, pixels);
    pack(images);

    EXPECT_EQ(sameAs(atlas), (std::vector<int>{-1, -1, -1, -1}));
    expectValidLayout(atlas);
    expectPagesHoldBitmaps(atlas);
}

// Pieces sharing a pointer start their lookup at the same slot: a repeat must look past the other
// shapes there to find its copy.
TEST_F(AssAtlasTest, RepeatsAreFoundPastAnotherShape) {
    const Bitmap a = makeBitmap(8, 8, 1);
    const unsigned char* pixels = a.data.data();
    ImageList images;
    images.addRaw(8, 8, 8, pixels);
    images.addRaw(4, 8, 8, pixels);
    images.addRaw(8, 8, 8, pixels);
    images.addRaw(4, 8, 8, pixels);
    pack(images);

    EXPECT_EQ(sameAs(atlas), (std::vector<int>{-1, -1, 0, 1}));
    expectValidLayout(atlas);
    expectPagesHoldBitmaps(atlas);
}

TEST_F(AssAtlasTest, SplitsIntoPagesInListOrder) {
    std::vector<Bitmap> bitmaps;
    for (int i = 0; i < 9; i++) {
        bitmaps.push_back(makeBitmap(8, 8, i));
    }
    ImageList images;
    for (const Bitmap& bitmap : bitmaps) {
        images.add(bitmap);
    }
    pack(images, 16);

    // Four 8x8 pieces fill a 16x16 page.
    EXPECT_EQ(pages(atlas), (std::vector<Page>{{0, 4, 16, 16}, {4, 4, 16, 16}, {8, 1, 8, 8}}));
    expectValidLayout(atlas);
    expectPagesHoldBitmaps(atlas);
}

// Three 9x9 pieces add up to less than a 16x16 page, but no two fit side by side or one above the
// other: the run shrinks until it packs.
TEST_F(AssAtlasTest, ShortensRunsThatDontPack) {
    std::vector<Bitmap> bitmaps;
    for (int i = 0; i < 3; i++) {
        bitmaps.push_back(makeBitmap(9, 9, i));
    }
    ImageList images;
    for (const Bitmap& bitmap : bitmaps) {
        images.add(bitmap);
    }
    pack(images, 16);

    EXPECT_EQ(pages(atlas), (std::vector<Page>{{0, 1, 9, 9}, {1, 1, 9, 9}, {2, 1, 9, 9}}));
    expectValidLayout(atlas);
    expectPagesHoldBitmaps(atlas);
}

// A page only shares texels within itself, so a repeat whose copy is on an earlier page is stored
// again, and a repeat of that one shares its texels.
TEST_F(AssAtlasTest, RepeatOfAnEarlierPageIsStoredAgain) {
    const Bitmap a = makeBitmap(16, 16, 1);
    const Bitmap b = makeBitmap(16, 16, 2);
    ImageList images;
    images.add(a);
    images.add(b);
    images.add(a);
    images.add(a);
    pack(images, 16);

    EXPECT_EQ(sameAs(atlas), (std::vector<int>{-1, -1, 0, 2}));
    EXPECT_EQ(pages(atlas), (std::vector<Page>{{0, 1, 16, 16}, {1, 1, 16, 16}, {2, 2, 16, 16}}));
    expectValidLayout(atlas);
    expectPagesHoldBitmaps(atlas);
}

TEST_F(AssAtlasTest, Truncate) {
    std::vector<Bitmap> bitmaps;
    for (int i = 0; i < 9; i++) {
        bitmaps.push_back(makeBitmap(8, 8, i));
    }
    ImageList images;
    for (const Bitmap& bitmap : bitmaps) {
        images.add(bitmap);
    }
    pack(images, 16);
    ASSERT_EQ(atlas.pageCount, 3);

    assAtlasTruncate(&atlas, 3); // keeps everything
    EXPECT_EQ(atlas.pageCount, 3);
    EXPECT_EQ(atlas.pieceCount, 9);

    assAtlasTruncate(&atlas, 1);
    EXPECT_EQ(atlas.pageCount, 1);
    EXPECT_EQ(atlas.pieceCount, 4);
    expectValidLayout(atlas);

    assAtlasTruncate(&atlas, 0);
    EXPECT_EQ(atlas.pageCount, 0);
    EXPECT_EQ(atlas.pieceCount, 0);
}

// Each pack starts from scratch, and the arrays grow when a frame has more pieces than any before.
TEST_F(AssAtlasTest, RepacksEachFrame) {
    std::vector<Bitmap> bitmaps;
    for (int i = 0; i < 100; i++) {
        bitmaps.push_back(makeBitmap(1 + i % 13, 1 + i % 7, i));
    }

    ImageList small;
    small.add(bitmaps[0]);
    small.add(bitmaps[1]);
    pack(small);
    EXPECT_EQ(atlas.pieceCount, 2);
    EXPECT_EQ(atlas.pageCount, 1);

    // 300 pieces: each bitmap three times.
    ImageList large;
    for (int round = 0; round < 3; round++) {
        for (const Bitmap& bitmap : bitmaps) {
            large.add(bitmap);
        }
    }
    pack(large);
    ASSERT_EQ(atlas.pieceCount, 300);
    for (int i = 0; i < 300; i++) {
        EXPECT_EQ(atlas.pieces[i].sameAs, i < 100 ? -1 : i - 100) << "piece " << i;
    }
    expectValidLayout(atlas);
    expectPagesHoldBitmaps(atlas);

    pack(small);
    EXPECT_EQ(atlas.pieceCount, 2);
    EXPECT_EQ(sameAs(atlas), (std::vector<int>{-1, -1}));
    expectValidLayout(atlas);
    expectPagesHoldBitmaps(atlas);
}

// Random frames, packed one after the other into the same atlas: mostly small images, at times one
// as large as a page or larger, zero-sized ones, repeats of an earlier bitmap, and earlier bitmaps read
// with another size, into pages whose size need not be a power of two.
TEST_F(AssAtlasTest, RandomFrames) {
    std::mt19937 rng(20261014);
    // rng() % n rather than std::uniform_int_distribution, whose algorithm varies between standard
    // libraries: a seed makes the same data everywhere.
    const auto random = [&rng](int min, int max) { return min + (int) (rng() % (uint32_t) (max - min + 1)); };
    for (int frame = 0; frame < 300; frame++) {
        SCOPED_TRACE(testing::Message() << "frame " << frame);
        const int maxPageSize = random(4, 80);
        std::deque<Bitmap> bitmaps; // a deque, so that adding a bitmap never moves the earlier ones
        ImageList images;
        const int count = random(0, random(1, 120));
        for (int i = 0; i < count; i++) {
            const int dstX = random(-8, 200);
            const int dstY = random(-8, 200);
            const auto color = (uint32_t) rng();
            const int kind = random(0, 19);
            const ASS_Image* earlier =
                images.images().empty() ? nullptr : &images.images()[(size_t) random(0, (int) images.images().size() - 1)];
            // Each random() call is a statement of its own, as the order of function arguments is
            // unspecified.
            if (kind == 0) {
                const int side = random(1, 4);
                if (random(0, 1) == 0) {
                    images.addRaw(0, side, 0, nullptr, dstX, dstY, color);
                } else {
                    images.addRaw(side, 0, side, nullptr, dstX, dstY, color);
                }
            } else if (kind <= 5 && earlier != nullptr && earlier->bitmap != nullptr) {
                // The same bitmap, or the same pixels read with another size.
                int w = earlier->w;
                int h = earlier->h;
                if (kind > 3) {
                    w = random(1, w);
                    h = random(1, h);
                }
                images.addRaw(w, h, earlier->stride, earlier->bitmap, dstX, dstY, color);
            } else {
                const int maxSide = kind == 6 ? maxPageSize + 4 : random(1, maxPageSize);
                const int w = random(1, maxSide);
                const int h = random(1, maxSide);
                const int stride = w + random(0, 3);
                bitmaps.push_back(makeBitmap(w, h, frame * 1000 + i, stride));
                images.add(bitmaps.back(), dstX, dstY, color);
            }
        }

        pack(images, maxPageSize);
        expectPieces(atlas, images);
        expectValidLayout(atlas);
        expectPagesHoldBitmaps(atlas);
        if (HasFailure()) {
            return;
        }
    }
}
