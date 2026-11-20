package org.osmdroid.mapsforge;

import android.app.Application;
import android.content.res.AssetManager;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import org.mapsforge.core.mapelements.MapElementContainer;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.graphics.AndroidTileBitmap;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.datastore.MapReadResult;
import org.mapsforge.map.datastore.MultiMapDataStore;
import org.mapsforge.map.layer.hills.HillsRenderConfig;
import org.mapsforge.map.layer.renderer.DirectRenderer;
import org.mapsforge.map.layer.renderer.RendererJob;
import org.mapsforge.map.layer.renderer.StandardRenderer;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.RenderContext;
import org.mapsforge.map.rendertheme.XmlRenderTheme;
import org.mapsforge.map.rendertheme.StreamRenderTheme;
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture;
import org.mapsforge.map.layer.labels.MapDataStoreLabelStore;
import org.osmdroid.api.IMapView;
import org.osmdroid.mapsforge.mvt.MvtDirectRenderer;
import org.osmdroid.mapsforge.mvt.MvtMapDataStore;
import org.osmdroid.mapsforge.mvt.MvtStandardRenderer;
import org.osmdroid.mapsforge.mvt.android.AndroidMbtilesArchive;
import org.osmdroid.tileprovider.tilesource.BitmapTileSourceBase;
import org.osmdroid.util.DisplayDensityManager;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapted from code from here: https://github.com/MKergall/osmbonuspack, which is LGPL
 * Updated for Mapsforge 0.30.0
 */
public class MapsForgeTileSource extends BitmapTileSourceBase {

    // Reasonable defaults ..
    public static int MIN_ZOOM = 3;
    public static int MAX_ZOOM = 29;
    public static final int TILE_SIZE_PIXELS = 256;

    // Store application context's AssetManager for loading assets-based themes
    private static AssetManager sAssetManager;
    private static android.content.res.Resources sResources;

    // Default theme settings - can be overridden
    private static String sDefaultThemeDir = "renderthemes";
    private static String sDefaultThemeFile = "rendertheme-v4.xml";

    private final DisplayModel model = new DisplayModel();
    private final float scale = DisplayModel.getDefaultUserScaleFactor();

    // Mapsforge widens lines 1.5x per zoom above 12 but draws text one size at every zoom.
    // Label text grows by sTextScaleGrowth per zoom above sTextScaleStartZoom; 1 = stock.
    private static final float TEXT_SCALE_MAX = 2.5f;
    private static float sTextScaleGrowth = 1f;
    private static int sTextScaleStartZoom = 15;
    // Snapshot at construction so a source always matches the cache key it was named with.
    private final float textScaleGrowth = sTextScaleGrowth;
    private final int textScaleStartZoom = sTextScaleStartZoom;

    private RenderThemeFuture theme = null;
    private XmlRenderTheme mXmlRenderTheme = null;
    private DirectRenderer renderer;
    private HillsRenderConfig hillsRenderConfig;

    private MultiMapDataStore mapDatabase;

    // Tiles keep an alpha channel and get no map background: the source is a layer over others.
    private volatile boolean transparent;

    /**
     * Main constructor
     */
    protected MapsForgeTileSource(String cacheTileSourceName, int minZoom, int maxZoom,
                                  int tileSizePixels, FileInputStream[] fileInputStream, XmlRenderTheme xmlRenderTheme,
                                  MultiMapDataStore.DataPolicy dataPolicy, HillsRenderConfig hillsRenderConfig,
                                  final String language) {
        this(cacheTileSourceName, minZoom, maxZoom, tileSizePixels, toMapFiles(fileInputStream, language),
                xmlRenderTheme, dataPolicy, hillsRenderConfig);
    }

