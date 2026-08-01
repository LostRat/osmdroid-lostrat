# Mapsforge Density Scaling Support - November 2025

## Overview
A new density-aware system has been implemented to automatically scale Mapsforge tiles, overlays, and UI elements based on the device's screen density. This eliminates the need for complex manual calculations and ensures consistent visual sizing across different devices (ldpi to xxxhdpi).

## Key Features

### 1. Automatic Scaling
The system now automatically calculates the optimal scale factor for Mapsforge rendering based on the device's display metrics.
*   **Old Way:** Developers had to manually calculate scale factors using `DisplayMetrics`, often leading to inconsistencies.
*   **New Way:** Simply call `applyDensityScaling()` on your tile source.

### 2. Consistent Overlay Sizing
Overlays such as `LatLonGridlineOverlay` now have built-in support for density scaling. Line widths and text sizes are automatically adjusted to look physical consistent (e.g., a 2mm line looks like 2mm on both a tablet and a phone).

## Usage Guide

### Basic Setup
To enable automatic scaling for a Mapsforge tile source:

```java
// Create your Mapsforge tile source. The cache key is derived from the render
// scale that applyDensityScaling() will apply (not the raw display density), so
// tiles rendered at different scales never share a cache namespace.
String cacheName = MapsForgeTileCacheKeys.forMapsAndTheme(maps, "default-path-contour");
MapsForgeTileSource fromForgeFiles = MapsForgeTileSource.createFromFiles(maps, theme, cacheName);

// ENABLE AUTOMATIC SCALING
fromForgeFiles.applyDensityScaling();
```

If you use `setUserScaleFactor(float)` instead of `applyDensityScaling()`, pass that
exact scale to the key builder so the cache tracks what is actually rendered:

```java
String cacheName = MapsForgeTileCacheKeys.forMapsAndTheme(maps, "default-path-contour", 0.5f);
// ...
fromForgeFiles.setUserScaleFactor(0.5f);
```

## Tile Cache Keys in Depth (`MapsForgeTileCacheKeys`)

### Why cache keys matter

Mapsforge tiles are *rendered*, not downloaded. Once rendered, osmdroid persists them in
its tile cache (`cache.db` via `SqlTileWriter`) under the tile source's **name**. That name
is therefore the cache namespace: if two different rendering configurations share a name,
the cache will happily serve tiles rendered under the *other* configuration — wrong theme,
wrong scale, wrong map data — because it has no other way to tell them apart.

`MapsForgeTileCacheKeys` builds a name that changes exactly when the rendered output would
change, and stays stable when it would not.

### Anatomy of a key

```
mapsforge-<map-part>-<theme-part>-scale-<NNN>[-<variant>]-<12-char-hash>
```

Example: `mapsforge-forest-park-202502.map-rendertheme-v4-scale-60-a1b2c3d4e5f6`

| Segment | Purpose | Example |
|---|---|---|
| `mapsforge` | Fixed prefix, groups all mapsforge namespaces | `mapsforge` |
| map part | Human-readable: first map's file name, plus a count for multi-map sets | `forest-park-202502.map` or `oregon.map-plus-2-maps` |
| theme part | Sanitized theme name | `rendertheme-v4` |
| `scale-NNN` | Render scale × 100, rounded | `scale-60` for scale 0.6 |
| variant | Optional caller-supplied discriminator (e.g. `"hillshade"`) | omitted when null |
| hash | 12 hex chars of SHA-256 over all inputs — the actual uniqueness guarantee | `a1b2c3d4e5f6` |

The readable segments exist for humans debugging `cache.db`; the **hash** is what makes the
key unique. It covers, for every map file: file **name**, **size in bytes**, and
**last-modified timestamp** — plus the theme name, the render scale, and the variant.

### What invalidates the cache — and what does not

A **new namespace** (one-time re-render, old tiles eventually trimmed by the cache's size
limit) is created when any of these change:

*   A map file is **updated** (new size or mtime — e.g. you ship a newer `.map` extract)
*   The **theme** changes
*   The **render scale** changes (different device density bucket, or a manual
    `setUserScaleFactor` value)
*   The **set or order** of map files changes
*   The **variant** string changes

The namespace is **preserved** (cache stays warm) when:

*   The same map file is **moved or renamed to a different directory** — the hash uses
    name + size + mtime, deliberately *not* the absolute path
*   The device density changes but **clamps to the same render scale** — the scale factor
    formula clamps to [0.2, 1.0], so e.g. densities 1.0 and 1.2 both render at scale 1.0
    and correctly share one cache

### Why the key uses render scale, not display density

`applyDensityScaling()` sets the Mapsforge user scale factor from
`DisplayDensityManager.getMapForgeScaleFactor()`, which is a **non-linear, clamped**
function of density — not density itself. Keying on raw density would be wrong in both
directions:

*   **Over-splitting:** densities that clamp to the same scale (≤ ~1.24 → 1.0, ≥ ~3.79 → 0.2)
    would get different keys despite producing pixel-identical tiles — duplicate caches,
    needless cold starts.
