package org.osmdroid.tileprovider.terrain;

import android.graphics.Bitmap;

/**
 * Turns an elevation grid into a translucent hillshade bitmap.
 * <p>
 * Slope and aspect come from Horn's 3x3 kernel. The result is transparent on level
 * ground, darkens slopes facing away from the sun and lightly brightens slopes facing
 * it, so it can be drawn over any base map, including a plain white one.
 * </p>
 * <p>
 * Edges are handled by clamping, which leaves a one pixel seam at tile borders.
 * {@link HillshadeTileProvider} is the place to pad the grid with neighbour tiles.
 * </p>
 *
 * @since 7.0.3-lostrat
 */
public final class Hillshader {

    /** Sun direction in degrees clockwise from north. 315 is the cartographic convention (north-west). */
    public static final double DEFAULT_AZIMUTH = 315.0;

    /** Sun elevation above the horizon in degrees. */
    public static final double DEFAULT_ALTITUDE = 45.0;

    /** Default opacity of a fully shadowed pixel, 0..255. */
    public static final int DEFAULT_MAX_ALPHA = 110;

    /** Default vertical exaggeration: slopes are used as measured. */
    public static final double DEFAULT_EXAGGERATION = 1.0;

    /** Highlights on sun-facing slopes use this fraction of the shadow opacity. */
    private static final double HIGHLIGHT_FACTOR = 0.5;

    private static final double EARTH_CIRCUMFERENCE = 2.0 * Math.PI * 6378137.0;

    private Hillshader() {
    }

    /**
     * Ground distance covered by one pixel of a Web Mercator tile, measured at the tile's centre latitude.
     *
     * @param zoom     tile zoom level
     * @param tileY    tile row (XYZ, row 0 at the north)
     * @param tileSize tile edge in pixels
     * @return metres per pixel
     */
    public static double metresPerPixel(final int zoom, final int tileY, final int tileSize) {
        final double n = Math.pow(2, zoom);
        final double latRad = Math.atan(Math.sinh(Math.PI * (1 - 2 * (tileY + 0.5) / n)));
        return EARTH_CIRCUMFERENCE * Math.cos(latRad) / (tileSize * n);
    }

    /**
     * @param elevation      row-major grid of {@code width * height} elevations in metres, row 0 at the north
     * @param width          grid width in pixels
     * @param height         grid height in pixels
     * @param metresPerPixel ground distance per grid cell, see {@link #metresPerPixel(int, int, int)}
     * @param azimuthDeg     sun direction, degrees clockwise from north
     * @param altitudeDeg    sun elevation above the horizon, degrees
     * @param maxAlpha       opacity of a fully shadowed pixel, 0..255
     * @return a new ARGB_8888 bitmap of the same size
     */
    public static Bitmap shade(final float[] elevation, final int width, final int height,
                               final double metresPerPixel,
                               final double azimuthDeg, final double altitudeDeg, final int maxAlpha) {
        return shade(elevation, width, height, metresPerPixel, azimuthDeg, altitudeDeg, maxAlpha, DEFAULT_EXAGGERATION);
    }

    /**
     * @param elevation      row-major grid of {@code width * height} elevations in metres, row 0 at the north
     * @param width          grid width in pixels
     * @param height         grid height in pixels
     * @param metresPerPixel ground distance per grid cell, see {@link #metresPerPixel(int, int, int)}
     * @param azimuthDeg     sun direction, degrees clockwise from north
     * @param altitudeDeg    sun elevation above the horizon, degrees
     * @param maxAlpha       opacity of a fully shadowed pixel, 0..255
     * @param exaggeration   vertical exaggeration; slopes are multiplied by this before lighting.
     *                       1 is true relief, 2 doubles every gradient (MapLibre hillshade-exaggeration)
     * @return a new ARGB_8888 bitmap of the same size
     */
    public static Bitmap shade(final float[] elevation, final int width, final int height,
                               final double metresPerPixel,
                               final double azimuthDeg, final double altitudeDeg, final int maxAlpha,
                               final double exaggeration) {
        final double az = Math.toRadians(azimuthDeg);
        final double alt = Math.toRadians(altitudeDeg);
        // Unit vector towards the sun: x east, y north, z up.
        final double lx = Math.sin(az) * Math.cos(alt);
        final double ly = Math.cos(az) * Math.cos(alt);
        final double lz = Math.sin(alt);
        // Level ground has normal (0, 0, 1), so its illumination is lz. Anything darker is shadow.
        final double flat = lz;
        // Dividing the cell size by the exaggeration multiplies every gradient by it.
        final double cell8 = 8.0 * metresPerPixel / exaggeration;

        final int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            final int y0 = Math.max(y - 1, 0) * width;
            final int y1 = y * width;
            final int y2 = Math.min(y + 1, height - 1) * width;
            for (int x = 0; x < width; x++) {
                final int x0 = Math.max(x - 1, 0);
                final int x2 = Math.min(x + 1, width - 1);
                // Horn kernel, a..i left to right, top to bottom.
                final float a = elevation[y0 + x0], b = elevation[y0 + x], c = elevation[y0 + x2];
                final float d = elevation[y1 + x0], f = elevation[y1 + x2];
                final float g = elevation[y2 + x0], h = elevation[y2 + x], i = elevation[y2 + x2];
                final double dzdx = ((c + 2 * f + i) - (a + 2 * d + g)) / cell8;   // rises to the east
                final double dzdn = ((a + 2 * b + c) - (g + 2 * h + i)) / cell8;   // rises to the north
                final double nx = -dzdx;
                final double ny = -dzdn;
                final double len = Math.sqrt(nx * nx + ny * ny + 1.0);
                double dot = (nx * lx + ny * ly + lz) / len;
                if (dot < 0) {
                    dot = 0;
                }
                final int argb;
                if (dot < flat) {
                    final int alpha = (int) ((flat - dot) / flat * maxAlpha);
                    argb = alpha << 24;                       // black, variable alpha
                } else {
                    final int alpha = (int) ((dot - flat) / (1.0 - flat) * maxAlpha * HIGHLIGHT_FACTOR);
                    argb = (alpha << 24) | 0x00FFFFFF;        // white, variable alpha
                }
                pixels[y1 + x] = argb;
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }
}
