# Cross-Platform Polyline Data Architecture - 2025-09-25

## Core Data Layer (Platform Agnostic)

```java
// Pure data - no Android/iOS dependencies
public class GeoPolyline {
    private final List<GeoPoint> points;
    private final String id;
    private final PolylineStyle style;
    private final BoundingBox bounds;
    
    public GeoPolyline(String id, List<GeoPoint> points, PolylineStyle style) {
        this.id = id;
        this.points = new ArrayList<>(points);
        this.style = style;
        this.bounds = calculateBounds(points);
    }
    
    // Getters only - immutable data
    public List<GeoPoint> getPoints() { return new ArrayList<>(points); }
    public String getId() { return id; }
    public PolylineStyle getStyle() { return style; }
    public BoundingBox getBounds() { return bounds; }
}

public class GeoPoint {
    private final double latitude;
    private final double longitude;
    private final double altitude; // optional
    
    public GeoPoint(double lat, double lon) {
        this(lat, lon, 0.0);
    }
    
    public GeoPoint(double lat, double lon, double alt) {
        this.latitude = lat;
        this.longitude = lon;
        this.altitude = alt;
    }
    
    // Getters only
}

public class PolylineStyle {
    private final int color;
    private final float width;
    private final boolean dashed;
    private final float[] dashPattern;
    
    // Immutable style data
}

public class BoundingBox {
    private final double north, south, east, west;
    
    public boolean contains(GeoPoint point) { /* ... */ }
    public boolean intersects(BoundingBox other) { /* ... */ }
}
```

## Data Repository (Platform Agnostic)

```java
public interface PolylineRepository {
    List<GeoPolyline> getPolylinesInBounds(BoundingBox bounds);
    void cachePolylines(List<GeoPolyline> polylines);
    void clearCache();
    Observable<List<GeoPolyline>> observePolylines(BoundingBox bounds);
}

public class SqlitePolylineRepository implements PolylineRepository {
    // Uses SQLite (available on Android/iOS)
    // Stores compressed polyline data
    // Platform-agnostic database operations
}

public class MemoryPolylineCache implements PolylineRepository {
    private final Map<String, GeoPolyline> cache = new ConcurrentHashMap<>();
    private final QuadTree<GeoPolyline> spatialIndex = new QuadTree<>();
    
    @Override
    public List<GeoPolyline> getPolylinesInBounds(BoundingBox bounds) {
        return spatialIndex.query(bounds);
    }
}
```

## Platform Adapters

### Android OSMDroid Adapter
```java
public class OsmDroidPolylineAdapter {
    private final PolylineRepository repository;
    
    public List<Polyline> createOsmPolylines(BoundingBox bounds) {
        List<GeoPolyline> geoPolylines = repository.getPolylinesInBounds(bounds);
        return geoPolylines.stream()
            .map(this::convertToOsmPolyline)
            .collect(Collectors.toList());
    }
    
    private Polyline convertToOsmPolyline(GeoPolyline geo) {
        Polyline osm = new Polyline();
        
        // Convert points
        List<org.osmdroid.util.GeoPoint> osmPoints = geo.getPoints().stream()
            .map(p -> new org.osmdroid.util.GeoPoint(p.getLatitude(), p.getLongitude()))
            .collect(Collectors.toList());
        osm.setPoints(osmPoints);
        
        // Convert style
        Paint paint = osm.getPaint();
        paint.setColor(geo.getStyle().getColor());
        paint.setStrokeWidth(geo.getStyle().getWidth());
        
        return osm;
    }
}
```

### MapForge Adapter
```java
public class MapForgePolylineAdapter {
    private final PolylineRepository repository;
    
    public List<org.mapsforge.map.layer.overlay.Polyline> createMapForgePolylines(BoundingBox bounds) {
        List<GeoPolyline> geoPolylines = repository.getPolylinesInBounds(bounds);
        return geoPolylines.stream()
            .map(this::convertToMapForgePolyline)
            .collect(Collectors.toList());
    }
    
    private org.mapsforge.map.layer.overlay.Polyline convertToMapForgePolyline(GeoPolyline geo) {
        // Convert to MapForge format
        List<org.mapsforge.core.model.LatLong> points = geo.getPoints().stream()
            .map(p -> new org.mapsforge.core.model.LatLong(p.getLatitude(), p.getLongitude()))
            .collect(Collectors.toList());
            
        Paint paint = new Paint();
        paint.setColor(geo.getStyle().getColor());
        paint.setStrokeWidth(geo.getStyle().getWidth());
        
        return new org.mapsforge.map.layer.overlay.Polyline(paint, points);
    }
}
```

### iOS Swift Protocol (for future)
```swift
protocol PolylineRepository {
    func getPolylinesInBounds(_ bounds: BoundingBox) -> [GeoPolyline]
    func cachePolylines(_ polylines: [GeoPolyline])
}

struct GeoPolyline {
    let id: String
    let points: [GeoPoint]
    let style: PolylineStyle
    let bounds: BoundingBox
}

class MapKitPolylineAdapter {
    private let repository: PolylineRepository
    
    func createMapKitPolylines(bounds: BoundingBox) -> [MKPolyline] {
        let geoPolylines = repository.getPolylinesInBounds(bounds)
        return geoPolylines.map { convertToMapKitPolyline($0) }
    }
}
```

## Usage Pattern

```java
// Platform-agnostic setup
PolylineRepository repository = new SqlitePolylineRepository(databasePath);
BoundingBox viewBounds = getCurrentViewBounds();

// Android OSMDroid
OsmDroidPolylineAdapter osmAdapter = new OsmDroidPolylineAdapter(repository);
List<Polyline> osmPolylines = osmAdapter.createOsmPolylines(viewBounds);
mapView.getOverlays().addAll(osmPolylines);

// MapForge (same data, different rendering)
MapForgePolylineAdapter mapForgeAdapter = new MapForgePolylineAdapter(repository);
List<org.mapsforge.map.layer.overlay.Polyline> mapForgePolylines = 
    mapForgeAdapter.createMapForgePolylines(viewBounds);
```

## Benefits

1. **Single data source** - SQLite database works on Android/iOS
2. **Platform adapters** - Convert to native map objects
3. **Testable** - Core logic has no platform dependencies
4. **Reusable** - Same caching/querying logic everywhere
5. **Maintainable** - Changes in one place affect all platforms

## Migration Strategy

1. **Extract current data** into `GeoPolyline` format
2. **Create repository interface** for your existing database
3. **Build OSMDroid adapter** first
4. **Add MapForge adapter** when needed
5. **Port to iOS** using same data structures

This architecture separates **what** (data) from **how** (rendering), making cross-platform development much cleaner.