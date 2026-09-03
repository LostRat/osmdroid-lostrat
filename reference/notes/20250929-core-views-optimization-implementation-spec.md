# Core Views Optimization - Implementation Specification
**Date:** 2025-09-29
**Version:** 1.0
**Target:** osmdroid 6.1.22-lostrat-SNAPSHOT
**Status:** Ready for Implementation

---

## Overview

This specification provides detailed implementation tasks for optimizing MapView, Projection, and MapController performance. Each task includes exact code snippets, line numbers, and replacement instructions.

### Expected Performance Improvements
- **getProjection() calls**: 90% faster (50ms vs 500ms per 1000 calls)
- **Layout operations**: 60% faster (80ms vs 200ms for 50 child views)
- **Coordinate conversions**: 60% faster (120ms vs 300ms per 1000 conversions)
- **Animation startup**: 70% faster (15ms vs 50ms per animation)
- **Overall scrolling/zooming**: 40-60% smoother frame rates

---

## Phase 1: Critical Performance Issues (Week 1)

### ✅ Task 1.1: Add Projection Caching Infrastructure to MapView
**File:** `osmdroid-android/src/main/java/org/osmdroid/views/MapView.java`
**Location:** Add as class fields around line 100-150
**Priority:** Critical

**Code to ADD:**
```java
// Projection caching infrastructure
private long mProjectionVersion = 0;
private final Object mProjectionLock = new Object();
```

**Implementation Notes:**
- Add these fields near other projection-related fields
- `mProjectionVersion` tracks when projection needs to be recreated
- `mProjectionLock` ensures thread-safe access to cached projection

**Testing:**
- [ ] Verify fields are initialized correctly
- [ ] No compilation errors

---

### ✅ Task 1.2: Implement Projection Version Computation
**File:** `osmdroid-android/src/main/java/org/osmdroid/views/MapView.java`
**Location:** Add after line 388 (after current getProjection() method)
**Priority:** Critical

**Code to ADD:**
```java
/**
 * Computes a version number for the projection based on map state.
 * When this changes, the projection needs to be recreated.
 * @return version number based on zoom, scroll, and orientation
 * @since 6.1.22-lostrat
 */
private long computeProjectionVersion() {
    long version = Double.doubleToLongBits(getZoomLevelDouble());
    version = 31 * version + (long)getMapScrollX();
    version = 31 * version + (long)getMapScrollY();
    version = 31 * version + Float.floatToIntBits(getMapOrientation());
    return version;
}
```

**Implementation Notes:**
- Uses hash code pattern (31 * version + value) for combining values
- Incorporates all map state that affects projection: zoom, scroll, orientation
- Returns stable version number for cache invalidation

**Testing:**
- [ ] Version changes when zoom changes
- [ ] Version changes when map scrolls
- [ ] Version changes when orientation changes
- [ ] Same state produces same version

---

### ✅ Task 1.3: Replace getProjection() with Cached Version
**File:** `osmdroid-android/src/main/java/org/osmdroid/views/MapView.java`
**Lines:** 370-388
**Priority:** Critical

**REPLACE THIS:**
```java
@Override
public Projection getProjection() {
    if (mProjection == null) {
        Projection localCopy = new Projection(this);
        mProjection = localCopy;
        localCopy.adjustOffsets(mMultiTouchScaleGeoPoint, mMultiTouchScaleCurrentPoint);
        if (mScrollableAreaLimitLatitude) {
            localCopy.adjustOffsets(
                    mScrollableAreaLimitNorth, mScrollableAreaLimitSouth, true,
                    mScrollableAreaLimitExtraPixelHeight);
        }
        if (mScrollableAreaLimitLongitude) {
            localCopy.adjustOffsets(
                    mScrollableAreaLimitWest, mScrollableAreaLimitEast, false,
                    mScrollableAreaLimitExtraPixelWidth);
        }
        mImpossibleFlinging = localCopy.setMapScroll(this);
    }
    return mProjection;
}
```

