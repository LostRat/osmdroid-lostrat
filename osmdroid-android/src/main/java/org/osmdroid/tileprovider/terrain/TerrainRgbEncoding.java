package org.osmdroid.tileprovider.terrain;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How a Terrain-RGB pixel maps to an elevation in metres:
 * <pre>
 * elevation = base + (R * 65536 + G * 256 + B) * interval
 * </pre>
 * Mapbox Terrain-RGB uses base -10000 and interval 0.1. Terrarium (Mapzen / AWS) is
 * base -32768 and interval 1/256, which is the same formula written differently.
 * Files built with {@code rio rgbify -b <base> -i <interval>} can use any pair; the
 * lostrat build scripts record the pair in the MBTiles {@code description} metadata
 * as {@code "base -10000 interval 1"}, which {@link #fromDescription(String)} parses.
 *
 * @since 7.0.3-lostrat
 */
public final class TerrainRgbEncoding {

    /** Mapbox Terrain-RGB: base -10000, interval 0.1 m. */
    public static final TerrainRgbEncoding MAPBOX = new TerrainRgbEncoding(-10000.0, 0.1);

    /** Terrarium (Mapzen / AWS Terrain Tiles): base -32768, interval 1/256 m. */
    public static final TerrainRgbEncoding TERRARIUM = new TerrainRgbEncoding(-32768.0, 1.0 / 256.0);

    private static final Pattern DESCRIPTION = Pattern.compile(
            "base\\s+(-?[0-9]+(?:\\.[0-9]+)?)\\s+interval\\s+([0-9]+(?:\\.[0-9]+)?)",
            Pattern.CASE_INSENSITIVE);

    public final double base;
    public final double interval;

    public TerrainRgbEncoding(final double base, final double interval) {
        this.base = base;
        this.interval = interval;
    }

    /**
     * @param argb an opaque ARGB_8888 pixel
     * @return elevation in metres
     */
    public float elevation(final int argb) {
        final int value = argb & 0x00FFFFFF; // R << 16 | G << 8 | B
        return (float) (base + value * interval);
    }

    /**
     * Parse {@code "... base <b> interval <i> ..."} from an MBTiles description.
     *
     * @return the encoding, or null when the description does not carry one
     */
    public static TerrainRgbEncoding fromDescription(final String description) {
        if (description == null) {
            return null;
        }
        final Matcher m = DESCRIPTION.matcher(description);
        if (!m.find()) {
            return null;
        }
        try {
            return new TerrainRgbEncoding(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)));
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "TerrainRgbEncoding(base=%s, interval=%s)", base, interval);
    }
}
