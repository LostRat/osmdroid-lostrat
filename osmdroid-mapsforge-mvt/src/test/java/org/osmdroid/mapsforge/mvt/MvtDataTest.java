package org.osmdroid.mapsforge.mvt;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mapsforge.core.model.Tag;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.datastore.MapReadResult;
import org.mapsforge.map.datastore.MultiMapDataStore;
import org.mapsforge.map.datastore.Way;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads the real tilemaker files (skipped when they are missing). */
public class MvtDataTest {

    private static final int TILE_SIZE = 256;
    /** forest-park-full z14 tile with known contents; TMS row 10528. */
    private static final int X = 2605;
    private static final int Y = 5855;

    private static JdbcMbtilesArchive full;
    private static MvtMapDataStore store;

    @BeforeClass
    public static void open() throws IOException {
        if (!TestData.DIR.isDirectory()) {
            return;
        }
        full = new JdbcMbtilesArchive(TestData.file("forest-park-full-202610.mbtiles"));
        store = new MvtMapDataStore(full);
    }

    @AfterClass
    public static void close() {
        if (store != null) {
            store.close();
        }
    }

    private static void requireData() {
        TestData.file("forest-park-full-202610.mbtiles");
    }

    @Test
    public void metadataIsRead() {
        requireData();
        Assert.assertEquals(0, full.getMinZoom());
        Assert.assertEquals(14, full.getMaxZoom());
        Assert.assertTrue(full.isVector());
        Assert.assertEquals(16, full.getVectorLayerIds().size());
        Assert.assertTrue(full.getVectorLayerIds().contains("transportation"));
        Assert.assertEquals(45.504320, full.getBoundingBox().minLatitude, 1e-9);
    }

    @Test
    public void knownTileHasKnownLayers() throws IOException {
        requireData();
        Assert.assertTrue(MbtilesMetadata.isCompressed(full.readStored(14, X, Y)));
        VectorTile tile = VectorTile.decode(full.readTile(14, X, Y));
        Assert.assertTrue(tile.isComplete());
        Map<String, int[]> counts = new HashMap<>();
        for (VectorTile.Layer layer : tile.getLayers()) {
            int[] byType = new int[4];
            for (VectorTile.Feature feature : layer.features) {
                byType[feature.type]++;
            }
            counts.put(layer.name, byType);
            Assert.assertEquals(4096, layer.extent);
        }
        Assert.assertEquals(2, counts.get("place")[VectorTile.POINT]);
        Assert.assertEquals(246, counts.get("poi")[VectorTile.POINT]);
        Assert.assertEquals(1597, counts.get("housenumber")[VectorTile.POINT]);
        Assert.assertEquals(766, counts.get("transportation")[VectorTile.LINESTRING]);
        Assert.assertEquals(2, counts.get("transportation")[VectorTile.POLYGON]);
        Assert.assertEquals(410, counts.get("transportation_name")[VectorTile.LINESTRING]);
        Assert.assertEquals(4379, counts.get("building")[VectorTile.POLYGON]);
        Assert.assertEquals(4, total(counts.get("water")));
        Assert.assertEquals(91, total(counts.get("landuse")));
        Assert.assertEquals(54, total(counts.get("landcover")));

        VectorTile filtered = VectorTile.decode(full.readTile(14, X, Y),
                new java.util.HashSet<>(java.util.Arrays.asList("transportation", "transportation_name")));
        Assert.assertEquals(2, filtered.getLayers().size());
        Assert.assertEquals(768, filtered.getLayer("transportation").features.size());
    }

    private static int total(int[] byType) {
        return byType[0] + byType[1] + byType[2] + byType[3];
    }

