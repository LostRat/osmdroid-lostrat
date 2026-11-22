package org.osmdroid.mapsforge.mvt;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Tag;
import org.mapsforge.core.model.Tile;
import org.mapsforge.core.util.MercatorProjection;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.datastore.MapReadResult;
import org.mapsforge.map.datastore.PointOfInterest;
import org.mapsforge.map.datastore.Way;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Vector tiles of the OpenMapTiles schema (tilemaker output) as a mapsforge
 * {@link MapDataStore}, so the mapsforge renderer and a mapsforge render theme draw them
 * like a .map file. Feature properties become OSM tags through {@link OpenMapTilesTags}.
 * <p>
 * One store reads one {@link VectorTileArchive}. Combine several (a base map, a contour
 * archive, a .map file) with mapsforge's {@code MultiMapDataStore}. Above the archive's
 * highest zoom its deepest tiles are used again, each requested tile getting the ways that
 * reach it; below its lowest zoom the store returns nothing.
 * <p>
 * Road names come as {@link LabelOnlyWay}s; draw with {@link MvtDirectRenderer} and
 * {@link MvtStandardRenderer} so they are not stroked as roads too.
 * <p>
 * Thread-safe. Decoded source tiles are kept in a cache bounded by coordinate count.
 *
 * @since 7.0.3-lostrat
 */
public class MvtMapDataStore extends MapDataStore {

    private static final Logger LOGGER = Logger.getLogger(MvtMapDataStore.class.getName());

    /**
     * Default cache budget in coordinates (way nodes plus POIs) of decoded source tiles:
     * the densest 3x3 block of z14 tiles in central Portland holds about 425,000. Decoded
     * tiles take about 55 bytes per coordinate (the {@code LatLong}s, their arrays, the
     * ways), so a full cache is roughly 27 MB, plus half as much again at most for the
     * trail backfill's cache.
     */
    public static final long DEFAULT_CACHE_COORDINATES = 500_000L;