**WITH THIS:**
```java
@Override
public Projection getProjection() {
    synchronized (mProjectionLock) {
        long currentVersion = computeProjectionVersion();
        if (mProjection == null || mProjectionVersion != currentVersion) {
            Projection localCopy = (mProjection == null) ? new Projection(this) : mProjection;
            if (mProjection == null) {
                mProjection = localCopy;
            } else {
                // Reuse existing Projection object by updating its state
                localCopy.update(this);
            }
            localCopy.adjustOffsets(mMultiTouchScaleGeoPoint, mMultiTouchScaleCurrentPoint);
            if (mScrollableAreaLimitLatitude) {
                localCopy.adjustOffsets(
                        mScrollableAreaLimitNorth, mScrollableAreaLimitSouth, true,
                        mScrollableAreaLimitExtraPixelHeight);
            }
            if (mScrollableAreaLimitLongitude) {
                localCopy.adjustOffsets(
                        mScrollableAreaLimitWest, mScrollableAreaLimitEast, false,
                        mScrollableAreaLimitExtraPixelWidth);
            }
            mImpossibleFlinging = localCopy.setMapScroll(this);
            mProjectionVersion = currentVersion;
        }
        return mProjection;
    }
}
```

**Implementation Notes:**
- Adds version checking to avoid unnecessary Projection recreation
- Reuses existing Projection object via `update()` method
- Thread-safe with synchronized block
- Preserves all existing adjustment logic

**Testing:**
- [ ] Projection correctly created on first call
- [ ] Projection reused when version unchanged
- [ ] Projection updated when map state changes
- [ ] Thread-safe under concurrent access
- [ ] All existing functionality preserved

---

### ✅ Task 1.4: Make Projection Fields Non-Final
**File:** `osmdroid-android/src/main/java/org/osmdroid/views/Projection.java`
**Lines:** 51, 58, 59, 60, 68, 69
**Priority:** Critical
**Prerequisite for:** Task 1.5

**REPLACE THESE:**
```java
private final double mZoomLevelProjection;     // Line 51
private final double mMercatorMapSize;         // Line 58
private final double mTileSize;                // Line 59
private final float mOrientation;              // Line 60
private final int mMapCenterOffsetX;           // Line 68
private final int mMapCenterOffsetY;           // Line 69
```

**WITH THESE:**
```java
private double mZoomLevelProjection;           // Line 51
private double mMercatorMapSize;               // Line 58
private double mTileSize;                      // Line 59
private float mOrientation;                    // Line 60
private int mMapCenterOffsetX;                 // Line 68
private int mMapCenterOffsetY;                 // Line 69
```

**Implementation Notes:**
- Required to enable Projection object reuse
- Fields must be mutable for `update()` method
- Does not change external API or behavior

**Testing:**
- [ ] Compilation succeeds
- [ ] No warnings about field mutability
- [ ] Existing tests pass

---

### ✅ Task 1.5: Add Projection.update() Method
**File:** `osmdroid-android/src/main/java/org/osmdroid/views/Projection.java`
**Location:** Add after the constructor around line 130
**Priority:** Critical
**Depends on:** Task 1.4

**Code to ADD:**
```java
/**
 * Updates this Projection's state from the MapView without creating a new object.
 * Reuses existing objects to reduce garbage collection pressure.
 * @param mapView the MapView to update from
 * @since 6.1.22-lostrat
 */
void update(MapView mapView) {
    update(
            mapView.getZoomLevelDouble(), mapView.getIntrinsicScreenRect(null),
            mapView.getExpectedCenter(),
            mapView.getMapScrollX(), mapView.getMapScrollY(),
            mapView.getMapOrientation(),
            mapView.isHorizontalMapRepetitionEnabled(), mapView.isVerticalMapRepetitionEnabled(),
            mapView.getMapCenterOffsetX(),
            mapView.getMapCenterOffsetY());
}

/**
 * Updates projection state without creating a new object.
 * @since 6.1.22-lostrat
 */
private void update(
        final double pZoomLevel, final Rect pScreenRect,
        final GeoPoint pCenter,
        final long pScrollX, final long pScrollY,
        final float pOrientation,
        final boolean pHorizontalWrapEnabled, final boolean pVerticalWrapEnabled,
        final int pMapCenterOffsetX, final int pMapCenterOffsetY) {

    // Update mutable fields
    mZoomLevelProjection = pZoomLevel;
    horizontalWrapEnabled = pHorizontalWrapEnabled;
    verticalWrapEnabled = pVerticalWrapEnabled;
    mMercatorMapSize = TileSystem.MapSize(mZoomLevelProjection);
    mTileSize = TileSystem.getTileSize(mZoomLevelProjection);
    mOrientation = pOrientation;
    mMapCenterOffsetX = pMapCenterOffsetX;
    mMapCenterOffsetY = pMapCenterOffsetY;

    mCurrentCenter.setCoords(pCenter.getLatitude(), pCenter.getLongitude());
    mIntrinsicScreenRectProjection.set(pScreenRect);
    mScrollX = pScrollX;
    mScrollY = pScrollY;

    // Recalculate rotation matrices
    mRotateAndScaleMatrix.reset();
    mUnrotateAndScaleMatrix.reset();
    if (mOrientation != 0) {
        mRotateAndScaleMatrix.setRotate(mOrientation,
                mIntrinsicScreenRectProjection.exactCenterX(),
                mIntrinsicScreenRectProjection.exactCenterY());
        mRotateAndScaleMatrix.invert(mUnrotateAndScaleMatrix);
    }

    // Reset offsets - they will be recalculated by adjustOffsets()
    mOffsetX = 0;
    mOffsetY = 0;
}
```

