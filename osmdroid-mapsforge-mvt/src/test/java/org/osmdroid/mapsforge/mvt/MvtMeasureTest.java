package org.osmdroid.mapsforge.mvt;

import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Measures coordinate counts and decode time on the Portland file, the numbers the default
 * cache budget is based on. Prints them; asserts only that the default holds a dense 3x3 block.
 */
public class MvtMeasureTest {

    private static final String FILE = "portland-full-202610.mbtiles";

    @Test
    public void measurePortlandZoom14() throws Exception {
        JdbcMbtilesArchive archive = TestData.archive(FILE);
        try {
            List<int[]> keys = new ArrayList<>();
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + TestData.file(FILE).getAbsolutePath());
                 Statement s = c.createStatement();
                 ResultSet r = s.executeQuery("SELECT tile_column, tile_row FROM tiles WHERE zoom_level = 14")) {
                while (r.next()) {
                    keys.add(new int[]{r.getInt(1), MbtilesMetadata.flipRow(14, r.getInt(2))});
                }
            }
            TagInterner interner = new TagInterner();
            Map<Long, Long> weights = new HashMap<>();
            long[] all = new long[keys.size()];
            long[] trailWeights = new long[keys.size()];
            long heaviest = 0;
            int[] heaviestKey = null;
            long totalNanos = 0;
            for (int i = 0; i < keys.size(); i++) {
                int x = keys.get(i)[0];
                int y = keys.get(i)[1];
                byte[] data = archive.readTile(14, x, y);
                long t0 = System.nanoTime();
                MvtMapDataStore.SourceTile tile = MvtMapDataStore.convert(VectorTile.decode(data), 14, x, y,
                        interner.local());
                totalNanos += System.nanoTime() - t0;
                all[i] = tile.weight;
                trailWeights[i] = tile.trailsOnly().weight;
                weights.put(((long) x << 32) | y, tile.weight);
                if (tile.weight > heaviest) {
                    heaviest = tile.weight;
                    heaviestKey = new int[]{x, y};
                }
            }
            long[] sorted = all.clone();
            Arrays.sort(sorted);
            long[] trailSorted = trailWeights.clone();
            Arrays.sort(trailSorted);
            long sum = 0;
            for (long w : all) {
                sum += w;
            }

            // the densest 3x3 block
            long bestBlock = 0;
            int[] bestCenter = null;
            for (int[] k : keys) {
                long block = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        Long w = weights.get(((long) (k[0] + dx) << 32) | (k[1] + dy));
                        block += w == null ? 0 : w;
                    }
                }
                if (block > bestBlock) {
                    bestBlock = block;
                    bestCenter = k;
                }
            }

            // decode + convert time of the heaviest tile, warmed up
            byte[] data = archive.readTile(14, heaviestKey[0], heaviestKey[1]);
            long[] decodeTimes = new long[15];
            long[] convertTimes = new long[15];
            MvtMapDataStore.SourceTile last = null;
            for (int run = 0; run < decodeTimes.length; run++) {
                long t0 = System.nanoTime();
                byte[] raw = MbtilesMetadata.unpack(archive.readStored(14, heaviestKey[0], heaviestKey[1]));
                VectorTile tile = VectorTile.decode(raw);
                long t1 = System.nanoTime();
                last = MvtMapDataStore.convert(tile, 14, heaviestKey[0], heaviestKey[1], interner.local());
                long t2 = System.nanoTime();
                decodeTimes[run] = t1 - t0;
                convertTimes[run] = t2 - t1;
            }
            Arrays.sort(decodeTimes);
            Arrays.sort(convertTimes);

            // heap held by the densest block's decoded tiles
            System.gc();
            Thread.sleep(100);
            Runtime rt = Runtime.getRuntime();
            long before = rt.totalMemory() - rt.freeMemory();
            List<MvtMapDataStore.SourceTile> held = new ArrayList<>();
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    byte[] d = archive.readTile(14, bestCenter[0] + dx, bestCenter[1] + dy);
                    if (d != null) {
                        held.add(MvtMapDataStore.convert(VectorTile.decode(d), 14, bestCenter[0] + dx,
                                bestCenter[1] + dy, interner.local()));
                    }
                }
            }
            System.gc();
            Thread.sleep(100);
            long after = rt.totalMemory() - rt.freeMemory();

            System.out.println("portland z14 tiles: " + keys.size()
                    + ", coordinates per tile: median " + sorted[sorted.length / 2]
                    + ", p90 " + sorted[sorted.length * 9 / 10]
                    + ", p99 " + sorted[sorted.length * 99 / 100]
                    + ", max " + heaviest + " (" + heaviestKey[0] + "/" + heaviestKey[1] + ")"
                    + ", mean " + sum / keys.size());
            System.out.println("trail coordinates per z14 tile: median " + trailSorted[trailSorted.length / 2]
                    + ", p90 " + trailSorted[trailSorted.length * 9 / 10] + ", max " + trailSorted[trailSorted.length - 1]);
            System.out.println("densest 3x3 block around " + bestCenter[0] + "/" + bestCenter[1] + ": " + bestBlock
                    + " coordinates; held heap " + (after - before) / 1024 + " KiB for " + held.size() + " tiles ("
                    + (after - before) / Math.max(1, bestBlock) + " bytes per coordinate)");
            System.out.println("heaviest tile " + data.length + " bytes unpacked, " + last.ways.length + " ways, "
                    + last.pois.length + " pois: read+unzip+decode median " + decodeTimes[7] / 1000 + " us, convert median "
                    + convertTimes[7] / 1000 + " us; all " + keys.size() + " tiles decode+convert "
                    + totalNanos / 1_000_000 + " ms (first pass, cold JIT)");
            Assert.assertTrue("default budget holds the densest 3x3 block",
                    MvtMapDataStore.DEFAULT_CACHE_COORDINATES >= bestBlock);
        } finally {
            archive.close();
        }
    }
}
