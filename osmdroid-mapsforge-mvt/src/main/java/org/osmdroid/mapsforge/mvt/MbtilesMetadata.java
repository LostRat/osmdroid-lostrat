package org.osmdroid.mapsforge.mvt;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.util.MercatorProjection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * The {@code metadata} name/value rows of an MBTiles file, parsed the same way on every
 * platform, plus the helpers both archive implementations share: the TMS row flip and
 * gzip/zlib unpacking of tile data.
 *
 * @since 7.0.3-lostrat
 */
public final class MbtilesMetadata {

    /** More than any real tile unpacks to; a tile claiming more is refused. */
    public static final int MAX_INFLATED_SIZE = 64 << 20;

    private final Map<String, String> values;
    private final int minZoom;
    private final int maxZoom;
    private final BoundingBox boundingBox;
    private final boolean hasBounds;
    private final LatLong center;
    private final int centerZoom;
    private final boolean tms;
    private final List<String> vectorLayerIds;

    /**
     * @param rows the metadata table as name to value; null is treated as empty
     */
    public MbtilesMetadata(Map<String, String> rows) {
        values = rows == null ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(rows));
        minZoom = zoom(values.get("minzoom"), 0);
        maxZoom = Math.max(minZoom, zoom(values.get("maxzoom"), 14));

        double[] bounds = numbers(values.get("bounds"));
        BoundingBox box = bounds.length == 4 ? box(bounds[1], bounds[0], bounds[3], bounds[2]) : null;
        hasBounds = box != null;
        boundingBox = box != null ? box : world();

        double[] middle = numbers(values.get("center"));
        LatLong c = null;
        if (middle.length >= 2) {
            try {
                c = new LatLong(clamp(middle[1], -MercatorProjection.LATITUDE_MAX, MercatorProjection.LATITUDE_MAX),
                        clamp(middle[0], -180, 180));
            } catch (IllegalArgumentException e) {
                c = null;
            }
        }
        center = c;
        centerZoom = middle.length >= 3 && !Double.isNaN(middle[2]) ? (int) middle[2] : -1;

