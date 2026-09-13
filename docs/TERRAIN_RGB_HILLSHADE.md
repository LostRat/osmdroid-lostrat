# Terrain-RGB Hillshade Overlay

Status: **working** (2026-09-13), verified on a Pixel 7a through PDXWalk / OsmTrails and the bundled sample. Tile-edge padding is still a TODO. Design notes, verification and the commit plan are in [2026-09-06-terrain-rgb-hillshade-report.html](2026-09-06-terrain-rgb-hillshade-report.html).

## What it is

`HillshadeOverlay` draws a translucent hillshade computed on the device from a
Terrain-RGB MBTiles file. Terrain-RGB tiles are ordinary PNG or WebP tiles whose
pixel colours encode elevation, the format used by MapLibre GL `raster-dem` sources.
The overlay decodes them, runs a Horn 3x3 slope kernel, and emits a bitmap that is
transparent on level ground, dark on slopes facing away from the sun and slightly
light on slopes facing it. It draws over any base map, including none.

The overlay owns its own file, archive and tile provider. It never joins the map's
base tile provider or the base archive list, so switching base maps, going offline,
or turning the base tiles off does not affect it.

## Quick setup

```java
HillshadeOverlay hill = new HillshadeOverlay(mapView);
mapView.getOverlays().add(hill);                     // once, after the base tiles

hill.setFile(new File(dir, "uk-terrain-rgb-webp.mbtiles"));   // opens, reads metadata
hill.setEnabled(true);                                        // toggle; tiles stay cached while off
mapView.invalidate();
```

`setFile(null)` or `unload()` closes the file. `setFile` on a new file replaces the old one.

Optional:

```java
hill.setMaxAlpha(140);                 // 0..255 opacity of full shadow, default 110
hill.setSun(315, 45);                  // azimuth clockwise from north, altitude; defaults shown
hill.setExaggeration(1.5);             // vertical exaggeration, 1 = true relief; brings out gentle terrain
hill.setEncoding(TerrainRgbEncoding.TERRARIUM);   // only if the file's metadata does not say
```

## What is read from the file

| Metadata key | Use | Fallback |
|---|---|---|
| `minzoom`, `maxzoom` | tile source zoom range; above `maxzoom` the last level is scaled up | `min/max(zoom_level)` from `tiles` |
| `bounds` | nothing is requested or drawn outside them | draw everywhere |
| `description` | `base <b> interval <i>` selects the pixel encoding | Mapbox (`-10000`, `0.1`) |
| first tile blob | decoded width gives the tile size (256 or 512) | 256 |

Elevation is `base + (R * 65536 + G * 256 + B) * interval`. Mapbox is base -10000,
interval 0.1; Terrarium is base -32768, interval 1/256. A file built with
`rio rgbify -b -10000 -i 1` must carry `base -10000 interval 1` in its description or
be given `TerrainRgbEncoding` explicitly, otherwise every elevation is off by 10x.

Tiles must be lossless (PNG, or WebP written as VP8L). Lossy WebP corrupts the elevations.

## Classes

All in `org.osmdroid.tileprovider.terrain`:

| Class | Role |
|---|---|
| `HillshadeOverlay` | Public entry point. One file, `setFile` / `setEnabled`, appearance setters, metadata reader. |
| `HillshadeTileProvider` | Module provider reading one archive; calls the tile source to shade each tile. Place for the seam fix. |
| `HillshadeTileSource` | `BitmapTileSourceBase` carrying encoding, sun, opacity and exaggeration; decodes and shades a tile given its index. |
| `Hillshader` | Pure maths: metres per pixel at a tile's latitude, Horn kernel, ARGB output. |
| `TerrainRgbEncoding` | `base` / `interval` pair, presets, and the description parser. |

## Known gaps

- **Tile seams.** Each tile is shaded alone, so the kernel clamps at the edge and a one
  pixel seam shows on every tile border, most visibly at high opacity. Fix in
  `HillshadeTileProvider.TileLoader.loadTile`: read the four edge neighbours from the
  archive, copy their facing row or column into a grid padded by one pixel, shade, crop.
- **512 px tiles on a 256 px map.** osmdroid's tile size is global and set by the base
  source. A 512 px hillshade bitmap is scaled into the 256 px tile rectangle, which looks
  fine but discards resolution. Mapping a z+1 request onto a quadrant of the z tile would
  reclaim it.
- **Tuning is live.** `setMaxAlpha`, `setSun` and `setExaggeration` re-shade the cached tiles at once, so they can sit behind sliders. Debounce slider drags to avoid clearing the cache dozens of times a second.
- **No point elevation API yet.** `HillshadeTileSource.elevationAt` decodes a pixel; a
  lat/lng lookup on the overlay is a small addition.

## Sample

`SampleHillshade` in the sample app ("Terrain-RGB hillshade overlay") loads the first
`*terrain*.mbtiles` found in the app's external files folder
(`Android/data/org.osmdroid/files` or its `osmdroid` / `Download` subfolders, where a file copied over
USB lands), the configured osmdroid base path, or the shared Downloads folder when a storage
permission makes it readable. It zooms to the file's bounds and offers an on/off button and
a file picker.
