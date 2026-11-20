package org.osmdroid.mapsforge.mvt;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.core.graphics.TileBitmap;
import org.mapsforge.core.mapelements.MapElementContainer;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Tile;
import org.mapsforge.core.util.MercatorProjection;
import org.mapsforge.map.awt.graphics.AwtGraphicFactory;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.datastore.MapReadResult;
import org.mapsforge.map.datastore.MultiMapDataStore;
import org.mapsforge.map.layer.labels.LabelStore;
import org.mapsforge.map.layer.labels.MapDataStoreLabelStore;
import org.mapsforge.map.layer.renderer.DatabaseRenderer;
import org.mapsforge.map.layer.renderer.DirectRenderer;
import org.mapsforge.map.layer.renderer.RendererJob;
import org.mapsforge.map.layer.renderer.StandardRenderer;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.rendertheme.RenderContext;
import org.mapsforge.map.rendertheme.StreamRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderTheme;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture;

import org.mapsforge.core.graphics.Canvas;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders real tiles with the AWT backend to {@code build/mvt-render/}: base map plus
 * contours through a {@code MultiMapDataStore}, drawn by {@link MvtDirectRenderer} with
 * labels from an {@link MvtStandardRenderer} label pass, as osmdroid's MapsForgeTileSource
 * does it.
 */
public class MvtRenderTest {

    private static final GraphicFactory GRAPHICS = AwtGraphicFactory.INSTANCE;
    private static final LatLong CENTER = new LatLong(45.5663, -122.7654);
    private static final File OUT = new File(System.getProperty("mvt.renderdir", "build/mvt-render"));
    private static final File USER_THEME = new File(
            "F:/ANDROIDPROJECT/TestOsmdroidLostRat/app/src/main/assets/themes/default-path-contour.xml");

    private static MultiMapDataStore multi;
    private static DisplayModel model;

