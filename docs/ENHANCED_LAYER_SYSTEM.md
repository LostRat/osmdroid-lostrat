# Enhanced Z-Index Layer System

**Last updated:** July 1, 2026  
**Enhancement:** 10-layer z-index system for proper overlay ordering  
**Solves:** Tiny markers over big markers, user drawing priority, flexible layering, FolderOverlay z-ordering

> **For AI-assisted development:** Point your AI coding tool at this file when you need help making polylines and markers interactive, assigning correct draw order, or debugging tap handling with many overlays.

## Problem Solved

- **Tiny decoration markers** drawn over important markers
- **User-drawn lines** need to be on top of everything
- **Flexible layering** without hundreds of individual z-indexes
- **Automatic categorization** with manual override capability
- **FolderOverlay children** flattened into their intrinsic layers (markers stay above polylines even inside folders)

## 10-Layer System

### Layer Hierarchy (Bottom to Top)

```
Layer 0: BACKGROUND_TILES      - Tile overlays, base maps
Layer 1: BACKGROUND_SHAPES     - Background polylines, polygons
Layer 2: DECORATION            - Tiny markers, vertex dots
Layer 3: MAIN_CONTENT          - Main polylines, primary content
Layer 4: INTERACTIVE_BACKGROUND - Clickable polylines, selectable shapes
Layer 5: INTERACTIVE_CONTENT   - Main markers, important elements
Layer 6: USER_DRAWING          - User-drawn lines on top
Layer 7: OVERLAY_CONTROLS      - UI overlays, controls
Layer 8: POPUP_CONTENT         - Info windows, popups
Layer 9: DEBUG_OVERLAY         - Debug information, always on top
```

## Automatic Assignment Logic

### Big Interactive Marker

```java
Marker bigMarker = new Marker(mapView);
bigMarker.setOnMarkerClickListener(...);
// → Automatically assigned to INTERACTIVE_CONTENT (Layer 5)
// → Drawn ABOVE decoration markers
// → Gets tap priority
```

If a marker has a title, snippet, info window, ID, or is draggable, it is treated as interactive.

### Tiny Decoration Markers

```java
Marker tinyMarker = new Marker(mapView);
// No title, no snippet, not draggable, no info window, no ID
// → Automatically assigned to DECORATION (Layer 2)
// → Drawn BELOW interactive markers
// → Lower tap priority
```

### Click Listeners Cannot Be Detected Automatically

The overlay manager cannot inspect whether a `Marker` has a click listener attached. If your marker has no title/snippet/ID but **is** clickable, call:

```java
DefaultOverlayManager manager = (DefaultOverlayManager) mapView.getOverlayManager();
manager.markAsInteractive(myMarker);
```

### Interactive Polylines and Polygons

Polylines/polygons are assigned to `INTERACTIVE_BACKGROUND` (Layer 4) when they are a `PolyOverlayWithIW` with an info window or title. Otherwise they go to `MAIN_CONTENT` (Layer 3).

To force interactivity:

```java
manager.markAsInteractive(myPolyline);  // → INTERACTIVE_BACKGROUND
```

### User-Drawn Lines

```java
Polyline userLine = new Polyline(mapView);
manager.markAsUserDrawing(userLine);
// → USER_DRAWING (Layer 6), drawn above main content and interactive layers
```

Or assign manually:

```java
manager.assignOverlayToLayer(userLine, OverlayLayer.USER_DRAWING);
```

### FolderOverlay Flattening

When a `FolderOverlay` is added, its **children** are assigned to their intrinsic layers (markers → Layer 5, polylines → Layer 3, etc.). The folder itself is not placed in a single z-layer. Disabling a folder hides all children via parent/hierarchy tracking (`isHierarchyEnabled()`).

## Usage Examples

### Automatic (Recommended)

```java
mapView.getOverlays().add(tinyMarker);   // → DECORATION (Layer 2)
mapView.getOverlays().add(polyline);     // → MAIN_CONTENT (Layer 3)
mapView.getOverlays().add(bigMarker);    // → INTERACTIVE_CONTENT (Layer 5)
// bigMarker is always visible on top and gets tap priority
```

### Manual Override

```java
DefaultOverlayManager manager = (DefaultOverlayManager) mapView.getOverlayManager();

manager.assignOverlayToLayer(userDrawnLine, OverlayLayer.USER_DRAWING);
manager.assignOverlayToLayer(vertexMarker, OverlayLayer.DECORATION);
manager.assignOverlayToLayer(importantLine, OverlayLayer.INTERACTIVE_BACKGROUND);

// Convenience helpers
manager.markAsInteractive(clickableMarker);
manager.markAsDecoration(vertexDot);
manager.markAsUserDrawing(userPolyline);
```

### Query Layer System

```java
DefaultOverlayManager manager = (DefaultOverlayManager) mapView.getOverlayManager();

OverlayLayer layer = manager.getOverlayLayer(myMarker);
List<Overlay> decorations = manager.getOverlaysInLayer(OverlayLayer.DECORATION);
manager.setUseLayerSystem(true); // Default: enabled
```

## Automatic Detection Logic

### Decoration Markers (Layer 2)

```java
private boolean isDecorationMarker(Marker marker) {
    return marker.getTitle() == null &&
           marker.getSnippet() == null &&
           !marker.isDraggable() &&
           marker.getInfoWindow() == null &&
           marker.getId() == null;
}
```

### Interactive Markers (Layer 5)

Any marker that fails the decoration check, or that you explicitly mark with `markAsInteractive()`.

### User Drawing Detection

Default implementation checks for title prefix `USER_DRAWN_` on markers. For polylines, use `markAsUserDrawing()` or `assignOverlayToLayer(..., USER_DRAWING)`.

## Tap Handling and Performance

- **Layer priority:** Higher layers are checked first for touch events.
- **Spatial index:** Used when overlay count ≥ 100 (256 px grid cells); direct search below that threshold.
- **Sequential tap search:** Parallel stream processing was removed (Dec 2025) to fix tap-handling reliability with large overlay sets.
- **Drawing order:** Layers drawn 0→9; minimal overhead (10 lists + overlay→layer map).

## Migration

Existing code works unchanged:

```java
mapView.getOverlays().add(marker);
mapView.getOverlays().add(polyline);
```

When you need explicit control:

```java
DefaultOverlayManager manager = (DefaultOverlayManager) mapView.getOverlayManager();

for (Marker vertexMarker : vertexMarkers) {
    manager.markAsDecoration(vertexMarker);
}

manager.markAsUserDrawing(userPolyline);
```

## Related Source Files

- `DefaultOverlayManager.java` — layer assignment, draw order, tap routing
- `Overlay.java` — `isInteractive()` base method
- `Marker.java` — marker-specific behavior
- `FolderOverlayLayerTest.java` — folder flattening tests
