package org.osmdroid.mapsforge.mvt;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A decoded Mapbox Vector Tile (the protobuf inside a {@code .pbf} tile): layers of
 * features, each with properties and a geometry in the tile's own grid.
 * <p>
 * Decoding never throws. Damaged input yields the layers and features read before the
 * damage, and {@link #isComplete()} returns false.
 *
 * @since 7.0.3-lostrat
 */
public final class VectorTile {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** Geometry types of the MVT specification. */
    public static final int UNKNOWN = 0;
    public static final int POINT = 1;
    public static final int LINESTRING = 2;
    public static final int POLYGON = 3;

    private final List<Layer> layers;
    private final boolean complete;

    private VectorTile(List<Layer> layers, boolean complete) {
        this.layers = Collections.unmodifiableList(layers);
        this.complete = complete;
    }

    /** The layers in file order. */
    public List<Layer> getLayers() {
        return layers;
    }

    /** The layer named {@code name}, or null. */
    public Layer getLayer(String name) {
        for (Layer layer : layers) {
            if (layer.name.equals(name)) {
                return layer;
            }
        }
        return null;
    }

    /** False when the bytes were damaged and only part of the tile was read. */
    public boolean isComplete() {
        return complete;
    }

    /** Decodes every layer of {@code data}. */
    public static VectorTile decode(byte[] data) {
        return decode(data, null);
    }

    /**
     * Decodes only the layers whose names are in {@code layerNames}; the bytes of other
     * layers are skipped without decoding their features. Null decodes every layer.
     */
    public static VectorTile decode(byte[] data, Set<String> layerNames) {
        List<Layer> layers = new ArrayList<>();
        if (data == null) {
            return new VectorTile(layers, true);
        }
        Reader reader = new Reader(data, 0, data.length);
        boolean ok = true;
        while (reader.hasMore()) {
            long key = reader.varint();
            if (reader.failed) {
                ok = false;
                break;
            }
            int field = (int) (key >>> 3);
            int wire = (int) (key & 7);
            if (field == 3 && wire == 2) {
                Reader sub = reader.message();
                if (sub == null) {
                    ok = false;
                    break;
                }
                if (layerNames != null) {
                    String name = layerName(sub);
                    if (name == null || !layerNames.contains(name)) {
                        continue;
                    }
                }
                Layer layer = layer(sub);
                if (layer == null) {
                    ok = false;
                    break;
                }
                layers.add(layer);
                if (!layer.complete) {
                    ok = false;
                }
            } else if (!reader.skip(wire)) {
                ok = false;
                break;
            }
        }
        return new VectorTile(layers, ok);
    }

    /** The name of the layer message under {@code reader}, without moving it; null if unreadable. */
    private static String layerName(Reader layer) {
        Reader r = new Reader(layer.data, layer.pos, layer.end);
        while (r.hasMore()) {
            long key = r.varint();
            if (r.failed) {
                return null;
            }
            int field = (int) (key >>> 3);
            int wire = (int) (key & 7);
            if (field == 1 && wire == 2) {
                return r.string();
            }
            if (!r.skip(wire)) {
                return null;
            }
        }
        return "";
    }

    /** Null when the layer's own fields are unreadable; a damaged feature list gives a partial layer. */
    private static Layer layer(Reader reader) {
        String name = "";
        int extent = 4096;
        List<String> keys = new ArrayList<>();
        List<Value> values = new ArrayList<>();
        // Features are decoded after the whole layer is read: keys and values may follow them.
        List<int[]> featureRanges = new ArrayList<>();
        boolean complete = true;
        while (reader.hasMore()) {
            long key = reader.varint();
            if (reader.failed) {
                complete = false;
                break;
            }
            int field = (int) (key >>> 3);
            int wire = (int) (key & 7);
            if (field == 1 && wire == 2) {
                String s = reader.string();
                name = s == null ? "" : s;
            } else if (field == 2 && wire == 2) {
                Reader sub = reader.message();
                if (sub != null) {
                    featureRanges.add(new int[]{sub.pos, sub.end});
                }
            } else if (field == 3 && wire == 2) {
                String s = reader.string();
                keys.add(s == null ? "" : s);
            } else if (field == 4 && wire == 2) {
                Reader sub = reader.message();
                if (sub == null) {
                    complete = false;
                    break;
                }
                values.add(value(sub));
            } else if (field == 5 && wire == 0) {
                long e = reader.varint();
                extent = e <= 0 || e > Integer.MAX_VALUE ? (e == 0 ? 1 : Integer.MAX_VALUE) : (int) e;
                if (reader.failed) {
                    extent = 4096;
                }
            } else if (!reader.skip(wire)) {
                complete = false;
                break;
            }
            if (reader.failed) {
                complete = false;
                break;
            }
        }
        if (!complete && featureRanges.isEmpty()) {
            return null;
        }
        String[] keyArray = keys.toArray(new String[0]);
        Value[] valueArray = values.toArray(new Value[0]);
        List<Feature> features = new ArrayList<>(featureRanges.size());
        for (int[] range : featureRanges) {
            Feature feature = feature(new Reader(reader.data, range[0], range[1]), keyArray, valueArray);
            if (feature != null) {
                features.add(feature);
            }
        }
        return new Layer(name, extent, features, complete);
    }

    static Value value(Reader reader) {
        Value result = Value.ofString("");
        while (reader.hasMore()) {
            long key = reader.varint();
            if (reader.failed) {
                return result;
            }
            int field = (int) (key >>> 3);
            int wire = (int) (key & 7);
            if (field == 1 && wire == 2) {
                String s = reader.string();
                result = Value.ofString(s == null ? "" : s);
            } else if (field == 2 && wire == 5) {
                result = Value.ofDouble(Float.intBitsToFloat(reader.fixed32()));
            } else if (field == 3 && wire == 1) {
                result = Value.ofDouble(Double.longBitsToDouble(reader.fixed64()));
            } else if (field == 4 && wire == 0) {
                result = Value.ofInt(reader.varint());
            } else if (field == 5 && wire == 0) {
                long raw = reader.varint();
                // uint64 beyond the signed range is clamped, not wrapped
                result = Value.ofInt(raw < 0 ? Long.MAX_VALUE : raw);
            } else if (field == 6 && wire == 0) {
                result = Value.ofInt(zigzag(reader.varint()));
            } else if (field == 7 && wire == 0) {
                result = Value.ofBool(reader.varint() != 0);
            } else if (!reader.skip(wire)) {
                return result;
            }
            if (reader.failed) {
                return result;
            }
        }
        return result;
    }

    /** Null for a feature of unknown type, with no geometry, or with damaged geometry. */
    static Feature feature(Reader reader, String[] keys, Value[] values) {
        int type = UNKNOWN;
        Reader tags = null;
        Reader geometry = null;
        while (reader.hasMore()) {
            long key = reader.varint();
            if (reader.failed) {
                return null;
            }
            int field = (int) (key >>> 3);
            int wire = (int) (key & 7);
            if (field == 2 && wire == 2) {
                tags = reader.message();
            } else if (field == 3 && wire == 0) {
                long t = reader.varint();
                type = t >= POINT && t <= POLYGON ? (int) t : UNKNOWN;
            } else if (field == 4 && wire == 2) {
                geometry = reader.message();
            } else if (!reader.skip(wire)) {
                return null;
            }
            if (reader.failed) {
                return null;
            }
        }
        if (type == UNKNOWN || geometry == null) {
            return null;
        }

        String[] propertyKeys;
        Value[] propertyValues;
        if (tags != null) {
            int capacity = 0;
            int[] pairs = new int[16];
            while (tags.hasMore()) {
                long k = tags.varint();
                if (tags.failed || !tags.hasMore()) {
                    break;
                }
                long v = tags.varint();
                if (tags.failed) {
                    break;
                }
                if (k >= 0 && k < keys.length && v >= 0 && v < values.length) {
                    if (capacity + 2 > pairs.length) {
                        int[] grown = new int[pairs.length * 2];
                        System.arraycopy(pairs, 0, grown, 0, capacity);
                        pairs = grown;
                    }
                    pairs[capacity++] = (int) k;
                    pairs[capacity++] = (int) v;
                }
            }
            int n = capacity / 2;
            propertyKeys = new String[n];
            propertyValues = new Value[n];
            int count = 0;
            for (int i = 0; i < n; i++) {
                String k = keys[pairs[2 * i]];
                Value v = values[pairs[2 * i + 1]];
                // a key given twice keeps its last value, as a dictionary would
                int existing = -1;
                for (int j = 0; j < count; j++) {
                    if (propertyKeys[j].equals(k)) {
                        existing = j;
                        break;
                    }
                }
                if (existing >= 0) {
                    propertyValues[existing] = v;
                } else {
                    propertyKeys[count] = k;
                    propertyValues[count] = v;
                    count++;
                }
            }
            if (count < n) {
                String[] k2 = new String[count];
                Value[] v2 = new Value[count];
                System.arraycopy(propertyKeys, 0, k2, 0, count);
                System.arraycopy(propertyValues, 0, v2, 0, count);
                propertyKeys = k2;
                propertyValues = v2;
            }
        } else {
            propertyKeys = new String[0];
            propertyValues = new Value[0];
        }

        int[][] parts = geometry(geometry, type);
        if (parts == null) {
            return null;
        }
        return new Feature(type, propertyKeys, propertyValues, parts);
    }

    /**
     * Commands: MoveTo (1) and LineTo (2) with a count and that many zigzag dx, dy pairs;
     * ClosePath (7) ends a polygon ring by repeating its first point. Points: one part
     * holding every point. Lines: one part per line. Polygons: one part per ring.
     * Each part is x0, y0, x1, y1, ... Null on damaged geometry.
     */
    static int[][] geometry(Reader geometry, int type) {
        List<int[]> parts = new ArrayList<>();
        IntList current = new IntList();
        int x = 0;
        int y = 0;
        while (geometry.hasMore()) {
            long command = geometry.varint();
            if (geometry.failed) {
                return null;
            }
            int id = (int) (command & 7);
            long count = command >>> 3;
            if (id == 1 || id == 2) {
                for (long i = 0; i < count; i++) {
                    if (!geometry.hasMore()) {
                        return null;
                    }
                    long dx = geometry.varint();
                    if (geometry.failed || !geometry.hasMore()) {
                        return null;
                    }
                    long dy = geometry.varint();
                    if (geometry.failed) {
                        return null;
                    }
                    x += (int) zigzag(dx);
                    y += (int) zigzag(dy);
                    if (id == 1 && type != POINT && current.size > 0) {
                        parts.add(current.toArray());
                        current.size = 0;
                    }
                    current.add(x, y);
                }
            } else if (id == 7) {
                if (current.size > 0) {
                    current.add(current.data[0], current.data[1]);
                }
            } else {
                return null;
            }
        }
        if (current.size > 0) {
            parts.add(current.toArray());
        }
        return parts.toArray(new int[0][]);
    }

    static long zigzag(long raw) {
        return (raw >>> 1) ^ -(raw & 1);
    }

    /** One layer: its name, grid size and features. */
    public static final class Layer {
        public final String name;
        /** Width and height of the tile in geometry units, usually 4096. */
        public final int extent;
        public final List<Feature> features;
        final boolean complete;

        Layer(String name, int extent, List<Feature> features, boolean complete) {
            this.name = name;
            this.extent = extent;
            this.features = Collections.unmodifiableList(features);
            this.complete = complete;
        }
    }

    /** One feature: geometry type, properties and geometry parts in tile units. */
    public static final class Feature {
        /** {@link #POINT}, {@link #LINESTRING} or {@link #POLYGON}. */
        public final int type;
        private final String[] keys;
        private final Value[] values;
        /**
         * Points: one part holding every point. Lines: one part per line. Polygons: one
         * part per ring, each closed (its first point repeated at the end). Each part
         * holds x, y pairs.
         */
        public final int[][] parts;

        Feature(int type, String[] keys, Value[] values, int[][] parts) {
            this.type = type;
            this.keys = keys;
            this.values = values;
            this.parts = parts;
        }

        /** The property {@code key}, or null. */
        public Value get(String key) {
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].equals(key)) {
                    return values[i];
                }
            }
            return null;
        }

        /** The property {@code key} when it is a non-empty string, else null. */
        public String string(String key) {
            Value value = get(key);
            if (value != null && value.type == Value.STRING && !value.stringValue.isEmpty()) {
                return value.stringValue;
            }
            return null;
        }

        /** The property {@code key} as an integer (see {@link Value#intValue()}), or null. */
        public Long integer(String key) {
            Value value = get(key);
            return value == null ? null : value.intValue();
        }

        /** Number of properties. */
        public int propertyCount() {
            return keys.length;
        }

        /** Name of property {@code i}. */
        public String propertyKey(int i) {
            return keys[i];
        }

        /** Value of property {@code i}. */
        public Value propertyValue(int i) {
            return values[i];
        }
    }

    /** A property value: string, double, integer or boolean. */
    public static final class Value {
        public static final int STRING = 0;
        public static final int DOUBLE = 1;
        public static final int INT = 2;
        public static final int BOOL = 3;

        public final int type;
        final String stringValue;
        final double doubleValue;
        final long longValue;

        private Value(int type, String s, double d, long l) {
            this.type = type;
            this.stringValue = s;
            this.doubleValue = d;
            this.longValue = l;
        }

        public static Value ofString(String value) {
            return new Value(STRING, value, 0, 0);
        }

        public static Value ofDouble(double value) {
            return new Value(DOUBLE, null, value, 0);
        }

        public static Value ofInt(long value) {
            return new Value(INT, null, 0, value);
        }

        public static Value ofBool(boolean value) {
            return new Value(BOOL, null, 0, value ? 1 : 0);
        }

        /** The value as text, as a tag value: whole numbers print with no decimals. */
        public String text() {
            switch (type) {
                case STRING:
                    return stringValue;
                case DOUBLE:
                    if (doubleValue == Math.rint(doubleValue) && Math.abs(doubleValue) < 1e15) {
                        return Long.toString((long) doubleValue);
                    }
                    return Double.toString(doubleValue);
                case INT:
                    return Long.toString(longValue);
                default:
                    return longValue != 0 ? "true" : "false";
            }
        }

        /**
         * The value as an integer: doubles truncated when finite and below 1e15, booleans
         * as 0 or 1, strings when they parse as an integer; null otherwise.
         */
        public Long intValue() {
            switch (type) {
                case INT:
                    return longValue;
                case DOUBLE:
                    if (!Double.isInfinite(doubleValue) && !Double.isNaN(doubleValue) && Math.abs(doubleValue) < 1e15) {
                        return (long) doubleValue;
                    }
                    return null;
                case BOOL:
                    return longValue;
                default:
                    try {
                        return Long.parseLong(stringValue);
                    } catch (NumberFormatException e) {
                        return null;
                    }
            }
        }

        /** The string, or null for other types. */
        public String stringValue() {
            return type == STRING ? stringValue : null;
        }

        /** The number, or NaN for strings and booleans. */
        public double doubleValue() {
            return type == DOUBLE ? doubleValue : (type == INT ? longValue : Double.NaN);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Value)) {
                return false;
            }
            Value v = (Value) o;
            if (type != v.type) {
                return false;
            }
            switch (type) {
                case STRING:
                    return stringValue.equals(v.stringValue);
                case DOUBLE:
                    return Double.compare(doubleValue, v.doubleValue) == 0;
                default:
                    return longValue == v.longValue;
            }
        }

        @Override
        public int hashCode() {
            switch (type) {
                case STRING:
                    return stringValue.hashCode();
                case DOUBLE:
                    long bits = Double.doubleToLongBits(doubleValue);
                    return (int) (bits ^ (bits >>> 32)) * 31 + type;
                default:
                    return (int) (longValue ^ (longValue >>> 32)) * 31 + type;
            }
        }

        @Override
        public String toString() {
            return text();
        }
    }

    /** A growable int array of x, y pairs. */
    private static final class IntList {
        int[] data = new int[32];
        int size;

        void add(int x, int y) {
            if (size + 2 > data.length) {
                int[] grown = new int[data.length * 2];
                System.arraycopy(data, 0, grown, 0, size);
                data = grown;
            }
            data[size++] = x;
            data[size++] = y;
        }

        int[] toArray() {
            int[] out = new int[size];
            System.arraycopy(data, 0, out, 0, size);
            return out;
        }
    }

    /** A cursor over protobuf bytes. Errors set {@link #failed} and are checked by the caller. */
    static final class Reader {
        final byte[] data;
        int pos;
        final int end;
        boolean failed;

        Reader(byte[] data, int pos, int end) {
            this.data = data;
            this.pos = pos;
            this.end = end;
        }

        boolean hasMore() {
            return !failed && pos < end;
        }

        /** The next varint; 0 with {@link #failed} set when it is truncated or too long. */
        long varint() {
            long result = 0;
            int shift = 0;
            while (pos < end) {
                byte b = data[pos++];
                result |= (long) (b & 0x7F) << shift;
                if (b >= 0) {
                    return result;
                }
                shift += 7;
                if (shift > 63) {
                    break;
                }
            }
            failed = true;
            return 0;
        }

        /** A length-delimited field as a cursor of its own; null with {@link #failed} set if it overruns. */
        Reader message() {
            long length = varint();
            if (failed || length < 0 || length > end - pos) {
                failed = true;
                return null;
            }
            Reader sub = new Reader(data, pos, pos + (int) length);
            pos += (int) length;
            return sub;
        }

        String string() {
            long length = varint();
            if (failed || length < 0 || length > end - pos) {
                failed = true;
                return null;
            }
            String text = new String(data, pos, (int) length, UTF8);
            pos += (int) length;
            return text;
        }

        int fixed32() {
            if (end - pos < 4) {
                failed = true;
                pos = end;
                return 0;
            }
            int value = (data[pos] & 0xFF) | (data[pos + 1] & 0xFF) << 8 | (data[pos + 2] & 0xFF) << 16
                    | (data[pos + 3] & 0xFF) << 24;
            pos += 4;
            return value;
        }

        long fixed64() {
            if (end - pos < 8) {
                failed = true;
                pos = end;
                return 0;
            }
            long value = 0;
            for (int i = 0; i < 8; i++) {
                value |= (long) (data[pos + i] & 0xFF) << (8 * i);
            }
            pos += 8;
            return value;
        }

        boolean skip(int wire) {
            switch (wire) {
                case 0:
                    varint();
                    break;
                case 1:
                    fixed64();
                    break;
                case 2:
                    message();
                    break;
                case 5:
                    fixed32();
                    break;
                default:
                    failed = true;
            }
            return !failed;
        }
    }
}
