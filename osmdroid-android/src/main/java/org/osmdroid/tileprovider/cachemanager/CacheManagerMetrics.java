package org.osmdroid.tileprovider.cachemanager;

import android.os.Build;
import android.util.Log;

import org.osmdroid.api.IMapView;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive metrics collection and reporting for CacheManager operations.
 * Tracks performance, errors, cache efficiency, and resource utilization.
 * 
 * @author osmdroid
 * @since 6.2.0
 */
public class CacheManagerMetrics {
    
    private static final String TAG = "CacheManagerMetrics";
    
    // Performance metrics
    private final AtomicLong totalTilesProcessed = new AtomicLong(0);
    private final AtomicLong totalTilesDownloaded = new AtomicLong(0);
    private final AtomicLong totalTilesDeleted = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);
    private final AtomicLong totalDownloadTimeMs = new AtomicLong(0);
    
    // Error metrics
    private final AtomicInteger totalErrors = new AtomicInteger(0);
    private final AtomicInteger networkErrors = new AtomicInteger(0);
    private final AtomicInteger ioErrors = new AtomicInteger(0);
    private final AtomicInteger retrySuccesses = new AtomicInteger(0);
    
    // Cache metrics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong cacheEvictions = new AtomicLong(0);
    
    // Task metrics
    private final AtomicInteger activeTasks = new AtomicInteger(0);
    private final AtomicInteger completedTasks = new AtomicInteger(0);
    private final AtomicInteger failedTasks = new AtomicInteger(0);
    private final AtomicInteger cancelledTasks = new AtomicInteger(0);
    
    // Thread pool metrics
    private final AtomicInteger peakThreadCount = new AtomicInteger(0);
    private final AtomicLong totalThreadExecutionTimeMs = new AtomicLong(0);
    
    // Per-zoom level metrics
    private final ConcurrentHashMap<Integer, ZoomLevelMetrics> zoomMetrics = new ConcurrentHashMap<>();
    
    // Timing tracking
    private final ConcurrentHashMap<Long, Long> taskStartTimes = new ConcurrentHashMap<>();
    private final long metricsStartTime = System.currentTimeMillis();
    
    /**
     * Records the start of a task.
     * 
     * @param taskId Unique task identifier
     */
    public void recordTaskStart(long taskId) {
        activeTasks.incrementAndGet();
        taskStartTimes.put(taskId, System.currentTimeMillis());
    }
    
    /**
     * Records the completion of a task.
     * 
     * @param taskId Unique task identifier
     * @param tilesProcessed Number of tiles processed
     */
    public void recordTaskComplete(long taskId, int tilesProcessed) {
        activeTasks.decrementAndGet();
        completedTasks.incrementAndGet();
        totalTilesProcessed.addAndGet(tilesProcessed);
        
        Long startTime = taskStartTimes.remove(taskId);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            totalProcessingTimeMs.addAndGet(duration);
        }
    }
    
    /**
     * Records a task failure.
     * 
     * @param taskId Unique task identifier
     * @param tilesProcessed Number of tiles processed before failure
     */
    public void recordTaskFailed(long taskId, int tilesProcessed) {
        activeTasks.decrementAndGet();
        failedTasks.incrementAndGet();
        totalTilesProcessed.addAndGet(tilesProcessed);
        totalErrors.incrementAndGet();
        
        Long startTime = taskStartTimes.remove(taskId);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            totalProcessingTimeMs.addAndGet(duration);
        }
    }
    
    /**
     * Records a task cancellation.
     * 
     * @param taskId Unique task identifier
     */
    public void recordTaskCancelled(long taskId) {
        activeTasks.decrementAndGet();
        cancelledTasks.incrementAndGet();
        taskStartTimes.remove(taskId);
    }
    
    /**
     * Records a tile download.
     * 
     * @param zoomLevel Zoom level of the tile
     * @param durationMs Download duration in milliseconds
     * @param success Whether the download was successful
     */
    public void recordTileDownload(int zoomLevel, long durationMs, boolean success) {
        if (success) {
            totalTilesDownloaded.incrementAndGet();
            totalDownloadTimeMs.addAndGet(durationMs);
        } else {
            totalErrors.incrementAndGet();
        }
        
        getZoomMetrics(zoomLevel).recordDownload(success, durationMs);
    }
    
    /**
     * Records a tile deletion.
     * 
     * @param zoomLevel Zoom level of the tile
     * @param success Whether the deletion was successful
     */
    public void recordTileDeletion(int zoomLevel, boolean success) {
        if (success) {
            totalTilesDeleted.incrementAndGet();
        } else {
            totalErrors.incrementAndGet();
        }
        
        getZoomMetrics(zoomLevel).recordDeletion(success);
    }
    
    /**
     * Records a network error.
     */
    public void recordNetworkError() {
        totalErrors.incrementAndGet();
        networkErrors.incrementAndGet();
    }
    
    /**
     * Records an I/O error.
     */
    public void recordIOError() {
        totalErrors.incrementAndGet();
        ioErrors.incrementAndGet();
    }
    
    /**
     * Records a successful retry.
     */
    public void recordRetrySuccess() {
        retrySuccesses.incrementAndGet();
    }
    
    /**
     * Records a cache hit.
     */
    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }
    
    /**
     * Records a cache miss.
     */
    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }
    
    /**
     * Records a cache eviction.
     */
    public void recordCacheEviction() {
        cacheEvictions.incrementAndGet();
    }
    
    /**
     * Updates the peak thread count if current count is higher.
     * 
     * @param currentThreadCount Current active thread count
     */
    public void updatePeakThreadCount(int currentThreadCount) {
        int current = peakThreadCount.get();
        while (currentThreadCount > current) {
            if (peakThreadCount.compareAndSet(current, currentThreadCount)) {
                break;
            }
            current = peakThreadCount.get();
        }
    }
    
    /**
     * Records thread execution time.
     * 
     * @param executionTimeMs Execution time in milliseconds
     */
    public void recordThreadExecutionTime(long executionTimeMs) {
        totalThreadExecutionTimeMs.addAndGet(executionTimeMs);
    }
    
    /**
     * Gets or creates zoom level metrics.
     * 
     * @param zoomLevel Zoom level
     * @return ZoomLevelMetrics for the specified zoom level
     */
    private ZoomLevelMetrics getZoomMetrics(int zoomLevel) {
        return zoomMetrics.computeIfAbsent(zoomLevel, k -> new ZoomLevelMetrics());
    }
    
    /**
     * Gets a comprehensive metrics snapshot.
     * 
     * @return MetricsSnapshot containing all current metrics
     */
    public MetricsSnapshot getSnapshot() {
        return new MetricsSnapshot(
            totalTilesProcessed.get(),
            totalTilesDownloaded.get(),
            totalTilesDeleted.get(),
            totalProcessingTimeMs.get(),
            totalDownloadTimeMs.get(),
            totalErrors.get(),
            networkErrors.get(),
            ioErrors.get(),
            retrySuccesses.get(),
            cacheHits.get(),
            cacheMisses.get(),
            cacheEvictions.get(),
            activeTasks.get(),
            completedTasks.get(),
            failedTasks.get(),
            cancelledTasks.get(),
            peakThreadCount.get(),
            totalThreadExecutionTimeMs.get(),
            System.currentTimeMillis() - metricsStartTime,
            new ConcurrentHashMap<>(zoomMetrics)
        );
    }
    
    /**
     * Resets all metrics to zero.
     */
    public void reset() {
        totalTilesProcessed.set(0);
        totalTilesDownloaded.set(0);
        totalTilesDeleted.set(0);
        totalProcessingTimeMs.set(0);
        totalDownloadTimeMs.set(0);
        totalErrors.set(0);
        networkErrors.set(0);
        ioErrors.set(0);
        retrySuccesses.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        cacheEvictions.set(0);
        activeTasks.set(0);
        completedTasks.set(0);
        failedTasks.set(0);
        cancelledTasks.set(0);
        peakThreadCount.set(0);
        totalThreadExecutionTimeMs.set(0);
        zoomMetrics.clear();
        taskStartTimes.clear();
    }
    
    /**
     * Logs a summary of current metrics.
     */
    public void logSummary() {
        MetricsSnapshot snapshot = getSnapshot();
        Log.i(TAG, "=== CacheManager Metrics Summary ===");
        Log.i(TAG, String.format("Total tiles processed: %d", snapshot.totalTilesProcessed));
        Log.i(TAG, String.format("Total tiles downloaded: %d", snapshot.totalTilesDownloaded));
        Log.i(TAG, String.format("Total tiles deleted: %d", snapshot.totalTilesDeleted));
        Log.i(TAG, String.format("Total errors: %d (Network: %d, I/O: %d)", 
            snapshot.totalErrors, snapshot.networkErrors, snapshot.ioErrors));
        Log.i(TAG, String.format("Retry successes: %d", snapshot.retrySuccesses));
        Log.i(TAG, String.format("Cache hit ratio: %.2f%%", snapshot.getCacheHitRatio() * 100));
        Log.i(TAG, String.format("Active tasks: %d, Completed: %d, Failed: %d, Cancelled: %d",
            snapshot.activeTasks, snapshot.completedTasks, snapshot.failedTasks, snapshot.cancelledTasks));
        Log.i(TAG, String.format("Average processing time: %.2f ms/tile", snapshot.getAverageProcessingTimePerTile()));
        Log.i(TAG, String.format("Average download time: %.2f ms/tile", snapshot.getAverageDownloadTimePerTile()));
        Log.i(TAG, String.format("Peak thread count: %d", snapshot.peakThreadCount));
        Log.i(TAG, String.format("Uptime: %.2f seconds", snapshot.uptimeMs / 1000.0));
        Log.i(TAG, "===================================");
    }
    
    /**
     * Metrics for a specific zoom level.
     */
    private static class ZoomLevelMetrics {
        private final AtomicInteger downloadsSuccessful = new AtomicInteger(0);
        private final AtomicInteger downloadsFailed = new AtomicInteger(0);
        private final AtomicInteger deletionsSuccessful = new AtomicInteger(0);
        private final AtomicInteger deletionsFailed = new AtomicInteger(0);
        private final AtomicLong totalDownloadTimeMs = new AtomicLong(0);
        
        void recordDownload(boolean success, long durationMs) {
            if (success) {
                downloadsSuccessful.incrementAndGet();
                totalDownloadTimeMs.addAndGet(durationMs);
            } else {
                downloadsFailed.incrementAndGet();
            }
        }
        
        void recordDeletion(boolean success) {
            if (success) {
                deletionsSuccessful.incrementAndGet();
            } else {
                deletionsFailed.incrementAndGet();
            }
        }
        
        int getDownloadsSuccessful() {
            return downloadsSuccessful.get();
        }
        
        int getDownloadsFailed() {
            return downloadsFailed.get();
        }
        
        int getDeletionsSuccessful() {
            return deletionsSuccessful.get();
        }
        
        int getDeletionsFailed() {
            return deletionsFailed.get();
        }
        
        long getTotalDownloadTimeMs() {
            return totalDownloadTimeMs.get();
        }
        
        double getAverageDownloadTimeMs() {
            int successful = downloadsSuccessful.get();
            return successful > 0 ? (double) totalDownloadTimeMs.get() / successful : 0.0;
        }
    }
    
    /**
     * Immutable snapshot of metrics at a point in time.
     */
    public static class MetricsSnapshot {
        public final long totalTilesProcessed;
        public final long totalTilesDownloaded;
        public final long totalTilesDeleted;
        public final long totalProcessingTimeMs;
        public final long totalDownloadTimeMs;
        public final int totalErrors;
        public final int networkErrors;
        public final int ioErrors;
        public final int retrySuccesses;
        public final long cacheHits;
        public final long cacheMisses;
        public final long cacheEvictions;
        public final int activeTasks;
        public final int completedTasks;
        public final int failedTasks;
        public final int cancelledTasks;
        public final int peakThreadCount;
        public final long totalThreadExecutionTimeMs;
        public final long uptimeMs;
        public final ConcurrentHashMap<Integer, ZoomLevelMetrics> zoomMetrics;
        
        MetricsSnapshot(long totalTilesProcessed, long totalTilesDownloaded, long totalTilesDeleted,
                       long totalProcessingTimeMs, long totalDownloadTimeMs,
                       int totalErrors, int networkErrors, int ioErrors, int retrySuccesses,
                       long cacheHits, long cacheMisses, long cacheEvictions,
                       int activeTasks, int completedTasks, int failedTasks, int cancelledTasks,
                       int peakThreadCount, long totalThreadExecutionTimeMs, long uptimeMs,
                       ConcurrentHashMap<Integer, ZoomLevelMetrics> zoomMetrics) {
            this.totalTilesProcessed = totalTilesProcessed;
            this.totalTilesDownloaded = totalTilesDownloaded;
            this.totalTilesDeleted = totalTilesDeleted;
            this.totalProcessingTimeMs = totalProcessingTimeMs;
            this.totalDownloadTimeMs = totalDownloadTimeMs;
            this.totalErrors = totalErrors;
            this.networkErrors = networkErrors;
            this.ioErrors = ioErrors;
            this.retrySuccesses = retrySuccesses;
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.cacheEvictions = cacheEvictions;
            this.activeTasks = activeTasks;
            this.completedTasks = completedTasks;
            this.failedTasks = failedTasks;
            this.cancelledTasks = cancelledTasks;
            this.peakThreadCount = peakThreadCount;
            this.totalThreadExecutionTimeMs = totalThreadExecutionTimeMs;
            this.uptimeMs = uptimeMs;
            this.zoomMetrics = zoomMetrics;
        }
        
        /**
         * Calculates cache hit ratio.
         * 
         * @return Cache hit ratio (0.0 to 1.0)
         */
        public double getCacheHitRatio() {
            long total = cacheHits + cacheMisses;
            return total > 0 ? (double) cacheHits / total : 0.0;
        }
        
        /**
         * Calculates average processing time per tile.
         * 
         * @return Average processing time in milliseconds
         */
        public double getAverageProcessingTimePerTile() {
            return totalTilesProcessed > 0 ? (double) totalProcessingTimeMs / totalTilesProcessed : 0.0;
        }
        
        /**
         * Calculates average download time per tile.
         * 
         * @return Average download time in milliseconds
         */
        public double getAverageDownloadTimePerTile() {
            return totalTilesDownloaded > 0 ? (double) totalDownloadTimeMs / totalTilesDownloaded : 0.0;
        }
        
        /**
         * Calculates error rate.
         * 
         * @return Error rate (0.0 to 1.0)
         */
        public double getErrorRate() {
            return totalTilesProcessed > 0 ? (double) totalErrors / totalTilesProcessed : 0.0;
        }
        
        /**
         * Calculates task success rate.
         * 
         * @return Task success rate (0.0 to 1.0)
         */
        public double getTaskSuccessRate() {
            int total = completedTasks + failedTasks;
            return total > 0 ? (double) completedTasks / total : 0.0;
        }
        
        /**
         * Gets metrics for a specific zoom level.
         * 
         * @param zoomLevel Zoom level
         * @return ZoomLevelMetrics or null if no data for that zoom level
         */
        public ZoomLevelMetrics getZoomMetrics(int zoomLevel) {
            return zoomMetrics.get(zoomLevel);
        }
        
        /**
         * Formats the metrics as a human-readable string.
         * 
         * @return Formatted metrics string
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("CacheManager Metrics:\n");
            sb.append(String.format("  Tiles: %d processed, %d downloaded, %d deleted\n",
                totalTilesProcessed, totalTilesDownloaded, totalTilesDeleted));
            sb.append(String.format("  Errors: %d total (%.2f%%), %d network, %d I/O, %d retry successes\n",
                totalErrors, getErrorRate() * 100, networkErrors, ioErrors, retrySuccesses));
            sb.append(String.format("  Cache: %.2f%% hit ratio, %d hits, %d misses, %d evictions\n",
                getCacheHitRatio() * 100, cacheHits, cacheMisses, cacheEvictions));
            sb.append(String.format("  Tasks: %d active, %d completed, %d failed, %d cancelled (%.2f%% success)\n",
                activeTasks, completedTasks, failedTasks, cancelledTasks, getTaskSuccessRate() * 100));
            sb.append(String.format("  Performance: %.2f ms/tile avg processing, %.2f ms/tile avg download\n",
                getAverageProcessingTimePerTile(), getAverageDownloadTimePerTile()));
            sb.append(String.format("  Threads: %d peak count, %d ms total execution time\n",
                peakThreadCount, totalThreadExecutionTimeMs));
            sb.append(String.format("  Uptime: %.2f seconds", uptimeMs / 1000.0));
            return sb.toString();
        }
    }
}
