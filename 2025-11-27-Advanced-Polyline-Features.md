# Advanced Polyline Features - November 2025

## Overview
We have introduced advanced styling capabilities for Polylines, enabling rich data visualization directly on the map. This includes support for multi-colored lines, gradients, and data-driven coloring (e.g., coloring a track based on speed or elevation).

## Key Capabilities

### 1. Polychromatic Lines
Instead of a single static color, polylines can now be painted with a list of colors that transition along the path.
*   **Gradients:** Smoothly blend from one color to another between points.
*   **Segments:** Distinct colors for different segments of the line.

### 2. Data-Driven Styling
You can map scalar data (like speed, altitude, heart rate) directly to colors.
*   **`ColorMappingForScalar`:** Automatically maps a range of values (min/max) to a color gradient (e.g., Blue -> Red).
*   **`ColorMappingRanges`:** Define specific color buckets (e.g., < 10km/h = Red, > 10km/h = Green).

## Usage Examples

### Simple Gradient
```java
// Create a gradient from Red to Blue
ColorMapping mapping = new ColorMappingPlain(Color.RED, Color.BLUE);
PolychromaticPaintList paintList = new PolychromaticPaintList(paint, mapping, true);

Polyline line = new Polyline();
line.getOutlinePaintLists().add(paintList);
```

### Speed-Based Coloring
```java
double[] speedData = {10.0, 25.0, 45.0, ...}; // Speed at each point

// Map speeds to a Red-Green gradient
ColorMapping speedMapping = new ColorMappingForScalar(
    speedData,
    Color.RED,   // Slow
    Color.GREEN  // Fast
);

PolychromaticPaintList speedPaint = new PolychromaticPaintList(paint, speedMapping, true);
line.getOutlinePaintLists().add(speedPaint);
```

## Performance Tips
*   **Gradients:** Enabling smooth gradients (`true` in constructor) looks better but is more expensive to draw. For very long lines on low-end devices, consider disabling gradients.
*   **Pre-computation:** The `ColorMapping` classes are efficient, but for massive datasets, consider pre-calculating colors if the data doesn't change.
