
# OSMDroid Scaling Analysis - 2025-09-24

## Issue

The `LatLonGridlineOverlay2` and `ScaleBarOverlay` overlays have incorrect scaling (appearing too small) when the `MapView`'s tile source is set via XML layout and `map.setTilesScaledToDpi(true)` is used. The scaling is correct when using `mapsforge` or `MBTiles` tile sources.

## Root Cause

The issue stems from the `DisplayDensityManager` class not being initialized in the application's startup sequence when a default tile source is used.

1.  **`DisplayDensityManager`:** This is a custom singleton class you've introduced to manage display density and provide consistent scaling for overlays and other UI elements.

2.  **Initialization Requirement:** The `DisplayDensityManager` has a static `initialize(Context context)` method that **must** be called before any other methods in the class can be used. The `LatLonGridlineOverlay2` and `ScaleBarOverlay` overlays check `DisplayDensityManager.isInitialized()` before applying scaling. When not initialized, the scaling code is skipped, and the overlays are drawn with their default, unscaled dimensions, which appear tiny on high-density screens.

3.  **Conditional Initialization:** The `DisplayDensityManager` is likely being initialized somewhere within the setup code for `mapsforge` or `MBTiles` tile providers, but not in the standard application `onCreate` method. This is why the scaling works correctly for those specific tile sources. When you use a tile source from the XML layout, the initialization code is never executed.

4.  **`setTilesScaledToDpi` Interaction:** The method `map.setTilesScaledToDpi(true)` in `MapView` scales the map tiles themselves, but it does not automatically initialize or interact with your custom `DisplayDensityManager`. There is a method `DisplayDensityManager.adjustForMapScaling(boolean mapUsesDpiScaling)` which is intended to be called to adjust the overlay scaling when the map tiles are scaled, but this method is also not being called.

## Solution

To resolve this issue, you need to ensure that the `DisplayDensityManager` is initialized early in your application's lifecycle, and that its scaling factors are adjusted when you enable tile scaling.

Here is the recommended sequence of operations in your Activity's `onCreate` method:

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // 1. Initialize DisplayDensityManager
    // This should be one of the first things you do.
    DisplayDensityManager.initialize(getApplicationContext());

    // 2. Set your content view
    setContentView(R.layout.your_layout_file);

    // 3. Get a reference to your MapView
    MapView map = findViewById(R.id.map);

    // 4. Set tiles scaled to DPI
    map.setTilesScaledToDpi(true);

    // 5. Adjust DisplayDensityManager for map scaling
    // This will ensure your overlays are scaled correctly relative to the map tiles.
    DisplayDensityManager.adjustForMapScaling(true);

    // 6. Add your overlays
    LatLonGridlineOverlay2 grids = new LatLonGridlineOverlay2();
    grids.setDensityScalingEnabled(true);
    map.getOverlayManager().add(grids);
    grids.setTextSizeOption(LatLonGridlineOverlay2.TextSize.SMALLER);
    grids.setCornerRadius(9f);
    map.getOverlayManager().add(grids);

    ScaleBarOverlay scaleBar = new ScaleBarOverlay(map);
    scaleBar.setDensityScalingEnabled(true);
    map.getOverlayManager().add(scaleBar);

    // ... other map setup
}
```

By following this sequence, you ensure that:

*   The `DisplayDensityManager` is always initialized, regardless of the tile source.
*   The overlay scaling is correctly adjusted to account for the tile scaling, providing a consistent and correctly scaled appearance for your gridlines and scale bar.
