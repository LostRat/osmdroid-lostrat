package org.osmdroid.mapsforge.mvt;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class VectorTileTest {

    private static VectorTile.Reader reader(byte[] data) {
        return new VectorTile.Reader(data, 0, data.length);
    }

    /** A reader over the bare command integers, as a feature's geometry field holds them. */
    private static VectorTile.Reader commands(long[] values) {
        Pbf pbf = new Pbf();
        for (long v : values) {
            pbf.varint(v);
        }
        return reader(pbf.toBytes());
    }

    @Test
    public void varintsDecode() {
        long[] values = {0, 1, 127, 128, 300, 16383, 16384, Integer.MAX_VALUE, 1L << 40, Long.MAX_VALUE, -1L};
        Pbf pbf = new Pbf();
        for (long v : values) {
            pbf.varint(v);
        }
        VectorTile.Reader r = reader(pbf.toBytes());
        for (long v : values) {
            Assert.assertEquals(v, r.varint());
            Assert.assertFalse(r.failed);
        }
        Assert.assertFalse(r.hasMore());
    }

    @Test
    public void truncatedVarintFails() {
        VectorTile.Reader r = reader(new byte[]{(byte) 0x80, (byte) 0x80});
        r.varint();
        Assert.assertTrue(r.failed);
    }

    @Test
    public void tooLongVarintFails() {
        byte[] data = new byte[11];
        Arrays.fill(data, (byte) 0xFF);
        VectorTile.Reader r = reader(data);
        r.varint();
        Assert.assertTrue(r.failed);
    }

    @Test
    public void zigzagDecodes() {
        Assert.assertEquals(0, VectorTile.zigzag(0));
        Assert.assertEquals(-1, VectorTile.zigzag(1));
        Assert.assertEquals(1, VectorTile.zigzag(2));
        Assert.assertEquals(-2, VectorTile.zigzag(3));
        Assert.assertEquals(2147483647L, VectorTile.zigzag(4294967294L));
        Assert.assertEquals(-2147483648L, VectorTile.zigzag(4294967295L));
        for (long n : new long[]{-5000, -1, 0, 1, 4096, 123456789}) {
            Assert.assertEquals(n, VectorTile.zigzag(Pbf.zigzag(n)));
        }
    }

    @Test
    public void lineGeometryMoveToStartsNewPart() {
        long[] geometry = new Pbf.Geometry()
                .path(false, 2, 2, 2, 10, 10, 10)
                .path(false, 1, 1, 3, 5)
                .build();
        int[][] parts = VectorTile.geometry(commands(geometry),
                VectorTile.LINESTRING);
        Assert.assertNotNull(parts);
        Assert.assertEquals(2, parts.length);
        Assert.assertArrayEquals(new int[]{2, 2, 2, 10, 10, 10}, parts[0]);
        Assert.assertArrayEquals(new int[]{1, 1, 3, 5}, parts[1]);
    }

    @Test
    public void polygonClosePathRepeatsFirstPoint() {
        long[] geometry = new Pbf.Geometry().path(true, 3, 6, 8, 12, 20, 34).build();
        int[][] parts = VectorTile.geometry(commands(geometry),
                VectorTile.POLYGON);
        Assert.assertNotNull(parts);
        Assert.assertEquals(1, parts.length);
        Assert.assertArrayEquals(new int[]{3, 6, 8, 12, 20, 34, 3, 6}, parts[0]);
    }

    @Test
    public void pointsShareOnePart() {
        long[] geometry = new Pbf.Geometry().points(5, 7, 3, 2).build();
        int[][] parts = VectorTile.geometry(commands(geometry),
                VectorTile.POINT);
        Assert.assertNotNull(parts);
        Assert.assertEquals(1, parts.length);
        Assert.assertArrayEquals(new int[]{5, 7, 3, 2}, parts[0]);
    }

    @Test
    public void unknownCommandOrTruncatedGeometryIsRefused() {
        long[] bad = {Pbf.command(3, 1), 0, 0};
        Assert.assertNull(VectorTile.geometry(commands(bad),
                VectorTile.LINESTRING));
        long[] truncated = {Pbf.command(2, 2), 2, 2, 2};
        Assert.assertNull(VectorTile.geometry(commands(truncated),
                VectorTile.LINESTRING));
    }

    @Test
    public void valueTypesDecode() {
        Assert.assertEquals("abc", value(new Pbf().string(1, "abc")).text());
        VectorTile.Value f = value(new Pbf().fixed32(2, Float.floatToIntBits(1.5f)));
        Assert.assertEquals(VectorTile.Value.DOUBLE, f.type);
        Assert.assertEquals("1.5", f.text());
        VectorTile.Value d = value(new Pbf().fixed64(3, Double.doubleToLongBits(1200.0)));
        Assert.assertEquals("1200", d.text());
        Assert.assertEquals(Long.valueOf(1200), d.intValue());
        VectorTile.Value i = value(new Pbf().uint(4, -3L));
        Assert.assertEquals(VectorTile.Value.INT, i.type);
        Assert.assertEquals("-3", i.text());
        VectorTile.Value u = value(new Pbf().uint(5, -1L)); // 2^64 - 1
        Assert.assertEquals(Long.MAX_VALUE, (long) u.intValue());
        VectorTile.Value s = value(new Pbf().uint(6, Pbf.zigzag(-42)));
        Assert.assertEquals(Long.valueOf(-42), s.intValue());
        VectorTile.Value b = value(new Pbf().uint(7, 1));
        Assert.assertEquals(VectorTile.Value.BOOL, b.type);
        Assert.assertEquals("true", b.text());
        Assert.assertEquals(Long.valueOf(1), b.intValue());
        Assert.assertEquals(Long.valueOf(17), VectorTile.Value.ofString("17").intValue());
        Assert.assertNull(VectorTile.Value.ofString("x").intValue());
        Assert.assertNull(VectorTile.Value.ofDouble(Double.NaN).intValue());
        Assert.assertEquals("2.25", VectorTile.Value.ofDouble(2.25).text());
    }

    private static VectorTile.Value value(Pbf pbf) {
        return VectorTile.value(reader(pbf.toBytes()));
    }

    /** Two layers: "roads" with a named line, "water" with a polygon. */
    static byte[] sampleTile() {
        Pbf roads = new Pbf()
                .string(1, "roads")
                .message(2, Pbf.feature(VectorTile.LINESTRING, new long[]{0, 0, 1, 1},
                        new Pbf.Geometry().path(false, 0, 0, 100, 100).build()))
                .string(3, "class")
                .string(3, "name")
                .message(4, Pbf.stringValue("path"))
                .message(4, Pbf.stringValue("Wildwood"))
                .uint(5, 4096)
                .uint(15, 2);
        Pbf water = new Pbf()
                .uint(15, 2)
                .string(1, "water")
                .message(2, Pbf.feature(VectorTile.POLYGON, null,
                        new Pbf.Geometry().path(true, 0, 0, 10, 0, 10, 10, 0, 10).build()))
                .uint(5, 512);
        return new Pbf().message(3, roads).message(3, water).toBytes();
    }

    @Test
    public void tileDecodesLayersKeysAndValues() {
        VectorTile tile = VectorTile.decode(sampleTile());
        Assert.assertTrue(tile.isComplete());
        Assert.assertEquals(2, tile.getLayers().size());
        VectorTile.Layer roads = tile.getLayer("roads");
        Assert.assertEquals(4096, roads.extent);
        Assert.assertEquals(1, roads.features.size());
        VectorTile.Feature road = roads.features.get(0);
        Assert.assertEquals("path", road.string("class"));
        Assert.assertEquals("Wildwood", road.string("name"));
        Assert.assertNull(road.string("missing"));
        Assert.assertEquals(512, tile.getLayer("water").extent);
        Assert.assertEquals(VectorTile.POLYGON, tile.getLayer("water").features.get(0).type);
    }

    @Test
    public void layerFilterSkipsOtherLayers() {
        VectorTile tile = VectorTile.decode(sampleTile(), new HashSet<>(Collections.singletonList("water")));
        Assert.assertTrue(tile.isComplete());
        Assert.assertEquals(1, tile.getLayers().size());
        Assert.assertEquals("water", tile.getLayers().get(0).name);
        Assert.assertEquals(0, VectorTile.decode(sampleTile(), Collections.<String>emptySet()).getLayers().size());
    }

    @Test
    public void truncatedTileKeepsWhatDecoded() {
        byte[] whole = sampleTile();
        byte[] cut = Arrays.copyOf(whole, whole.length - 5);
        VectorTile tile = VectorTile.decode(cut);
        Assert.assertFalse(tile.isComplete());
        Assert.assertNotNull(tile.getLayer("roads"));
        Assert.assertNull(tile.getLayer("water"));
        // garbage never throws
        VectorTile.decode(new byte[]{(byte) 0xFF, (byte) 0xFF, 0x01, 0x1A, 0x7F});
        VectorTile.decode(new byte[0]);
        VectorTile.decode(null);
    }

    @Test
    public void featureWithUnknownTypeIsDropped() {
        Pbf layer = new Pbf().string(1, "x")
                .message(2, Pbf.feature(0, null, new Pbf.Geometry().points(1, 1).build()))
                .message(2, Pbf.feature(VectorTile.POINT, null, new Pbf.Geometry().points(1, 1).build()));
        VectorTile tile = VectorTile.decode(new Pbf().message(3, layer).toBytes());
        Assert.assertEquals(1, tile.getLayer("x").features.size());
    }
}