    private static int count(List<Way> ways, String key, String value) {
        int n = 0;
        for (Way way : ways) {
            for (Tag tag : way.tags) {
                if (tag.key.equals(key) && (value == null || tag.value.equals(value))) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    private static int labelOnly(List<Way> ways) {
        int n = 0;
        for (Way way : ways) {
            if (way instanceof LabelOnlyWay) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void zoom14HasRoadsBuildingsPois() {
        requireData();
        MapReadResult result = store.readMapData(new Tile(X, Y, (byte) 14, TILE_SIZE));
        Assert.assertFalse(result.isWater);
        Assert.assertTrue(count(result.ways, "highway", null) > 500);
        Assert.assertTrue(count(result.ways, "building", "yes") >= 4379);
        Assert.assertTrue(labelOnly(result.ways) >= 400);
        Assert.assertFalse(result.pois.isEmpty());
        Assert.assertEquals(store.readPoiData(new Tile(X, Y, (byte) 14, TILE_SIZE)).pois.size(), result.pois.size());
        System.out.println("z14 " + X + "/" + Y + ": " + result.ways.size() + " ways, " + result.pois.size()
                + " pois, " + count(result.ways, "building", "yes") + " buildings, "
                + labelOnly(result.ways) + " label-only");
    }

    @Test
    public void overzoomReturnsSubsetOfAncestor() {
        requireData();
        MapReadResult z14 = store.readMapData(new Tile(X, Y, (byte) 14, TILE_SIZE));
        Set<Way> ancestor = Collections.newSetFromMap(new IdentityHashMap<Way, Boolean>());
        ancestor.addAll(z14.ways);
        for (int zoom : new int[]{16, 18}) {
            int shift = zoom - 14;
            Tile tile = new Tile((X << shift) + 1, (Y << shift) + 1, (byte) zoom, TILE_SIZE);
            MapReadResult result = store.readMapData(tile);
            Assert.assertFalse(result.ways.isEmpty());
            Assert.assertTrue(result.ways.size() < z14.ways.size());
            for (Way way : result.ways) {
                Assert.assertTrue("from the z14 tile", ancestor.contains(way));
            }
            System.out.println("z" + zoom + ": " + result.ways.size() + " of " + z14.ways.size() + " ways, "
                    + result.pois.size() + " pois");
        }
    }

    @Test
    public void zoom12GetsBackfilledTrails() {
        requireData();
        Tile tile = new Tile(X >> 2, Y >> 2, (byte) 12, TILE_SIZE);
        MvtMapDataStore plain = new MvtMapDataStore(full);
        plain.setTrailBackfill(false);
        MapReadResult without = plain.readMapData(tile);
        MapReadResult with = store.readMapData(tile);
        int pathsWithout = count(without.ways, "highway", "path") + count(without.ways, "highway", "footway");
        int pathsWith = count(with.ways, "highway", "path") + count(with.ways, "highway", "footway");
        System.out.println("z12 paths+footways: " + pathsWithout + " own, " + pathsWith + " with backfill");
        Assert.assertTrue(pathsWith > pathsWithout);
        Assert.assertTrue(pathsWith > 100);
    }

    @Test
    public void contourStoreYieldsContours() throws IOException {
        TestData.Counting counting = new TestData.Counting(TestData.archive("forest-park-contour-202610.mbtiles"));
        MvtMapDataStore contours = new MvtMapDataStore(counting);
        try {
            Assert.assertEquals(11, contours.getArchive().getMinZoom());
            Assert.assertFalse(contours.supportsTile(new Tile(X >> 4, Y >> 4, (byte) 10, TILE_SIZE)));
            MapReadResult z14 = contours.readMapData(new Tile(X, Y, (byte) 14, TILE_SIZE));
            int n = count(z14.ways, "contour_ext", null);
            Assert.assertTrue(n > 0);
            Assert.assertEquals(z14.ways.size(), n);
            Way first = z14.ways.get(0);
            Assert.assertTrue(first.tags.get(0).value.startsWith("elevation_"));
            Assert.assertEquals("ele", first.tags.get(1).key);
            Assert.assertTrue(contours.readMapData(new Tile(X >> 4, Y >> 4, (byte) 10, TILE_SIZE)).ways.isEmpty());
            // z12: no deepest tiles are read for a contour file
            int before = counting.reads.get();
            MapReadResult z12 = contours.readMapData(new Tile(X >> 2, Y >> 2, (byte) 12, TILE_SIZE));
            Assert.assertEquals(before + 1, counting.reads.get());
            System.out.println("contours: z14 " + n + " ways, z12 " + z12.ways.size());
        } finally {
            contours.close();
        }
    }

    @Test
    public void areaReadReturnsNoWayTwice() {
        requireData();
        for (int zoom : new int[]{14, 16, 18}) {
            int shift = zoom - 14;
            Tile center = new Tile((X << shift) + 1, (Y << shift) + 1, (byte) zoom, TILE_SIZE);
            MapReadResult area = store.readMapData(center.getAboveLeft(), center.getBelowRight());
            Set<Way> seen = Collections.newSetFromMap(new IdentityHashMap<Way, Boolean>());
            for (Way way : area.ways) {
                Assert.assertTrue("way twice at z" + zoom, seen.add(way));
            }
            Set<Object> pois = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            for (Object poi : area.pois) {
                Assert.assertTrue("poi twice at z" + zoom, pois.add(poi));
            }
            // the default per-tile loop would repeat ways
            int perTile = 0;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    perTile += store.readMapData(new Tile(center.tileX + dx, center.tileY + dy, (byte) zoom, TILE_SIZE))
                            .ways.size();
                }
            }
            System.out.println("z" + zoom + " 3x3: " + area.ways.size() + " ways once (per-tile sum " + perTile + ")");
        }
    }

    @Test
    public void multiMapDataStoreCombinesBaseAndContours() throws IOException {
        requireData();
        MvtMapDataStore base = new MvtMapDataStore(TestData.archive("forest-park-full-202610.mbtiles"));
        MvtMapDataStore contours = new MvtMapDataStore(TestData.archive("forest-park-contour-202610.mbtiles"));
        MultiMapDataStore multi = new MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_ALL);
        multi.addMapDataStore(base, true, true);
        multi.addMapDataStore(contours, false, false);
        try {
            for (int zoom : new int[]{14, 16}) {
                int shift = zoom - 14;
                // the z14 tile's contours run through its middle
                Tile tile = new Tile((X << shift) + (1 << shift) / 2, (Y << shift) + (1 << shift) / 2,
                        (byte) zoom, TILE_SIZE);
                MapReadResult result = multi.readMapData(tile);
                Assert.assertTrue(count(result.ways, "contour_ext", null) > 0);
                Assert.assertTrue(count(result.ways, "highway", null) > 0);
                MapReadResult area = multi.readMapData(tile.getAboveLeft(), tile.getBelowRight());
                Assert.assertTrue(count(area.ways, "contour_ext", null) > 0);
                Assert.assertFalse(area.isWater);
            }
            MultiMapDataStore dedupe = new MultiMapDataStore();
            dedupe.addMapDataStore(new MvtMapDataStore(TestData.archive("forest-park-full-202610.mbtiles")), true, true);
            MapReadResult deduped = dedupe.readMapData(new Tile(X, Y, (byte) 14, TILE_SIZE));
            MapReadResult raw = base.readMapData(new Tile(X, Y, (byte) 14, TILE_SIZE));
            Assert.assertEquals("label-only ways survive deduplication", labelOnly(raw.ways), labelOnly(deduped.ways));
            dedupe.close();
        } finally {
            multi.close();
        }
    }
}
