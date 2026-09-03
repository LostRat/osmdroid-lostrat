# Git Commit Plan - 2025-11-25

This plan outlines the strategy to safely commit the current pending changes identified in `20251125_git_diff_HEAD.txt`. The changes cover UI modernization, GeoPackage rendering corrections, database robustness, and the initial implementation of the spatial index optimizations.

## Strategy
We will split the large diff into **4 logical commits**. This isolates the stable maintenance fixes from the complex optimization logic, making it easier to bisect or revert specific changes if needed.

## Commit 1: Sample App Modernization
**Files:**
- `OpenStreetMapViewer/src/main/java/org/osmdroid/samplefragments/BaseSampleFragment.java`
- `OpenStreetMapViewer/src/main/java/org/osmdroid/samplefragments/ui/SamplesMenuFragment.java`
- `osmdroid-android/src/main/java/org/osmdroid/util/DisplayDensityManager.java`
- `build.gradle`

**Reasoning:**
Updates to AndroidX APIs (MenuProvider, FragmentManager) and Java reflection fixes. These are standard maintenance updates.

**Commit Message:**
```text
refactor: modernize sample app and fix density calculation

- Replace deprecated setHasOptionsMenu with AndroidX MenuProvider in BaseSampleFragment
- Update SamplesMenuFragment to use getParentFragmentManager and constructor-based reflection
- Fix DisplayDensityManager to account for fontScale in scaledDensity
- Minor build.gradle formatting
```

## Commit 2: GeoPackage Context & Rendering Fixes
**Files:**
- `OpenStreetMapViewer/src/main/java/org/osmdroid/samplefragments/geopackage/GeopackageFeatureTiles.java`
- `osmdroid-geopackage/src/main/java/org/osmdroid/gpkg/tiles/feature/GeoPackageFeatureTileProvider.java`
- `osmdroid-geopackage/src/main/java/org/osmdroid/gpkg/tiles/raster/GeoPackageMapTileModuleProvider.java`

**Reasoning:**
Passes `Context` to tile providers to ensure `BitmapDrawable` respects the device's screen density, preventing rendering artifacts.

**Commit Message:**
```text
fix(geopackage): ensure correct bitmap density scaling

- Propagate Context to GeoPackageFeatureTileProvider and GeoPackageMapTileModuleProvider
- Use Resources-aware BitmapDrawable constructor to handle screen density correctly
```

## Commit 3: SqlTileWriter Robustness
**Files:**
- `osmdroid-android/src/main/java/org/osmdroid/tileprovider/modules/SqlTileWriter.java`

**Reasoning:**
Adds try-catch blocks around WAL enabling and Index creation. This prevents crashes when opening read-only databases (e.g., from scoped storage or assets).

**Commit Message:**
```text
fix(cache): prevent crashes on read-only tile databases

- Catch SQLiteException when enabling Write-Ahead Logging (WAL)
- Catch exceptions during 'expires' index creation
- Allows graceful degradation for read-only cache files
```

## Commit 4: DefaultOverlayManager Spatial Index & Layering
**Files:**
- `osmdroid-android/src/main/java/org/osmdroid/views/overlay/DefaultOverlayManager.java`

**Reasoning:**
Implementation of the Memory-Safe Spatial Index, Layer System, and Memory Callbacks.
*Note: This commit establishes the baseline for the fixes identified in `gemini/20251125-OverlayManager-Fixes.md`.*

**Commit Message:**
```text
feat(overlay): implement spatial index and layer system

- Replace HashMap spatial index with fixed-grid array to reduce allocation
- Implement Adaptive Search for tap detection
- Add Layer System for explicit Z-ordering
- Add OverlayMemoryCallback for cache coordination
- Optimize loop structures for API 24+
```

## Next Steps
After applying these commits, proceed immediately to **Task 1** in `gemini/20251125-OverlayManager-Fixes.md` to address the thread-safety and logic issues identified in the `DefaultOverlayManager` implementation.
