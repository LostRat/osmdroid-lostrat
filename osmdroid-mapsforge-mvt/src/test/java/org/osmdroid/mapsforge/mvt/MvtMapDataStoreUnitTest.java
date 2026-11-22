package org.osmdroid.mapsforge.mvt;

import org.junit.Assert;
import org.junit.Test;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Tag;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.datastore.MapReadResult;
import org.mapsforge.map.datastore.PointOfInterest;
import org.mapsforge.map.datastore.Way;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Store and conversion behaviour on hand-built tiles. */
public class MvtMapDataStoreUnitTest {

    private static final int Z = 14;
    private static final int X = 2605;
    private static final int Y = 5855;
    private static final int TILE_SIZE = 256;

    @Test
    public void polygonRingsGroupBySignedArea() {
        int[] outer = {0, 0, 10, 0, 10, 10, 0, 10, 0, 0};
        int[] hole = {2, 2, 2, 8, 8, 8, 8, 2, 2, 2};
        int[] outer2 = {20, 20, 30, 20, 30, 30, 20, 30, 20, 20};
        int[] tooShort = {0, 0, 5, 5, 0, 0};
        Assert.assertTrue(MvtMapDataStore.signedArea(outer) > 0);
        Assert.assertTrue(MvtMapDataStore.signedArea(hole) < 0);
        List<List<int[]>> groups = MvtMapDataStore.groupRings(new int[][]{outer, hole, tooShort, outer2});
        Assert.assertEquals(2, groups.size());
        Assert.assertEquals(2, groups.get(0).size());
        Assert.assertSame(outer, groups.get(0).get(0));
        Assert.assertSame(hole, groups.get(0).get(1));
        Assert.assertSame(outer2, groups.get(1).get(0));
        // a polygon starting with a hole-wound ring still keeps it as its first ring
        List<List<int[]>> odd = MvtMapDataStore.groupRings(new int[][]{hole, outer});
        Assert.assertEquals(2, odd.size());
    }

    @Test
    public void labelOnlyWayDiffersFromWay() {
        List<Tag> tags = Arrays.asList(new Tag("highway", "path"), new Tag("name", "Wildwood"));
        LatLong[][] points = {{new LatLong(45.5, -122.7), new LatLong(45.6, -122.8)}};
        Way way = new Way((byte) 5, tags, points, null);
        LabelOnlyWay label = new LabelOnlyWay((byte) 5, tags, points, null);
        LabelOnlyWay label2 = new LabelOnlyWay((byte) 5, tags, points, null);
        Assert.assertNotEquals(label, way);
        Assert.assertEquals(label, label2);
        Assert.assertEquals(label.hashCode(), label2.hashCode());
        Assert.assertNotEquals(way.hashCode(), label.hashCode());
        Assert.assertEquals(1, label.compareTo(way));
        Assert.assertEquals(0, label.compareTo(label2));

        MapReadResult result = new MapReadResult();
        result.ways.add(label);
        result.ways.add(label2);
        result.deduplicate();
        Assert.assertEquals(1, result.ways.size());
    }

    private static byte[] sampleTile() {
        TileBuilder tile = new TileBuilder();
        // a one-way street drawn against its direction, in the NW corner
        tile.layer("transportation").feature(VectorTile.LINESTRING,
                new Pbf.Geometry().path(false, 100, 100, 900, 100),
                "class", "minor", "subclass", "residential", "oneway", -1, "layer", 1);
        tile.layer("transportation_name").feature(VectorTile.LINESTRING,
                new Pbf.Geometry().path(false, 100, 120, 900, 120),
                "class", "minor", "subclass", "residential", "name", "NW Thurman St");
        // a footway in the SE corner
        tile.layer("transportation").feature(VectorTile.LINESTRING,
                new Pbf.Geometry().path(false, 3500, 3500, 4000, 4000),
                "class", "path", "subclass", "footway");
        tile.layer("building").feature(VectorTile.POLYGON,
                new Pbf.Geometry().path(true, 200, 200, 300, 200, 300, 300, 200, 300)
                        .path(true, 220, 220, 220, 280, 280, 280, 280, 220)
                        .path(true, 400, 400, 500, 400, 500, 500, 400, 500));
        tile.layer("poi").feature(VectorTile.POINT, new Pbf.Geometry().points(2000, 2000),
                "class", "park", "subclass", "park", "name", "Forest Park");
        tile.layer("poi").feature(VectorTile.POINT, new Pbf.Geometry().points(1000, 1000),
                "class", "cafe", "subclass", "cafe", "name", "Cafe");
        tile.layer("poi").feature(VectorTile.POINT, new Pbf.Geometry().points(1100, 1000),
                "class", "shelter", "subclass", "shelter");
        // on the tile's western and northern edges, and a buffered copy beyond the east edge
        tile.layer("poi").feature(VectorTile.POINT, new Pbf.Geometry().points(0, 1500),
                "class", "shelter", "subclass", "shelter", "name", "West edge");
        tile.layer("poi").feature(VectorTile.POINT, new Pbf.Geometry().points(1500, 0),
                "class", "shelter", "subclass", "shelter", "name", "North edge");
        tile.layer("poi").feature(VectorTile.POINT, new Pbf.Geometry().points(4100, 1500),
                "class", "shelter", "subclass", "shelter", "name", "Beyond east");
        tile.layer("water_name").feature(VectorTile.POINT, new Pbf.Geometry().points(3000, 1000),
                "class", "lake", "name", "Lake");
        return tile.build();
    }

