package org.osmdroid.mapsforge.mvt;

import org.mapsforge.core.model.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** An MBTiles file read through sqlite-jdbc, read-only, for JVM tests. */
final class JdbcMbtilesArchive implements VectorTileArchive {

    private final File file;
    private final Connection connection;
    private final PreparedStatement tileQuery;
    private final MbtilesMetadata metadata;

    JdbcMbtilesArchive(File file) throws IOException {
        this.file = file;
        if (!file.isFile()) {
            throw new IOException("no such file: " + file);
        }
        try {
            Properties properties = new Properties();
            properties.setProperty("open_mode", "1"); // SQLITE_OPEN_READONLY
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath(), properties);
            connection.setReadOnly(true);
            Map<String, String> rows = new HashMap<>();
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT name, value FROM metadata")) {
                while (resultSet.next()) {
                    String name = resultSet.getString(1);
                    String value = resultSet.getString(2);
                    if (name != null && value != null) {
                        rows.put(name, value);
                    }
                }
            } catch (SQLException e) {
                // no metadata table: defaults apply
            }
            metadata = new MbtilesMetadata(rows);
            tileQuery = connection.prepareStatement(
                    "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?");
        } catch (SQLException e) {
            throw new IOException("cannot open " + file, e);
        }
    }

    MbtilesMetadata getMetadata() {
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

    /** Raw stored bytes (still gzipped), for tests. */
    synchronized byte[] readStored(int zoom, int x, int y) throws IOException {
        try {
            tileQuery.setInt(1, zoom);
            tileQuery.setInt(2, x);
            tileQuery.setInt(3, metadata.storedRow(zoom, y));
            try (ResultSet resultSet = tileQuery.executeQuery()) {
                return resultSet.next() ? resultSet.getBytes(1) : null;
            }
        } catch (SQLException e) {
            throw new IOException("cannot read tile " + zoom + "/" + x + "/" + y, e);
        }
    }

    @Override
    public byte[] readTile(int zoom, int x, int y) throws IOException {
        if (zoom < 0 || zoom > 30 || x < 0 || y < 0 || x >= (1 << zoom) || y >= (1 << zoom)) {
            return null;
        }
        byte[] data = readStored(zoom, x, y);
        if (data == null || data.length == 0) {
            return null;
        }
        return MbtilesMetadata.unpack(data);
    }

    @Override
    public synchronized void close() {
        try {
            tileQuery.close();
            connection.close();
        } catch (SQLException ignored) {
            // closing anyway
        }
    }
}
