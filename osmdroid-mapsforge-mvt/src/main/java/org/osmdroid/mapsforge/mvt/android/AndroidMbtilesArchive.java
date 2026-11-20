package org.osmdroid.mapsforge.mvt.android;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import org.mapsforge.core.model.BoundingBox;
import org.osmdroid.mapsforge.mvt.MbtilesMetadata;
import org.osmdroid.mapsforge.mvt.VectorTileArchive;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An MBTiles file read through the Android SQLite framework. The file is opened read-only
 * and never written. Thread-safe: {@link SQLiteDatabase} serialises access itself.
 *
 * @since 7.0.3-lostrat
 */
public final class AndroidMbtilesArchive implements VectorTileArchive {

    private final File file;
    private final SQLiteDatabase db;
    private final MbtilesMetadata metadata;

    /**
     * Opens {@code file} read-only.
     *
     * @throws IOException when the file is missing or is not a readable SQLite database
     */
    public AndroidMbtilesArchive(File file) throws IOException {
        this.file = file;
        if (!file.isFile()) {
            throw new IOException("no such file: " + file);
        }
        try {
            db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        } catch (SQLiteException e) {
            throw new IOException("cannot open " + file + ": " + e.getMessage(), e);
        }
        Map<String, String> rows = new HashMap<>();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT name, value FROM metadata", null);
            while (cursor.moveToNext()) {
                if (!cursor.isNull(0) && !cursor.isNull(1)) {
                    rows.put(cursor.getString(0), cursor.getString(1));
                }
            }
        } catch (SQLiteException e) {
            // no metadata table: the defaults apply
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        metadata = new MbtilesMetadata(rows);
    }

    /** The parsed metadata rows. */
    public MbtilesMetadata getMetadata() {
        return metadata;
    }

    @Override
    public int getMinZoom() {
        return metadata.getMinZoom();
    }

    @Override
    public int getMaxZoom() {
        return metadata.getMaxZoom();
    }

    @Override
    public BoundingBox getBoundingBox() {
        return metadata.getBoundingBox();
    }

    @Override
    public List<String> getVectorLayerIds() {
        return metadata.getVectorLayerIds();
    }

    @Override
    public boolean isVector() {
        return metadata.isVector();
    }

    @Override
    public long getTimestamp() {
        return file.lastModified();
    }

    @Override
    public byte[] readTile(int zoom, int x, int y) throws IOException {
        if (zoom < 0 || zoom > 30 || x < 0 || y < 0 || x >= (1 << zoom) || y >= (1 << zoom)) {
            return null;
        }
        // The numbers go into the SQL text rather than as bound strings: bound arguments
        // are text and would rely on column affinity to compare as integers.
        String sql = "SELECT tile_data FROM tiles WHERE zoom_level = " + zoom
                + " AND tile_column = " + x + " AND tile_row = " + metadata.storedRow(zoom, y);
        byte[] data = null;
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, null);
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                data = cursor.getBlob(0);
            }
        } catch (SQLiteException | IllegalStateException e) {
            throw new IOException("cannot read tile " + zoom + "/" + x + "/" + y + ": " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (data == null || data.length == 0) {
            return null;
        }
        return MbtilesMetadata.unpack(data);
    }

    @Override
    public void close() {
        db.close();
    }
}