        String scheme = values.get("scheme");
        tms = scheme == null || !"xyz".equals(scheme.trim().toLowerCase(Locale.ROOT));
        vectorLayerIds = Collections.unmodifiableList(vectorLayerIds(values.get("json")));
    }

    /** All metadata rows, unmodifiable. */
    public Map<String, String> getValues() {
        return values;
    }

    /** {@code minzoom}, 0 when absent. */
    public int getMinZoom() {
        return minZoom;
    }

    /** {@code maxzoom}, 14 when absent; never below the min zoom. */
    public int getMaxZoom() {
        return maxZoom;
    }

    /** {@code bounds}, or the whole Mercator world when absent or unreadable. */
    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    /** True when {@code bounds} was present and valid. */
    public boolean hasBounds() {
        return hasBounds;
    }

    /** Position from {@code center}, null when absent. */
    public LatLong getCenter() {
        return center;
    }

    /** Zoom from the third value of {@code center}, -1 when absent. */
    public int getCenterZoom() {
        return centerZoom;
    }

    /** True unless {@code scheme=xyz}: MBTiles rows count from the south by default. */
    public boolean isTms() {
        return tms;
    }

    /** True when {@code format=pbf}. */
    public boolean isVector() {
        String format = values.get("format");
        return format != null && "pbf".equals(format.trim().toLowerCase(Locale.ROOT));
    }

    /** Ids of {@code vector_layers} in the {@code json} row; empty if absent. Unmodifiable. */
    public List<String> getVectorLayerIds() {
        return vectorLayerIds;
    }

    /** The {@code tile_row} that stores the XYZ row {@code y} under this file's scheme. */
    public int storedRow(int zoom, int y) {
        return tms ? flipRow(zoom, y) : y;
    }

    /** Converts between XYZ and TMS row numbering; the flip is its own inverse. */
    public static int flipRow(int zoom, int row) {
        return (1 << zoom) - 1 - row;
    }

    /** True for data starting like gzip ({@code 1f 8b}) or zlib ({@code 78} with valid RFC 1950 check bits), as nearly every writer stores tiles. */
    public static boolean isCompressed(byte[] data) {
        if (data == null || data.length <= 2) {
            return false;
        }
        int b0 = data[0] & 0xFF;
        int b1 = data[1] & 0xFF;
        return (b0 == 0x1F && b1 == 0x8B)
                || (b0 == 0x78 && ((b0 << 8) | b1) % 31 == 0);
    }

    /** Unpacks gzip or zlib wrapped data; returns anything else unchanged. Null stays null. */
    public static byte[] unpack(byte[] data) throws IOException {
        if (data == null || !isCompressed(data)) {
            return data;
        }
        return inflate(data);
    }

    /**
     * Unpacks gzip or zlib data, refusing output over {@link #MAX_INFLATED_SIZE}.
     *
     * @throws IOException on corrupt or truncated input or when the output is too large
     */
    public static byte[] inflate(byte[] data) throws IOException {
        InputStream in = data[0] == (byte) 0x1F
                ? new GZIPInputStream(new ByteArrayInputStream(data), 8192)
                : new InflaterInputStream(new ByteArrayInputStream(data));
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(data.length * 4, 16 << 10));
            byte[] buffer = new byte[16 << 10];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_INFLATED_SIZE) {
                    throw new IOException("tile unpacks to more than " + MAX_INFLATED_SIZE + " bytes");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    /** The whole area Web Mercator can show. */
    static BoundingBox world() {
        return new BoundingBox(-MercatorProjection.LATITUDE_MAX, -180, MercatorProjection.LATITUDE_MAX, 180);
    }

    private static BoundingBox box(double minLat, double minLon, double maxLat, double maxLon) {
        double lim = MercatorProjection.LATITUDE_MAX;
        minLat = clamp(minLat, -lim, lim);
        maxLat = clamp(maxLat, -lim, lim);
        minLon = clamp(minLon, -180, 180);
        maxLon = clamp(maxLon, -180, 180);
        if (Double.isNaN(minLat) || Double.isNaN(maxLat) || Double.isNaN(minLon) || Double.isNaN(maxLon)
                || minLat > maxLat || minLon > maxLon) {
            return null;
        }
        try {
            return new BoundingBox(minLat, minLon, maxLat, maxLon);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static int zoom(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            double value = Double.parseDouble(text.trim());
            if (Double.isNaN(value) || value < 0 || value > 30) {
                return fallback;
            }
            return (int) value;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Comma separated numbers; any that do not parse are left out. */
    private static double[] numbers(String text) {
        if (text == null) {
            return new double[0];
        }
        String[] parts = text.split(",");
        double[] result = new double[parts.length];
        int n = 0;
        for (String part : parts) {
            try {
                result[n] = Double.parseDouble(part.trim());
                n++;
            } catch (NumberFormatException ignored) {
                // left out
            }
        }
        double[] trimmed = new double[n];
        System.arraycopy(result, 0, trimmed, 0, n);
        return trimmed;
    }

    /**
     * The {@code id} strings of the objects in the top-level {@code vector_layers} array.
     * A small JSON reader, since the JVM has no {@code org.json}; anything malformed gives
     * the ids read so far.
     */
    static List<String> vectorLayerIds(String json) {
        List<String> ids = new ArrayList<>();
        if (json == null) {
            return ids;
        }
        try {
            JsonScanner scanner = new JsonScanner(json);
            scanner.skipSpace();
            if (!scanner.consume('{')) {
                return ids;
            }
            if (scanner.consumeAfterSpace('}')) {
                return ids;
            }
            do {
                String key = scanner.string();
                scanner.expect(':');
                if ("vector_layers".equals(key)) {
                    scanner.skipSpace();
                    if (!scanner.consume('[')) {
                        scanner.skipValue();
                        continue;
                    }
                    if (scanner.consumeAfterSpace(']')) {
                        continue;
                    }
                    do {
                        scanner.skipSpace();
                        if (scanner.peek() == '{') {
                            String id = scanner.objectStringField("id");
                            if (id != null) {
                                ids.add(id);
                            }
                        } else {
                            scanner.skipValue();
                        }
                    } while (scanner.consumeAfterSpace(','));
                    scanner.expect(']');
                } else {
                    scanner.skipValue();
                }
            } while (scanner.consumeAfterSpace(','));
        } catch (IllegalArgumentException malformed) {
            // the ids read before the error are kept
        }
        return ids;
    }

    /** Just enough of a JSON reader to walk past values and read strings. */
    private static final class JsonScanner {
        private final String text;
        private int pos;

        JsonScanner(String text) {
            this.text = text;
        }

        char peek() {
            if (pos >= text.length()) {
                throw new IllegalArgumentException("end of json");
            }
            return text.charAt(pos);
        }

        void skipSpace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        boolean consume(char c) {
            if (pos < text.length() && text.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        boolean consumeAfterSpace(char c) {
            skipSpace();
            return consume(c);
        }

        void expect(char c) {
            if (!consumeAfterSpace(c)) {
                throw new IllegalArgumentException("expected " + c + " at " + pos);
            }
        }

        String string() {
            skipSpace();
            if (!consume('"')) {
                throw new IllegalArgumentException("expected string at " + pos);
            }
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = peek();
                pos++;
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char e = peek();
                pos++;
                switch (e) {
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'u':
                        if (pos + 4 > text.length()) {
                            throw new IllegalArgumentException("bad escape");
                        }
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default: out.append(e); break;
                }
            }
        }

        /** Reads an object and returns its top-level string member {@code name}, or null. */
        String objectStringField(String name) {
            expect('{');
            String found = null;
            if (consumeAfterSpace('}')) {
                return null;
            }
            do {
                String key = string();
                expect(':');
                skipSpace();
                if (name.equals(key) && peek() == '"') {
                    found = string();
                } else {
                    skipValue();
                }
            } while (consumeAfterSpace(','));
            expect('}');
            return found;
        }

        void skipValue() {
            skipSpace();
            char c = peek();
            if (c == '"') {
                string();
            } else if (c == '{' || c == '[') {
                char close = c == '{' ? '}' : ']';
                pos++;
                if (consumeAfterSpace(close)) {
                    return;
                }
                do {
                    if (c == '{') {
                        string();
                        expect(':');
                    }
                    skipValue();
                } while (consumeAfterSpace(','));
                expect(close);
            } else {
                // number, true, false, null
                int start = pos;
                while (pos < text.length() && ",}] \t\r\n".indexOf(text.charAt(pos)) < 0) {
                    pos++;
                }
                if (pos == start) {
                    throw new IllegalArgumentException("expected value at " + pos);
                }
            }
        }
    }
}