    private static MvtMapDataStore store(MemoryArchive archive) {
        return new MvtMapDataStore(archive);
    }

    private static Way find(List<Way> ways, String key, String value, boolean labelOnly) {
        for (Way way : ways) {
            if ((way instanceof LabelOnlyWay) == labelOnly && way.tags.contains(new Tag(key, value))) {
                return way;
            }
        }
        return null;
    }

    private static List<String> poiNames(MapReadResult result) {
        List<String> names = new ArrayList<>();
        for (PointOfInterest poi : result.pois) {
            String name = poi.tags.get(0).value;
            for (Tag tag : poi.tags) {
                if (tag.key.equals("name")) {
                    name = tag.value;
                }
            }
            names.add(name);
        }
        Collections.sort(names);
        return names;
    }

    @Test
    public void convertsTheSampleTile() {
        MvtMapDataStore store = store(new MemoryArchive(0, Z).put(Z, X, Y, sampleTile()));
        Tile tile = new Tile(X, Y, (byte) Z, TILE_SIZE);
        MapReadResult result = store.readMapData(tile);
        Assert.assertFalse(result.isWater);

        Way street = find(result.ways, "highway", "residential", false);
        Assert.assertNotNull(street);
        Assert.assertEquals(6, street.layer);
        Assert.assertTrue(street.tags.contains(new Tag("oneway", "yes")));
        // reversed: runs east to west
        Assert.assertTrue(street.latLongs[0][0].longitude > street.latLongs[0][1].longitude);

        Way name = find(result.ways, "name", "NW Thurman St", true);
        Assert.assertNotNull(name);
        Assert.assertEquals(Arrays.asList(new Tag("highway", "residential"), new Tag("name", "NW Thurman St")),
                name.tags);
        Assert.assertNull(name.labelPosition);

        // building with a hole, and a second building
        int buildings = 0;
        for (Way way : result.ways) {
            if (way.tags.contains(new Tag("building", "yes"))) {
                buildings++;
                if (way.latLongs.length == 2) {
                    Assert.assertEquals(5, way.latLongs[0].length);
                    Assert.assertSame("closing point shares the first", way.latLongs[0][0], way.latLongs[0][4]);
                }
            }
        }
        Assert.assertEquals(2, buildings);

        // the park name: a one-point label-only way with its label position
        Way park = find(result.ways, "leisure", "park", true);
        Assert.assertNotNull(park);
        Assert.assertEquals(1, park.latLongs.length);
        Assert.assertEquals(1, park.latLongs[0].length);
        Assert.assertEquals(park.latLongs[0][0], park.labelPosition);
        Way lake = find(result.ways, "natural", "water", true);
        Assert.assertNotNull(lake);
        Assert.assertTrue(lake.tags.contains(new Tag("name", "Lake")));

        // POIs: the cafe waits for zoom 16, the shelter shows, the buffered copy beyond the
        // east edge belongs to the neighbour
        List<String> names = poiNames(result);
        Assert.assertTrue(names.contains("shelter"));
        Assert.assertFalse(names.contains("Cafe"));
        Assert.assertFalse(names.contains("Beyond east"));
        Assert.assertFalse("the park is a label-only way, not a POI", names.contains("Forest Park"));
    }