    /** Layers the trail backfill needs; the rest of a deepest tile is not decoded for it. */
    private static final Set<String> TRAIL_LAYERS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("transportation", "transportation_name")));

    /** Kinds of way a walker's map needs from further out than the tiles hold them. */
    private static final Set<String> TRAIL_VALUES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "path", "footway", "steps", "cycleway", "bridleway", "track")));

    private static final int KIND_TILE = 0;
    private static final int KIND_TRAILS = 1;

    private final VectorTileArchive archive;
    private final int minZoom;
    private final int maxZoom;
    private final BoundingBox boundingBox;
    /** False for archives that list their layers and have no {@code transportation}, e.g. contours. */
    private final boolean mayHaveTrails;
    private volatile boolean trailBackfill = true;
    private volatile boolean closed;

    private final Object lock = new Object();
    private final WeightedCache tiles;
    private final WeightedCache trails;
    /** Source tiles some thread is decoding now: others wanting one wait for it. */
    private final Set<Long> loading = new HashSet<>();
    private final TagInterner interner = new TagInterner();

    /** A store with the {@linkplain #DEFAULT_CACHE_COORDINATES default cache budget}. */
    public MvtMapDataStore(VectorTileArchive archive) {
        this(archive, DEFAULT_CACHE_COORDINATES);
    }

    /**
     * @param archive          the tiles; closed by {@link #close()}
     * @param cacheCoordinates budget of the decoded-tile cache in coordinates; the newest
     *                         tile is always kept, however large. Half as much again is
     *                         allowed for the trail backfill's cache, which at zoom 12
     *                         holds the trails of 16 deepest tiles per tile.
     */
    public MvtMapDataStore(VectorTileArchive archive, long cacheCoordinates) {
        if (archive == null) {
            throw new IllegalArgumentException("archive is null");
        }
        this.archive = archive;
        this.minZoom = archive.getMinZoom();
        this.maxZoom = Math.max(minZoom, archive.getMaxZoom());
        BoundingBox box = archive.getBoundingBox();
        this.boundingBox = box != null ? box : MbtilesMetadata.world();
        List<String> ids = archive.getVectorLayerIds();
        this.mayHaveTrails = ids == null || ids.isEmpty() || ids.contains("transportation");
        long budget = Math.max(1, cacheCoordinates);
        this.tiles = new WeightedCache(budget);
        this.trails = new WeightedCache(Math.max(1, budget / 2));
    }

    /** The archive this store reads. */
    public VectorTileArchive getArchive() {
        return archive;
    }

    /**
     * Whether the two zooms above the archive's deepest get the trails (paths, footways,
     * tracks, ...) and their names from the deepest tiles, when their own tiles lack them.
     * On by default; has no effect for an archive without a {@code transportation} layer.
     */
    public void setTrailBackfill(boolean trailBackfill) {
        this.trailBackfill = trailBackfill;
    }

    public boolean isTrailBackfill() {
        return trailBackfill;
    }

    @Override
    public BoundingBox boundingBox() {
        return boundingBox;
    }

    @Override
    public void close() {
        closed = true;
        synchronized (lock) {
            tiles.clear();
            trails.clear();
        }
        archive.close();
    }

    @Override
    public long getDataTimestamp(Tile tile) {
        return archive.getTimestamp();
    }

    @Override
    public LatLong startPosition() {
        return boundingBox.getCenterPoint();
    }

    @Override
    public Byte startZoomLevel() {
        return (byte) Math.max(minZoom, Math.min(maxZoom, 12));
    }

    @Override
    public boolean supportsTile(Tile tile) {
        return tile.zoomLevel >= minZoom && tile.getBoundingBox().intersects(boundingBox);
    }

    @Override
    public boolean supportsFullTile(Tile tile) {
        return tile.zoomLevel >= minZoom && covers(boundingBox, tile.getBoundingBox());
    }

    @Override
    public boolean supportsArea(BoundingBox area, byte zoomLevel) {
        return zoomLevel >= minZoom && area.intersects(boundingBox);
    }

    @Override
    public boolean supportsFullArea(BoundingBox area, byte zoomLevel) {
        return zoomLevel >= minZoom && covers(boundingBox, area);
    }

    @Override
    public MapReadResult readMapData(Tile tile) {
        return read(tile, tile, true);
    }

    /**
     * Each way at most once, though at overzoom one source tile serves every requested
     * tile: the ways meeting the area plus a margin of an eighth of a tile. Each POI from
     * the one source tile containing it, inside the area's half-open bounds.
     */
    @Override
    public MapReadResult readMapData(Tile upperLeft, Tile lowerRight) {
        return read(upperLeft, lowerRight, true);
    }

    @Override
    public MapReadResult readNamedItems(Tile upperLeft, Tile lowerRight) {
        return read(upperLeft, lowerRight, true);
    }

    @Override
    public MapReadResult readPoiData(Tile tile) {
        return read(tile, tile, false);
    }

    @Override
    public MapReadResult readPoiData(Tile upperLeft, Tile lowerRight) {
        return read(upperLeft, lowerRight, false);
    }

    /** Never throws: a damaged archive or tile gives what was read, or an empty result. */
    private MapReadResult read(Tile upperLeft, Tile lowerRight, boolean withWays) {
        MapReadResult result = new MapReadResult();
        try {
            if (closed || upperLeft.zoomLevel != lowerRight.zoomLevel || upperLeft.zoomLevel < minZoom
                    || upperLeft.tileX > lowerRight.tileX || upperLeft.tileY > lowerRight.tileY) {
                return result;
            }
            if (upperLeft.zoomLevel <= maxZoom) {
                for (int y = upperLeft.tileY; y <= lowerRight.tileY; y++) {
                    for (int x = upperLeft.tileX; x <= lowerRight.tileX; x++) {
                        readOwnTile(new Tile(x, y, upperLeft.zoomLevel, upperLeft.tileSize), withWays, result);
                    }
                }
            } else {
                readOverzoom(upperLeft, lowerRight, withWays, result);
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "reading " + upperLeft + " failed", e);
        }
        return result;
    }

    /** A requested tile at a zoom the archive holds: all its ways, its own POIs, maybe backfilled trails. */
    private void readOwnTile(Tile tile, boolean withWays, MapReadResult result) {
        int zoom = tile.zoomLevel;
        SourceTile source = obtain(KIND_TILE, zoom, tile.tileX, tile.tileY);
        BoundingBox box = tile.getBoundingBox();
        addPois(source, zoom, box, null, result);
        if (!withWays) {
            return;
        }
        Collections.addAll(result.ways, source.ways);
        if (trailBackfill && mayHaveTrails && zoom < maxZoom && zoom >= Math.max(minZoom, maxZoom - 2)) {
            appendTrails(tile, source, result);
        }
    }

    private void readOverzoom(Tile upperLeft, Tile lowerRight, boolean withWays, MapReadResult result) {
        int zoom = upperLeft.zoomLevel;
        int shift = zoom - maxZoom;
        BoundingBox first = upperLeft.getBoundingBox();
        BoundingBox last = lowerRight.getBoundingBox();
        BoundingBox area = first.extendBoundingBox(last);
        // Only what reaches the requested area, with room for wide lines.
        double marginLat = Math.max(first.maxLatitude - first.minLatitude, last.maxLatitude - last.minLatitude) / 8;
        double marginLon = (first.maxLongitude - first.minLongitude) / 8;
        double minLat = area.minLatitude - marginLat;
        double maxLat = area.maxLatitude + marginLat;
        double minLon = area.minLongitude - marginLon;
        double maxLon = area.maxLongitude + marginLon;
        Set<Way> seen = Collections.newSetFromMap(new IdentityHashMap<Way, Boolean>());
        for (int sy = upperLeft.tileY >> shift; sy <= lowerRight.tileY >> shift; sy++) {
            for (int sx = upperLeft.tileX >> shift; sx <= lowerRight.tileX >> shift; sx++) {
                SourceTile source = obtain(KIND_TILE, maxZoom, sx, sy);
                BoundingBox sourceBox = new Tile(sx, sy, (byte) maxZoom, upperLeft.tileSize).getBoundingBox();
                addPois(source, zoom, area, sourceBox, result);
                if (!withWays) {
                    continue;
                }
                double[] boxes = source.boxes;
                for (int i = 0; i < source.ways.length; i++) {
                    int b = 4 * i;
                    if (boxes[b] <= maxLat && boxes[b + 2] >= minLat && boxes[b + 1] <= maxLon && boxes[b + 3] >= minLon
                            && seen.add(source.ways[i])) {
                        result.ways.add(source.ways[i]);
                    }
                }
            }
        }
    }

    /**
     * A point belongs to the one tile it is in (tiles overlap a little, so the same point
     * can be in two of them): latitude in (min, max], longitude in [min, max).
     */
    private static void addPois(SourceTile source, int zoom, BoundingBox box, BoundingBox sourceBox,
                                MapReadResult result) {
        for (int i = 0; i < source.pois.length; i++) {
            PointOfInterest poi = source.pois[i];
            if (zoom >= source.poiMinZoom[i] && inside(poi.position, box)
                    && (sourceBox == null || inside(poi.position, sourceBox))) {
                result.pois.add(poi);
            }
        }
    }

    private static boolean inside(LatLong p, BoundingBox box) {
        return p.latitude <= box.maxLatitude && p.latitude > box.minLatitude
                && p.longitude >= box.minLongitude && p.longitude < box.maxLongitude;
    }

    private static boolean covers(BoundingBox outer, BoundingBox inner) {
        return outer.minLatitude <= inner.minLatitude && outer.maxLatitude >= inner.maxLatitude
                && outer.minLongitude <= inner.minLongitude && outer.maxLongitude >= inner.maxLongitude;
    }

    // Trail backfill

    /** The trail value of {@code way} ({@code path}, {@code track}, ...) or null if it is no trail. */
    static String trailValue(Way way) {
        List<Tag> tags = way.tags;
        if (tags.isEmpty()) {
            return null;
        }
        Tag first = tags.get(0);
        if (!first.key.equals("highway") || !TRAIL_VALUES.contains(first.value)) {
            return null;
        }
        for (int i = 1; i < tags.size(); i++) {
            if (tags.get(i).key.equals("area")) {
                return null;
            }
        }
        return first.value;
    }

    private static String trailKind(Way way, String value) {
        return way instanceof LabelOnlyWay ? value + "/name" : value;
    }

    /**
     * Tilemaker's OpenMapTiles config puts paths and footways only into the deepest zoom
     * (14) and tracks from 13, but a walker's theme, like a .map file, shows trails from 12.
     * So for the two zooms above the deepest, the kinds of trail (and trail name) the tile
     * at that zoom lacks entirely are taken from the deepest tiles under it.
     */
    private void appendTrails(Tile tile, SourceTile own, MapReadResult result) {
        Set<String> present = new HashSet<>();
        for (Way way : own.ways) {
            String value = trailValue(way);
            if (value != null) {
                present.add(trailKind(way, value));
            }
        }
        int shift = maxZoom - tile.zoomLevel;
        int count = 1 << shift;
        for (int dy = 0; dy < count; dy++) {
            for (int dx = 0; dx < count; dx++) {
                SourceTile deep = obtain(KIND_TRAILS, maxZoom, (tile.tileX << shift) + dx, (tile.tileY << shift) + dy);
                for (Way way : deep.ways) {
                    String value = trailValue(way);
                    if (value != null && !present.contains(trailKind(way, value))) {
                        result.ways.add(way);
                    }
                }
            }
        }
    }

    // Loading and caching

    private static long key(int kind, int zoom, int x, int y) {
        return ((long) kind << 62) | ((long) zoom << 56) | ((long) x << 28) | (long) y;
    }

    /**
     * A source tile from the cache, decoding it if need be. Tiles are rendered on several
     * threads and sixteen z16 tiles share one z14 source tile, so a thread wanting a tile
     * another is already decoding waits for that one's result.
     */
    private SourceTile obtain(int kind, int zoom, int x, int y) {
        if (zoom < 0 || zoom > 28 || x < 0 || y < 0 || x >= (1 << zoom) || y >= (1 << zoom)) {
            return SourceTile.EMPTY;
        }
        long key = key(kind, zoom, x, y);
        WeightedCache cache = kind == KIND_TILE ? tiles : trails;
        SourceTile whole = null;
        boolean registered = false;
        synchronized (lock) {
            while (true) {
                SourceTile cached = cache.get(key);
                if (cached != null) {
                    return cached;
                }
                if (kind == KIND_TRAILS) {
                    whole = tiles.peek(key(KIND_TILE, zoom, x, y));
                    if (whole != null) {
                        break;
                    }
                }
                if (loading.add(key)) {
                    registered = true;
                    break;
                }
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    // decode it here rather than wait on
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        SourceTile loaded = null;
        try {
            if (kind == KIND_TRAILS) {
                loaded = (whole != null ? whole : load(zoom, x, y, TRAIL_LAYERS)).trailsOnly();
            } else {
                loaded = load(zoom, x, y, null);
            }
        } catch (IOException e) {
            // not cached: the archive may read it next time
            LOGGER.log(Level.WARNING, "cannot read tile " + zoom + "/" + x + "/" + y, e);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "cannot decode tile " + zoom + "/" + x + "/" + y, e);
            loaded = SourceTile.EMPTY;
        } finally {
            synchronized (lock) {
                if (registered) {
                    loading.remove(key);
                }
                if (loaded != null && !closed) {
                    cache.put(key, loaded, loaded.weight);
                }
                lock.notifyAll();
            }
        }
        return loaded != null ? loaded : SourceTile.EMPTY;
    }

    private SourceTile load(int zoom, int x, int y, Set<String> layers) throws IOException {
        if (closed) {
            return SourceTile.EMPTY;
        }
        byte[] data = archive.readTile(zoom, x, y);
        if (data == null || data.length == 0) {
            return SourceTile.EMPTY;
        }
        VectorTile tile = VectorTile.decode(data, layers);
        if (!tile.isComplete()) {
            LOGGER.warning("tile " + zoom + "/" + x + "/" + y + " is damaged; drawing what could be read");
        }
        return convert(tile, zoom, x, y, interner.local());
    }

    // Conversion

    /** One source tile as mapsforge elements, with each way's outer-ring box. */
    static final class SourceTile {
        static final SourceTile EMPTY = new SourceTile(new Way[0], new double[0], new PointOfInterest[0], new int[0]);

        final Way[] ways;
        /** minLatitude, minLongitude, maxLatitude, maxLongitude of each way's outer ring. */
        final double[] boxes;
        final PointOfInterest[] pois;
        final int[] poiMinZoom;
        /** Coordinates held: way nodes plus POIs, at least 1. */
        final long weight;

        SourceTile(Way[] ways, double[] boxes, PointOfInterest[] pois, int[] poiMinZoom) {
            this.ways = ways;
            this.boxes = boxes;
            this.pois = pois;
            this.poiMinZoom = poiMinZoom;
            long w = pois.length + 1;
            for (Way way : ways) {
                for (LatLong[] ring : way.latLongs) {
                    w += ring.length;
                }
            }
            this.weight = w;
        }

        SourceTile trailsOnly() {
            int n = 0;
            for (Way way : ways) {
                if (trailValue(way) != null) {
                    n++;
                }
            }
            Way[] kept = new Way[n];
            double[] keptBoxes = new double[4 * n];
            int j = 0;
            for (int i = 0; i < ways.length; i++) {
                if (trailValue(ways[i]) != null) {
                    kept[j] = ways[i];
                    System.arraycopy(boxes, 4 * i, keptBoxes, 4 * j, 4);
                    j++;
                }
            }
            return new SourceTile(kept, keptBoxes, new PointOfInterest[0], new int[0]);
        }
    }

    /** Collects ways and POIs of one tile in order. */
    private static final class Builder {
        final List<Way> ways = new ArrayList<>();
        double[] boxes = new double[64];
        final List<PointOfInterest> pois = new ArrayList<>();
        int[] poiMinZoom = new int[16];

        void addWay(Way way) {
            int i = ways.size();
            if (4 * i + 4 > boxes.length) {
                boxes = Arrays.copyOf(boxes, boxes.length * 2);
            }
            LatLong[] outer = way.latLongs[0];
            double minLat = outer[0].latitude;
            double maxLat = minLat;
            double minLon = outer[0].longitude;
            double maxLon = minLon;
            for (LatLong p : outer) {
                if (p.latitude < minLat) {
                    minLat = p.latitude;
                } else if (p.latitude > maxLat) {
                    maxLat = p.latitude;
                }
                if (p.longitude < minLon) {
                    minLon = p.longitude;
                } else if (p.longitude > maxLon) {
                    maxLon = p.longitude;
                }
            }
            boxes[4 * i] = minLat;
            boxes[4 * i + 1] = minLon;
            boxes[4 * i + 2] = maxLat;
            boxes[4 * i + 3] = maxLon;
            ways.add(way);
        }

        void addPoi(PointOfInterest poi, int minZoom) {
            int i = pois.size();
            if (i + 1 > poiMinZoom.length) {
                poiMinZoom = Arrays.copyOf(poiMinZoom, poiMinZoom.length * 2);
            }
            poiMinZoom[i] = minZoom;
            pois.add(poi);
        }

        SourceTile build() {
            if (ways.isEmpty() && pois.isEmpty()) {
                return SourceTile.EMPTY;
            }
            return new SourceTile(ways.toArray(new Way[0]), Arrays.copyOf(boxes, 4 * ways.size()),
                    pois.toArray(new PointOfInterest[0]), Arrays.copyOf(poiMinZoom, pois.size()));
        }
    }

    /** Tile-grid coordinates of one tile to latitude and longitude, with latitudes memoised per grid row. */
    private static final class Projector {
        private final int zoom;
        private final int x;
        private final int y;
        private int extent = -1;
        private int memoOffset;
        private double[] latitudes;

        Projector(int zoom, int x, int y) {
            this.zoom = zoom;
            this.x = x;
            this.y = y;
        }

        void setExtent(int extent) {
            if (extent == this.extent) {
                return;
            }
            this.extent = extent;
            // grid rows from half a tile above to half a tile below cover any sane buffer
            memoOffset = extent / 2;
            int size = (int) Math.min(2L * extent + 1, 1 << 16);
            latitudes = new double[size];
            Arrays.fill(latitudes, Double.NaN);
        }

        LatLong latLong(int cx, int cy) {
            double tiles = (double) (1L << zoom);
            double longitude = (x + (double) cx / extent) / tiles * 360 - 180;
            if (longitude < -180) {
                longitude = -180;
            } else if (longitude > 180) {
                longitude = 180;
            }
            double latitude;
            int slot = cy + memoOffset;
            if (slot >= 0 && slot < latitudes.length) {
                latitude = latitudes[slot];
                if (Double.isNaN(latitude)) {
                    latitude = latitude(cy, tiles);
                    latitudes[slot] = latitude;
                }
            } else {
                latitude = latitude(cy, tiles);
            }
            return new LatLong(latitude, longitude);
        }

        private double latitude(int cy, double tiles) {
            double n = Math.PI * (1 - 2 * (y + (double) cy / extent) / tiles);
            double latitude = Math.toDegrees(Math.atan(Math.sinh(n)));
            double limit = MercatorProjection.LATITUDE_MAX;
            return latitude < -limit ? -limit : (latitude > limit ? limit : latitude);
        }

        /** One ring or line; the closing point of a closed ring shares the first point's object. */
        LatLong[] ring(int[] part, boolean reversed) {
            int n = part.length / 2;
            LatLong[] out = new LatLong[n];
            for (int i = 0; i < n; i++) {
                int src = reversed ? n - 1 - i : i;
                int cx = part[2 * src];
                int cy = part[2 * src + 1];
                if (i == n - 1 && n > 1 && cx == part[reversed ? 2 * (n - 1) : 0]
                        && cy == part[reversed ? 2 * (n - 1) + 1 : 1]) {
                    out[i] = out[0];
                } else {
                    out[i] = latLong(cx, cy);
                }
            }
            return out;
        }
    }

    /** Converts one decoded tile at {@code zoom/x/y} (XYZ) into mapsforge ways and POIs. */
    static SourceTile convert(VectorTile tile, int zoom, int x, int y, TagInterner.Local tags) {
        Builder result = new Builder();
        Projector projector = new Projector(zoom, x, y);
        for (VectorTile.Layer layer : tile.getLayers()) {
            int extent = Math.max(1, layer.extent);
            projector.setExtent(extent);
            boolean waterName = layer.name.equals("water_name");
            for (VectorTile.Feature feature : layer.features) {
                try {
                    convertFeature(layer.name, waterName, extent, feature, projector, tags, result);
                } catch (IllegalArgumentException e) {
                    // a feature mapsforge refuses (e.g. coordinates out of range) is left out
                }
            }
        }
        return result.build();
    }

    private static void convertFeature(String layer, boolean waterName, int extent, VectorTile.Feature feature,
                                       Projector projector, TagInterner.Local tags, Builder result) {
        switch (feature.type) {
            case VectorTile.POINT: {
                if (waterName) {
                    List<Tag> label = OpenMapTilesTags.waterLabel(feature);
                    if (label == null) {
                        return;
                    }
                    List<Tag> shared = tags.intern(label);
                    for (int[] part : feature.parts) {
                        for (int i = 0; i + 1 < part.length; i += 2) {
                            int cx = part[i];
                            int cy = part[i + 1];
                            if (cx >= 0 && cx < extent && cy >= 0 && cy < extent) {
                                addPointLabel(result, (byte) 5, shared, projector.latLong(cx, cy));
                            }
                        }
                    }
                    return;
                }
                OpenMapTilesTags.Element element = OpenMapTilesTags.node(layer, feature);
                if (element == null) {
                    return;
                }
                List<Tag> shared = tags.intern(element.tags);
                for (int[] part : feature.parts) {
                    for (int i = 0; i + 1 < part.length; i += 2) {
                        int cx = part[i];
                        int cy = part[i + 1];
                        if (element.textOnly) {
                            if (cx >= 0 && cx < extent && cy >= 0 && cy < extent) {
                                addPointLabel(result, (byte) element.layer, shared, projector.latLong(cx, cy));
                            }
                        } else {
                            result.addPoi(new PointOfInterest((byte) element.layer, shared, projector.latLong(cx, cy)),
                                    element.minZoom);
                        }
                    }
                }
                return;
            }
            case VectorTile.LINESTRING: {
                OpenMapTilesTags.Element element = OpenMapTilesTags.way(layer, feature);
                if (element == null) {
                    return;
                }
                List<Tag> shared = tags.intern(element.tags);
                for (int[] line : feature.parts) {
                    if (line.length >= 4) {
                        LatLong[][] latLongs = {projector.ring(line, element.reversed)};
                        result.addWay(way(element, shared, latLongs, null));
                    }
                }
                return;
            }
            case VectorTile.POLYGON: {
                OpenMapTilesTags.Element element = OpenMapTilesTags.way(layer, feature);
                if (element == null) {
                    return;
                }
                List<Tag> shared = tags.intern(element.tags);
                // An outer ring winds one way and the holes after it the other.
                List<int[]> polygon = new ArrayList<>(1);
                for (int[] ring : feature.parts) {
                    if (ring.length < 8) {
                        continue;
                    }
                    if (signedArea(ring) > 0 && !polygon.isEmpty()) {
                        addPolygon(result, element, shared, polygon, projector);
                        polygon.clear();
                    }
                    polygon.add(ring);
                }
                if (!polygon.isEmpty()) {
                    addPolygon(result, element, shared, polygon, projector);
                }
                return;
            }
            default:
        }
    }

    private static void addPolygon(Builder result, OpenMapTilesTags.Element element, List<Tag> tags,
                                   List<int[]> rings, Projector projector) {
        LatLong[][] latLongs = new LatLong[rings.size()][];
        for (int i = 0; i < latLongs.length; i++) {
            latLongs[i] = projector.ring(rings.get(i), false);
        }
        result.addWay(way(element, tags, latLongs, null));
    }

    /**
     * A one-point label-only way: mapsforge treats a one-point way as closed, so the theme's
     * area rules match it, and its label position makes the caption sit on the point.
     */
    private static void addPointLabel(Builder result, byte layer, List<Tag> tags, LatLong position) {
        result.addWay(new LabelOnlyWay(layer, tags, new LatLong[][]{{position}}, position));
    }

    private static Way way(OpenMapTilesTags.Element element, List<Tag> tags, LatLong[][] latLongs,
                           LatLong labelPosition) {
        byte layer = (byte) element.layer;
        return element.textOnly ? new LabelOnlyWay(layer, tags, latLongs, labelPosition)
                : new Way(layer, tags, latLongs, labelPosition);
    }

    /** Twice the ring's area in grid units; positive for an outer ring (clockwise on screen, y down). */
    static double signedArea(int[] ring) {
        double sum = 0;
        int n = ring.length / 2;
        for (int i = 0; i < n - 1; i++) {
            sum += (double) ring[2 * i] * ring[2 * i + 3] - (double) ring[2 * i + 2] * ring[2 * i + 1];
        }
        return sum;
    }

    /** Groups polygon rings as the converter does; exposed for tests. Each group starts with its outer ring. */
    static List<List<int[]>> groupRings(int[][] parts) {
        List<List<int[]>> groups = new ArrayList<>();
        List<int[]> polygon = new ArrayList<>();
        for (int[] ring : parts) {
            if (ring.length < 8) {
                continue;
            }
            if (signedArea(ring) > 0 && !polygon.isEmpty()) {
                groups.add(polygon);
                polygon = new ArrayList<>();
            }
            polygon.add(ring);
        }
        if (!polygon.isEmpty()) {
            groups.add(polygon);
        }
        return groups;
    }

    /** Cached source tiles and the total weight held. */
    int cachedTileCount() {
        synchronized (lock) {
            return tiles.size();
        }
    }

    long cachedWeight() {
        synchronized (lock) {
            return tiles.weight();
        }
    }

    /**
     * Least-recently-used map from tile key to source tile, bounded by total weight; the
     * newest entry is always kept. Callers hold the store's lock.
     */
    private static final class WeightedCache {
        private final long budget;
        private final LinkedHashMap<Long, SourceTile> map = new LinkedHashMap<>(32, 0.75f, true);
        private long total;

        WeightedCache(long budget) {
            this.budget = budget;
        }

        SourceTile get(long key) {
            return map.get(key);
        }

        /** Without counting as a use. */
        SourceTile peek(long key) {
            for (Map.Entry<Long, SourceTile> entry : map.entrySet()) {
                if (entry.getKey() == key) {
                    return entry.getValue();
                }
            }
            return null;
        }

        void put(long key, SourceTile value, long weight) {
            SourceTile old = map.put(key, value);
            if (old != null) {
                total -= old.weight;
            }
            total += weight;
            Iterator<Map.Entry<Long, SourceTile>> eldest = map.entrySet().iterator();
            while (total > budget && map.size() > 1 && eldest.hasNext()) {
                Map.Entry<Long, SourceTile> entry = eldest.next();
                if (entry.getKey() == key) {
                    continue;
                }
                total -= entry.getValue().weight;
                eldest.remove();
            }
        }

        void clear() {
            map.clear();
            total = 0;
        }

        int size() {
            return map.size();
        }

        long weight() {
            return total;
        }
    }
}
