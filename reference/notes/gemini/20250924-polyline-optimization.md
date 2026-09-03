# Polyline Drawing Performance Analysis - 2025-09-24

## Issue

Drawing a large number of polylines (hundreds) can lead to performance issues and a sluggish user experience.

## Analysis

The core drawing logic for polylines is in `PolyOverlayWithIW.java`. The main performance bottlenecks are:

1.  **Point Projection:** Each point in every polyline is projected from geographical coordinates to screen pixels on every draw call. This is a CPU-intensive operation.
2.  **Object Allocation:** New objects (like `Paint` or `Path` segments) can be allocated during the draw phase, leading to garbage collection and stuttering.
3.  **Overhead of Multiple Overlays:** Each polyline is an overlay, and there is an overhead associated with iterating through the `OverlayManager` and calling `draw` for each one.

With a minimum API level of 23, we can leverage better hardware acceleration support and more efficient drawing techniques.

## Proposed Optimizations

Here are several suggestions to improve polyline drawing performance:

### 1. Polyline Simplification (High Impact)

At lower zoom levels, polylines with many points can be simplified without any noticeable loss of quality. This is the most effective optimization for a large number of complex polylines.

**Suggestion:**

Implement a simplification algorithm (like Ramer-Douglas-Peucker) that reduces the number of points in a polyline based on the current zoom level.

**Implementation in `PolyOverlayWithIW.java`:**

1.  Add a field for the simplification tolerance and a list to hold the simplified points:

    ```java
    public abstract class PolyOverlayWithIW extends OverlayWithIW {
        // ... existing fields
        private double mSimplificationTolerance = 0;
        private final ArrayList<PointL> mSimplifiedPoints = new ArrayList<>();
        // ...
    }
    ```

2.  Add a method to set the simplification tolerance:

    ```java
    /**
     * Sets the tolerance for polyline simplification.
     * A higher tolerance will result in a more simplified polyline.
     * @param tolerance in pixels
     */
    public void setSimplificationTolerance(double tolerance) {
        mSimplificationTolerance = tolerance;
    }
    ```

3.  In the `draw` method, before drawing, apply the simplification:

    ```java
    @Override
    public void draw(final Canvas pCanvas, final Projection pProjection) {
        if (!isVisible(pProjection)) {
            return;
        }

        // ... existing downgrade logic ...

        // Apply simplification
        if (mSimplificationTolerance > 0) {
            mSimplifiedPoints.clear();
            // Assuming mOutline.getPoints() returns the original GeoPoints
            // You would need a method to get the projected points
            // and then apply the simplification.
            // This is a conceptual example. The actual implementation will depend
            // on how you access the projected points.
            // simplify(pProjection, mOutline.getPoints(), mSimplifiedPoints, mSimplificationTolerance);
            // Then, use mSimplifiedPoints for drawing.
        }

        if (mPath != null) {
            drawWithPath(pCanvas, pProjection); // Modify to use simplified points
        } else {
            drawWithLines(pCanvas, pProjection); // Modify to use simplified points
        }
    }
    ```

You would need to implement the `simplify` method, which would contain the Ramer-Douglas-Peucker algorithm.

### 2. Batch Drawing (High Impact)

Instead of adding hundreds of `Polyline` overlays to the `OverlayManager`, create a single custom overlay that manages and draws all the polylines.

**Suggestion:**

Create a `PolylineBatch` overlay that takes a `List<Polyline>`.

**Implementation:**

```java
public class PolylineBatch extends Overlay {

    private List<Polyline> mPolylines;

    public PolylineBatch(List<Polyline> polylines) {
        mPolylines = polylines;
    }

    @Override
    public void draw(Canvas c, Projection p) {
        for (Polyline polyline : mPolylines) {
            // It is important to call the draw method of the polyline
            // with the same canvas and projection.
            // This is a simplified example. For a real implementation,
            // you would want to inline the drawing logic from PolyOverlayWithIW
            // to avoid the overhead of method calls and to batch drawing operations
            // (e.g., draw all lines with the same paint at once).
            polyline.draw(c, p);
        }
    }
}
```

This approach reduces the overhead of the `OverlayManager` and allows for more advanced optimizations within the `PolylineBatch` overlay, such as grouping polylines by `Paint` style and drawing them in a single `drawLines` call.

### 3. Hardware Layer (Conditional Impact)

For relatively static polylines, you can render them to a hardware layer. This will composite the polylines using the GPU, which can be faster than redrawing them on the CPU on every frame.

**Suggestion:**

If you are using a `PolylineBatch` overlay, you can enable a hardware layer for the `MapView` before drawing the batch, and then disable it afterwards. This is an advanced technique and should be used with care, as it can have a high memory overhead.

```java
// In your Activity or View
mapView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
```

### 4. Caching Projected Points (Medium Impact)

The `PolyOverlayWithIW` class already has some caching for hit testing. This could be extended to cache the projected points.

**Suggestion:**

Create a cache that stores the projected points for a given `Projection`. When the map is panned, the cached points can be translated. When the map is zoomed, the cache is invalidated, and the points are re-projected.

This is a more complex optimization, as it requires careful management of the cache and handling of map view changes.

## Recommendations

1.  **Start with Polyline Simplification:** This will likely give you the biggest performance improvement with the least amount of code restructuring.

2.  **Implement Batch Drawing:** If simplification is not enough, move to a batch drawing approach. This will require more changes to your application logic but will provide a significant performance boost.

3.  **Consider Hardware Layers and Caching as advanced optimizations:** These should only be considered if the above two options are not sufficient.

By implementing these optimizations, you can significantly improve the performance of drawing a large number of polylines in your application.
