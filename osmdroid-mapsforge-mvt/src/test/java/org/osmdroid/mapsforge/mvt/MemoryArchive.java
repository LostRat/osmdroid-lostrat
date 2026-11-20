package org.osmdroid.mapsforge.mvt;

import org.mapsforge.core.model.BoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Hand-built tiles in memory, counting reads. */
class MemoryArchive implements VectorTileArchive {
    final Map<String, byte[]> tiles = new ConcurrentHashMap<>();
    final AtomicInteger reads = new AtomicInteger();
    final List<String> layerIds;
    final int minZoom;
    final int maxZoom;
    volatile boolean closed;

    MemoryArchive(int minZoom, int maxZoom, String... layerIds) {
        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
        List<String> ids = new ArrayList<>();
        Collections.addAll(ids, layerIds);
        this.layerIds = Collections.unmodifiableList(ids);
    }

    MemoryArchive put(int zoom, int x, int y, byte[] data) {
        tiles.put(zoom + "/" + x + "/" + y, data);
        return this;
    }

    @Override
    public int getMinZoom() {
        return minZoom;
    }

    @Override
    public int getMaxZoom() {
        return maxZoom;
    }

    @Override
    public BoundingBox getBoundingBox() {
        return MbtilesMetadata.world();
    }

    @Override
    public List<String> getVectorLayerIds() {
        return layerIds;
    }

    @Override
    public boolean isVector() {
        return true;
    }

    @Override
    public long getTimestamp() {
        return 1234L;
    }

    @Override
    public byte[] readTile(int zoom, int x, int y) {
        reads.incrementAndGet();
        return tiles.get(zoom + "/" + x + "/" + y);
    }

    @Override
    public void close() {
        closed = true;
    }
}
