package org.osmdroid.tileprovider.terrain;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.Log;

import org.osmdroid.api.IMapView;
import org.osmdroid.tileprovider.MapTileProviderArray;
import org.osmdroid.tileprovider.modules.MBTilesFileArchive;
import org.osmdroid.tileprovider.modules.MapTileApproximater;
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.TilesOverlay;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * A hillshade layer driven by one Terrain-RGB MBTiles file at a time.
 * <p>
 * Add it to the map once, after the base tiles, then drive it with two calls:
 * </p>
 * <pre>
 * HillshadeOverlay hill = new HillshadeOverlay(mapView);
 * mapView.getOverlays().add(hill);
 * hill.setFile(new File(".../uk-terrain-rgb-webp.mbtiles"));   // opens the file, reads metadata
 * hill.setEnabled(true);                                        // on / off, tiles stay cached
 * </pre>
 * <p>
 * The overlay owns its archive and tile provider, so it is independent of the map's
 * base tile source: it draws over online tiles, MBTiles, Mapsforge, or nothing at all.
 * Zoom range, bounds, tile size and pixel encoding are read from the file. Outside the
 * file's bounds nothing is requested or drawn. Above the file's maximum zoom the last
 * native level is scaled up.
 * </p>
 * <p>
 * Passive: the overlay registers nothing with the system and reacts only to the app's
 * calls, so {@link #setFile(File)} may be called any number of times on one overlay.
 * </p>
 *
 * @since 7.0.3-lostrat
 */
public class HillshadeOverlay extends Overlay {

    private static final String TAG = "HillshadeOverlay";

    private final MapView mMapView;

    private File mFile;
    private MBTilesFileArchive mArchive;
    private HillshadeTileSource mTileSource;
    private MapTileProviderArray mProvider;
    private TilesOverlay mTiles;
    private BoundingBox mBounds;

    private TerrainRgbEncoding mEncodingOverride;
    private double mAzimuth = Hillshader.DEFAULT_AZIMUTH;
    private double mAltitude = Hillshader.DEFAULT_ALTITUDE;
    private int mMaxAlpha = Hillshader.DEFAULT_MAX_ALPHA;
    private double mExaggeration = Hillshader.DEFAULT_EXAGGERATION;

    public HillshadeOverlay(final MapView pMapView) {
        mMapView = pMapView;
        setEnabled(false);
    }

    // ===========================================================
    // File
    // ===========================================================

    /**
     * Load a Terrain-RGB MBTiles file, replacing any previous one. Null unloads.
     *
     * @return true when the file opened and its metadata was read
     */
    public boolean setFile(final File pFile) {
        unload();
        if (pFile == null) {
            mMapView.invalidate();
            return false;
        }
        final Map<String, String> meta = readMetadata(pFile);
        if (meta == null) {
            return false;
        }

        TerrainRgbEncoding encoding = mEncodingOverride;
        if (encoding == null) {
            encoding = TerrainRgbEncoding.fromDescription(meta.get("description"));
        }
        if (encoding == null) {
            encoding = TerrainRgbEncoding.MAPBOX;
            Log.i(IMapView.LOGTAG, TAG + ": no encoding in metadata, assuming " + encoding);
        }
        final int minZoom = parseInt(meta.get("minzoom"), 0);
        final int maxZoom = parseInt(meta.get("maxzoom"), 12);
        final int tileSize = parseInt(meta.get("tilesize"), 256);
        mBounds = parseBounds(meta.get("bounds"));

        try {
            mArchive = MBTilesFileArchive.getDatabaseFileArchive(pFile);
        } catch (final Exception e) {
            Log.e(IMapView.LOGTAG, TAG + ": cannot open " + pFile, e);
            mArchive = null;
            return false;
        }

        mTileSource = new HillshadeTileSource("hillshade-" + pFile.getName(), minZoom, maxZoom, tileSize, encoding);
        mTileSource.setSun(mAzimuth, mAltitude);
        mTileSource.setMaxAlpha(mMaxAlpha);
        mTileSource.setExaggeration(mExaggeration);

        final HillshadeTileProvider native_ = new HillshadeTileProvider(mTileSource, mArchive);
        final MapTileApproximater overzoom = new MapTileApproximater();
        overzoom.addProvider(native_);
        mProvider = new MapTileProviderArray(mTileSource, null,   // no receiver to destroy on detach
                new MapTileModuleProviderBase[]{native_, overzoom});
        // Redraw the map when a tile finishes loading, as the base tiles do; without this the
        // tiles sit in the cache until something else invalidates the map.
        mProvider.setTileRequestCompleteHandler(mMapView.getTileRequestCompleteHandler());

        mTiles = new TilesOverlay(mProvider, mMapView.getContext());
        mTiles.setLoadingBackgroundColor(Color.TRANSPARENT);
        mTiles.setLoadingLineColor(Color.TRANSPARENT);

        mFile = pFile;
        Log.i(IMapView.LOGTAG, TAG + ": loaded " + pFile.getName() + " z" + minZoom + "-" + maxZoom
                + " " + tileSize + "px " + encoding + " bounds " + mBounds);
        mMapView.invalidate();
        return true;
    }

    /** Close the current file, if any. The overlay stays in the map and can take a new file. */
    public void unload() {
        if (mTiles != null) {
            mTiles.onDetach(mMapView);   // detaches mProvider and its module providers
            mTiles = null;
        }
        mProvider = null;
        mTileSource = null;
        if (mArchive != null) {
            mArchive.close();
            mArchive = null;
        }
        mFile = null;
        mBounds = null;
    }

    public File getFile() {
        return mFile;
    }

    public boolean hasFile() {
        return mTiles != null;
    }

    /** Geographic extent from the file's metadata, or null when it had none. */
    public BoundingBox getBounds() {
        return mBounds;
    }

    public HillshadeTileSource getTileSource() {
        return mTileSource;
    }

    // ===========================================================
    // Appearance
    // ===========================================================

    /**
     * Force a pixel encoding instead of reading it from the file's description.
     * Takes effect on the next {@link #setFile(File)}.
     */
    public void setEncoding(final TerrainRgbEncoding pEncoding) {
        mEncodingOverride = pEncoding;
    }

    /** Opacity of a fully shadowed pixel, 0..255. Re-shades cached tiles. */
    public void setMaxAlpha(final int pMaxAlpha) {
        mMaxAlpha = pMaxAlpha;
        if (mTileSource != null) {
            mTileSource.setMaxAlpha(pMaxAlpha);
            reshade();
        }
    }

    public int getMaxAlpha() {
        return mMaxAlpha;
    }

    /** Sun position in degrees: azimuth clockwise from north, altitude above the horizon. Re-shades cached tiles. */
    public void setSun(final double pAzimuthDeg, final double pAltitudeDeg) {
        mAzimuth = pAzimuthDeg;
        mAltitude = pAltitudeDeg;
        if (mTileSource != null) {
            mTileSource.setSun(pAzimuthDeg, pAltitudeDeg);
            reshade();
        }
    }

    /**
     * Vertical exaggeration, 1 is true relief. Values above 1 bring out gentle terrain, which is
     * often more useful than opacity in flat country. Re-shades cached tiles.
     */
    public void setExaggeration(final double pExaggeration) {
        if (pExaggeration <= 0) {
            return;
        }
        mExaggeration = pExaggeration;
        if (mTileSource != null) {
            mTileSource.setExaggeration(pExaggeration);
            reshade();
        }
    }

    public double getExaggeration() {
        return mExaggeration;
    }

    private void reshade() {
        if (mProvider != null) {
            mProvider.clearTileCache();
        }
        mMapView.invalidate();
    }

    // ===========================================================
    // Overlay
    // ===========================================================

    @Override
    public void draw(final Canvas pCanvas, final Projection pProjection) {
        if (mTiles == null) {
            return;
        }
        if (mBounds != null && !mBounds.overlaps(pProjection.getBoundingBox(), pProjection.getZoomLevel())) {
            return;
        }
        mTiles.draw(pCanvas, pProjection);
    }

    @Override
    public void onPause() {
        if (mTiles != null) {
            mTiles.onPause();
        }
    }

    @Override
    public void onResume() {
        if (mTiles != null) {
            mTiles.onResume();
        }
    }

    @Override
    public void onDetach(final MapView pMapView) {
        unload();
        super.onDetach(pMapView);
    }

    // ===========================================================
    // MBTiles metadata
    // ===========================================================

    /**
     * Read the {@code metadata} table plus two derived keys: {@code tilesize} (pixel width
     * of one decoded tile) and, when the table lacks them, {@code minzoom} / {@code maxzoom}
     * from the {@code tiles} table.
     *
     * @return the key/value map, or null when the file could not be opened
     */
    public static Map<String, String> readMetadata(final File pFile) {
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(pFile.getAbsolutePath(), null,
                    SQLiteDatabase.NO_LOCALIZED_COLLATORS | SQLiteDatabase.OPEN_READONLY);
            final Map<String, String> meta = new HashMap<>();
            Cursor cur = db.query("metadata", new String[]{"name", "value"}, null, null, null, null, null);
            try {
                while (cur.moveToNext()) {
                    meta.put(cur.getString(0), cur.getString(1));
                }
            } finally {
                cur.close();
            }
            if (!meta.containsKey("minzoom") || !meta.containsKey("maxzoom")) {
                cur = db.rawQuery("select min(zoom_level), max(zoom_level) from tiles", null);
                try {
                    if (cur.moveToFirst()) {
                        meta.put("minzoom", cur.getString(0));
                        meta.put("maxzoom", cur.getString(1));
                    }
                } finally {
                    cur.close();
                }
            }
            cur = db.rawQuery("select tile_data from tiles limit 1", null);
            try {
                if (cur.moveToFirst()) {
                    final byte[] blob = cur.getBlob(0);
                    final BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(blob, 0, blob.length, o);
                    if (o.outWidth > 0) {
                        meta.put("tilesize", Integer.toString(o.outWidth));
                    }
                }
            } finally {
                cur.close();
            }
            return meta;
        } catch (final Exception e) {
            Log.e(IMapView.LOGTAG, TAG + ": cannot read metadata of " + pFile, e);
            return null;
        } finally {
            if (db != null) {
                db.close();
            }
        }
    }

    private static int parseInt(final String s, final int fallback) {
        if (s == null) {
            return fallback;
        }
        try {
            return (int) Double.parseDouble(s.trim());
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }

    /** MBTiles bounds are "west,south,east,north". */
    private static BoundingBox parseBounds(final String s) {
        if (s == null) {
            return null;
        }
        final String[] p = s.split(",");
        if (p.length != 4) {
            return null;
        }
        try {
            final double west = Double.parseDouble(p[0].trim());
            final double south = Double.parseDouble(p[1].trim());
            final double east = Double.parseDouble(p[2].trim());
            final double north = Double.parseDouble(p[3].trim());
            return new BoundingBox(north, east, south, west);
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
