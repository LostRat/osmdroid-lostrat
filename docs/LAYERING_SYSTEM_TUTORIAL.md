# OSMDroid Enhanced Layering System Tutorial

## Overview

The enhanced OSMDroid fork includes a comprehensive 10-layer z-index system that provides predictable drawing order and tap event handling. This system ensures that overlays are drawn and interact in a logical hierarchy, solving common issues with marker visibility and tap detection.

## Layer Hierarchy

The system uses 10 predefined layers, ordered from bottom (0) to top (9):

| Layer | Name | Z-Index | Purpose | Examples |
|-------|------|---------|---------|----------|
| 0 | `BACKGROUND_TILES` | 0 | Base maps, tile overlays | Map tiles, satellite imagery |
| 1 | `BACKGROUND_SHAPES` | 1 | Background geometry | Base polylines, area boundaries |
| 2 | `DECORATION` | 2 | **Tiny markers, dots** | **Vertex dots, trail markers** |
| 3 | `MAIN_CONTENT` | 3 | Primary content | Main polylines, routes |
| 4 | `INTERACTIVE_BACKGROUND` | 4 | Clickable shapes | Selectable polylines, areas |
| 5 | `INTERACTIVE_CONTENT` | 5 | **Important markers** | **POI markers, elevation markers** |
| 6 | `USER_DRAWING` | 6 | User-created content | Hand-drawn lines, annotations |
| 7 | `OVERLAY_CONTROLS` | 7 | UI elements | Control buttons, overlays |
| 8 | `POPUP_CONTENT` | 8 | Information displays | Info windows, popups |
| 9 | `DEBUG_OVERLAY` | 9 | Debug information | Debug markers, test overlays |

## Key Benefits

### ✅ Predictable Drawing Order
- Higher layers always draw on top of lower layers
- No more "last added wins" confusion
- Consistent visual hierarchy

### ✅ Proper Tap Event Handling  
- Higher layers receive tap events first
- Interactive elements always respond correctly
- No more blocked markers

### ✅ Automatic Classification
- System automatically assigns overlays to appropriate layers
- Smart detection based on overlay properties
- Manual override available when needed

### ✅ Performance Optimized
- Spatial indexing for fast tap detection
- Layer-based event processing
- Viewport culling for better performance

## Basic Usage

### Automatic Layer Assignment

The system automatically assigns overlays based on their properties:

```java
// This marker will automatically go to INTERACTIVE_CONTENT (Layer 5)
Marker importantMarker = new Marker(mapView);
importantMarker.setTitle("Important Location");
importantMarker.setPosition(geoPoint);
mapView.getOverlays().add(importantMarker);

// This marker will automatically go to DECORATION (Layer 2)
Marker tinyDot = new Marker(mapView);
// No title, snippet, or info window = decoration
tinyDot.setPosition(geoPoint);
mapView.getOverlays().add(tinyDot);
```

### Manual Layer Control

For precise control, use the overlay manager methods:

```java
DefaultOverlayManager manager = (DefaultOverlayManager) mapView.getOverlayManager();

// Mark tiny dots as decoration (behind other markers)
manager.markAsDecoration(tinyRedDot);

// Mark important markers as interactive (in front)
manager.markAsInteractive(elevationMarker);

// Mark user-drawn content as top layer
manager.markAsUserDrawn(userPolyline);

// Assign to specific layer
manager.assignOverlayToLayer(overlay, DefaultOverlayManager.OverlayLayer.USER_DRAWING);
```

## Common Use Cases

### 1. Trail Mapping with Elevation Markers

**Problem:** Tiny trail markers draw over elevation markers

**Solution:**
```java
// Add trail points as decoration
for (GeoPoint trailPoint : trailPoints) {
    Marker dot = new Marker(mapView);
    dot.setIcon(smallRedDot);
    dot.setPosition(trailPoint);
    mapView.getOverlays().add(dot);
    
    // Ensure it stays in background
    manager.markAsDecoration(dot);
}

// Add elevation markers as interactive content
for (ElevationPoint elevPoint : elevationPoints) {
    Marker marker = new Marker(mapView);
    marker.setTitle("Elevation: " + elevPoint.elevation + "m");
    marker.setIcon(elevationIcon);
    marker.setPosition(elevPoint.location);
    mapView.getOverlays().add(marker);
    
    // Will automatically go to INTERACTIVE_CONTENT
    // Or explicitly: manager.markAsInteractive(marker);
}
```

