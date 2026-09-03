# Scroll-Triggered Hardware Layer Pattern - 2025-09-24

## Perfect Workflow for Your Use Case

```java
public class ScrollTriggeredPolylineUpdater implements MapListener {
    private MapView mapView;
    private BreadcrumbPlusDisplay breadcrumbDisplay;
    private Handler handler = new Handler();
    private Runnable updateRunnable;
    
    public ScrollTriggeredPolylineUpdater(MapView mapView, BreadcrumbPlusDisplay display) {
        this.mapView = mapView;
        this.breadcrumbDisplay = display;
        mapView.addMapListener(new DelayedMapListener(this, 300)); // 300ms delay
    }
    
    @Override
    public boolean onScroll(ScrollEvent event) {
        // Map is still at this point - perfect timing
        
        // Cancel any pending update
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
        
        // Create new update runnable
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                // 1. Enable hardware layer BEFORE updating polylines
                mapView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                
                // 2. Update your polylines
                breadcrumbDisplay.drawRedraw_LineCache();
                
                // 3. Force redraw to capture new content in hardware layer
                mapView.invalidate();
                
                // 4. Reset to normal after drawing completes
                mapView.post(() -> {
                    mapView.setLayerType(View.LAYER_TYPE_NONE, null);
                });
            }
        };
        
        // Execute immediately (map is already still)
        handler.post(updateRunnable);
        
        return false;
    }
    
    @Override
    public boolean onZoom(ZoomEvent event) {
        // Same pattern for zoom
        return onScroll(null);
    }
}
```

## Key Timing Points

### 1. onScroll() Called
- **Map is stationary** ✓
- **Perfect time to start updates** ✓

### 2. Enable Hardware Layer
```java
mapView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
// No invalidate() needed yet - just preparing the layer
```

### 3. Update Polylines
```java
breadcrumbDisplay.drawRedraw_LineCache();
// Your existing logic runs
// Polylines are updated in memory
```

### 4. Force Redraw
```java
mapView.invalidate();
// This triggers draw() calls
// Hardware layer captures the new polyline content
```

### 5. Reset Layer Type
```java
mapView.post(() -> {
    mapView.setLayerType(View.LAYER_TYPE_NONE, null);
});
// Posted to run after drawing completes
// Returns to normal rendering
```

## invalidate() Timing Explained

```java
// WRONG - invalidate before hardware layer
mapView.invalidate();
mapView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

// RIGHT - hardware layer first, then invalidate
mapView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
breadcrumbDisplay.drawRedraw_LineCache();
mapView.invalidate(); // Captures new content in hardware layer
```

## Alternative: Keep Hardware Layer Active

```java
public class PersistentHardwareLayerManager implements MapListener {
    private MapView mapView;
    private BreadcrumbPlusDisplay breadcrumbDisplay;
    
    @Override
    public boolean onScroll(ScrollEvent event) {
        // Keep hardware layer active for smooth panning
        if (mapView.getLayerType() != View.LAYER_TYPE_HARDWARE) {
            mapView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        
        // Update polylines
        breadcrumbDisplay.drawRedraw_LineCache();
        mapView.invalidate();
        
        return false;
    }
    
    // Only disable on memory pressure or app pause
    public void onLowMemory() {
        mapView.setLayerType(View.LAYER_TYPE_NONE, null);
    }
}
```

## Usage

```java
// In your Activity onCreate()
ScrollTriggeredPolylineUpdater updater = 
    new ScrollTriggeredPolylineUpdater(mapView, breadcrumbDisplay);
```

## Benefits of This Pattern

1. **Perfect timing** - Map is stationary when onScroll() fires
2. **Hardware acceleration** - Smooth rendering after updates
3. **Memory efficient** - Layer reset after each update
4. **No race conditions** - Sequential execution guaranteed
5. **Automatic triggering** - No manual coordination needed

The `invalidate()` call is crucial - it forces the hardware layer to capture your updated polylines before resetting to normal rendering.