*   **Under-splitting (dangerous):** the key would not change if you switched from
    `applyDensityScaling()` to a manual `setUserScaleFactor(0.5f)` on the same device —
    tiles rendered at *different* scales would share one namespace, and stale
    wrongly-scaled tiles would be served from cache.

Keying on the scale that is actually applied eliminates both failure modes.

### Map file order is significant — sort once, use everywhere

With the default `MultiMapDataStore.DataPolicy.RETURN_ALL`, the order in which map files
are added can affect the rendered result when maps overlap. The key builder therefore
treats `[a.map, b.map]` and `[b.map, a.map]` as **different** configurations — it does not
sort for you. Canonicalize the order yourself, once, and feed the *same array* to both the
key builder and `createFromFiles(...)`:

### Complete example

```java
import org.osmdroid.mapsforge.MapsForgeTileCacheKeys;
import org.osmdroid.mapsforge.MapsForgeTileProvider;
import org.osmdroid.mapsforge.MapsForgeTileSource;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;

// One-time Mapsforge init (e.g. in onCreate)
MapsForgeTileSource.createInstance(getActivity().getApplication());

// 1. Collect map files and sort them into a canonical order. The SAME sorted
//    array must go to both the key builder and createFromFiles(), because file
//    order can change rendered output for overlapping maps (RETURN_ALL policy).
List<File> mapFileList = new ArrayList<>(findMapFiles());
Collections.sort(mapFileList);
File[] maps = mapFileList.toArray(new File[0]);

// 2. Load the render theme.
XmlRenderTheme theme = new AssetsRenderTheme(
        getContext().getApplicationContext().getAssets(),
        "renderthemes/", "rendertheme-v4.xml");
final String themeName = "rendertheme-v4";

// 3. Build the cache key. The no-float overload resolves the render scale that
//    applyDensityScaling() will apply (via DisplayDensityManager), falling back
//    to 1.0 if the manager is not initialized — in which case
//    applyDensityScaling() is also a no-op, so key and rendering stay in sync.
String cacheName = MapsForgeTileCacheKeys.forMapsAndTheme(maps, themeName);

// 4. Create the tile source under that name and apply the matching scale.
MapsForgeTileSource tileSource = MapsForgeTileSource.createFromFiles(maps, theme, cacheName);
tileSource.applyDensityScaling();

// 5. Hook it up. MapsForgeTileProvider defaults to SqlTileWriter with a matching
//    SQL cache-read provider, so rendered tiles persist across app restarts.
MapsForgeTileProvider provider = new MapsForgeTileProvider(
        new SimpleRegisterReceiver(getContext()), tileSource);
mMapView.setTileProvider(provider);
```

Manual scale variant — pass the identical value to both calls:

```java
final float renderScale = 0.5f;
String cacheName = MapsForgeTileCacheKeys.forMapsAndTheme(maps, themeName, renderScale);
MapsForgeTileSource tileSource = MapsForgeTileSource.createFromFiles(maps, theme, cacheName);
tileSource.setUserScaleFactor(renderScale);
```

Variant discriminator — for configurations the other inputs cannot capture (a custom
`HillsRenderConfig`, a language setting, an experimental renderer flag):

```java
String cacheName = MapsForgeTileCacheKeys.forMapsThemeAndVariant(
        maps, themeName, MapsForgeTileCacheKeys.currentRenderScale(), "hillshade-de");
```

### Migration notes

*   Code that passed `getResources().getDisplayMetrics().density` as the third argument of
    `forMapsAndTheme(...)` **must** switch to the two-argument overload (or pass a true
    render scale): that parameter now means *render scale*, and a raw density like 2.75
    would produce a semantically wrong key.
*   The first run after upgrading re-renders tiles once — the key scheme changed (density →
    scale; absolute path dropped from the hash), so existing namespaces are abandoned and
    reclaimed later by the cache's size-based trimming.

### Advanced Configuration
You can access the `DisplayDensityManager` directly if you need custom scaling logic or unit conversions:

```java
DisplayDensityManager density = DisplayDensityManager.getInstance();

// Get the calculated scale factor
float autoScale = density.getMapForgeScaleFactor();

// Convert DP to Pixels
float pixels = density.dpToPx(16.0f);
```

### Comparison: Old vs New

**Legacy Manual Calculation (Deprecated):**
```java
final float GESTURE_THRESHOLD_DP = 16.0f;
float gestureThreshold = applyDimension(COMPLEX_UNIT_DIP, GESTURE_THRESHOLD_DP + 0.5f, getResources().getDisplayMetrics());
float scaleFactor = 0.6F * (34F / gestureThreshold);
fromForgeFiles.setUserScaleFactor(scaleFactor);
```

**New Automatic Method:**
```java
fromForgeFiles.applyDensityScaling();
```

## Benefits
*   **Simplicity:** Reduces boilerplate code.
*   **Consistency:** Ensures map elements are legible on high-density screens.
*   **Future-Proof:** Automatically handles new density buckets (e.g., if 800dpi screens become common).