    @Test
    public void halfOpenBoundsGiveEdgePointsToOneTile() {
        // the same edge points again in the buffers of the western and northern neighbours
        TileBuilder west = new TileBuilder();
        west.layer("poi").feature(VectorTile.POINT, new Pbf.Geometry().points(4096, 1500),
                "class", "shelter", "subclass", "shelter", "name", "West edge");
        TileBuilder north = new TileBuilder();
        north.layer("poi").feature(VectorTile.POINT, new Pbf.Geometry().points(1500, 4096),
                "class", "shelter", "subclass", "shelter", "name", "North edge");
        MvtMapDataStore store = store(new MemoryArchive(0, Z)
                .put(Z, X, Y, sampleTile())
                .put(Z, X - 1, Y, west.build())
                .put(Z, X, Y - 1, north.build()));
        List<String> own = poiNames(store.readPoiData(new Tile(X, Y, (byte) Z, TILE_SIZE)));
        List<String> westNames = poiNames(store.readPoiData(new Tile(X - 1, Y, (byte) Z, TILE_SIZE)));
        List<String> northNames = poiNames(store.readPoiData(new Tile(X, Y - 1, (byte) Z, TILE_SIZE)));
        Assert.assertEquals(1, Collections.frequency(own, "West edge") + Collections.frequency(westNames, "West edge"));
        Assert.assertEquals(1, Collections.frequency(own, "North edge") + Collections.frequency(northNames, "North edge"));
        // and the area read over both tiles has each once
        MapReadResult area = store.readPoiData(new Tile(X - 1, Y - 1, (byte) Z, TILE_SIZE), new Tile(X, Y, (byte) Z, TILE_SIZE));
        Assert.assertEquals(1, Collections.frequency(poiNames(area), "West edge"));
        Assert.assertEquals(1, Collections.frequency(poiNames(area), "North edge"));
    }

    @Test
    public void halfOpenBoundsGivePointLabelsToOneTile() {
        TileBuilder own = new TileBuilder();
        own.layer("poi").feature(VectorTile.POINT, new Pbf.Geometry().points(0, 1500),
                "class", "park", "subclass", "park", "name", "Edge Park");
        own.layer("water_name").feature(VectorTile.POINT, new Pbf.Geometry().points(1500, 0),
                "class", "lake", "name", "Edge Lake");
        own.layer("water_name").feature(VectorTile.POINT, new Pbf.Geometry().points(4100, 1500),
                "class", "lake", "name", "Buffered Lake");
        TileBuilder west = new TileBuilder();
        west.layer("poi").feature(VectorTile.POINT, new Pbf.Geometry().points(4096, 1500),
                "class", "park", "subclass", "park", "name", "Edge Park");
        TileBuilder north = new TileBuilder();
        north.layer("water_name").feature(VectorTile.POINT, new Pbf.Geometry().points(1500, 4096),
                "class", "lake", "name", "Edge Lake");
        MvtMapDataStore store = store(new MemoryArchive(0, Z)
                .put(Z, X, Y, own.build())
                .put(Z, X - 1, Y, west.build())
                .put(Z, X, Y - 1, north.build()));
        MapReadResult area = store.readMapData(new Tile(X - 1, Y - 1, (byte) Z, TILE_SIZE),
                new Tile(X, Y, (byte) Z, TILE_SIZE));
        int parks = 0;
        int lakes = 0;
        for (Way way : area.ways) {
            if (way instanceof LabelOnlyWay) {
                if (way.tags.contains(new Tag("name", "Edge Park"))) {
                    parks++;
                } else if (way.tags.contains(new Tag("name", "Edge Lake"))) {
                    lakes++;
                } else if (way.tags.contains(new Tag("name", "Buffered Lake"))) {
                    Assert.fail("buffered water_name emitted outside [0, extent)");
                }
            }
        }
        Assert.assertEquals(1, parks);
        Assert.assertEquals(1, lakes);
    }

    @Test
    public void poiMinZoomApplies() {
        MvtMapDataStore store = store(new MemoryArchive(0, Z).put(Z, X, Y, sampleTile()));
        // cafe at grid 1000,1000 is in z16 tile (4X + 0, 4Y + 0)
        MapReadResult z16 = store.readPoiData(new Tile(4 * X, 4 * Y, (byte) 16, TILE_SIZE));
        Assert.assertTrue(poiNames(z16).contains("Cafe"));
        // the shelter at grid 1100,1000 is the next z16 tile's
        Assert.assertFalse(poiNames(z16).contains("shelter"));
        Assert.assertTrue(poiNames(store.readPoiData(new Tile(4 * X + 1, 4 * Y, (byte) 16, TILE_SIZE))).contains("shelter"));
    }

