package org.osmdroid.mapsforge.mvt;

import org.junit.Assume;

import java.io.File;
import java.io.IOException;

/** The real tilemaker files, found by system property {@code mvt.testdata}; tests are skipped without them. */
final class TestData {
    static final File DIR = new File(System.getProperty("mvt.testdata", "E:/202610-mbtiles"));

    private TestData() {
    }

    static File file(String name) {
        File file = new File(DIR, name);
        Assume.assumeTrue("test data missing: " + file, file.isFile());
        return file;
    }

    static JdbcMbtilesArchive archive(String name) throws IOException {
        return new JdbcMbtilesArchive(file(name));
    }

    /** Passes everything to another archive, counting tile reads. */
    static final class Counting implements VectorTileArchive {
        final VectorTileArchive inner;
        final java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();

        Counting(VectorTileArchive inner) {
            this.inner = inner;
        }

        @Override
        public int getMinZoom() {
            return inner.getMinZoom();
        }

        @Override
        public int getMaxZoom() {
            return inner.getMaxZoom();
        }

        @Override
        public org.mapsforge.core.model.BoundingBox getBoundingBox() {
            return inner.getBoundingBox();
        }

        @Override
        public java.util.List<String> getVectorLayerIds() {
            return inner.getVectorLayerIds();
        }

        @Override
        public boolean isVector() {
            return inner.isVector();
        }

        @Override
        public long getTimestamp() {
            return inner.getTimestamp();
        }

        @Override
        public byte[] readTile(int zoom, int x, int y) throws IOException {
            reads.incrementAndGet();
            return inner.readTile(zoom, x, y);
        }

        @Override
        public void close() {
            inner.close();
        }
    }
}
