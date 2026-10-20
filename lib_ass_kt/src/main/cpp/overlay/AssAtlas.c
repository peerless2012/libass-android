#include "AssAtlas.h"

#include <stddef.h>
#include <stdlib.h>
#include <string.h>

#include "AssOverlayLog.h"

// Piece capacity to start from; it doubles whenever a frame has more pieces.
#define MIN_PIECE_CAP 64
// Page widths double from this until they fit a page's pieces.
#define MIN_PAGE_WIDTH 256

/**
 * \brief Whether an image has nothing to draw: libass emits zero-sized ones.
 * \param img libass image
 * \return whether img is zero-sized
 */
static bool isEmpty(const ASS_Image* img) {
    return img->w <= 0 || img->h <= 0;
}

/**
 * \brief Makes room for pieces and their packing scratch.
 *
 * Capacity at least doubles, so a piece count creeping up frame by frame rarely reallocates.
 * \param atlas atlas to grow
 * \param count how many pieces must fit
 * \return false on OOM
 */
static bool ensurePieces(AssAtlas* atlas, int count) {
    if (count <= atlas->pieceCap) {
        return true;
    }

    int newCap = atlas->pieceCap > 0 ? atlas->pieceCap * 2 : MIN_PIECE_CAP;
    if (newCap < count) {
        newCap = count;
    }

    AssAtlasPiece* pieces = (AssAtlasPiece*) realloc(atlas->pieces, (size_t) newCap * sizeof(AssAtlasPiece));
    if (pieces == NULL) {
        return false;
    }

    atlas->pieces = pieces;
    AssAtlasPackEntry* packOrder = (AssAtlasPackEntry*) realloc(atlas->packOrder, (size_t) newCap * sizeof(AssAtlasPackEntry));
    if (packOrder == NULL) {
        return false;
    }

    atlas->packOrder = packOrder;
    if (!assSkylineReserve(&atlas->skyline, newCap)) {
        return false;
    }

    int slotCap = atlas->bitmapSlotCap > 0 ? atlas->bitmapSlotCap : 2 * MIN_PIECE_CAP;
    while (slotCap < 2 * newCap) {
        slotCap *= 2;
    }

    int* slots = (int*) realloc(atlas->bitmapSlots, (size_t) slotCap * sizeof(int));
    if (slots == NULL) {
        return false;
    }

    atlas->bitmapSlots = slots;
    atlas->bitmapSlotCap = slotCap;
    atlas->pieceCap = newCap;
    return true;
}

/**
 * \brief Makes room for pages. Capacity at least doubles.
 * \param atlas atlas to grow
 * \param count how many pages must fit
 * \return false on OOM
 */
static bool ensurePages(AssAtlas* atlas, int count) {
    if (count <= atlas->pageCap) {
        return true;
    }
    int newCap = atlas->pageCap > 0 ? atlas->pageCap * 2 : 2;
    if (newCap < count) {
        newCap = count;
    }
    AssAtlasPage* pages = (AssAtlasPage*) realloc(atlas->pages, (size_t) newCap * sizeof(AssAtlasPage));
    if (pages == NULL) {
        return false;
    }
    atlas->pages = pages;
    atlas->pageCap = newCap;
    return true;
}

/**
 * \brief Links every piece to the closest earlier piece showing the same bitmap.
 *
 * The same bitmap means the same pixels, size and stride. The link is stored in each piece's sameAs.
 * \param atlas atlas whose pieces to link
 */
static void linkRepeats(AssAtlas* atlas) {
    const uint32_t mask = (uint32_t) atlas->bitmapSlotCap - 1;

    // Set all slot to -1
    memset(atlas->bitmapSlots, 0xFF, (size_t) atlas->bitmapSlotCap * sizeof(int));

    for (int i = 0; i < atlas->pieceCount; i++) {
        AssAtlasPiece* p = &atlas->pieces[i];
        p->sameAs = -1;

        // Linear probing from a Fibonacci hash of the pointer. At most half the slots are used, so
        // the probe always ends on a match or an empty slot.
        uint32_t slot = (uint32_t) (((uint64_t) (uintptr_t) p->bitmap * 0x9E3779B97F4A7C15ULL) >> 32) & mask;

        for (; atlas->bitmapSlots[slot] >= 0; slot = (slot + 1) & mask) {
            const AssAtlasPiece* q = &atlas->pieces[atlas->bitmapSlots[slot]];
            if (q->bitmap == p->bitmap && q->w == p->w && q->h == p->h && q->stride == p->stride) {
                p->sameAs = atlas->bitmapSlots[slot];
                break;
            }
        }

        atlas->bitmapSlots[slot] = i;
    }
}