### 2. Route Planning with Waypoints

```java
// Background route line
Polyline routeLine = new Polyline(mapView);
routeLine.setPoints(routePoints);
routeLine.setColor(Color.BLUE);
mapView.getOverlays().add(routeLine);
// Automatically goes to MAIN_CONTENT

// Interactive waypoint markers
for (Waypoint waypoint : waypoints) {
    Marker marker = new Marker(mapView);
    marker.setTitle(waypoint.name);
    marker.setDraggable(true);
    marker.setPosition(waypoint.location);
    mapView.getOverlays().add(marker);
    // Automatically goes to INTERACTIVE_CONTENT
}

// User's current drawing
Polyline userLine = new Polyline(mapView);
userLine.setPoints(userDrawnPoints);
mapView.getOverlays().add(userLine);
manager.markAsUserDrawn(userLine); // Goes on top
```

### 3. Complex Multi-Layer Visualization

```java
DefaultOverlayManager manager = (DefaultOverlayManager) mapView.getOverlayManager();

// Base area boundaries (background)
Polygon area = new Polygon(mapView);
area.setPoints(boundaryPoints);
mapView.getOverlays().add(area);
// Automatically goes to BACKGROUND_SHAPES

// Clickable sub-areas
for (SubArea subArea : subAreas) {
    Polygon poly = new Polygon(mapView);
    poly.setPoints(subArea.points);
    poly.setTitle(subArea.name); // Makes it interactive
    mapView.getOverlays().add(poly);
    // Automatically goes to INTERACTIVE_BACKGROUND
}

// Small reference dots
for (GeoPoint refPoint : referencePoints) {
    Marker dot = new Marker(mapView);
    dot.setIcon(smallDot);
    dot.setPosition(refPoint);
    mapView.getOverlays().add(dot);
    manager.markAsDecoration(dot); // Stays in background
}

// Important POI markers
for (POI poi : pointsOfInterest) {
    Marker marker = new Marker(mapView);
    marker.setTitle(poi.name);
    marker.setSnippet(poi.description);
    marker.setPosition(poi.location);
    mapView.getOverlays().add(marker);
    // Automatically goes to INTERACTIVE_CONTENT
}

// User annotations on top
for (Annotation annotation : userAnnotations) {
    Marker marker = new Marker(mapView);
    marker.setTitle(annotation.text);
    marker.setPosition(annotation.location);
    mapView.getOverlays().add(marker);
    manager.markAsUserDrawn(marker); // Always on top
}
```

## Advanced Features

### Layer-Based Event Processing

The system processes tap events from highest to lowest layer:

```java
// Higher layers get tap events first
// USER_DRAWING (6) -> INTERACTIVE_CONTENT (5) -> INTERACTIVE_BACKGROUND (4) -> etc.

// This ensures important markers always respond to taps
// even if there are many background elements
```

### Spatial Optimization

For performance with many overlays:

```java
// System automatically uses spatial indexing for:
// - Fast tap detection with 600+ overlays
// - Viewport culling for better performance
// - Layer-based processing optimization

// No additional code needed - works automatically
```

### Backward Compatibility

The system is 100% backward compatible:

```java
// Existing code continues to work unchanged
mapView.getOverlays().add(marker);

// New layering features are opt-in
DefaultOverlayManager manager = (DefaultOverlayManager) mapView.getOverlayManager();
manager.markAsDecoration(marker); // Only if you want layer control
```

## Migration Guide

### From Legacy OSMDroid

1. **No changes required** - existing code works as-is
2. **Add layer control** where needed:
   ```java
   DefaultOverlayManager manager = (DefaultOverlayManager) mapView.getOverlayManager();
   manager.markAsDecoration(backgroundMarker);
   ```

### Common Migration Patterns

**Before (problematic):**
```java
// Last added overlay appears on top - unpredictable
mapView.getOverlays().add(backgroundPolyline);
mapView.getOverlays().add(importantMarker);
mapView.getOverlays().add(tinyDot); // Oops! Covers important marker
```