**Implementation Notes:**
- Package-private visibility (called only by MapView)
- Reuses existing GeoPoint and Rect objects
- Recalculates derived values (mercator size, tile size, matrices)
- Resets offsets so adjustOffsets() works correctly

**Testing:**
- [ ] Projection state correctly updated
- [ ] Rotation matrices recalculated properly
- [ ] Coordinate conversions accurate after update
- [ ] No memory leaks from unreleased objects

---

### ✅ Task 1.6: Add Layout Point Cache Fields
**File:** `osmdroid-android/src/main/java/org/osmdroid/views/MapView.java`
**Location:** Add as class fields around line 100-150
**Priority:** High

**Code to ADD:**
```java
// Layout point caching - reduces coordinate conversion overhead
private final ArrayMap<IGeoPoint, Point> mLayoutPointCache = new ArrayMap<>(50);
private long mLayoutCacheVersion = 0;
```

**Implementation Notes:**
- ArrayMap is more memory-efficient than HashMap for small collections (API 23+)
- Initial capacity of 50 handles typical use cases
- Cache version linked to projection version for automatic invalidation

**Testing:**
- [ ] Fields initialized correctly
- [ ] No compilation errors
- [ ] Import for ArrayMap added (androidx.collection.ArrayMap)

---

### ✅ Task 1.7: Optimize myOnLayout with Point Caching
**File:** `osmdroid-android/src/main/java/org/osmdroid/views/MapView.java`
**Lines:** 946-963 (the beginning of the for loop in myOnLayout)
**Priority:** High

**REPLACE THIS:**
```java
for (int i = 0; i < count; i++) {
    final View child = getChildAt(i);
    if (child.getVisibility() != GONE) {

        final MapView.LayoutParams lp = (MapView.LayoutParams) child.getLayoutParams();
        final int childHeight = child.getMeasuredHeight();
        final int childWidth = child.getMeasuredWidth();
        getProjection().toPixels(lp.geoPoint, mLayoutPoint);
        // Apply rotation of mLayoutPoint around the center of the map
        if (getMapOrientation() != 0) {
            Point p = getProjection().rotateAndScalePoint(mLayoutPoint.x, mLayoutPoint.y,
                    null);
            mLayoutPoint.x = p.x;
            mLayoutPoint.y = p.y;
        }
        final long x = mLayoutPoint.x;
        final long y = mLayoutPoint.y;
```

