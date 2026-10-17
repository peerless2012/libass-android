#ifndef LIBASS_ANDROID_ASS_SKYLINE_H
#define LIBASS_ANDROID_ASS_SKYLINE_H

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct AssSkylineNode AssSkylineNode;

// Packs rectangles into a width x maxHeight area, each at the highest spot it fits - the closest
// to row 0 - then the leftmost. Only the bottom edge of what is packed is tracked - the skyline,
// hanging from row 0 - so the space above a rectangle that sticks out past the one it sits under
// is lost; fed tallest first, little is.
//
// For example, A 4x5, B 3x4, C 5x2 and D 2x2 packed in that order into a 10 wide area, drawn as
// the page is laid out in memory, row 0 first:
//
//      0123456789
//   0  AAAABBB##.   A at (0, 0): the area is empty.
//   1  AAAABBB##.   B at (4, 0): beside A is higher than under it.
//   2  AAAABBB##.   C at (4, 4): under B is higher than under A, and from x = 7 it would stick out
//   3  AAAABBB##.                past the right edge.
//   4  AAAACCCCC.   D at (0, 5): under A is higher than under C, and from x = 9 it would stick out.
//   5  DD..CCCCC.
//   6  DD........   ## is lost: C closes it off from below, and only C's bottom is tracked.
//                   Column 9 is still free from row 0.
typedef struct {
    AssSkylineNode* nodes;
    int count, nodeCap;
    int width, maxHeight;
} AssSkyline;

/**
 * \brief Makes room for packing count rectangles between two resets. Never shrinks.
 * \param skyline skyline to grow
 * \param count how many rectangles a packing may hold
 * \return false on OOM, leaving the room as it was
 */
bool assSkylineReserve(AssSkyline* skyline, int count);

/**
 * \brief Starts an empty width x maxHeight area, keeping the room reserved.
 * \param skyline skyline to reset
 * \param width area width
 * \param maxHeight area height: a rectangle that would reach past it doesn't fit
 */
void assSkylineReset(AssSkyline* skyline, int width, int maxHeight);

/**
 * \brief Places a w x h rectangle at the highest spot it fits, then the leftmost.
 *
 * At most as many rectangles as reserved may be placed between two resets.
 * \param skyline skyline to place the rectangle in
 * \param w rectangle width
 * \param h rectangle height
 * \param x receives the rectangle's first column
 * \param y receives the rectangle's first row
 * \return false if the rectangle fits nowhere, leaving the skyline, x and y unchanged
 */
bool assSkylineAdd(AssSkyline* skyline, int w, int h, int* x, int* y);

/**
 * \brief Frees the room reserved.
 * \param skyline skyline to free
 */
void assSkylineFree(AssSkyline* skyline);

#ifdef __cplusplus
}
#endif

#endif // LIBASS_ANDROID_ASS_SKYLINE_H