    @BeforeClass
    public static void open() throws IOException {
        if (!TestData.DIR.isDirectory()) {
            return;
        }
        multi = new MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_ALL);
        multi.addMapDataStore(new MvtMapDataStore(TestData.archive("forest-park-full-202610.mbtiles")), true, true);
        multi.addMapDataStore(new MvtMapDataStore(TestData.archive("forest-park-contour-202610.mbtiles")), false, false);
        model = new DisplayModel();
        // a phone-like 2x scale: 512 px tiles, lines and text twice as wide
        model.setUserScaleFactor(2f);
        OUT.mkdirs();
    }

    @AfterClass
    public static void close() {
        if (multi != null) {
            multi.close();
        }
    }

    /** The user's theme with its {@code jar:} resources resolved from the mapsforge-themes jar. */
    private static XmlRenderTheme userTheme() throws IOException {
        if (!USER_THEME.isFile()) {
            return null;
        }
        return new StreamRenderTheme("/assets/", new FileInputStream(USER_THEME));
    }

    private static RenderThemeFuture theme(XmlRenderTheme xml) {
        RenderThemeFuture future = new RenderThemeFuture(GRAPHICS, xml, model);
        future.run();
        return future;
    }

    /** As osmdroid's MapsForgeTileSource builds its label store, with the given label renderer. */
    private static LabelStore labelStore(final MapDataStore store, final RenderThemeFuture theme,
                                         final StandardRenderer labelRenderer) {
        // DatabaseRenderer only takes labels from a MapDataStoreLabelStore when it has no tile cache
        return new MapDataStoreLabelStore(store, theme, 1f, model, GRAPHICS) {
            @Override
            public List<MapElementContainer> getVisibleItems(Tile upperLeft, Tile lowerRight) {
                try {
                    RendererJob job = new RendererJob(upperLeft, store, theme, model, 1f, true, true);
                    RenderContext context = new RenderContext(job, GRAPHICS);
                    MapReadResult result = store.readMapData(upperLeft, lowerRight);
                    if (result == null) {
                        return new ArrayList<>();
                    }
                    labelRenderer.processReadMapData(context, result);
                    return context.getLabels();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            }
        };
    }

    private static DatabaseRenderer renderer(RenderThemeFuture theme, boolean mvt) {
        StandardRenderer labels = mvt ? new MvtStandardRenderer(multi, GRAPHICS, true)
                : new StandardRenderer(multi, GRAPHICS, true);
        LabelStore store = labelStore(multi, theme, labels);
        return mvt ? new MvtDirectRenderer(multi, GRAPHICS, store, true, false, null)
                : new DirectRenderer(multi, GRAPHICS, store, true, false, null);
    }

    private static TileBitmap render(DatabaseRenderer renderer, RenderThemeFuture theme, Tile tile) {
        RendererJob job = new RendererJob(tile, multi, theme, model, 1f, false, false);
        TileBitmap bitmap = renderer.executeJob(job);
        Assert.assertNotNull("tile rendered", bitmap);
        return bitmap;
    }

    /**
     * A 2x2 block of tiles at {@code zoom} around the centre, stitched into one PNG.
     *
     * @return the pixels, as {@link Images#read} returns them
     */
    private static int[] block(DatabaseRenderer renderer, RenderThemeFuture theme, int zoom, String name)
            throws Exception {
        int size = model.getTileSize();
        long mapSize = MercatorProjection.getMapSize((byte) zoom, size);
        double px = MercatorProjection.longitudeToPixelX(CENTER.longitude, mapSize);
        double py = MercatorProjection.latitudeToPixelY(CENTER.latitude, mapSize);
        // the 2x2 block whose shared corner is nearest the centre
        int x0 = (int) Math.round(px / size) - 1;
        int y0 = (int) Math.round(py / size) - 1;
        TileBitmap out = GRAPHICS.createTileBitmap(2 * size, false);
        Canvas canvas = GRAPHICS.createCanvas();
        canvas.setBitmap(out);
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                TileBitmap tile = render(renderer, theme, new Tile(x0 + dx, y0 + dy, (byte) zoom, size));
                canvas.drawBitmap(tile, dx * size, dy * size);
                tile.decrementRefCount();
            }
        }
        canvas.destroy();
        File file = new File(OUT, name);
        try (OutputStream stream = new FileOutputStream(file)) {
            out.compress(stream);
        }
        out.decrementRefCount();
        System.out.println("wrote " + file.getAbsolutePath() + " (tiles " + zoom + "/" + x0 + "/" + y0 + " to "
                + (x0 + 1) + "/" + (y0 + 1) + ")");
        return Images.read(file);
    }

    private static void assertNotBlank(int[] image, String what) {
        int n = image.length - 2;
        Set<Integer> colors = new HashSet<>();
        int background = image[0];
        int different = 0;
        for (int i = 0; i < n; i++) {
            colors.add(image[i]);
            if (image[i] != background) {
                different++;
            }
        }
        System.out.println(what + ": " + colors.size() + " colours, " + different + " of " + n
                + " pixels differ from the corner");
        Assert.assertTrue(what + " has some colours", colors.size() > 20);
        Assert.assertTrue(what + " is not blank", different > n / 20);
    }

    @Test
    public void rendersWithUserTheme() throws Exception {
        TestData.file("forest-park-full-202610.mbtiles");
        XmlRenderTheme xml = userTheme();
        org.junit.Assume.assumeTrue("user theme missing: " + USER_THEME, xml != null);
        RenderThemeFuture theme = theme(xml);
        DatabaseRenderer renderer = renderer(theme, true);
        for (int zoom : new int[]{12, 14, 16, 18}) {
            assertNotBlank(block(renderer, theme, zoom, "user-theme-z" + zoom + ".png"), "user theme z" + zoom);
        }
        theme.decrementRefCount();
    }

    @Test
    public void rendersWithDefaultTheme() throws Exception {
        TestData.file("forest-park-full-202610.mbtiles");
        RenderThemeFuture theme = theme(MapsforgeThemes.DEFAULT);
        DatabaseRenderer renderer = renderer(theme, true);
        for (int zoom : new int[]{14, 16}) {
            assertNotBlank(block(renderer, theme, zoom, "default-theme-z" + zoom + ".png"), "default theme z" + zoom);
        }
        theme.decrementRefCount();
    }

    /** The stock DirectRenderer strokes the label-only road-name ways as roads too. */
    @Test
    public void labelOnlyHandlingMakesADifference() throws Exception {
        TestData.file("forest-park-full-202610.mbtiles");
        XmlRenderTheme xml = userTheme();
        RenderThemeFuture theme = theme(xml != null ? xml : MapsforgeThemes.DEFAULT);
        int zoom = 16;
        int[] mvt = block(renderer(theme, true), theme, zoom, "compare-z16-mvt.png");
        int[] plain = block(renderer(theme, false), theme, zoom, "compare-z16-plain.png");
        int n = mvt.length - 2;
        int[] diff = new int[n];
        int differ = 0;
        for (int i = 0; i < n; i++) {
            if (mvt[i] != plain[i]) {
                differ++;
                diff[i] = 0xFF0000;
            } else {
                diff[i] = ((mvt[i] & 0xFEFEFE) >> 1) | 0x808080;
            }
        }
        Images.write(diff, mvt[n], mvt[n + 1], new File(OUT, "compare-z16-diff.png"));
        System.out.println("plain DirectRenderer differs in " + differ + " of " + n + " pixels");
        Assert.assertTrue(differ > 0);
        theme.decrementRefCount();
    }
}