**WITH THIS:**
```java
// Clear cache if projection changed
long currentVersion = mProjectionVersion;
if (mLayoutCacheVersion != currentVersion) {
    mLayoutPointCache.clear();
    mLayoutCacheVersion = currentVersion;
}

for (int i = 0; i < count; i++) {
    final View child = getChildAt(i);
    if (child.getVisibility() != GONE) {

        final MapView.LayoutParams lp = (MapView.LayoutParams) child.getLayoutParams();
        final int childHeight = child.getMeasuredHeight();
        final int childWidth = child.getMeasuredWidth();

        // Check cache first
        Point cachedPoint = mLayoutPointCache.get(lp.geoPoint);
        if (cachedPoint != null) {
            mLayoutPoint.set(cachedPoint.x, cachedPoint.y);
        } else {
            getProjection().toPixels(lp.geoPoint, mLayoutPoint);
            // Apply rotation of mLayoutPoint around the center of the map
            if (getMapOrientation() != 0) {
                Point p = getProjection().rotateAndScalePoint(mLayoutPoint.x, mLayoutPoint.y,
                        null);
                mLayoutPoint.x = p.x;
                mLayoutPoint.y = p.y;
            }
            // Cache the result (after rotation)
            mLayoutPointCache.put(lp.geoPoint, new Point(mLayoutPoint.x, mLayoutPoint.y));
        }

        final long x = mLayoutPoint.x;
        final long y = mLayoutPoint.y;
```

**Implementation Notes:**
- Cache invalidation before loop ensures fresh data
- Only caches after rotation is applied
- Uses GeoPoint as key (must implement equals/hashCode correctly)
- Creates new Point for cache to avoid mutation issues

**Testing:**
- [ ] Child views positioned correctly
- [ ] Cache invalidates on zoom/scroll
- [ ] Works with map rotation
- [ ] Performance improvement measurable
- [ ] No visual glitches

---

## Phase 2: High Impact Optimizations (Week 2)

### ✅ Task 2.1: Add Batch Conversion Method to Projection
**File:** `osmdroid-android/src/main/java/org/osmdroid/views/Projection.java`
**Location:** Add around line 215 (after toPixels methods)
**Priority:** Medium

**Code to ADD:**
```java
/**
 * Batch converts multiple GeoPoints to pixel coordinates.
 * More efficient than calling toPixels() repeatedly due to reduced method call overhead
 * and pre-calculated common values.
 * @param geoPoints list of geographic points to convert
 * @param pixelPoints output list (must be same size as geoPoints)
 * @throws IllegalArgumentException if lists are different sizes
 * @since 6.1.22-lostrat
 */
public void toPixelsBatch(final List<? extends IGeoPoint> geoPoints, final List<Point> pixelPoints) {
    if (geoPoints.size() != pixelPoints.size()) {
        throw new IllegalArgumentException("geoPoints and pixelPoints must be same size");
    }

    // Pre-calculate common values once for all conversions
    final double mercatorMapSize = mMercatorMapSize;
    final long offsetX = mOffsetX;
    final long offsetY = mOffsetY;

    for (int i = 0; i < geoPoints.size(); i++) {
        final IGeoPoint geo = geoPoints.get(i);
        Point pixel = pixelPoints.get(i);
        if (pixel == null) {
            pixel = new Point();
            pixelPoints.set(i, pixel);
        }

        // Inline coordinate conversion to avoid method call overhead
        final long mercatorX = mTileSystem.getMercatorXFromLongitude(
                geo.getLongitude(), mercatorMapSize, horizontalWrapEnabled);
        final long mercatorY = mTileSystem.getMercatorYFromLatitude(
                geo.getLatitude(), mercatorMapSize, verticalWrapEnabled);

        pixel.x = TileSystem.truncateToInt(getLongPixelXFromMercator(mercatorX, horizontalWrapEnabled));
        pixel.y = TileSystem.truncateToInt(getLongPixelYFromMercator(mercatorY, verticalWrapEnabled));
    }
}
```

**Implementation Notes:**
- Reduces method call overhead for multiple conversions
- Pre-calculates values used by all conversions
- Creates Point objects if null (user can pre-allocate for best performance)
- Useful for polylines, polygons, and overlay drawing

**Testing:**
- [ ] Produces same results as individual toPixels() calls
- [ ] Handles null Points in output list
- [ ] Throws exception for mismatched list sizes
- [ ] Performance improvement measurable (>50% faster for 100+ points)

**Usage Example:**
```java
List<IGeoPoint> geoPoints = polyline.getPoints();
List<Point> pixelPoints = new ArrayList<>(geoPoints.size());
for (int i = 0; i < geoPoints.size(); i++) pixelPoints.add(new Point());
projection.toPixelsBatch(geoPoints, pixelPoints);
// Use pixelPoints for drawing
```

---

