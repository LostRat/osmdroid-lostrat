# LostRat Fork Changelog

Changes specific to the [LostRat osmdroid fork](https://github.com/LostRat/osmdroid-lostrat).  
Upstream osmdroid history remains in [CHANGELOG.md](../CHANGELOG.md) at the repository root.

**Current development version:** `7.0.2-lostrat-SNAPSHOT`  
**Latest tagged release:** `v7.0.1-lostrat`

---

## [Unreleased] — 7.0.2-lostrat-SNAPSHOT (in progress)

### MapsForge

- Add `MapsForgeTileCacheKeys`: stable tile-source names keyed by map files, theme, and the *render scale* actually applied (not raw display density), so tiles rendered at different scales never share a cache namespace (2026-08-01)
- Fix `MapsForgeTileProvider` pairing the cache *reader* with the cache *writer*: the default `SqlTileWriter` is now read back through `MapTileSqlCacheProvider` instead of `MapTileFilesystemProvider`, so rendered tiles are actually reused from `cache.db`
- Add two-argument `MapsForgeTileProvider(receiver, tileSource)` constructor and `getTileWriter()` override
- `applyDensityScaling()` now checks `DisplayDensityManager.isInitialized()` instead of a null check
- Samples sort map files and call `applyDensityScaling()` after `createInstance()`

### Build & tooling

- Bump AGP to 9.3.1 and Gradle wrapper to 9.6.1

### Sample app

- Add `SampleRotationListener` (Map Rotation Listener) demonstrating `CustomRotationOverlay` with a compass icon and `RotationGestureListener` / `RotationEvent` callbacks; replaces a stray example Activity that had been sitting in the library source set (2026-09-03)

### Documentation

- Document `MapsForgeTileCacheKeys` in `2025-11-27-Mapsforge-Density-Scaling.md`
- Add [2026-09-03 change survey and commit plan](2026-09-03-change-survey-and-commit-plan.html)

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