    /**
     * Constructor over already opened data stores: Mapsforge {@code .map} files, vector
     * MBTiles ({@link MvtMapDataStore}) or a mix of them, drawn together with one theme.
     *
     * @since 7.0.3-lostrat
     */
    protected MapsForgeTileSource(String cacheTileSourceName, int minZoom, int maxZoom,
                                  int tileSizePixels, MapDataStore[] dataStores, XmlRenderTheme xmlRenderTheme,
                                  MultiMapDataStore.DataPolicy dataPolicy, HillsRenderConfig hillsRenderConfig) {

        super(cacheTileSourceName, minZoom, maxZoom, tileSizePixels, ".png", "© OpenStreetMap contributors");

        mapDatabase = new MultiMapDataStore(dataPolicy);
        for (MapDataStore dataStore : dataStores) {
            mapDatabase.addMapDataStore(dataStore, false, false);
        }

        if (AndroidGraphicFactory.INSTANCE == null) {
            throw new RuntimeException("Must call MapsForgeTileSource.createInstance(context.getApplication()); once before creating a MapsForgeTileSource.");
        }

        // Load default theme if none provided
        if (xmlRenderTheme == null) {
            xmlRenderTheme = loadDefaultThemeFromAssets();
        }

        // Line widths are fixed when the theme parses, so set the scale the cache key
        // assumes before the parse thread starts, not after the constructor returns.
        model.setUserScaleFactor(MapsForgeTileCacheKeys.currentRenderScale());

        // Create theme future
        if (xmlRenderTheme != mXmlRenderTheme || theme == null) {
            theme = new RenderThemeFuture(AndroidGraphicFactory.INSTANCE, xmlRenderTheme, model);
            new Thread(theme).start();
        }
        mXmlRenderTheme = xmlRenderTheme;

        // Labels are drawn from this store, not from the tile's own RendererJob, so it must
        // use the same per-zoom text scale. Body mirrors MapDataStoreLabelStore.getVisibleItems
        // (Mapsforge 0.30.0) with the fixed textScale swapped for textScaleForZoom().
        // The Mvt renderers draw only the text of a vector tile's label-only ways (road names
        // come apart from the roads there); for .map data they behave as their superclasses.
        final StandardRenderer labelRenderer =
                new MvtStandardRenderer(mapDatabase, AndroidGraphicFactory.INSTANCE, true);
        MapDataStoreLabelStore labelStore = new MapDataStoreLabelStore(
                mapDatabase, theme, scale, model, AndroidGraphicFactory.INSTANCE) {
            @Override
            public List<MapElementContainer> getVisibleItems(Tile upperLeft, Tile lowerRight) {
                try {
                    RendererJob rendererJob = new RendererJob(upperLeft, mapDatabase, theme, model,
                            textScaleForZoom(upperLeft.zoomLevel), true, true);
                    RenderContext renderContext = new RenderContext(rendererJob, AndroidGraphicFactory.INSTANCE);
                    MapReadResult mapReadResult = mapDatabase.readMapData(upperLeft, lowerRight);
                    if (mapReadResult == null) {
                        return new ArrayList<>();
                    }
                    labelRenderer.processReadMapData(renderContext, mapReadResult);
                    return renderContext.getLabels();
                } catch (Exception e) {
                    return new ArrayList<>();
                }
            }
        };

        renderer = new MvtDirectRenderer(
                mapDatabase,
                AndroidGraphicFactory.INSTANCE,
                labelStore,
                true,   // renderLabels
                false,  // cacheLabels
                hillsRenderConfig
        );

        minZoom = MIN_ZOOM;
        maxZoom = renderer.getZoomLevelMax();

        Log.d(IMapView.LOGTAG, "min=" + minZoom + " max=" + maxZoom + " tilesize=" + tileSizePixels);
    }

    /**
     * Load default theme from assets using StreamRenderTheme
     */
    private XmlRenderTheme loadDefaultThemeFromAssets() {
        if (sAssetManager == null) {
            throw new IllegalStateException(
                    "Call MapsForgeTileSource.createInstance(app) before creating a MapsForgeTileSource " +
                            "(AssetManager is required to load assets theme)."
            );
        }

        try {
            // Try with subdirectory first
            String fullPath = sDefaultThemeDir.isEmpty() ? sDefaultThemeFile : sDefaultThemeDir + "/" + sDefaultThemeFile;
            InputStream inputStream = sAssetManager.open(fullPath);
            return new StreamRenderTheme("/assets/", inputStream);
        } catch (IOException e) {
            Log.w(IMapView.LOGTAG, "Could not load default theme from assets: " + sDefaultThemeDir + "/" + sDefaultThemeFile, e);
            // Fallback - try without subdirectory
            try {
                InputStream inputStream = sAssetManager.open(sDefaultThemeFile);
                return new StreamRenderTheme("/assets/", inputStream);
            } catch (IOException e2) {
                Log.e(IMapView.LOGTAG, "Could not load any default theme", e2);
                throw new RuntimeException("Could not load default theme from assets: " + sDefaultThemeFile, e2);
            }
        }
    }

    /**
     * Overloaded constructor without language parameter
     */
    protected MapsForgeTileSource(String cacheTileSourceName, int minZoom, int maxZoom,
                                  int tileSizePixels, FileInputStream[] fileInputStream, XmlRenderTheme xmlRenderTheme,
                                  MultiMapDataStore.DataPolicy dataPolicy, HillsRenderConfig hillsRenderConfig) {
        this(cacheTileSourceName, minZoom, maxZoom, tileSizePixels, fileInputStream,
                xmlRenderTheme, dataPolicy, hillsRenderConfig, null);
    }