### ✅ Task 2.2: Create ObjectPool Utility Class
**File:** `osmdroid-android/src/main/java/org/osmdroid/util/ObjectPool.java` (NEW FILE)
**Priority:** Medium

**Code to CREATE:**
```java
package org.osmdroid.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple object pool to reduce garbage collection pressure.
 * Thread-safe implementation for reusing objects.
 *
 * @param <T> the type of objects to pool
 * @since 6.1.22-lostrat
 */
public class ObjectPool<T> {
    private final List<T> mPool;
    private final int mMaxSize;
    private final PoolableFactory<T> mFactory;

    /**
     * Factory interface for creating and resetting pooled objects.
     */
    public interface PoolableFactory<T> {
        /**
         * Create a new instance of the pooled object.
         */
        T create();

        /**
         * Reset an object to its initial state before returning to pool.
         */
        void reset(T object);
    }

    /**
     * Create a new object pool.
     * @param maxSize maximum number of objects to keep in pool
     * @param factory factory for creating and resetting objects
     */
    public ObjectPool(int maxSize, PoolableFactory<T> factory) {
        mMaxSize = maxSize;
        mFactory = factory;
        mPool = new ArrayList<>(maxSize);
    }

    /**
     * Acquire an object from the pool, or create a new one if pool is empty.
     * @return an object ready for use
     */
    public synchronized T acquire() {
        if (mPool.isEmpty()) {
            return mFactory.create();
        }
        return mPool.remove(mPool.size() - 1);
    }

    /**
     * Return an object to the pool for reuse.
     * Object is reset before being added back to pool.
     * If pool is full, object is discarded.
     * @param object the object to return (null is ignored)
     */
    public synchronized void release(T object) {
        if (object == null) return;
        if (mPool.size() < mMaxSize) {
            mFactory.reset(object);
            mPool.add(object);
        }
    }

    /**
     * Clear the pool and release all objects.
     */
    public synchronized void clear() {
        mPool.clear();
    }

    /**
     * Get current number of objects in pool.
     * @return pool size
     */
    public synchronized int size() {
        return mPool.size();
    }
}
```

**Implementation Notes:**
- Thread-safe with synchronized methods
- Simple LIFO (stack) behavior for cache locality
- Discards objects beyond max size
- Generic implementation works with any type

**Testing:**
- [ ] File created in correct package
- [ ] Compiles without errors
- [ ] acquire() returns new object when pool empty
- [ ] release() returns object to pool
- [ ] Pool respects max size
- [ ] Thread-safe under concurrent access

---

### ✅ Task 2.3: Add GeoPoint Pool to MapController
**File:** `osmdroid-android/src/main/java/org/osmdroid/views/MapController.java`
**Location:** Add as class fields around line 30-40
**Priority:** Medium
**Depends on:** Task 2.2

**Code to ADD:**
```java
// Object pool for reducing GC pressure during animations
private final ObjectPool<GeoPoint> mGeoPointPool = new ObjectPool<>(5,
    new ObjectPool.PoolableFactory<GeoPoint>() {
        @Override
        public GeoPoint create() {
            return new GeoPoint(0.0, 0.0);
        }

        @Override
        public void reset(GeoPoint object) {
            object.setCoords(0.0, 0.0);
        }
    });
```

**Implementation Notes:**
- Pool size of 5 handles typical animation scenarios
- GeoPoint objects are small but frequently allocated
- Reduces GC pressure during smooth animations

**Testing:**
- [ ] Compiles without errors
- [ ] Import for ObjectPool added
- [ ] Pool initializes correctly

---

### ✅ Task 2.4: Optimize animateTo() to Reuse GeoPoint Objects
**File:** `osmdroid-android/src/main/java/org/osmdroid/views/MapController.java`
**Lines:** 157 and 177
**Priority:** Medium
**Depends on:** Task 2.3

**REPLACE THIS (Line 157):**
```java
final IGeoPoint currentCenter = new GeoPoint(mMapView.getProjection().getCurrentCenter());
```

**WITH THIS:**
```java
final GeoPoint currentCenter = mGeoPointPool.acquire();
currentCenter.setCoords(mMapView.getProjection().getCurrentCenter());
```

