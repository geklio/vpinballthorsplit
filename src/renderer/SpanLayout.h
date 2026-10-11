// license:GPLv3+

#pragma once

#include <algorithm>
#include <cmath>

// Layout of a playfield spanned over two displays stacked vertically (top display above bottom display),
// for example the two screens of the AYN Thor clamshell handheld.
//
// The scene is rendered once on a tall virtual "canvas" whose pixels all have the same physical size.
// The canvas covers: the top display area, the physical gap between the displays, then the bottom display
// area. The gap part is rendered but never shown, so objects "pass under" the hinge like on a real device.
// Each display shows its slice, scaled by its own pixel density, so one millimeter of table is one
// millimeter of glass on both displays.
//
// The playfield is one rectangle, so the canvas is as wide as the narrower of the two displays. On the
// wider display, the playfield column is centered (plus a user offset) and the sides stay black.
//
// All rectangles use a top-left origin, in pixels.
struct SpanRect
{
   int x = 0, y = 0, w = 0, h = 0;
};

struct SpanLayout
{
   bool valid = false;
   int canvasWidth = 0, canvasHeight = 0;
   SpanRect topSrc, topDst; // Canvas slice shown on the top display, and where it lands on the top display
   SpanRect bottomSrc, bottomDst; // Canvas slice shown on the bottom display, and where it lands on the bottom display
   int gapHeight = 0; // Canvas rows hidden by the hinge

   static SpanLayout Compute(const int topPxW, const int topPxH, const int bottomPxW, const int bottomPxH, //
      const float topPPI, const float bottomPPI, const float gapMM, const int topOffsetX = 0, const int bottomOffsetX = 0)
   {
      SpanLayout l;
      if (topPxW <= 0 || topPxH <= 0 || bottomPxW <= 0 || bottomPxH <= 0 || topPPI <= 0.f || bottomPPI <= 0.f || gapMM < 0.f)
         return l;

      // Physical sizes, in inches
      const double topW = static_cast<double>(topPxW) / topPPI;
      const double topH = static_cast<double>(topPxH) / topPPI;
      const double bottomW = static_cast<double>(bottomPxW) / bottomPPI;
      const double bottomH = static_cast<double>(bottomPxH) / bottomPPI;
      const double gap = static_cast<double>(gapMM) / 25.4;
      const double columnW = std::min(topW, bottomW);

      // Canvas density is the higher one, so no display is upscaled
      const double density = std::max(topPPI, bottomPPI);

      const auto px = [](double v) { return static_cast<int>(std::lround(v)); };
      l.canvasWidth = std::max(1, px(columnW * density));
      const int topRows = std::max(1, px(topH * density));
      l.gapHeight = px(gap * density);
      const int bottomRows = std::max(1, px(bottomH * density));
      l.canvasHeight = topRows + l.gapHeight + bottomRows;

      l.topSrc = { 0, 0, l.canvasWidth, topRows };
      l.bottomSrc = { 0, topRows + l.gapHeight, l.canvasWidth, bottomRows };

      // Destination columns, at each display own density, centered then shifted by the user offset
      const int topColW = std::min(topPxW, std::max(1, px(columnW * topPPI)));
      const int bottomColW = std::min(bottomPxW, std::max(1, px(columnW * bottomPPI)));
      l.topDst = { (topPxW - topColW) / 2 + topOffsetX, 0, topColW, topPxH };
      l.bottomDst = { (bottomPxW - bottomColW) / 2 + bottomOffsetX, 0, bottomColW, bottomPxH };

      l.valid = true;
      return l;
   }
};