    /**
     * Initialize MapsForge with Application context and density management
     */
    public static void createInstance(Application app) {
        sAssetManager = app.getApplicationContext().getAssets();
        sResources = app.getResources();
        AndroidGraphicFactory.createInstance(app);
        
        // Initialize density manager for consistent scaling
        DisplayDensityManager.initialize(app);
        
        Log.d(IMapView.LOGTAG, "MapsForgeTileSource initialized with density: " + 
                DisplayDensityManager.getInstance().getDensityCategory());
    }

    /**
     * Set the default theme location in assets
     * @param themeDir subdirectory in assets (can be empty string for root)
     * @param themeFile filename of the theme XML
     */
    public static void setDefaultTheme(String themeDir, String themeFile) {
        sDefaultThemeDir = themeDir;
        sDefaultThemeFile = themeFile;
    }

    // Factory methods - simplified for clarity
    public static MapsForgeTileSource createFromFiles(File[] file) {
        return createFromFiles(file, null, "mapsforge-default");
    }

    public static MapsForgeTileSource createFromFiles(File[] file, XmlRenderTheme theme, String themeName) {
        return createFromFiles(file, theme, themeName, MultiMapDataStore.DataPolicy.RETURN_ALL, null, null);
    }

    public static MapsForgeTileSource createFromFiles(File[] file, XmlRenderTheme theme, String themeName,
                                                      final String language) {
        return createFromFiles(file, theme, themeName, MultiMapDataStore.DataPolicy.RETURN_ALL, null, language);
    }

    public static MapsForgeTileSource createFromFiles(File[] file, XmlRenderTheme theme, String themeName,
                                                      MultiMapDataStore.DataPolicy dataPolicy, HillsRenderConfig hillsRenderConfig) {
        return createFromFiles(file, theme, themeName, dataPolicy, hillsRenderConfig, null);
    }

    public static MapsForgeTileSource createFromFiles(File[] file, XmlRenderTheme theme, String themeName,
                                                      MultiMapDataStore.DataPolicy dataPolicy, HillsRenderConfig hillsRenderConfig, final String language) {

        // Any vector MBTiles file in the list: open every file as a data store instead.
        for (File f : file) {
            if (isVectorTileArchive(f)) {
                MapDataStore[] dataStores = new MapDataStore[file.length];
                try {
                    for (int i = 0; i < file.length; i++) {
                        dataStores[i] = openDataStore(file[i], language);
                    }
                } catch (RuntimeException e) {
                    for (MapDataStore opened : dataStores) {   // one file failed: close the others
                        if (opened != null) {
                            opened.close();
                        }
                    }
                    throw e;
                }
                return createFromDataStores(dataStores, theme, themeName, dataPolicy, hillsRenderConfig);
            }
        }

        FileInputStream[] fileInputStream = convertFilesToInputStreams(file);
        return new MapsForgeTileSource(themeName != null ? themeName : "mapsforge",
                MIN_ZOOM, MAX_ZOOM, TILE_SIZE_PIXELS, fileInputStream, theme, dataPolicy, hillsRenderConfig, language);
    }

    // FileInputStream factory methods
    public static MapsForgeTileSource createFromFileInputStream(FileInputStream[] fileInputStream) {
        return createFromFileInputStream(fileInputStream, null, "mapsforge-default");
    }

    public static MapsForgeTileSource createFromFileInputStream(FileInputStream[] fileInputStream,
                                                                XmlRenderTheme theme, String themeName) {
        return createFromFileInputStream(fileInputStream, theme, themeName,
                MultiMapDataStore.DataPolicy.RETURN_ALL, null, null);
    }

    public static MapsForgeTileSource createFromFileInputStream(FileInputStream[] fileInputStream,
                                                                XmlRenderTheme theme, String themeName, final String language) {
        return createFromFileInputStream(fileInputStream, theme, themeName,
                MultiMapDataStore.DataPolicy.RETURN_ALL, null, language);
    }

    public static MapsForgeTileSource createFromFileInputStream(FileInputStream[] fileInputStream,
                                                                XmlRenderTheme theme, String themeName, MultiMapDataStore.DataPolicy dataPolicy,
                                                                HillsRenderConfig hillsRenderConfig) {
        return createFromFileInputStream(fileInputStream, theme, themeName, dataPolicy, hillsRenderConfig, null);
    }

