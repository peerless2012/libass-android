#ifndef LIBASS_ANDROID_ASS_ATLAS_H
#define LIBASS_ANDROID_ASS_ATLAS_H

#include <stdbool.h>
#include <stdint.h>

#include "AssSkyline.h"
#include "ass/ass.h"

#ifdef __cplusplus
extern "C" {
#endif

// Where a frame's subtitle bitmaps go in the atlas pages. CPU side only: AssOverlayGl.c uploads
// each page into a texture.

// One libass image, and where its bitmap sits in its page.
typedef struct {
    int atlasX;
    int atlasY;
    int dstX;
    int dstY;
    int w;
    int h;
    uint32_t color; // libass RGBA, A being transparency
    // Owned by libass: only valid until the next ass_render_frame call.
    const unsigned char* bitmap;
    int stride;
    // The closest earlier piece showing the very same bitmap, or -1. When that one is on the same
    // page, this piece shares its texels instead of being stored again.
    int sameAs;
} AssAtlasPiece;

// A page is one atlas texture: a single 8-bit texture (GL_R8, or GL_ALPHA on GLES2) that many pieces
// are packed into side by side.
//
// Why more than one page: a texture can't be larger than maxPageSize (GL_MAX_TEXTURE_SIZE).
// When a frame's bitmaps don't all fit in one maxPageSize x maxPageSize texture,
// the rest go in a second page, then a third...
typedef struct {
    int firstPiece, pieceCount;
    // The packed area, from (0, 0): the part of the texture that is uploaded. The texture itself may
    // be larger, as AssOverlayGl.c reuses it from frame to frame.
    int usedW, usedH;
} AssAtlasPage;

typedef struct {
    int h, w, piece;
} AssAtlasPackEntry;

typedef struct {
    int maxPageSize; // the largest texture the GPU takes (GL_MAX_TEXTURE_SIZE)

    AssAtlasPiece* pieces; // a.k.a libass bitmap
    int pieceCount; // the pieces of the current frame, valid in pieces[0, pieceCount[
    int pieceCap; // how many pieces are allocated; never shrinks

    AssAtlasPage* pages; // a.k.a. texture: one per page
    int pageCount; // the pages of the current frame, valid in pages[0, pageCount[
    int pageCap; // how many pages are allocated; never shrinks

    // Packing scratch, sized along with the pieces.
    AssAtlasPackEntry* packOrder;
    AssSkyline skyline;
    int* bitmapSlots; // hash table of piece indices, -1 when empty
    int bitmapSlotCap; // a power of two, at least twice pieceCap
} AssAtlas;

/**
 * \brief Lays out images from scratch.
 *
 * Images larger than a page are dropped, which takes a frame larger than the GPU's max texture
 * size. On OOM, fewer images are laid out, possibly none.
 * \param atlas atlas to lay out into
 * \param images libass image list, as returned by ass_render_frame
 */
void assAtlasPack(AssAtlas* atlas, const ASS_Image* images);

/**
 * \brief Writes a page's bitmaps into a buffer.
 *
 * Bytes between the pieces are left as they are: they are never sampled.
 * \param atlas atlas holding the page's pieces
 * \param page page to fill
 * \param buf a usedW x usedH area with rows usedW bytes apart
 */
void assAtlasFillPage(const AssAtlas* atlas, const AssAtlasPage* page, unsigned char* buf);

/**
 * \brief Drops the pages from pageCount on, with their pieces.
 * \param atlas atlas to truncate
 * \param pageCount how many pages to keep
 */
void assAtlasTruncate(AssAtlas* atlas, int pageCount);

/**
 * \brief Frees the atlas's arrays. The AssAtlas struct itself belongs to the caller.
 * \param atlas atlas to free
 */
void assAtlasFree(AssAtlas* atlas);

#ifdef __cplusplus
}
#endif

#endif // LIBASS_ANDROID_ASS_ATLAS_H