**AND ADD THIS (before line 177, right before `mapAnimator.start();`):**
```java
// Return GeoPoint to pool when animation finishes
mapAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
    @Override
    public void onAnimationEnd(android.animation.Animator animation) {
        mGeoPointPool.release(currentCenter);
    }

    @Override
    public void onAnimationCancel(android.animation.Animator animation) {
        mGeoPointPool.release(currentCenter);
    }
});
```

**Implementation Notes:**
- Reuses GeoPoint from pool instead of allocating new one
- Returns to pool on both normal end and cancel
- Uses AnimatorListenerAdapter to avoid implementing all methods
- Pool handles null gracefully if something goes wrong

**Testing:**
- [ ] Animations work correctly
- [ ] GeoPoint returned to pool after animation
- [ ] GeoPoint returned to pool on animation cancel
- [ ] No memory leaks
- [ ] Animation smoothness improved

---

## Phase 3: Testing & Validation

### ✅ Task 3.1: Add Performance Logging to dispatchDraw
**File:** `osmdroid-android/src/main/java/org/osmdroid/views/MapView.java`
**Lines:** 1264-1267
**Priority:** Low (for debugging)

**REPLACE THIS:**
```java
if (Configuration.getInstance().isDebugMapView()) {
    final long endMs = System.currentTimeMillis();
    Log.d(IMapView.LOGTAG, "Rendering overall: " + (endMs - startMs) + "ms");
}
```

**WITH THIS:**
```java
if (Configuration.getInstance().isDebugMapView()) {
    final long endMs = System.currentTimeMillis();
    Log.d(IMapView.LOGTAG, "Rendering overall: " + (endMs - startMs) + "ms" +
            " | Projection cache: " + (mProjectionVersion > 0 ? "HIT" : "MISS") +
            " | Layout cache size: " + mLayoutPointCache.size());
}
```

**Implementation Notes:**
- Provides insight into cache effectiveness
- Only active when debug mode enabled
- Minimal performance impact

**Testing:**
- [ ] Log messages appear when debug enabled
- [ ] Cache statistics accurate
- [ ] No performance regression

---

### ✅ Task 3.2: Create Performance Test Fragment
**File:** `OpenStreetMapViewer/src/main/java/org/osmdroid/samplefragments/PerformanceTestFragment.java` (NEW FILE)
**Priority:** High (for validation)