/**
 * \brief The texels a piece adds to a page.
 * \param atlas atlas holding the pieces
 * \param first first piece of the page's run
 * \param i piece to add
 * \return the piece's pixel count, or 0 if the run already has its bitmap
 */
static long long runTexels(const AssAtlas* atlas, int first, int i) {
    const AssAtlasPiece* p = &atlas->pieces[i];
    return p->sameAs >= first ? 0 : (long long) p->w * p->h;
}

/**
 * \brief qsort comparator ordering pack entries tallest first, then widest.
 *
 * The skyline wastes little that way. Ties keep list order, so the layout is deterministic.
 * \param a first AssAtlasPackEntry
 * \param b second AssAtlasPackEntry
 * \return negative if a goes first, positive if b does
 */
static int compareTallestFirst(const void* a, const void* b) {
    const AssAtlasPackEntry* p = (const AssAtlasPackEntry*) a;
    const AssAtlasPackEntry* q = (const AssAtlasPackEntry*) b;
    if (p->h != q->h) {
        return q->h - p->h;
    }

    if (p->w != q->w) {
        return q->w - p->w;
    }

    return p->piece - q->piece;
}

/**
 * \brief Picks the width of a roughly square page.
 *
 * One as wide as the max texture size (often 16384) would turn a row of large signs into a
 * ~16384 x 1080 upload. Powers of two keep page sizes, and so textures, stable from frame to frame.
 * \param areaPixelCount texels the page must hold
 * \param widest width of the widest piece
 * \param maxSize the largest texture size
 * \return a power of two from MIN_PAGE_WIDTH on, capped at maxSize
 */
static int pageWidth(long long areaPixelCount, int widest, int maxSize) {
    int width = MIN_PAGE_WIDTH;
    while ((long long) width * width < areaPixelCount && width < maxSize) {
        width *= 2;
    }

    while (width < widest && width < maxSize) {
        width *= 2;
    }

    return width < maxSize ? width : maxSize;
}

/**
 * \brief Packs the run pieces[first, end[ into one page, tallest first, storing each distinct
 * bitmap once.
 *
 * Placement order is free: only the pieces' list order decides the paint order. Pieces sit edge to
 * edge: they are drawn 1:1 with GL_NEAREST, so no fragment samples outside its own piece.
 * \param atlas atlas holding the pieces
 * \param first first piece of the run
 * \param end one past the run's last piece
 * \param usedW receives the packed width
 * \param usedH receives the packed height
 * \return false if the run doesn't fit in one page
 */
static bool packPage(AssAtlas* atlas, int first, int end, int* usedW, int* usedH) {
    long long areaPixelCount = 0;
    int widest = 0;
    for (int i = first; i < end; i++) {
        areaPixelCount += runTexels(atlas, first, i);
        if (atlas->pieces[i].w > widest) {
            widest = atlas->pieces[i].w;
        }
    }

    // The page is as tall as the max texture size: what it actually uses is read back afterward.
    assSkylineReset(&atlas->skyline, pageWidth(areaPixelCount, widest, atlas->maxPageSize), atlas->maxPageSize);
    *usedW = 0;
    *usedH = 0;
    for (int k = 0; k < atlas->pieceCount; k++) {
        const int i = atlas->packOrder[k].piece;
        AssAtlasPiece* p = &atlas->pieces[i];
        if (i < first || i >= end || p->sameAs >= first) {
            continue;
        }
        if (!assSkylineAdd(&atlas->skyline, p->w, p->h, &p->atlasX, &p->atlasY)) {
            return false;
        }
        if (p->atlasX + p->w > *usedW) {
            *usedW = p->atlasX + p->w;
        }
        if (p->atlasY + p->h > *usedH) {
            *usedH = p->atlasY + p->h;
        }
    }
    // In list order, so a repeat's copy, possibly a repeat itself, is already placed.
    for (int i = first; i < end; i++) {
        AssAtlasPiece* p = &atlas->pieces[i];
        if (p->sameAs < first) {
            continue;
        }
        p->atlasX = atlas->pieces[p->sameAs].atlasX;
        p->atlasY = atlas->pieces[p->sameAs].atlasY;
    }
    return true;
}

/**
 * \brief Adds a page holding the maximum pieces from first on that fits in it.
 * \param atlas atlas to add the page to
 * \param first first piece of the run
 * \return where the run ends, or -1 on OOM
 */
