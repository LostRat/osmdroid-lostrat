package org.osmdroid.mapsforge.mvt;

import org.mapsforge.core.model.BoundingBox;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * Tile bytes and metadata of one vector tile archive, such as an MBTiles file of Mapbox
 * Vector Tiles. Implementations must be thread-safe: tiles are read from several render
 * threads at once.
 *
 * @since 7.0.3-lostrat
 */
public interface VectorTileArchive extends Closeable {

    /** Lowest zoom level the archive holds tiles for. */
    int getMinZoom();

    /** Highest zoom level the archive holds tiles for; deeper zooms reuse these tiles. */
    int getMaxZoom();

    /** Area covered; never null, the whole world when the metadata gives no bounds. */
    BoundingBox getBoundingBox();

    /** Ids of the vector layers from the metadata json {@code vector_layers}; empty if absent. */
    List<String> getVectorLayerIds();

    /** True when the metadata {@code format} is {@code pbf}, i.e. the tiles are vector tiles. */
    boolean isVector();

    /** Last-modified time of the archive file, in milliseconds since the epoch. */
    long getTimestamp();

    /**
     * Reads one tile in XYZ addressing (rows counted from the north).
     *
     * @return the raw MVT protobuf, already un-gzipped, or null when the archive has no such tile
     * @throws IOException when the tile cannot be read or unpacked
     */
    byte[] readTile(int zoom, int x, int y) throws IOException;

    @Override
    void close();
}
