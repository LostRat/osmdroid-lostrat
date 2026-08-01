package org.osmdroid.samplefragments.tileproviders;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.rendertheme.AssetsRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderTheme;
import org.osmdroid.api.IMapView;
import org.osmdroid.config.Configuration;
import org.osmdroid.mapsforge.MapsForgeTileCacheKeys;
import org.osmdroid.mapsforge.MapsForgeTileProvider;
import org.osmdroid.mapsforge.MapsForgeTileSource;
import org.osmdroid.samplefragments.BaseSampleFragment;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.tileprovider.util.StorageUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An example of using MapsForge in osmdroid
 * created on 1/12/2017.
 *
 * @author Alex O'Ree
 */

public class MapsforgeTileProviderSample extends BaseSampleFragment {
    MapsForgeTileSource fromFiles = null;
    MapsForgeTileProvider forge = null;
    AlertDialog alertDialog = null;

    @Override
    public String getSampleTitle() {
        return "Mapsforge tiles";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);   //turn off the menu to prevent accidential tile source changes
        Log.d(TAG, "onCreate");

        /**
         * super important to configure some of the mapsforge settings first
         */
        MapsForgeTileSource.createInstance(this.getActivity().getApplication());
        /*
        not sure how important these are....
        MapFile.wayFilterEnabled = true;
        MapFile.wayFilterDistance = 20;
        MapWorkerPool.DEBUG_TIMING = true;
        MapWorkerPool.NUMBER_OF_THREADS = MapWorkerPool.DEFAULT_NUMBER_OF_THREADS;
*/

    }

    /**
     * Copies a file from assets to internal storage, creating subdirectories if needed.
     * This is the most reliable way to ensure correct file permissions.
     * @return The File object for the file in internal storage, or null on failure.
     */
    private File copyAssetToInternalStorage(String assetPath, String destinationPath) {
        File destFile = new File(getContext().getFilesDir(), destinationPath);

        // If file already exists, no need to copy again.
        if (destFile.exists()) {
            Log.d(IMapView.LOGTAG, "Map file already exists. No need to copy.");
            return destFile;
        }

        // Ensure parent directories exist.
        File parentDir = destFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                Log.e(IMapView.LOGTAG, "Failed to create directory: " + parentDir.getAbsolutePath());
                return null;
            }
        }

        try (InputStream inputStream = getContext().getAssets().open(assetPath);
             FileOutputStream outputStream = new FileOutputStream(destFile)) {

            byte[] buffer = new byte[4096];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            Log.d(IMapView.LOGTAG, "Successfully copied map file to " + destFile.getAbsolutePath());
            return destFile;
        } catch (IOException e) {
            Log.e(IMapView.LOGTAG, "Failed to copy map file from assets", e);
            // Clean up a partially created file if it exists
            if (destFile.exists()) {
                destFile.delete();
            }
            return null;
        }
    }



    @Override
    public void addOverlays() {
        super.addOverlays();

        // you can find other files at OpenAndroMaps.com
        // 1. Copy the map file from assets to ensure it has correct permissions.
        // This will create the file at: /data/data/org.osmdroid/files/osmdroid/portland.map
        File mapFile = copyAssetToInternalStorage("maps/forest-park-202502.map", "osmdroid/forest-park-202502.map");

        // 2. Check if the file was copied successfully.
        if (mapFile == null || !mapFile.exists()) {
            Toast.makeText(getContext(), "Could not load map file!", Toast.LENGTH_LONG).show();
            return;
        }

        // 3. Now, create the tile source using the file we know is accessible.
        File[] mapFiles = new File[] { mapFile };

//        NOTES FOR TESTING -- BEGIN
//        // This will now succeed because our app created the file.
//        MapsForgeTileSource tileSource = MapsForgeTileSource.createFromFiles(mapFiles);
//
//        // Set the new tile source
//        mMapView.setTileSource(tileSource);
//
//        // Optional: Center the map on the new data
//        org.osmdroid.util.BoundingBox box = tileSource.getBoundsOsmdroid();
//        if (box != null) {
//            mMapView.post(new Runnable() {
//                @Override
//                public void run() {
//                    mMapView.zoomToBoundingBox(box, true, 50);
//                }
//            });
//        }
//        NOTES FOR TESTING -- END

        //first let's up our map source, mapsforge needs you to explicitly specify which map files to load
        //this bit does some basic file system scanning
        Set<File> mapfiles = findMapFiles();
        //do a simple scan of local storage for .map files.
        List<File> sortedMapFiles = new ArrayList<>(mapfiles);
        Collections.sort(sortedMapFiles);
        File[] maps = sortedMapFiles.toArray(new File[0]);
        if (maps == null || maps.length == 0) {
            //show a warning that no map files were found
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                    getContext());

            // set title
            alertDialogBuilder.setTitle("No Mapsforge files found");

            // set dialog message
            alertDialogBuilder
                    .setMessage("In order to render map tiles, you'll need to either create or obtain mapsforge .map files. See https://github.com/mapsforge/mapsforge for more info. Store them in "
                            + Configuration.getInstance().getOsmdroidBasePath().getAbsolutePath())
                    .setCancelable(false)
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            if (alertDialog != null) alertDialog.dismiss();
                        }
                    });


            // create alert dialog
            alertDialog = alertDialogBuilder.create();

            // show it
            alertDialog.show();

        } else {
            Toast.makeText(getContext(), "Loaded " + maps.length + " map files", Toast.LENGTH_LONG).show();

            //this creates the forge provider and tile sources

            //protip: when changing themes, you should also change the tile source name to prevent cached tiles

            //null is ok here, uses the default rendering theme if it's not set
            XmlRenderTheme theme = null;
            try {
                theme = new AssetsRenderTheme(getContext().getApplicationContext().getAssets(), "renderthemes/", "rendertheme-v4.xml");
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            final String themeName = "rendertheme-v4";
            // Keyed on the render scale applyDensityScaling() will apply, so the cache
            // namespace tracks what is actually rendered rather than the raw density.
            final String tileCacheSourceName = MapsForgeTileCacheKeys.forMapsAndTheme(
                    maps, themeName);
            fromFiles = MapsForgeTileSource.createFromFiles(maps, theme, tileCacheSourceName);
            fromFiles.applyDensityScaling();
            forge = new MapsForgeTileProvider(
                    new SimpleRegisterReceiver(getContext()),
                    fromFiles);

            mMapView.setTileProvider(forge);


            //now for a magic trick
            //since we have no idea what will be on the
            //user's device and what geographic area it is, this will attempt to center the map
            //on whatever the map data provides

            // Assuming you have these methods available
            int minZoom = fromFiles.getMinimumZoomLevel();
            int maxZoom = fromFiles.getMaximumZoomLevel();

            mMapView.setMinZoomLevel((double) minZoom);
            mMapView.setMaxZoomLevel((double) maxZoom);

// Calculate the average
            double averageZoom = (minZoom + maxZoom) / 2.0; // Use 2.0 to ensure double division

// Round up to the nearest integer
            int roundedUpAverageZoom = (int) Math.ceil(averageZoom);

// Set the zoom level
            mMapView.getController().setZoom((double) roundedUpAverageZoom);
            mMapView.getController().setCenter(fromFiles.getBoundsOsmdroid().getCenterWithDateLine());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (alertDialog != null) alertDialog.dismiss();
        alertDialog = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (alertDialog != null) {
            alertDialog.hide();
            alertDialog.dismiss();
            alertDialog = null;
        }
        if (fromFiles != null)
            fromFiles.dispose();
        if (forge != null)
            forge.detach();
        AndroidGraphicFactory.clearResourceMemoryCache();
    }

    /**
     * simple function to scan for paths that match /something/osmdroid/*.map to find mapforge database files
     *
     * @return
     */
    protected Set<File> findMapFiles() {
        Set<File> maps = new HashSet<>();
        List<StorageUtils.StorageInfo> storageList = StorageUtils.getStorageList(getActivity());
        for (int i = 0; i < storageList.size(); i++) {
            File f = new File(storageList.get(i).path + File.separator + "osmdroid" + File.separator);
            if (f.exists()) {
                maps.addAll(scan(f));
            }
        }
        return maps;
    }

    private Collection<? extends File> scan(File f) {
        List<File> ret = new ArrayList<>();
        File[] files = f.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().toLowerCase().endsWith(".map");
            }
        });
        if (files != null) {
            Collections.addAll(ret, files);
        }
        return ret;
    }
}