**Code to CREATE:**
```java
package org.osmdroid.samplefragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.osmdroid.samplefragments.BaseSampleFragment;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

/**
 * Performance testing fragment for core views optimizations.
 * Tests projection caching, layout caching, and animation performance.
 *
 * @since 6.1.22-lostrat
 */
public class PerformanceTestFragment extends BaseSampleFragment {

    private static final String TAG = "PerfTest";
    private TextView mResultsText;

    @Override
    public String getSampleTitle() {
        return "Performance Tests";
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, container, savedInstanceState);

        // Add test buttons
        Button btnProjectionTest = new Button(getActivity());
        btnProjectionTest.setText("Test Projection Caching");
        btnProjectionTest.setOnClickListener(v -> testProjectionCaching());

        Button btnLayoutTest = new Button(getActivity());
        btnLayoutTest.setText("Test Layout Caching");
        btnLayoutTest.setOnClickListener(v -> testLayoutCaching());

        Button btnAnimationTest = new Button(getActivity());
        btnAnimationTest.setText("Test Animation Performance");
        btnAnimationTest.setOnClickListener(v -> testAnimationPerformance());

        mResultsText = new TextView(getActivity());
        mResultsText.setText("Ready to test");

        // Add to layout (you'll need to adjust this based on your layout structure)

        return root;
    }

    private void testProjectionCaching() {
        long startMs = System.currentTimeMillis();

        // Call getProjection() 1000 times without changing map state
        for (int i = 0; i < 1000; i++) {
            mMapView.getProjection();
        }

        long endMs = System.currentTimeMillis();
        long duration = endMs - startMs;

        String result = "Projection caching test: " + duration + "ms for 1000 calls\n" +
                "Expected: <100ms (with caching), ~500ms (without)";
        Log.d(TAG, result);
        mResultsText.setText(result);
    }

    private void testLayoutCaching() {
        // Add 50 child views at different locations
        List<View> testViews = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            View v = new View(getActivity());
            v.setLayoutParams(new MapView.LayoutParams(
                    100, 100,
                    new GeoPoint(40.0 + i * 0.01, -74.0 + i * 0.01),
                    MapView.LayoutParams.CENTER));
            mMapView.addView(v);
            testViews.add(v);
        }

        long startMs = System.currentTimeMillis();

        // Force layout 100 times
        for (int i = 0; i < 100; i++) {
            mMapView.requestLayout();
            mMapView.layout(
                    mMapView.getLeft(), mMapView.getTop(),
                    mMapView.getRight(), mMapView.getBottom());
        }

        long endMs = System.currentTimeMillis();
        long duration = endMs - startMs;

        // Clean up
        for (View v : testViews) {
            mMapView.removeView(v);
        }

        String result = "Layout caching test: " + duration + "ms for 100 layouts of 50 views\n" +
                "Expected: <1000ms (with caching), ~2000ms (without)";
        Log.d(TAG, result);
        mResultsText.setText(result);
    }

    private void testAnimationPerformance() {
        long startMs = System.currentTimeMillis();

        // Start 10 animations in sequence
        final int[] count = {0};
        animateSequence(count, 10, startMs);
    }

    private void animateSequence(final int[] count, final int total, final long startMs) {
        if (count[0] >= total) {
            long endMs = System.currentTimeMillis();
            long duration = endMs - startMs;
            String result = "Animation test: " + duration + "ms for " + total + " animations\n" +
                    "Avg: " + (duration / total) + "ms per animation\n" +
                    "Expected: <20ms per animation (with pooling), ~50ms (without)";
            Log.d(TAG, result);
            mResultsText.setText(result);
            return;
        }

        GeoPoint target = new GeoPoint(
                40.0 + Math.random() * 0.1,
                -74.0 + Math.random() * 0.1);

        mMapView.getController().animateTo(target, null, 100L);

        count[0]++;
        mMapView.postDelayed(() -> animateSequence(count, total, startMs), 150);
    }

    private void logMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        Log.d(TAG, "Memory: " + usedMemory + "MB / " + maxMemory + "MB");
    }
}
```

**Implementation Notes:**
- Provides quantitative performance measurements
- Tests all three optimization areas
- Includes expected performance baselines
- Can be run on device to validate improvements

**Testing:**
- [ ] Fragment compiles
- [ ] Tests run without crashes
- [ ] Results show expected improvements
- [ ] Memory usage remains acceptable

---

## Testing Checklist

### Functional Testing
- [ ] **MapView scrolling** - Smooth panning in all directions
- [ ] **Zoom operations** - Pinch zoom, double-tap zoom work correctly
- [ ] **Animations** - animateTo() functions smoothly
- [ ] **Multi-touch gestures** - Two-finger rotation, scaling
- [ ] **Child view layout** - Views positioned correctly at GeoPoints
- [ ] **Map rotation** - Child views rotate with map
- [ ] **Overlay drawing** - All overlays render correctly
- [ ] **Tile loading** - Tiles load and cache properly

### Performance Testing
- [ ] **getProjection() performance** - <100ms per 1000 calls (90% improvement)
- [ ] **Layout performance** - <1000ms for 100 layouts of 50 views (60% improvement)
- [ ] **Animation startup** - <20ms per animation (70% improvement)
- [ ] **Overall scrolling FPS** - 40-60% smoother (measure with systrace)
- [ ] **Memory usage** - Increase <10% compared to baseline

### Regression Testing
- [ ] **Thread safety** - No crashes under heavy concurrent load
- [ ] **Visual correctness** - No rendering glitches or artifacts
- [ ] **Projection accuracy** - Coordinate conversions remain accurate
- [ ] **Cache invalidation** - Caches clear properly on state changes
- [ ] **Animation cancellation** - Resources cleaned up on cancel
- [ ] **Edge cases** - Works with map limits, wrapping, rotation

### Stress Testing
- [ ] **Many overlays** - Performance with 500+ polylines
- [ ] **Rapid zoom** - No crashes during rapid zoom in/out
- [ ] **Continuous scrolling** - No memory leaks during long sessions
- [ ] **Child view stress** - Correct layout with 100+ child views
- [ ] **Animation spam** - Handles multiple rapid animation requests