    public static MapsForgeTileSource createFromFileInputStream(FileInputStream[] fileInputStream,
                                                                XmlRenderTheme theme, String themeName, MultiMapDataStore.DataPolicy dataPolicy,
                                                                HillsRenderConfig hillsRenderConfig, final String language) {

        return new MapsForgeTileSource(themeName != null ? themeName : "mapsforge",
                MIN_ZOOM, MAX_ZOOM, TILE_SIZE_PIXELS, fileInputStream, theme, dataPolicy, hillsRenderConfig, language);
    }

    /**
     * Creates a tile source over already opened data stores, drawn together with one theme.
     * {@link #openDataStore(File, String)} opens a {@code .map} or a vector {@code .mbtiles}
     * file. The stores are closed by {@link #dispose()}.
     *
     * @since 7.0.3-lostrat
     */
    public static MapsForgeTileSource createFromDataStores(MapDataStore[] dataStores, XmlRenderTheme theme, String themeName,
                                                           MultiMapDataStore.DataPolicy dataPolicy, HillsRenderConfig hillsRenderConfig) {
        return new MapsForgeTileSource(themeName != null ? themeName : "mapsforge",
                MIN_ZOOM, MAX_ZOOM, TILE_SIZE_PIXELS, dataStores, theme, dataPolicy, hillsRenderConfig);
    }

    /**
     * Opens one map data file: a vector MBTiles file (name ending in {@code .mbtiles}, holding
     * Mapbox Vector Tiles in the OpenMapTiles schema, e.g. from tilemaker) as a
     * {@link MvtMapDataStore}, anything else as a Mapsforge {@code .map} file. The file is
     * only read, never written.
     *
     * @throws IllegalArgumentException if an MBTiles file cannot be opened or holds raster tiles
     * @since 7.0.3-lostrat
     */
    public static MapDataStore openDataStore(File file, String language) {
        if (!isVectorTileArchive(file)) {
            return new MapFile(file, language);
        }
        final AndroidMbtilesArchive archive;
        try {
            archive = new AndroidMbtilesArchive(file);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot open MBTiles file " + file, e);
        }
        if (!archive.isVector()) {
            archive.close();
            throw new IllegalArgumentException(file
                    + " holds raster tiles; only vector tiles (format pbf) can be drawn with a render theme");
        }
        return new MvtMapDataStore(archive);
    }

    /**
     * True for a file {@link #openDataStore(File, String)} opens as vector MBTiles, judged by its name.
     *
     * @since 7.0.3-lostrat
     */
    public static boolean isVectorTileArchive(File file) {
        return file != null && file.getName().toLowerCase(Locale.US).endsWith(".mbtiles");
    }

    private static MapDataStore[] toMapFiles(FileInputStream[] fileInputStream, String language) {
        MapDataStore[] mapFiles = new MapDataStore[fileInputStream.length];
        for (int i = 0; i < fileInputStream.length; i++) {
            mapFiles[i] = new MapFile(fileInputStream[i], language);
        }
        return mapFiles;
    }

    public BoundingBox getBounds() {
        return mapDatabase.boundingBox();
    }

    public org.osmdroid.util.BoundingBox getBoundsOsmdroid() {
        BoundingBox boundingBox = mapDatabase.boundingBox();
        final double latNorth = Math.min(MapView.getTileSystem().getMaxLatitude(), boundingBox.maxLatitude);
        final double latSouth = Math.max(MapView.getTileSystem().getMinLatitude(), boundingBox.minLatitude);
        return new org.osmdroid.util.BoundingBox(
                latNorth, boundingBox.maxLongitude,
                latSouth, boundingBox.minLongitude);
    }

    public synchronized Drawable renderTile(final long pMapTileIndex) {
        Tile tile = new Tile(MapTileIndex.getX(pMapTileIndex), MapTileIndex.getY(pMapTileIndex),
                (byte) MapTileIndex.getZoom(pMapTileIndex), 256);
        model.setFixedTileSize(256);

        if (mapDatabase == null) return null;

        try {
            RendererJob mapGeneratorJob = new RendererJob(tile, mapDatabase, theme, model,
                    textScaleForZoom(tile.zoomLevel), transparent, false);
            AndroidTileBitmap bmp = (AndroidTileBitmap) renderer.executeJob(mapGeneratorJob);
            if (bmp != null) {
                return new BitmapDrawable(sResources, AndroidGraphicFactory.getBitmap(bmp));
            }
        } catch (Exception ex) {
            Log.d(IMapView.LOGTAG, "Mapsforge tile generation failed", ex);
        }
        return null;
    }