    @Test
    public void overzoomKeepsOnlyNearbyWays() {
        MemoryArchive archive = new MemoryArchive(0, Z).put(Z, X, Y, sampleTile());
        MvtMapDataStore store = store(archive);
        MapReadResult nw = store.readMapData(new Tile(4 * X, 4 * Y, (byte) 16, TILE_SIZE));
        MapReadResult se = store.readMapData(new Tile(4 * X + 3, 4 * Y + 3, (byte) 16, TILE_SIZE));
        Assert.assertNotNull(find(nw.ways, "highway", "residential", false));
        Assert.assertNull(find(nw.ways, "highway", "footway", false));
        Assert.assertNotNull(find(se.ways, "highway", "footway", false));
        Assert.assertNull(find(se.ways, "highway", "residential", false));
        Assert.assertEquals("one archive read serves both", 1, archive.reads.get());
        // below min zoom nothing
        MvtMapDataStore high = store(new MemoryArchive(11, Z));
        Assert.assertTrue(high.readMapData(new Tile(0, 0, (byte) 10, TILE_SIZE)).ways.isEmpty());
        Assert.assertFalse(high.supportsTile(new Tile(0, 0, (byte) 10, TILE_SIZE)));
        Assert.assertTrue(high.supportsTile(new Tile(X, Y, (byte) Z, TILE_SIZE)));
    }

    @Test
    public void areaReadReturnsEachWayOnce() {
        MvtMapDataStore store = store(new MemoryArchive(0, Z).put(Z, X, Y, sampleTile()));
        MapReadResult area = store.readMapData(new Tile(4 * X, 4 * Y, (byte) 16, TILE_SIZE),
                new Tile(4 * X + 3, 4 * Y + 3, (byte) 16, TILE_SIZE));
        IdentityHashMap<Way, Boolean> seen = new IdentityHashMap<>();
        for (Way way : area.ways) {
            Assert.assertNull("way twice", seen.put(way, Boolean.TRUE));
        }
        MapReadResult whole = store.readMapData(new Tile(X, Y, (byte) Z, TILE_SIZE));
        Assert.assertEquals(whole.ways.size(), area.ways.size());
        // POIs: at z16 the cafe shows too, and nothing twice
        List<String> names = poiNames(area);
        Assert.assertTrue(names.contains("Cafe"));
        Assert.assertTrue(names.contains("shelter"));
        Assert.assertEquals(new java.util.HashSet<>(names).size(), names.size());
    }

    @Test
    public void trailBackfillFillsZoomTwelve() {
        TileBuilder z12 = new TileBuilder();
        z12.layer("transportation").feature(VectorTile.LINESTRING, new Pbf.Geometry().path(false, 0, 0, 10, 10),
                "class", "minor", "subclass", "residential");
        MemoryArchive archive = new MemoryArchive(0, Z, "transportation", "poi")
                .put(12, X >> 2, Y >> 2, z12.build())
                .put(Z, X, Y, sampleTile());
        MvtMapDataStore store = store(archive);
        Tile tile = new Tile(X >> 2, Y >> 2, (byte) 12, TILE_SIZE);
        MapReadResult result = store.readMapData(tile);
        Assert.assertNotNull("footway from z14", find(result.ways, "highway", "footway", false));
        Assert.assertNull("streets are not backfilled", find(result.ways, "highway", "residential", true));
        Assert.assertEquals(17, archive.reads.get());

        store.setTrailBackfill(false);
        Assert.assertNull(find(store.readMapData(tile).ways, "highway", "footway", false));
    }

    @Test
    public void noBackfillForArchivesWithoutTransportation() {
        MemoryArchive archive = new MemoryArchive(11, Z, "contour");
        MvtMapDataStore store = store(archive);
        store.readMapData(new Tile(X >> 2, Y >> 2, (byte) 12, TILE_SIZE));
        Assert.assertEquals("only the z12 tile is read", 1, archive.reads.get());
    }

