# Hardware Layer Batch Polylines Implementation - 2025-09-24

## Overview
Sample code to implement hardware layer optimization for batch polyline drawing in your osmdroid app.

## 1. PolylineBatch Overlay

```java
public class PolylineBatch extends Overlay {
    private List<Polyline> mPolylines;
    private boolean mUseHardwareLayer = false;
    
    public PolylineBatch(List<Polyline> polylines) {
        mPolylines = polylines;
    }
    
    public void setUseHardwareLayer(boolean useHardwareLayer) {
        mUseHardwareLayer = useHardwareLayer;
    }
    
    @Override
    public void draw(Canvas canvas, Projection projection) {
        for (Polyline polyline : mPolylines) {
            polyline.draw(canvas, projection);
        }
    }
}
```

## 2. Activity Implementation

```java
public class MainActivity extends AppCompatActivity {
    private MapView mapView;
    private PolylineBatch polylineBatch;
    private boolean hardwareLayerEnabled = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize MapView
        mapView = findViewById(R.id.map);
        
        // Create polylines
        List<Polyline> polylines = createPolylines();
        
        // Create batch overlay
        polylineBatch = new PolylineBatch(polylines);
        mapView.getOverlays().add(polylineBatch);
        
        // Enable hardware layer for static polylines
        enableHardwareLayer();
    }
    
    private void enableHardwareLayer() {
        if (!hardwareLayerEnabled) {
            mapView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            polylineBatch.setUseHardwareLayer(true);
            hardwareLayerEnabled = true;
        }
    }
    
    private void disableHardwareLayer() {
        if (hardwareLayerEnabled) {
            mapView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            polylineBatch.setUseHardwareLayer(false);
            hardwareLayerEnabled = false;
        }
    }
    
    // Call when polylines change
    private void onPolylinesChanged() {
        // Disable hardware layer when content changes
        disableHardwareLayer();
        mapView.invalidate();
        
        // Re-enable after drawing settles
        mapView.post(() -> enableHardwareLayer());
    }
}
```

## 3. Advanced PolylineBatch with Paint Grouping

```java
public class OptimizedPolylineBatch extends Overlay {
    private Map<Paint, List<Polyline>> mPolylinesByPaint;
    
    public OptimizedPolylineBatch(List<Polyline> polylines) {
        groupPolylinesByPaint(polylines);
    }
    
    private void groupPolylinesByPaint(List<Polyline> polylines) {
        mPolylinesByPaint = new HashMap<>();
        for (Polyline polyline : polylines) {
            Paint paint = polyline.getPaint();
            mPolylinesByPaint.computeIfAbsent(paint, k -> new ArrayList<>()).add(polyline);
        }
    }
    
    @Override
    public void draw(Canvas canvas, Projection projection) {
        // Draw all polylines with same paint together
        for (Map.Entry<Paint, List<Polyline>> entry : mPolylinesByPaint.entrySet()) {
            Paint paint = entry.getKey();
            for (Polyline polyline : entry.getValue()) {
                polyline.draw(canvas, projection);
            }
        }
    }
}
```

## 4. Memory Management

```java
public class HardwareLayerManager {
    private MapView mapView;
    private boolean isHardwareLayerActive = false;
    
    public HardwareLayerManager(MapView mapView) {
        this.mapView = mapView;
    }
    
    public void enableForStaticContent() {
        if (!isHardwareLayerActive) {
            mapView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            isHardwareLayerActive = true;
        }
    }
    
    public void disableForDynamicContent() {
        if (isHardwareLayerActive) {
            mapView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            isHardwareLayerActive = false;
        }
    }
    
    // Call in onPause to free GPU memory
    public void onPause() {
        disableForDynamicContent();
    }
    
    // Call in onResume for static content
    public void onResume() {
        // Only re-enable if content is static
        if (hasStaticContent()) {
            enableForStaticContent();
        }
    }
    
    private boolean hasStaticContent() {
        // Your logic to determine if polylines are static
        return true;
    }
}
```

## Usage Guidelines

### When to Use Hardware Layer
- **Static polylines** that don't change frequently
- **Complex polylines** with many points
- **High-density displays** where GPU acceleration helps most

### When NOT to Use Hardware Layer
- **Frequently changing polylines** (causes layer recreation)
- **Simple polylines** (overhead > benefit)
- **Memory-constrained devices** (hardware layers use GPU memory)

### Performance Tips
1. **Group by Paint** - Batch polylines with same styling
2. **Toggle on content changes** - Disable during updates, re-enable after
3. **Monitor memory usage** - Hardware layers consume GPU memory
4. **Test on target devices** - Performance varies by hardware

## Dynamic Polyline Update Workflow

```java
public void updatePolylines(List<Polyline> toRemove, List<Polyline> toAdd) {
    // 1. Disable hardware layer
    mapView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    
    // 2. Modify polylines
    polylineBatch.removePolylines(toRemove);
    polylineBatch.addPolylines(toAdd);
    
    // 3. Force redraw with software rendering
    mapView.invalidate();
    
    // 4. Re-enable hardware layer (creates new GPU texture)
    mapView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    
    // 5. Next draw will use new hardware layer
    mapView.invalidate();
}
```

### Why This Works
- **Step 1**: Software rendering for modifications
- **Step 2**: Update polyline data
- **Step 3**: Draw changes with CPU
- **Step 4**: Create fresh GPU texture with new content
- **Step 5**: Future draws use GPU acceleration

### Enhanced PolylineBatch for Dynamic Updates

```java
public class PolylineBatch extends Overlay {
    private List<Polyline> mPolylines;
    
    public PolylineBatch(List<Polyline> polylines) {
        mPolylines = new ArrayList<>(polylines);
    }
    
    public void removePolylines(List<Polyline> toRemove) {
        mPolylines.removeAll(toRemove);
    }
    
    public void addPolylines(List<Polyline> toAdd) {
        mPolylines.addAll(toAdd);
    }
    
    public void replacePolylines(List<Polyline> newPolylines) {
        mPolylines.clear();
        mPolylines.addAll(newPolylines);
    }
    
    @Override
    public void draw(Canvas canvas, Projection projection) {
        for (Polyline polyline : mPolylines) {
            polyline.draw(canvas, projection);
        }
    }
}
```

## Integration Steps

1. Replace individual `Polyline` overlays with `PolylineBatch`
2. Add hardware layer management in Activity lifecycle
3. Toggle hardware layer based on content changes
4. Monitor performance and memory usage
5. Fine-tune based on your specific use case