    /**
     * Render tiles with an alpha channel and without the theme's map background, so this source
     * can be drawn as a layer over another map, e.g. a theme of contour lines only over
     * hillshade. Set it before the first tile is rendered, and give the tile cache name a
     * variant ({@link MapsForgeTileCacheKeys#forMapsThemeAndVariant}): cached tiles keep the
     * background they were rendered with.
     *
     * @since 7.0.3-lostrat
     */
    public void setTransparent(boolean transparent) {
        this.transparent = transparent;
    }

    /**
     * Closes the data stores and releases the theme. Waits for a tile being rendered, so call
     * it off the UI thread when the source may still be rendering.
     */
    public synchronized void dispose() {
        if (theme != null) {
            theme.decrementRefCount();
            theme = null;
        }
        renderer = null;
        if (mapDatabase != null) {
            mapDatabase.close();
            mapDatabase = null;
        }
    }

    /**
     * Note: TileRefresher is now obsolete in newer MapsForge versions due to deterministic labels.
     * This method is kept for backward compatibility but may not have any effect.
     */
    public void addTileRefresher(DirectRenderer.TileRefresher pDirectTileRefresher) {
        // TileRefresher interface is obsolete as of 2024 - deterministic labels made it unnecessary
        Log.w(IMapView.LOGTAG, "TileRefresher is obsolete in newer MapsForge versions");
    }

    /**
     * Grow label text by {@code growthPerZoom} for each zoom level above {@code startZoom},
     * capped at 2.5x. Lines and symbols are unaffected. Applies to sources created afterwards,
     * and to cache keys built afterwards by {@link MapsForgeTileCacheKeys}; call it before both.
     * {@code growthPerZoom} 1 restores stock Mapsforge (same text size at every zoom).
     */
    public static void setTextScaleByZoom(float growthPerZoom, int startZoom) {
        if (growthPerZoom <= 0f) {
            throw new IllegalArgumentException("growthPerZoom must be > 0: " + growthPerZoom);
        }
        sTextScaleGrowth = growthPerZoom;
        sTextScaleStartZoom = startZoom;
    }

    /**
     * Cache key part for the current {@link #setTextScaleByZoom} setting, or null when text
     * scaling is stock so existing tile cache names are unchanged.
     */
    static String textScaleCacheKeyPart() {
        if (sTextScaleGrowth == 1f) {
            return null;
        }
        return "text-" + Math.round(sTextScaleGrowth * 100) + "-z" + sTextScaleStartZoom;
    }

    private float textScaleForZoom(byte zoom) {
        int steps = Math.max(0, zoom - textScaleStartZoom);
        return scale * Math.min(TEXT_SCALE_MAX, (float) Math.pow(textScaleGrowth, steps));
    }

    /**
     * Set custom user scale factor (overrides automatic density scaling)
     */
    public synchronized void setUserScaleFactor(float scaleFactor) {
        if (scaleFactor == model.getUserScaleFactor()) {
            return;
        }
        model.setUserScaleFactor(scaleFactor);
        // The parsed theme holds line widths at the old scale; parse it again.
        if (theme != null) {
            theme.decrementRefCount();
            theme = new RenderThemeFuture(AndroidGraphicFactory.INSTANCE, mXmlRenderTheme, model);
            new Thread(theme).start();
        }
        Log.d(IMapView.LOGTAG, "MapForge scale factor set to: " + scaleFactor);
    }

    /**
     * Apply automatic density-aware scaling (recommended)
     */
    public void applyDensityScaling() {
        if (DisplayDensityManager.isInitialized()) {
            float autoScale = DisplayDensityManager.getInstance().getMapForgeScaleFactor();
            setUserScaleFactor(autoScale);
            Log.d(IMapView.LOGTAG, "Applied automatic MapForge scaling: " + autoScale + 
                    " for density: " + DisplayDensityManager.getInstance().getDensityCategory());
        }
    }
    
    /**
     * Get the current user scale factor
     */
    public float getUserScaleFactor() {
        return model.getUserScaleFactor();
    }


    private static FileInputStream[] convertFilesToInputStreams(File[] files) {
        FileInputStream[] fileInputStreams = new FileInputStream[files.length];
        for (int i = 0; i < files.length; i++) {
            try {
                fileInputStreams[i] = new FileInputStream(files[i]);
            } catch (FileNotFoundException ex) {
                Log.d(IMapView.LOGTAG, "###################### Mapsforge file input stream conversion failed", ex);
            }
        }
        return fileInputStreams;
    }

}
