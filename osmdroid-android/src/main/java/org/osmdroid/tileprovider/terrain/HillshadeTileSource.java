package org.osmdroid.tileprovider.terrain;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import org.osmdroid.tileprovider.tilesource.BitmapTileSourceBase;
import org.osmdroid.util.MapTileIndex;

import java.io.InputStream;

/**
 * Tile source that decodes Terrain-RGB tiles into hillshade tiles.
 * <p>
 * It is a {@link BitmapTileSourceBase} so that the tile provider machinery gets a
 * name, ordinal, zoom range and tile size for caching. The plain
 * {@link #getDrawable(InputStream)} is not overridden and would return the raw
 * colour-coded tile; hillshading needs the tile index (for ground resolution), so
 * {@link HillshadeTileProvider} calls {@link #shade(InputStream, long)} instead.
 * </p>
 *
 * @since 7.0.3-lostrat
 */
public class HillshadeTileSource extends BitmapTileSourceBase {

    private final TerrainRgbEncoding mEncoding;
    private volatile double mAzimuth = Hillshader.DEFAULT_AZIMUTH;
    private volatile double mAltitude = Hillshader.DEFAULT_ALTITUDE;
    private volatile int mMaxAlpha = Hillshader.DEFAULT_MAX_ALPHA;
    private volatile double mExaggeration = Hillshader.DEFAULT_EXAGGERATION;

    /**
     * @param aName           cache-safe name, unique per terrain file
     * @param aZoomMinLevel   lowest zoom present in the file
     * @param aZoomMaxLevel   highest zoom present in the file; higher zooms are approximated
     * @param aTileSizePixels tile edge in pixels (Terrain-RGB files are often 512)
     * @param encoding        pixel to metres mapping
     */
    public HillshadeTileSource(final String aName, final int aZoomMinLevel, final int aZoomMaxLevel,
                               final int aTileSizePixels, final TerrainRgbEncoding encoding) {
        super(aName, aZoomMinLevel, aZoomMaxLevel, aTileSizePixels, ".webp");
        mEncoding = encoding;
    }

    public TerrainRgbEncoding getEncoding() {
        return mEncoding;
    }

    public double getAzimuth() {
        return mAzimuth;
    }

    public double getAltitude() {
        return mAltitude;
    }

    /** Sun position. Callers must clear the tile cache afterwards for it to take effect. */
    public void setSun(final double azimuthDeg, final double altitudeDeg) {
        mAzimuth = azimuthDeg;
        mAltitude = altitudeDeg;
    }

    public int getMaxAlpha() {
        return mMaxAlpha;
    }

    /** Opacity of a fully shadowed pixel, 0..255. Callers must clear the tile cache afterwards. */
    public void setMaxAlpha(final int maxAlpha) {
        mMaxAlpha = Math.max(0, Math.min(255, maxAlpha));
    }

    public double getExaggeration() {
        return mExaggeration;
    }

    /**
     * Vertical exaggeration: every slope is multiplied by this before lighting. 1 is true relief;
     * values above 1 bring out gentle terrain. Non-positive values are ignored.
     * Callers must clear the tile cache afterwards.
     */
    public void setExaggeration(final double exaggeration) {
        if (exaggeration > 0) {
            mExaggeration = exaggeration;
        }
    }

    /**
     * Decode one Terrain-RGB tile and return its hillshade.
     *
     * @param in            encoded tile bytes (PNG or WebP); not closed here
     * @param pMapTileIndex the tile's index, used for zoom and latitude
     * @return the hillshade, or null when the bytes do not decode
     */
    @SuppressWarnings("deprecation")
    public Drawable shade(final InputStream in, final long pMapTileIndex) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        // The RGB triplet is data, not colour: never let the decoder drop to RGB_565 or rescale.
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inScaled = false;
        options.inDither = false;
        final Bitmap rgb = BitmapFactory.decodeStream(in, null, options);
        if (rgb == null) {
            return null;
        }
        try {
            return new BitmapDrawable(shade(rgb, pMapTileIndex));
        } finally {
            rgb.recycle();
        }
    }

    /**
     * @param rgb           decoded Terrain-RGB tile
     * @param pMapTileIndex the tile's index, used for zoom and latitude
     * @return a new hillshade bitmap of the same size
     */
    public Bitmap shade(final Bitmap rgb, final long pMapTileIndex) {
        final int width = rgb.getWidth();
        final int height = rgb.getHeight();
        final int[] pixels = new int[width * height];
        rgb.getPixels(pixels, 0, width, 0, 0, width, height);
        final float[] elevation = new float[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            elevation[i] = mEncoding.elevation(pixels[i]);
        }
        final double metresPerPixel = Hillshader.metresPerPixel(
                MapTileIndex.getZoom(pMapTileIndex), MapTileIndex.getY(pMapTileIndex), width);
        return Hillshader.shade(elevation, width, height, metresPerPixel, mAzimuth, mAltitude, mMaxAlpha, mExaggeration);
    }

    /**
     * Elevation at a pixel of a decoded Terrain-RGB tile. Handy for point lookups.
     */
    public float elevationAt(final Bitmap rgb, final int px, final int py) {
        return mEncoding.elevation(rgb.getPixel(px, py));
    }
}
