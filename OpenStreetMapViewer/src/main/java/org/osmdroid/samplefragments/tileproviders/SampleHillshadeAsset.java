package org.osmdroid.samplefragments.tileproviders;

import android.util.Log;
import android.widget.Toast;

import org.osmdroid.api.IMapView;
import org.osmdroid.samplefragments.BaseSampleFragment;
import org.osmdroid.tileprovider.terrain.HillshadeOverlay;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.views.overlay.DefaultOverlayManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Minimal Terrain-RGB hillshade: the bundled {@code assets/mbtiles/forest-park-terrain-rgb.mbtiles}
 * (Forest Park, Portland OR) shaded over the default online base map. No buttons, no file picker.
 * <p>
 * The whole recipe is in {@link #addOverlays()}: create a {@link HillshadeOverlay}, give it the
 * file, enable it, add it to the map on the background-tiles layer, zoom to its bounds.
 * See {@link SampleHillshade} for a file picker and toggle.
 * </p>
 *
 * @since 7.0.3-lostrat
 */
public class SampleHillshadeAsset extends BaseSampleFragment {

    private static final String ASSET = "mbtiles/forest-park-terrain-rgb.mbtiles";

    @Override
    public String getSampleTitle() {
        return "Terrain-RGB hillshade (bundled Forest Park file)";
    }

    @Override
    public void addOverlays() {
        super.addOverlays();
        mMapView.setTileSource(TileSourceFactory.MAPNIK);

        // SQLite needs a real file, so copy the asset into the app's cache folder once.
        final File file = copyAssetToCache(ASSET);
        if (file == null) {
            Toast.makeText(getContext(), "Cannot copy " + ASSET, Toast.LENGTH_LONG).show();
            return;
        }

        final HillshadeOverlay hillshade = new HillshadeOverlay(mMapView);
        if (!hillshade.setFile(file)) {
            Toast.makeText(getContext(), "Cannot open " + file.getName(), Toast.LENGTH_LONG).show();
            return;
        }
        hillshade.setEnabled(true);
        // Optional tuning; these are the defaults' neighbours, all live.
        hillshade.setMaxAlpha(140);          // 0..255 opacity of the shading
        hillshade.setExaggeration(1.5);      // 1 = true relief
        hillshade.setSun(315, 45);           // azimuth, altitude in degrees

        mMapView.getOverlays().add(hillshade);
        if (mMapView.getOverlayManager() instanceof DefaultOverlayManager) {
            ((DefaultOverlayManager) mMapView.getOverlayManager()).assignOverlayToLayer(
                    hillshade, DefaultOverlayManager.OverlayLayer.BACKGROUND_TILES);
        }

        final BoundingBox bounds = hillshade.getBounds();
        if (bounds != null) {
            mMapView.post(new Runnable() {
                @Override
                public void run() {
                    mMapView.zoomToBoundingBox(bounds, false);
                }
            });
        }
        mMapView.invalidate();
    }

    private File copyAssetToCache(final String assetPath) {
        final File dest = new File(getContext().getCacheDir(), new File(assetPath).getName());
        if (dest.exists() && dest.length() > 0) {
            return dest;
        }
        try (InputStream in = getContext().getAssets().open(assetPath);
             FileOutputStream out = new FileOutputStream(dest)) {
            final byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return dest;
        } catch (IOException e) {
            Log.e(IMapView.LOGTAG, "Failed to copy " + assetPath, e);
            dest.delete();
            return null;
        }
    }
}
