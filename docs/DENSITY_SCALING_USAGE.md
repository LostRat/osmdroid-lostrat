# Display Density Scaling Usage Guide

## Overview
The new density-aware system automatically scales MapForge tiles, overlays, and UI elements based on screen density, eliminating the need for manual calculations.

## Quick Setup

### 1. Initialize in Application Class
```java
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize MapForge with density management
        MapsForgeTileSource.createInstance(this);
        
        // DisplayDensityManager is automatically initialized
    }
}
```

### 2. Automatic MapForge Scaling (Recommended)
```java
// Create MapForge tile source
String cacheName = MapsForgeTileCacheKeys.forMapsAndTheme(
    maps, "default-path-contour", getResources().getDisplayMetrics().density);
fromForgeFiles = MapsForgeTileSource.createFromFiles(maps, theme, cacheName);
forgeProvider = new MapsForgeTileProvider(new SimpleRegisterReceiver(this), fromForgeFiles);

// Apply automatic density scaling (replaces your manual calculation)
fromForgeFiles.applyDensityScaling();

// Optional: Check what scale factor was applied
Log.d(TAG, "Applied scale factor: " + fromForgeFiles.getUserScaleFactor());
```

### 3. Manual Override (If Needed)
```java
// Your old manual calculation (now optional)
final float GESTURE_THRESHOLD_DP = 16.0f;
float gestureThreshold = applyDimension(
    COMPLEX_UNIT_DIP,
    GESTURE_THRESHOLD_DP + 0.5f,
    getResources().getDisplayMetrics());

float scaleFactor = 0.6F * (34F / gestureThreshold);

// Apply custom scale factor
fromForgeFiles.setUserScaleFactor(scaleFactor);
```

### 4. Density-Aware Overlays
```java
// Grid lines automatically scale
LatLonGridlineOverlay.setDensityScalingEnabled(true); // Default: enabled

// Get density-aware measurements
DisplayDensityManager density = DisplayDensityManager.getInstance();
float scaledLineWidth = density.getScaledLineWidth(2.0f);
float scaledTextSize = density.getScaledTextSize(16.0f);
```

## Scale Factor Comparison

### Your Manual Calculation:
```java
// Complex manual calculation
float gestureThreshold = applyDimension(COMPLEX_UNIT_DIP, 16.5f, metrics);
float scaleFactor = 0.6F * (34F / gestureThreshold);
```

### New Automatic Calculation:
```java
// Simple automatic scaling
fromForgeFiles.applyDensityScaling();
```

### Scale Factor Results by Density:
- **ldpi (120dpi):** 0.60 (your calc: ~0.52)
- **mdpi (160dpi):** 0.80 (your calc: ~0.60) 
- **hdpi (240dpi):** 1.20 (your calc: ~0.90)
- **xhdpi (320dpi):** 1.60 (your calc: ~1.20)
- **xxhdpi (480dpi):** 2.40 (your calc: ~1.80)
- **xxxhdpi (640dpi):** 3.00 (your calc: ~2.40)

## Advanced Usage

### Custom Density Adjustments:
```java
DisplayDensityManager density = DisplayDensityManager.getInstance();

// Get base scale factors
float mapForgeScale = density.getMapForgeScaleFactor();
float overlayScale = density.getOverlayScaleFactor();
float textScale = density.getTextScaleFactor();

// Apply custom multipliers
fromForgeFiles.setUserScaleFactor(mapForgeScale * 1.2f); // 20% larger
```

### Density Information:
```java
DisplayDensityManager density = DisplayDensityManager.getInstance();
Log.d(TAG, "Density category: " + density.getDensityCategory());
Log.d(TAG, "Density value: " + density.getDensity());
Log.d(TAG, "DPI: " + density.getDensityDpi());
```

### Unit Conversions:
```java
DisplayDensityManager density = DisplayDensityManager.getInstance();

// Convert units
float pixelsFromDp = density.dpToPx(16.0f);
float pixelsFromSp = density.spToPx(14.0f);
float dpFromPixels = density.pxToDp(48.0f);
```

## Migration Guide

### Before (Your Current Code):
```java
// Manual calculation required
final float GESTURE_THRESHOLD_DP = 16.0f;
float gestureThreshold = applyDimension(COMPLEX_UNIT_DIP, 
    GESTURE_THRESHOLD_DP + 0.5f, getResources().getDisplayMetrics());
float scaleFactor = .6F * (34F / gestureThreshold);
fromForgeFiles.setUserScaleFactor(scaleFactor);

// Fixed sizes for overlays
LatLonGridlineOverlay.lineWidth = 1f;
LatLonGridlineOverlay.fontSizeDp = 24;
```

### After (New Automatic System):
```java
// Automatic scaling
fromForgeFiles.applyDensityScaling();

// Overlays automatically scale
// No changes needed - they scale automatically
```

## Benefits

1. **Eliminates Manual Calculations** - No more complex density math
2. **Consistent Scaling** - All components use same density system
3. **Better Readability** - Text and lines properly sized for all screens
4. **Future-Proof** - Handles new density categories automatically
5. **Backward Compatible** - Old manual scaling still works

## Troubleshooting

### If Text/Lines Are Too Small:
```java
// Increase base sizes
LatLonGridlineOverlay.lineWidth = 2f;  // Will be auto-scaled
LatLonGridlineOverlay.fontSizeDp = 32; // Will be auto-scaled
```

### If MapForge Tiles Are Wrong Size:
```java
// Fine-tune the automatic scaling
float autoScale = DisplayDensityManager.getInstance().getMapForgeScaleFactor();
fromForgeFiles.setUserScaleFactor(autoScale * 1.1f); // 10% adjustment
```

### Disable Automatic Scaling:
```java
// For overlays
LatLonGridlineOverlay.setDensityScalingEnabled(false);

// For MapForge (use manual scaling)
fromForgeFiles.setUserScaleFactor(yourCustomScale);
```
