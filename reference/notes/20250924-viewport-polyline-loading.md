# Viewport-Based Polyline Loading - 2025-09-24

## Your Use Case Analysis
- **Problem**: Database retrieval + decompression delay on map pan/zoom
- **Current behavior**: Sliding with existing polylines is smooth
- **Need**: Load only visible polylines efficiently

## Hardware Layer Reality Check
```java
@Override
public void draw(Canvas canvas, Projection projection) {
    // This ALWAYS gets called regardless of hardware layer
    // Hardware layer only changes WHERE drawing goes:
    // - No layer: draws directly to screen
    // - Hardware layer: draws to GPU texture, then GPU composites to screen
}
```

**Hardware layer won't help your database delay problem.**

## Solution: Viewport-Based Loading

```java
public class ViewportPolylineManager {
    private MapView mapView;
    private PolylineBatch currentBatch;
    private BoundingBox lastLoadedBounds;
    private double loadThreshold = 0.2; // Load when 20% outside current bounds
    
    public ViewportPolylineManager(MapView mapView) {
        this.mapView = mapView;
        this.currentBatch = new PolylineBatch(new ArrayList<>());
        mapView.getOverlays().add(currentBatch);
        
        // Listen for map changes
        mapView.addMapListener(new DelayedMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                checkAndLoadPolylines();
                return true;
            }
            
            @Override
            public boolean onZoom(ZoomEvent event) {
                checkAndLoadPolylines();
                return true;
            }
        }, 500)); // 500ms delay to avoid rapid calls
    }
    
    private void checkAndLoadPolylines() {
        BoundingBox currentBounds = mapView.getBoundingBox();
        
        if (shouldLoadNewPolylines(currentBounds)) {
            loadPolylinesAsync(currentBounds);
        }
    }
    
    private boolean shouldLoadNewPolylines(BoundingBox currentBounds) {
        if (lastLoadedBounds == null) return true;
        
        // Check if current view extends beyond loaded bounds by threshold
        double expandedNorth = lastLoadedBounds.getLatNorth() + 
            (lastLoadedBounds.getLatitudeSpan() * loadThreshold);
        double expandedSouth = lastLoadedBounds.getLatSouth() - 
            (lastLoadedBounds.getLatitudeSpan() * loadThreshold);
        double expandedEast = lastLoadedBounds.getLonEast() + 
            (lastLoadedBounds.getLongitudeSpan() * loadThreshold);
        double expandedWest = lastLoadedBounds.getLonWest() - 
            (lastLoadedBounds.getLongitudeSpan() * loadThreshold);
            
        return currentBounds.getLatNorth() > expandedNorth ||
               currentBounds.getLatSouth() < expandedSouth ||
               currentBounds.getLonEast() > expandedEast ||
               currentBounds.getLonWest() < expandedWest;
    }
    
    private void loadPolylinesAsync(BoundingBox bounds) {
        // Expand bounds for loading (load more than visible)
        BoundingBox loadBounds = expandBounds(bounds, 0.5); // 50% buffer
        
        new AsyncTask<BoundingBox, Void, List<Polyline>>() {
            @Override
            protected List<Polyline> doInBackground(BoundingBox... bounds) {
                // Your database query + decompression here
                return loadPolylinesFromDatabase(bounds[0]);
            }
            
            @Override
            protected void onPostExecute(List<Polyline> polylines) {
                currentBatch.replacePolylines(polylines);
                lastLoadedBounds = loadBounds;
                mapView.invalidate();
            }
        }.execute(loadBounds);
    }
    
    private BoundingBox expandBounds(BoundingBox bounds, double factor) {
        double latSpan = bounds.getLatitudeSpan() * factor;
        double lonSpan = bounds.getLongitudeSpan() * factor;
        
        return new BoundingBox(
            bounds.getLatNorth() + latSpan,
            bounds.getLonEast() + lonSpan,
            bounds.getLatSouth() - latSpan,
            bounds.getLonWest() - lonSpan
        );
    }
    
    private List<Polyline> loadPolylinesFromDatabase(BoundingBox bounds) {
        // Your existing database query logic
        // Filter by bounds, decompress, create Polyline objects
        return new ArrayList<>();
    }
}
```

## Key Benefits for Your Use Case

1. **Reduces database calls** - Only loads when significantly outside current area
2. **Preloads buffer area** - Smooth panning without constant reloading  
3. **Debounced loading** - Prevents rapid-fire database calls during fast panning
4. **Replaces entire batch** - Clean memory management

## Usage

```java
// In your Activity
ViewportPolylineManager polylineManager = new ViewportPolylineManager(mapView);
```

That's it - automatic viewport-based loading with no hardware layer complexity needed.