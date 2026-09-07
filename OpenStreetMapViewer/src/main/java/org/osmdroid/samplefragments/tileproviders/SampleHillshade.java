package org.osmdroid.samplefragments.tileproviders;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import org.osmdroid.R;
import org.osmdroid.config.Configuration;
import org.osmdroid.samplefragments.BaseSampleFragment;
import org.osmdroid.tileprovider.terrain.HillshadeOverlay;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Terrain-RGB hillshade over the default online base map.
 * <p>
 * Button 1 toggles the layer. Button 2 picks a {@code *.mbtiles} file from the app's
 * external files folders ({@code Android/data/org.osmdroid/files} and its {@code osmdroid}
 * or {@code Download} subfolders, where a file copied over USB lands), the configured osmdroid base path, or the
 * shared Downloads folder when it is readable; the first file with "terrain" in its name is
 * loaded automatically on start. Build such a file with
 * {@code rio rgbify -b -10000 -i 1 --format webp ...} or use any Mapbox / Terrarium tileset.
 * </p>
 *
 * @since 7.0.3-lostrat
 */
public class SampleHillshade extends BaseSampleFragment implements View.OnClickListener {

    private Button mBtnToggle;
    private Button mBtnPick;
    private HillshadeOverlay mHillshade;
    private List<File> mFiles = new ArrayList<>();

    @Override
    public String getSampleTitle() {
        return "Terrain-RGB hillshade overlay";
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.sample_map_two_button, container, false);
        mMapView = root.findViewById(R.id.mapview);
        mBtnToggle = root.findViewById(R.id.button1);
        mBtnToggle.setOnClickListener(this);
        mBtnPick = root.findViewById(R.id.button2);
        mBtnPick.setOnClickListener(this);
        mBtnPick.setText("Pick terrain file");
        return root;
    }

    @Override
    public void addOverlays() {
        super.addOverlays();
        mMapView.setTileSource(TileSourceFactory.MAPNIK);

        mHillshade = new HillshadeOverlay(mMapView);
        mMapView.getOverlays().add(mHillshade);

        mFiles = findMbtiles();
        for (final File f : mFiles) {
            if (f.getName().toLowerCase().contains("terrain")) {
                load(f);
                break;
            }
        }
        updateToggleText();
    }

    @Override
    public void onClick(final View v) {
        if (v == mBtnToggle) {
            if (!mHillshade.hasFile()) {
                Toast.makeText(getContext(), "Pick a terrain file first", Toast.LENGTH_SHORT).show();
                return;
            }
            mHillshade.setEnabled(!mHillshade.isEnabled());
            mMapView.invalidate();
            updateToggleText();
        } else if (v == mBtnPick) {
            pick();
        }
    }

    private void load(final File f) {
        if (!mHillshade.setFile(f)) {
            Toast.makeText(getContext(), "Cannot open " + f.getName(), Toast.LENGTH_LONG).show();
            return;
        }
        mHillshade.setEnabled(true);
        final BoundingBox b = mHillshade.getBounds();
        if (b != null) {
            mMapView.post(new Runnable() {
                @Override
                public void run() {
                    mMapView.zoomToBoundingBox(b, false);
                }
            });
        }
        mMapView.invalidate();
        updateToggleText();
    }

    private void pick() {
        mFiles = findMbtiles();
        if (mFiles.isEmpty()) {
            Toast.makeText(getContext(), "No *.mbtiles in " + getContext().getExternalFilesDir(null)
                    + ", " + Configuration.getInstance().getOsmdroidBasePath()
                    + " or Downloads", Toast.LENGTH_LONG).show();
            return;
        }
        final String[] names = new String[mFiles.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = mFiles.get(i).getName();
        }
        new AlertDialog.Builder(getContext())
                .setTitle("Terrain-RGB MBTiles")
                .setItems(names, (dialog, which) -> load(mFiles.get(which)))
                .show();
    }

    private void updateToggleText() {
        final String name = mHillshade.hasFile() ? mHillshade.getFile().getName() : "no file";
        mBtnToggle.setText("Hillshade " + (mHillshade.isEnabled() ? "ON" : "OFF") + " (" + name + ")");
    }

    private List<File> findMbtiles() {
        final List<File> dirs = new ArrayList<>();
        // App-specific external storage: no permission needed, and where a USB copy lands.
        final File[] external = getContext().getExternalFilesDirs(null);
        if (external != null) {
            for (final File dir : external) {
                if (dir != null) {
                    dirs.add(dir);
                    dirs.add(new File(dir, "osmdroid"));
                    dirs.add(new File(dir, "Download"));
                }
            }
        }
        dirs.add(Configuration.getInstance().getOsmdroidBasePath());
        // Readable only with a storage permission on API 29+; harmless when it is not.
        dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));

        final List<File> out = new ArrayList<>();
        for (final File dir : dirs) {
            final File[] files = dir == null ? null : dir.listFiles();
            if (files == null) {
                continue;
            }
            for (final File f : files) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(".mbtiles") && !out.contains(f)) {
                    out.add(f);
                }
            }
        }
        Collections.sort(out);
        return out;
    }
}
