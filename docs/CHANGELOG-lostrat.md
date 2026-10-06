# LostRat Fork Changelog

Changes specific to the [LostRat osmdroid fork](https://github.com/LostRat/osmdroid-lostrat).  
Upstream osmdroid history remains in [CHANGELOG.md](../CHANGELOG.md) at the repository root.

**Current development version:** `7.0.3-lostrat-SNAPSHOT`

**Latest tagged release:** `v7.0.2-lostrat`

---

## [Unreleased] — 7.0.3-lostrat-SNAPSHOT (in progress)

### Prepared polyline batches

- Add `PolylineBatch` and `DefaultOverlayManager.replacePolylineBatch` for worker-prepared geometry and linear-time UI publication, preserving unrelated overlays and explicit layer/tap ordering. Add `Polyline.attachToMap` for detached construction with deferred density/info-window setup. Includes ownership/thread checks and regression tests (2026-09-11). See [integration and verification report](2026-09-11-polyline-batch-report.html).

### Terrain-RGB hillshade

- Add `org.osmdroid.tileprovider.terrain`: `HillshadeOverlay` (one Terrain-RGB MBTiles file at a time, `setFile` / `setEnabled`), `HillshadeTileProvider`, `HillshadeTileSource`, `Hillshader` (Horn 3x3 kernel, translucent output for any base map) and `TerrainRgbEncoding` (Mapbox, Terrarium, or `base / interval` parsed from the file description). Zoom range, bounds, tile size and encoding are read from the file; overzoom uses `MapTileApproximater`. Edge padding with neighbour tiles is still a TODO in the provider, so tile seams are visible at high opacity (2026-09-06)
- Add vertical exaggeration to the hillshade: `Hillshader.shade(..., exaggeration)`, `HillshadeTileSource.setExaggeration`, `HillshadeOverlay.setExaggeration`; 1 is true relief, above 1 brings out gentle terrain, like MapLibre `hillshade-exaggeration`. Live, like opacity and sun (2026-09-06)
- Add `SampleHillshade` (Terrain-RGB hillshade overlay) to the sample app; see `docs/TERRAIN_RGB_HILLSHADE.md` and `docs/2026-09-06-terrain-rgb-hillshade-report.html` (2026-09-06)
- Make `HillshadeOverlay` passive: `HillshadeTileProvider` now extends `MapTileModuleProviderBase` and neither class registers a broadcast receiver. Before, `unload()` (also called by `setFile` on a new file) detached the provider, which destroyed the overlay's one `SimpleRegisterReceiver`, and the next `setFile` failed with a null context; apps had to replace the overlay to switch files. Constructor of `HillshadeTileProvider` loses its `IRegisterReceiver` parameter (2026-09-13)
- Add `SampleHillshadeAsset`: minimal hillshade example using the bundled `assets/mbtiles/forest-park-terrain-rgb.mbtiles` (Forest Park, Portland OR); copies the asset to the cache dir, loads it into a `HillshadeOverlay` on the background-tiles layer and zooms to its bounds (2026-09-13)

### Build

- Move the AGP, Kotlin plugin, SpotBugs, JUnit, Robolectric and MapsForge versions into a `gradle/libs.versions.toml` version catalog, and apply the plugins through the root `plugins {}` block instead of a `buildscript` classpath. Android Studio's Upgrade Assistant could not change the AGP version while it was read from `gradle.properties` (it found 0 usages). The `android-plugin.version`, `junit.version` and `robolectric.version` properties are gone. Bump AGP 9.4.0 to 9.4.1 (2026-10-05)
- Move every remaining library coordinate from the module `build.gradle` files into the version catalog, so Android Studio no longer flags them with "Use version catalog instead". `resolutionStrategy.force` entries now build their versions from `libs.versions.*`. Resolved dependency trees are unchanged. Bump Gradle wrapper to 9.8.0, Kotlin plugin to 2.4.20, androidx appcompat 1.8.0 / core 1.19.1 / sqlite 2.7.1 / lifecycle (forced) 2.10.0, Robolectric 4.17, and the JDK-tool libraries (Spring 7, Jersey 4) (2026-10-05)
- Make javac `-Xlint:deprecation` opt-in (`-PlintDeprecation`); it printed 300+ per-call warnings on every clean build. Remove `android.nonFinalResIds=false` (no `switch` on resource ids remains; AGP 10 drops the option). Fix the `OSMTileInfo` javadoc `@param` names (2026-10-05)

### Sample app

- Fix the intro screen on devices with a taskbar: at targetSdk 35+ the app is drawn edge-to-edge, and `IntroActivity` applied no window insets, so its Next/Prev bar sat under the taskbar (seen on an unfolded Pixel 10 Pro emulator). The intro root now pads itself by the system-bar and display-cutout insets (2026-10-05)

### MapsForge

- Bump MapsForge from 0.28.0 to 0.30.0 (JitPack). 0.29/0.30 are POI v4 and theme updates; the DirectRenderer tile API we use is unchanged.
- Add `MapsForgeTileSource.setTextScaleByZoom(growthPerZoom, startZoom)`: label text grows by `growthPerZoom` per zoom level above `startZoom`, capped at 2.5x, so labels keep up with Mapsforge's 1.5x-per-zoom line widening above zoom 12. Lines and symbols are unchanged; `1` is stock Mapsforge. The setting is static and each source snapshots it at construction. `MapsForgeTileCacheKeys` adds a `text-<percent>-z<zoom>` part for non-stock settings, so stock text keeps existing cache names; call it before building the cache key and the source. The label store's `getVisibleItems` mirrors Mapsforge 0.30.0's `MapDataStoreLabelStore`, so recheck it on MapsForge upgrades (2026-10-05)

---

## [7.0.2-lostrat] — 2026-09-03

Tagged release `v7.0.2-lostrat`.

### MapsForge

- Add `MapsForgeTileCacheKeys`: stable tile-source names keyed by map files, theme, and the *render scale* actually applied (not raw display density), so tiles rendered at different scales never share a cache namespace (2026-08-01)
- Fix `MapsForgeTileProvider` pairing the cache *reader* with the cache *writer*: the default `SqlTileWriter` is now read back through `MapTileSqlCacheProvider` instead of `MapTileFilesystemProvider`, so rendered tiles are actually reused from `cache.db`
- Add two-argument `MapsForgeTileProvider(receiver, tileSource)` constructor and `getTileWriter()` override
- `applyDensityScaling()` now checks `DisplayDensityManager.isInitialized()` instead of a null check
- Samples sort map files and call `applyDensityScaling()` after `createInstance()`

### Build & tooling

- Bump AGP to 9.3.1 and Gradle wrapper to 9.6.1 (2026-08-01); then AGP 9.4.0, Gradle wrapper 9.7.1, and JUnit 6.1.3 (2026-09-03)

### Sample app

- Add `SampleRotationListener` (Map Rotation Listener) demonstrating `CustomRotationOverlay` with a compass icon and `RotationGestureListener` / `RotationEvent` callbacks; replaces a stray example Activity that had been sitting in the library source set (2026-09-03)

### Documentation

- Document `MapsForgeTileCacheKeys` in `2025-11-27-Mapsforge-Density-Scaling.md`
- Add [2026-09-03 change survey and commit plan](2026-09-03-change-survey-and-commit-plan.html)
- Promote six usage/behaviour docs from the root notes into `docs/`: density scaling usage, layering system tutorial, advanced polyline tutorial, 16 KB page-size compliance, SqlTileWriter null safety, and the 2026-06-14 display-layer optimization report; remaining session notes live under `reference/notes/`

---

## [7.0.1-lostrat] — 2026-07-01

Tagged release `v7.0.1-lostrat`.

### Build & tooling

- Update Gradle 9.5, AGP 9.2, compile/target SDK 37, and module dependencies
- Update Gradle and Android build tooling (May 2026)
- Remove MultiDex from OpenStreetMapViewer sample app

### MapsForge

- Update `MapsForgeTileSource` for MapsForge 0.28.0 DirectRenderer API

### Performance

- Reduce per-frame allocations in marker and polyline draw paths
- Eliminate per-frame allocations in polyline/polygon draw path
- Speed up tile index decode and zoom factor lookup

### Overlays & gestures

- Improve ScaleBarOverlay contrast and styling options
- Fix one-finger double-tap-hold zoom behavior
- Handle edge-to-edge status bar insets on sample app toolbars

### Sample app

- Update ACRA crash reporting integration
- Replace resource ID switches with safer navigation patterns

### Bug fixes

- Fix GeoPoint distance precision for nearby points

### Documentation

- Add `docs/` folder with fork changelog and enhanced layer system guide
- Link documentation from README; track `docs/` in git

---

## [7.0.0-lostrat] — 2025-11-27

Tagged release `v7.0.0-lostrat`.

### Overlay system (major)

- **10-layer z-index system** — automatic draw order and tap priority for markers, polylines, and polygons ([Enhanced Layer System](ENHANCED_LAYER_SYSTEM.md))
- **Spatial indexing** — grid-based hit testing for 100+ overlays
- **FolderOverlay flattening** — children assigned to intrinsic layers; folder disable hides all descendants
- **`Overlay.isInteractive()`** — base-class interactivity flag
- **Smart marker detection** — decoration vs interactive assignment
- **Helper methods** — `markAsInteractive()`, `markAsDecoration()`, `markAsUserDrawing()`
- **Thread safety** improvements in `DefaultOverlayManager`
- **Remove parallel stream tap processing** — fixes unreliable tap handling at scale (Dec 2025)

### Cache & database

- Modular CacheManager architecture optimized for API 23+
- SQLite optimizations for Android P+; read-only database crash prevention
- SqlTileWriter null-safety for unavailable databases

### Map interaction

- Rotation gesture listener interface and smoother rotation input
- Improved single-tap detection for polylines and polygons
- MapView projection and layout caching

### Utilities

- Rewrite `BoundingBox.overlaps()` with correct AABB and date-line logic
- `BoundingBox.fromGeoPointsSafe()` helper
- Coordinate wrapping and distance calculation optimizations (API 23+)
- GeoPackage bitmap density scaling fix

### Build & platform

- Minimum SDK 23 (Android 6.0); Java 17
- Gradle 8.x → 9.x migration path; 16 KB page size compliance
- Sample app modernization (`getOnBackPressedDispatcher`, density scaling)
- MapsForge integration with density-aware scaling

### Documentation

- Root-level markdown notes from AI-assisted development sessions (overlay performance, cache manager, marker touch buffer, etc.)

---

## [6.1.22-lostrat] — earlier fork baseline

- Fork from upstream osmdroid for personal app use
- Minimum API 23, Gradle 8 updates
- MapsForge 0.25.0 integration in sample app
- JitPack publishing support

---

*This changelog covers LostRat fork changes only. For upstream osmdroid releases, see [CHANGELOG.md](../CHANGELOG.md).*
