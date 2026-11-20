package org.osmdroid.mapsforge.mvt;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** A minimal protobuf writer for building vector tiles by hand in tests. */
final class Pbf {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    Pbf varint(long value) {
        while ((value & ~0x7FL) != 0) {
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.write((int) value);
        return this;
    }

    Pbf key(int field, int wire) {
        return varint(((long) field << 3) | wire);
    }

    Pbf uint(int field, long value) {
        return key(field, 0).varint(value);
    }

    Pbf bytes(int field, byte[] data) {
        key(field, 2).varint(data.length);
        out.write(data, 0, data.length);
        return this;
    }

    Pbf string(int field, String s) {
        return bytes(field, s.getBytes(StandardCharsets.UTF_8));
    }

    Pbf message(int field, Pbf sub) {
        return bytes(field, sub.toBytes());
    }

    Pbf fixed32(int field, int bits) {
        key(field, 5);
        for (int i = 0; i < 4; i++) {
            out.write((bits >>> (8 * i)) & 0xFF);
        }
        return this;
    }

    Pbf fixed64(int field, long bits) {
        key(field, 1);
        for (int i = 0; i < 8; i++) {
            out.write((int) ((bits >>> (8 * i)) & 0xFF));
        }
        return this;
    }

    /** A packed field of varints. */
    Pbf packed(int field, long... values) {
        Pbf sub = new Pbf();
        for (long v : values) {
            sub.varint(v);
        }
        return bytes(field, sub.toBytes());
    }

    byte[] toBytes() {
        return out.toByteArray();
    }

    static long zigzag(long n) {
        return (n << 1) ^ (n >> 63);
    }

    static long command(int id, int count) {
        return ((long) count << 3) | id;
    }

    /** Builds MVT geometry command streams from absolute coordinates, tracking the cursor. */
    static final class Geometry {
        private final java.util.List<Long> out = new java.util.ArrayList<>();
        private int cx;
        private int cy;

        /** A ring or line: MoveTo the first point, LineTo the rest, ClosePath if {@code close}. */
        Geometry path(boolean close, int... xy) {
            int n = xy.length / 2;
            out.add(command(1, 1));
            delta(xy[0], xy[1]);
            if (n > 1) {
                out.add(command(2, n - 1));
                for (int p = 1; p < n; p++) {
                    delta(xy[2 * p], xy[2 * p + 1]);
                }
            }
            if (close) {
                out.add(command(7, 1));
            }
            return this;
        }

        /** Several points under one MoveTo, as a multipoint is written. */
        Geometry points(int... xy) {
            out.add(command(1, xy.length / 2));
            for (int p = 0; p < xy.length / 2; p++) {
                delta(xy[2 * p], xy[2 * p + 1]);
            }
            return this;
        }

        Geometry raw(long value) {
            out.add(value);
            return this;
        }

        private void delta(int x, int y) {
            out.add(zigzag(x - cx));
            out.add(zigzag(y - cy));
            cx = x;
            cy = y;
        }

        long[] build() {
            long[] result = new long[out.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = out.get(i);
            }
            return result;
        }
    }

    /** A feature message: tag index pairs, type, geometry. */
    static Pbf feature(int type, long[] tags, long[] geometry) {
        Pbf f = new Pbf();
        if (tags != null && tags.length > 0) {
            f.packed(2, tags);
        }
        f.uint(3, type);
        f.packed(4, geometry);
        return f;
    }

    /** A string value message. */
    static Pbf stringValue(String s) {
        return new Pbf().string(1, s);
    }
}
