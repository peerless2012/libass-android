#include "AssSkyline.h"

#include <stddef.h>
#include <stdlib.h>
#include <string.h>

// One step of the skyline: over the columns [x, next node's x), the rows from y on are free.
//
// skyline->nodes[0, count[ are the steps from left to right, level neighbors merged so neighbors
// always differ in y, and nodes[count] marks the right edge: (width, 0). In the example from
// AssSkyline.h, they end up as (0, 7), (2, 5), (4, 6) and (9, 0), then (10, 0).
//
// The spots tried are the left ends of the steps: moving a rectangle left to the start of the step
// it starts on never puts it lower, so no other column fits higher.
struct AssSkylineNode {
    int x, y;
};

bool assSkylineReserve(AssSkyline* skyline, int count) {
    // An empty skyline is one node plus the right-edge marker, and each rectangle adds at most one
    // node: count rectangles need at most count + 2.
    const int nodeCap = count + 2;
    if (nodeCap <= skyline->nodeCap) {
        return true;
    }

    AssSkylineNode* nodes = (AssSkylineNode*) realloc(skyline->nodes, (size_t) nodeCap * sizeof(AssSkylineNode));
    if (nodes == NULL) {
        return false;
    }

    skyline->nodes = nodes;
    skyline->nodeCap = nodeCap;
    return true;
}

void assSkylineReset(AssSkyline* skyline, int width, int maxHeight) {
    skyline->nodes[0] = (AssSkylineNode) {0, 0};
    skyline->nodes[1] = (AssSkylineNode) {width, 0};
    skyline->count = 1;
    skyline->width = width;
    skyline->maxHeight = maxHeight;
}

/**
 * \brief Finds the node a w x h rectangle fits highest under, then leftmost.
 * The rectangle's left edge goes on the node's x.
 * \param skyline skyline to search
 * \param w rectangle width
 * \param h rectangle height
 * \param fitY receives the rectangle's row; meaningless if -1 is returned
 * \return the node or -1 if the rectangle fits nowhere.
 */
static int findHighestNode(const AssSkyline* skyline, int w, int h, int* fitY) {
    const AssSkylineNode* nodes = skyline->nodes;
    int bestNode = -1;
    int bestY = skyline->maxHeight - h + 1;

    for (int i = 0; i < skyline->count && nodes[i].x + w <= skyline->width; i++) {
        const int right = nodes[i].x + w;
        int y = 0;
        for (int k = i; nodes[k].x < right && y < bestY; k++) {
            if (nodes[k].y > y) {
                y = nodes[k].y;
            }
        }
        if (y < bestY) {
            bestY = y;
            bestNode = i;
        }
    }

    *fitY = bestY;
    return bestNode;
}

/**
 * \brief Removes node i, moving the nodes after it, right-edge marker included, down by one.
 * \param skyline skyline to update
 * \param i node to remove
 */
static void removeNode(AssSkyline* skyline, int i) {
    memmove(&skyline->nodes[i], &skyline->nodes[i + 1], (size_t) (skyline->count - i) * sizeof(AssSkylineNode));
    skyline->count--;
}

/**
 * \brief Lowers the skyline over the columns of a rectangle just placed under node i.
 * The last node the rectangle only partly covers keeps its uncovered part, and level neighbors
 * merge, so neighbors always differ in y.
 * \param skyline skyline to update
 * \param i node the rectangle was placed under, its left edge on the node's x
 * \param w rectangle width
 * \param bottom first row below the rectangle: its columns are free from there on
 */
static void lowerSkyline(AssSkyline* skyline, int i, int w, int bottom) {
    AssSkylineNode* nodes = skyline->nodes;
    const int right = nodes[i].x + w;
    // the first node from the rectangle's right edge on
    int end = i + 1;
    while (nodes[end].x < right) {
        end++;
    }

    const bool partial = nodes[end].x > right;
    const int uncoveredY = nodes[end - 1].y;

    // Nodes [i, end[ become node i, plus the uncovered part.
    const int kept = partial ? 2 : 1;
    memmove(&nodes[i + kept], &nodes[end], (size_t) (skyline->count + 1 - end) * sizeof(AssSkylineNode));
    skyline->count += kept - (end - i);
    nodes[i].y = bottom;

    if (partial) {
        nodes[i + 1] = (AssSkylineNode) {right, uncoveredY}; // above `bottom`, so it can't merge
    } else if (i + 1 < skyline->count && nodes[i + 1].y == bottom) {
        removeNode(skyline, i + 1);
    }

    if (i > 0 && nodes[i - 1].y == bottom) {
        removeNode(skyline, i);
    }
}

bool assSkylineAdd(AssSkyline* skyline, int w, int h, int* x, int* y) {
    int fitY;
    const int node = findHighestNode(skyline, w, h, &fitY);
    if (node < 0) {
        return false;
    }

    *x = skyline->nodes[node].x;
    *y = fitY;

    lowerSkyline(skyline, node, w, fitY + h);
    return true;
}

void assSkylineFree(AssSkyline* skyline) {
    free(skyline->nodes);
}
