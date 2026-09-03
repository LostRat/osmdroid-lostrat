# Fix for XML Tile Source Scaling Issue

When MapView uses XML-defined tile source like:
```xml
<org.osmdroid.views.MapView
    android:id="@+id/map"
    tilesource="USGS National Map Topo"
    ...
```

Add this to your onCreate after MapView initialization:

```java
// After findViewById and before adding overlays
MapView map = findViewById(R.id.map);

// Always set DPI scaling
map.setTilesScaledToDpi(true);

// Check if using XML-defined tile source and adjust
String currentTileSourceName = map.getTileProvider().getTileSource().name();
boolean isXmlTileSource = currentTileSourceName.contains("USGS") || 
                         currentTileSourceName.contains("National Map") ||
                         !currentTileSourceName.equals("mapsforge-default");

if (isXmlTileSource) {
    // XML tile sources need different scaling adjustment
    DisplayDensityManager.adjustForMapScaling(false); // Don't reduce scaling
} else {
    // Programmatic tile sources
    DisplayDensityManager.adjustForMapScaling(map.isTilesScaledToDpi());
}

// Add your overlays after this
```

This detects XML-defined tile sources and applies appropriate scaling.