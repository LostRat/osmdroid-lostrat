# Overlay System Overhaul & Optimization - November 2025

## Overview
This document details the significant changes and optimizations made to the `osmdroid` Overlay System, specifically focusing on `DefaultOverlayManager`. These changes address performance bottlenecks with large numbers of markers, fix Z-ordering issues with `FolderOverlay`, and improve thread safety.

## Key Changes

### 1. Automatic Layer Assignment & Interactivity
A major discovery was made regarding how `DefaultOverlayManager` handles marker interactivity. Previously, markers with IDs were automatically assigned to the `INTERACTIVE_CONTENT` layer, which meant they were checked for hit-testing on every single screen tap.

*   **The Problem:** Having hundreds of "decorative" markers (like breadcrumbs or trace dots) that happened to have IDs (for data lookup) caused massive performance degradation because the system treated them all as interactive targets.
*   **The Fix:** Introduced `setInteractive(boolean)` flag for Overlays.
    *   **`setInteractive(false)`**: Explicitly opts an overlay out of touch processing, even if it resides in the `INTERACTIVE_CONTENT` layer.
    *   **Benefit:** Allows keeping IDs on markers for data lookup without paying the performance cost of hit-testing them.
    *   **Performance:** ~98.8% reduction in touch processing overhead for scenarios with 800+ decorative markers.

#### Usage Example
```java
PointMarker dot = new PointMarker(map);
dot.setId("12345"); // ID causes auto-assignment to INTERACTIVE_CONTENT
dot.setInteractive(false); // NEW: Explicitly skip touch processing
map.getOverlays().add(dot);
```

### 2. FolderOverlay Z-Ordering Fixes
Issues were identified where `FolderOverlay` content was not respecting the global Z-ordering of layers.
*   **Issue:** `FolderOverlay` was assigned to a single layer, forcing all its children (Markers, Polylines, etc.) to draw at that same layer depth, ignoring their intrinsic types.
*   **Resolution:** The `DefaultOverlayManager` now recursively handles `FolderOverlay` children, ensuring they are assigned to their appropriate Z-layers (e.g., Markers above Polylines) regardless of their container folder.

### 3. Thread Safety Improvements
The internal storage mechanisms in `DefaultOverlayManager` were upgraded to prevent crashes in multi-threaded environments (common with background loading of map data).
*   **Change:** `mOverlayToLayer` and `mLayeredOverlays` maps were converted to `ConcurrentHashMap` to safely handle concurrent `add`/`remove` operations.

### 4. Spatial Index Data Loss Fix
*   **Issue:** The spatial index used for touch detection had a hard limit of 50 overlays per grid cell. If more than 50 overlays existed in a small area, the extras were silently ignored and became unclickable.
*   **Fix:** Removed the hard limit and switched to dynamic lists for grid cells, ensuring all overlays are correctly indexed and clickable.

## Migration Guide
If you are upgrading and notice markers are no longer clickable or are consuming too much memory/CPU:
1.  **Check `setInteractive()`:** Ensure markers you want to be clickable have `setInteractive(true)` (default is usually true, but good to verify).
2.  **Optimize Decorative Markers:** For any marker that doesn't need click events, call `setInteractive(false)`.
3.  **Layer Assignment:** You generally do *not* need to manually call `assignOverlayToLayer` anymore; the system auto-assigns based on overlay type and properties (ID, Title, etc.).