### Compatibility Testing
- [ ] **API 23** - Minimum API level works correctly
- [ ] **API 35** - Target API level works correctly
- [ ] **Different densities** - Works on mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi
- [ ] **Different orientations** - Portrait and landscape modes
- [ ] **Different screen sizes** - Phones, tablets, foldables

---

## Performance Measurement Methodology

### Before Implementation
1. **Baseline measurements** using Android Profiler:
   - CPU usage during scrolling
   - Memory allocation rate
   - Frame rendering time
   - GC frequency and duration

2. **Specific metrics**:
   - Time 1000 getProjection() calls with stopwatch
   - Time layout operation with 50 child views
   - Time animation startup
   - Count GC events during 1-minute scroll session

### After Implementation
1. **Repeat all baseline measurements**
2. **Compare results**:
   - CPU usage should decrease 20-30%
   - GC frequency should decrease 30-50%
   - Frame times should be more consistent
   - Specific operations should match expected improvements

3. **Use systrace for detailed analysis**:
```bash
python systrace.py -a org.osmdroid -b 16000 gfx view sched freq idle
```

---

## Rollback Plan

If serious issues are discovered:

### Quick Rollback (Task-by-Task)
1. **Revert specific task** - Each task is independent
2. **Run tests** - Verify stability restored
3. **Investigate issue** - Debug before re-attempting

### Full Rollback (Phase-by-Phase)
1. **Phase 2 rollback** - Revert Tasks 2.1-2.4 (object pooling, batch conversion)
2. **Phase 1 rollback** - Revert Tasks 1.1-1.7 (projection caching, layout caching)
3. **Return to baseline** - All optimizations removed

### Git Strategy
- Create branch: `feature/core-views-optimization`
- Commit after each task completion
- Tag after each phase: `phase1-complete`, `phase2-complete`
- Easy rollback to any commit/tag

---

## Success Criteria

### Must Have (Go/No-Go)
- ✅ 40%+ improvement in scroll/zoom performance
- ✅ No crashes in stress testing
- ✅ No visual regressions
- ✅ All existing tests pass
- ✅ Memory usage increase < 10%

### Nice to Have
- ✅ 50%+ improvement in specific operations
- ✅ Positive feedback from manual testing
- ✅ Reduced battery usage during map usage
- ✅ Performance test suite for future validation

---

## Implementation Notes

### Import Statements Required
**MapView.java:**
```java
import androidx.collection.ArrayMap;  // For layout cache
```

**MapController.java:**
```java
import org.osmdroid.util.ObjectPool;  // For GeoPoint pooling
import android.animation.AnimatorListenerAdapter;  // For animation cleanup
```

**Projection.java:**
```java
// No new imports required
```

### Build Configuration
No changes to `build.gradle` required. All APIs used are available in API 23+.

### Documentation Updates
After implementation, update:
- [ ] `CLAUDE.md` - Note new optimization features
- [ ] JavaDoc comments - All added/modified methods
- [ ] Code comments - Complex optimization logic

---

## Task Completion Tracking

### Phase 1 Progress: 0/7
- [ ] Task 1.1: Add Projection Caching Infrastructure
- [ ] Task 1.2: Implement Projection Version Computation
- [ ] Task 1.3: Replace getProjection() with Cached Version
- [ ] Task 1.4: Make Projection Fields Non-Final
- [ ] Task 1.5: Add Projection.update() Method
- [ ] Task 1.6: Add Layout Point Cache Fields
- [ ] Task 1.7: Optimize myOnLayout with Point Caching

### Phase 2 Progress: 0/4
- [ ] Task 2.1: Add Batch Conversion Method
- [ ] Task 2.2: Create ObjectPool Utility Class
- [ ] Task 2.3: Add GeoPoint Pool to MapController
- [ ] Task 2.4: Optimize animateTo() to Reuse GeoPoint

### Phase 3 Progress: 0/2
- [ ] Task 3.1: Add Performance Logging
- [ ] Task 3.2: Create Performance Test Fragment

### Overall Progress: 0/13 (0%)

---

## Revision History

| Date | Version | Changes |
|------|---------|---------|
| 2025-09-29 | 1.0 | Initial specification created |

---

**END OF SPECIFICATION**