**After (predictable):**
```java
DefaultOverlayManager manager = (DefaultOverlayManager) mapView.getOverlayManager();

mapView.getOverlays().add(backgroundPolyline); // Auto: MAIN_CONTENT
mapView.getOverlays().add(importantMarker);    // Auto: INTERACTIVE_CONTENT
mapView.getOverlays().add(tinyDot);            // Auto: DECORATION (if no title/snippet)

// Or explicit control:
manager.markAsDecoration(tinyDot);             // Ensures background
manager.markAsInteractive(importantMarker);    // Ensures foreground
```

## Best Practices

### 1. Use Automatic Assignment When Possible
```java
// Good: Let system decide based on properties
Marker marker = new Marker(mapView);
marker.setTitle("POI"); // System knows this is interactive
mapView.getOverlays().add(marker);
```

### 2. Manual Assignment for Special Cases
```java
// Good: Explicit control when needed
manager.markAsDecoration(vertexDot);
manager.markAsUserDrawn(handDrawnLine);
```

### 3. Group Related Overlays
```java
// Good: Consistent layering for related elements
for (Marker dot : trailDots) {
    mapView.getOverlays().add(dot);
    manager.markAsDecoration(dot);
}
```

### 4. Consider User Interaction
```java
// Good: Interactive elements in higher layers
if (marker.isClickable()) {
    manager.markAsInteractive(marker);
} else {
    manager.markAsDecoration(marker);
}
```

## Troubleshooting

### Problem: Markers Still Drawing in Wrong Order

**Check:**
1. Are you using `DefaultOverlayManager`?
2. Did you call layer assignment after adding to map?
3. Is the layer system enabled? (It's on by default)

**Solution:**
```java
// Ensure proper order
mapView.getOverlays().add(marker);
manager.markAsDecoration(marker); // Call after adding
```

### Problem: Tap Events Not Working

**Check:**
1. Are interactive overlays in interactive layers?
2. Are background overlays blocking events?

**Solution:**
```java
// Move interactive elements to higher layers
manager.markAsInteractive(clickableMarker);
manager.markAsDecoration(backgroundDot);
```

### Problem: Performance Issues with Many Overlays

**Check:**
1. Are you using the spatial indexing features?
2. Are overlays properly categorized?

**Solution:**
```java
// System handles this automatically
// Ensure proper layer assignment for optimization
manager.markAsDecoration(manySmallDots);
```

## API Reference

### DefaultOverlayManager Methods

```java
// Manual layer assignment
void markAsDecoration(Overlay overlay)
void markAsInteractive(Overlay overlay)  
void markAsUserDrawn(Overlay overlay)
void assignOverlayToLayer(Overlay overlay, OverlayLayer layer)

// Layer enumeration
enum OverlayLayer {
    BACKGROUND_TILES(0),
    BACKGROUND_SHAPES(1), 
    DECORATION(2),
    MAIN_CONTENT(3),
    INTERACTIVE_BACKGROUND(4),
    INTERACTIVE_CONTENT(5),
    USER_DRAWING(6),
    OVERLAY_CONTROLS(7),
    POPUP_CONTENT(8),
    DEBUG_OVERLAY(9)
}
```

### Automatic Assignment Rules

**DECORATION Layer (2):**
- Markers with no title, snippet, info window, and not draggable

**INTERACTIVE_CONTENT Layer (5):**
- Markers with title, snippet, or info window
- ItemizedIconOverlay, ClickableIconOverlay

**MAIN_CONTENT Layer (3):**
- Polylines and Polygons without interactive features

**INTERACTIVE_BACKGROUND Layer (4):**
- Polylines and Polygons with info windows or titles

## Performance Notes

- **Spatial indexing** automatically optimizes tap detection for 600+ overlays
- **Layer-based processing** reduces unnecessary calculations
- **Viewport culling** improves performance with many off-screen overlays
- **API 23+ optimizations** provide additional performance benefits

## Conclusion

The enhanced layering system provides:
- ✅ Predictable overlay ordering
- ✅ Proper tap event handling  
- ✅ Performance optimizations
- ✅ Backward compatibility
- ✅ Easy migration path

Use automatic assignment for most cases, and manual control when you need precise layering behavior.