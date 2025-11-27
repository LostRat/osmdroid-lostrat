# Mapsforge Density Scaling Support - November 2025

## Overview
A new density-aware system has been implemented to automatically scale Mapsforge tiles, overlays, and UI elements based on the device's screen density. This eliminates the need for complex manual calculations and ensures consistent visual sizing across different devices (ldpi to xxxhdpi).

## Key Features

### 1. Automatic Scaling
The system now automatically calculates the optimal scale factor for Mapsforge rendering based on the device's display metrics.
*   **Old Way:** Developers had to manually calculate scale factors using `DisplayMetrics`, often leading to inconsistencies.
*   **New Way:** Simply call `applyDensityScaling()` on your tile source.

### 2. Consistent Overlay Sizing
Overlays such as `LatLonGridlineOverlay` now have built-in support for density scaling. Line widths and text sizes are automatically adjusted to look physical consistent (e.g., a 2mm line looks like 2mm on both a tablet and a phone).

## Usage Guide

### Basic Setup
To enable automatic scaling for a Mapsforge tile source:

```java
// Create your Mapsforge tile source
MapsForgeTileSource fromForgeFiles = MapsForgeTileSource.createFromFiles(maps, theme, "default-path-contour");

// ENABLE AUTOMATIC SCALING
fromForgeFiles.applyDensityScaling();
```

### Advanced Configuration
You can access the `DisplayDensityManager` directly if you need custom scaling logic or unit conversions:

```java
DisplayDensityManager density = DisplayDensityManager.getInstance();

// Get the calculated scale factor
float autoScale = density.getMapForgeScaleFactor();

// Convert DP to Pixels
float pixels = density.dpToPx(16.0f);
```

### Comparison: Old vs New

**Legacy Manual Calculation (Deprecated):**
```java
final float GESTURE_THRESHOLD_DP = 16.0f;
float gestureThreshold = applyDimension(COMPLEX_UNIT_DIP, GESTURE_THRESHOLD_DP + 0.5f, getResources().getDisplayMetrics());
float scaleFactor = 0.6F * (34F / gestureThreshold);
fromForgeFiles.setUserScaleFactor(scaleFactor);
```

**New Automatic Method:**
```java
fromForgeFiles.applyDensityScaling();
```

## Benefits
*   **Simplicity:** Reduces boilerplate code.
*   **Consistency:** Ensures map elements are legible on high-density screens.
*   **Future-Proof:** Automatically handles new density buckets (e.g., if 800dpi screens become common).