    @Test
    public void corruptTileGivesEmptyResult() {
        byte[] good = sampleTile();
        MemoryArchive archive = new MemoryArchive(0, Z)
                .put(Z, X, Y, Arrays.copyOf(good, good.length / 2))
                .put(Z, X + 1, Y, new byte[]{0x1A, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x0F, 0x00});
        MvtMapDataStore store = store(archive);
        store.readMapData(new Tile(X, Y, (byte) Z, TILE_SIZE));
        Assert.assertTrue(store.readMapData(new Tile(X + 1, Y, (byte) Z, TILE_SIZE)).ways.isEmpty());
        // an archive that throws
        VectorTileArchive failing = new MemoryArchive(0, Z) {
            @Override
            public byte[] readTile(int zoom, int x, int y) {
                throw new IllegalStateException("closed");
            }
        };
        Assert.assertTrue(new MvtMapDataStore(failing).readMapData(new Tile(X, Y, (byte) Z, TILE_SIZE)).ways.isEmpty());
    }

    @Test
    public void tagListsAreSharedAndUnmodifiable() {
        TileBuilder tile = new TileBuilder();
        for (int i = 0; i < 3; i++) {
            int o = 100 * i;
            tile.layer("building").feature(VectorTile.POLYGON,
                    new Pbf.Geometry().path(true, o, o, o + 50, o, o + 50, o + 50, o, o + 50));
        }
        MvtMapDataStore store = store(new MemoryArchive(0, Z).put(Z, X, Y, tile.build()).put(Z, X + 1, Y, tile.build()));
        List<Way> a = store.readMapData(new Tile(X, Y, (byte) Z, TILE_SIZE)).ways;
        List<Way> b = store.readMapData(new Tile(X + 1, Y, (byte) Z, TILE_SIZE)).ways;
        Assert.assertSame(a.get(0).tags, a.get(1).tags);
        Assert.assertSame(a.get(0).tags, b.get(2).tags);
        try {
            a.get(0).tags.add(new Tag("x", "y"));
            Assert.fail("tags are shared, must not be modifiable");
        } catch (UnsupportedOperationException expected) {
            // fine
        }
    }

    @Test
    public void concurrentReadersShareOneDecode() throws InterruptedException {
        final MemoryArchive archive = new MemoryArchive(0, Z) {
            @Override
            public byte[] readTile(int zoom, int x, int y) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return super.readTile(zoom, x, y);
            }
        };
        archive.put(Z, X, Y, sampleTile());
        final MvtMapDataStore store = store(archive);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            final int dx = i % 4;
            final int dy = i / 4;
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                        MapReadResult r = store.readMapData(new Tile(4 * X + dx, 4 * Y + dy, (byte) 16, TILE_SIZE));
                        if (r == null) {
                            throw new AssertionError("null result");
                        }
                    } catch (Throwable e) {
                        failure.set(e);
                    }
                }
            });
            threads.add(t);
            t.start();
        }
        start.countDown();
        for (Thread t : threads) {
            t.join();
        }
        Assert.assertNull(failure.get());
        Assert.assertEquals(1, archive.reads.get());
    }

    @Test
    public void cacheIsBoundedByWeightButKeepsNewest() {
        MemoryArchive archive = new MemoryArchive(0, Z);
        for (int i = 0; i < 4; i++) {
            archive.put(Z, X + i, Y, sampleTile());
        }
        MvtMapDataStore store = new MvtMapDataStore(archive, 1);
        for (int i = 0; i < 4; i++) {
            store.readMapData(new Tile(X + i, Y, (byte) Z, TILE_SIZE));
        }
        Assert.assertEquals(1, store.cachedTileCount());
        store.readMapData(new Tile(X + 3, Y, (byte) Z, TILE_SIZE));
        Assert.assertEquals("newest still cached", 4, archive.reads.get());

        MvtMapDataStore roomy = new MvtMapDataStore(archive, 1_000_000);
        for (int i = 0; i < 4; i++) {
            roomy.readMapData(new Tile(X + i, Y, (byte) Z, TILE_SIZE));
        }
        Assert.assertEquals(4, roomy.cachedTileCount());
    }

    @Test
    public void closeClosesArchive() {
        MemoryArchive archive = new MemoryArchive(0, Z).put(Z, X, Y, sampleTile());
        MvtMapDataStore store = store(archive);
        Assert.assertEquals(1234L, store.getDataTimestamp(new Tile(X, Y, (byte) Z, TILE_SIZE)));
        store.close();
        Assert.assertTrue(archive.closed);
        Assert.assertTrue(store.readMapData(new Tile(X, Y, (byte) Z, TILE_SIZE)).ways.isEmpty());
    }
}
