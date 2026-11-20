package org.osmdroid.mapsforge.mvt;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

public class MbtilesMetadataTest {

    @Test
    public void tmsRowFlips() {
        Assert.assertEquals(5855, MbtilesMetadata.flipRow(14, 10528));
        Assert.assertEquals(10528, MbtilesMetadata.flipRow(14, 5855));
        Assert.assertEquals(0, MbtilesMetadata.flipRow(0, 0));
        Assert.assertEquals(3, MbtilesMetadata.flipRow(2, 0));
    }

    @Test
    public void schemeDecidesStoredRow() {
        MbtilesMetadata tms = new MbtilesMetadata(Collections.<String, String>emptyMap());
        Assert.assertTrue(tms.isTms());
        Assert.assertEquals(10528, tms.storedRow(14, 5855));
        MbtilesMetadata xyz = new MbtilesMetadata(Collections.singletonMap("scheme", "XYZ"));
        Assert.assertFalse(xyz.isTms());
        Assert.assertEquals(5855, xyz.storedRow(14, 5855));
    }

    @Test
    public void defaultsWithoutMetadata() {
        MbtilesMetadata m = new MbtilesMetadata(null);
        Assert.assertEquals(0, m.getMinZoom());
        Assert.assertEquals(14, m.getMaxZoom());
        Assert.assertFalse(m.hasBounds());
        Assert.assertEquals(-180, m.getBoundingBox().minLongitude, 0);
        Assert.assertEquals(180, m.getBoundingBox().maxLongitude, 0);
        Assert.assertTrue(m.getVectorLayerIds().isEmpty());
        Assert.assertFalse(m.isVector());
        Assert.assertNull(m.getCenter());
    }

    @Test
    public void parsesRows() {
        Map<String, String> rows = new HashMap<>();
        rows.put("minzoom", "11");
        rows.put("maxzoom", "14.0");
        rows.put("bounds", "-122.837584, 45.504320,-122.693271,45.628345");
        rows.put("center", "-122.765427,45.566332,12");
        rows.put("format", "pbf");
        rows.put("json", "{\"vector_layers\":[{\"id\":\"contour\",\"fields\":{\"class\":\"String\"},"
                + "\"minzoom\":11,\"maxzoom\":14},{\"fields\":{\"id\":\"nested, not the layer's\"},"
                + "\"description\":\"say \\\"id\\\": [x]\",\"id\":\"place\"}],\"tilestats\":{\"layers\":[]}}");
        MbtilesMetadata m = new MbtilesMetadata(rows);
        Assert.assertEquals(11, m.getMinZoom());
        Assert.assertEquals(14, m.getMaxZoom());
        Assert.assertTrue(m.hasBounds());
        Assert.assertEquals(45.504320, m.getBoundingBox().minLatitude, 1e-9);
        Assert.assertEquals(-122.693271, m.getBoundingBox().maxLongitude, 1e-9);
        Assert.assertEquals(45.566332, m.getCenter().latitude, 1e-9);
        Assert.assertEquals(12, m.getCenterZoom());
        Assert.assertTrue(m.isVector());
        Assert.assertEquals(Arrays.asList("contour", "place"), m.getVectorLayerIds());
    }

    @Test
    public void malformedValuesFallBack() {
        Map<String, String> rows = new HashMap<>();
        rows.put("minzoom", "x");
        rows.put("bounds", "1,2,3");
        rows.put("json", "{\"vector_layers\":[{\"id\":\"a\"},{\"id\":");
        MbtilesMetadata m = new MbtilesMetadata(rows);
        Assert.assertEquals(0, m.getMinZoom());
        Assert.assertFalse(m.hasBounds());
        Assert.assertEquals(Collections.singletonList("a"), m.getVectorLayerIds());
        Assert.assertTrue(MbtilesMetadata.vectorLayerIds("[]").isEmpty());
        Assert.assertTrue(MbtilesMetadata.vectorLayerIds("{}").isEmpty());
        Assert.assertTrue(MbtilesMetadata.vectorLayerIds("{\"vector_layers\":[]}").isEmpty());
    }

    @Test
    public void detectsAndInflatesGzipAndZlib() throws IOException {
        byte[] plain = new byte[100_000];
        for (int i = 0; i < plain.length; i++) {
            plain[i] = (byte) (i % 7);
        }
        Assert.assertFalse(MbtilesMetadata.isCompressed(plain));
        Assert.assertSame(plain, MbtilesMetadata.unpack(plain));

        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
            out.write(plain);
        }
        Assert.assertTrue(MbtilesMetadata.isCompressed(gz.toByteArray()));
        Assert.assertArrayEquals(plain, MbtilesMetadata.unpack(gz.toByteArray()));

        ByteArrayOutputStream z = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(z)) {
            out.write(plain);
        }
        Assert.assertEquals(0x78, z.toByteArray()[0] & 0xFF);
        Assert.assertArrayEquals(plain, MbtilesMetadata.unpack(z.toByteArray()));
    }

    @Test(expected = IOException.class)
    public void truncatedGzipThrows() throws IOException {
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
            out.write(new byte[5000]);
        }
        byte[] data = gz.toByteArray();
        MbtilesMetadata.unpack(Arrays.copyOf(data, data.length / 2));
    }
}