static int addPage(AssAtlas* atlas, int first) {
    // Start from the longest run whose distinct bitmaps don't add up to more than a whole page.
    const long long pagePixelCount = (long long) atlas->maxPageSize * atlas->maxPageSize;

    long long runArea = runTexels(atlas, first, first);
    int end = first + 1;
    while (end < atlas->pieceCount) {
        const long long piecePixelCount = runTexels(atlas, first, end);
        if (runArea + piecePixelCount > pagePixelCount) {
            break;
        }
        runArea += piecePixelCount;
        end++;
    }

    int usedW, usedH;
    while (!packPage(atlas, first, end, &usedW, &usedH)) {
        // Packing never fills a page completely: retry without the run's last 1/8 of texels, and at
        // least its last piece. A lone piece always fits, as larger ones were dropped.
        const long long target = runArea - runArea / 8;
        runArea = runTexels(atlas, first, first);
        int shorter = first + 1;
        while (shorter < end - 1) {
            const long long piecePixelCount = runTexels(atlas, first, shorter);
            if (runArea + piecePixelCount > target) {
                break;
            }
            runArea += piecePixelCount;
            shorter++;
        }
        end = shorter;
    }
    if (!ensurePages(atlas, atlas->pageCount + 1)) {
        return -1;
    }
    atlas->pages[atlas->pageCount++] = (AssAtlasPage) {first, end - first, usedW, usedH};
    return end;
}

void assAtlasPack(AssAtlas* atlas, const ASS_Image* images) {
    atlas->pieceCount = 0;
    atlas->pageCount = 0;

    int count = 0;
    for (const ASS_Image* img = images; img != NULL; img = img->next) {
        if (!isEmpty(img)) {
            count++;
        }
    }

    if (count == 0 || !ensurePieces(atlas, count)) {
        return;
    }

    for (const ASS_Image* img = images; img != NULL; img = img->next) {
        if (isEmpty(img)) {
            continue;
        }
        if (img->w > atlas->maxPageSize || img->h > atlas->maxPageSize) {
            // This should never happen.
            LOGE("dropping %dx%d image: larger than max atlas size %d", img->w, img->h, atlas->maxPageSize);
            continue;
        }
        const int i = atlas->pieceCount++;
        atlas->pieces[i] = (AssAtlasPiece) {
            .dstX = img->dst_x,
            .dstY = img->dst_y,
            .w = img->w,
            .h = img->h,
            .color = img->color,
            .bitmap = img->bitmap,
            .stride = img->stride,
        };
        atlas->packOrder[i] = (AssAtlasPackEntry) {img->h, img->w, i};
    }

    if (atlas->pieceCount == 0) {
        return;
    }

    linkRepeats(atlas);
    qsort(atlas->packOrder, (size_t) atlas->pieceCount, sizeof(AssAtlasPackEntry), compareTallestFirst);

    // A frame that fits in one page is the case where the first page's run is the whole list.
    for (int first = 0; first < atlas->pieceCount;) {
        const int end = addPage(atlas, first);
        if (end < 0) { // OOM: keep the pages added so far
            atlas->pieceCount = first;
            return;
        }
        first = end;
    }
}

void assAtlasFillPage(const AssAtlas* atlas, const AssAtlasPage* page, unsigned char* buf) {
    const size_t rowBytes = (size_t) page->usedW;
    for (int i = page->firstPiece; i < page->firstPiece + page->pieceCount; i++) {
        const AssAtlasPiece* p = &atlas->pieces[i];
        if (p->sameAs >= page->firstPiece) {
            continue; // its copy on this page holds the texels
        }
        unsigned char* dst = buf + (size_t) p->atlasY * rowBytes + p->atlasX;
        const unsigned char* src = p->bitmap;
        for (int row = 0; row < p->h; row++) {
            memcpy(dst, src, (size_t) p->w);
            dst += rowBytes;
            src += p->stride;
        }
    }
}

void assAtlasTruncate(AssAtlas* atlas, int pageCount) {
    if (pageCount >= atlas->pageCount) {
        return;
    }
    atlas->pieceCount = atlas->pages[pageCount].firstPiece;
    atlas->pageCount = pageCount;
}

void assAtlasFree(AssAtlas* atlas) {
    free(atlas->pieces);
    free(atlas->pages);
    free(atlas->packOrder);
    assSkylineFree(&atlas->skyline);
    free(atlas->bitmapSlots);
}
