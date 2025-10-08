package org.osmdroid.tileprovider.cachemanager;

/**
 * Configuration for progress reporting behavior.
 * <p>
 * Controls how frequently progress updates are reported and whether detailed
 * statistics are included. Proper configuration balances responsiveness with
 * performance overhead.
 * </p>
 * 
 * <h3>Usage Examples:</h3>
 * 
 * <h4>Default Configuration:</h4>
 * <pre>{@code
 * // Updates every 500ms, batch size 10, no detailed reporting
 * ProgressConfig config = new ProgressConfig();
 * }</pre>
 * 
 * <h4>Responsive UI Configuration:</h4>
 * <pre>{@code
 * ProgressConfig config = new ProgressConfig.Builder()
 *     .setUpdateIntervalMs(100)              // Update every 100ms
 *     .setBatchSize(5)                       // Smaller batches
 *     .setEnableDetailedReporting(true)      // Include statistics
 *     .build();
 * }</pre>
 * 
 * <h4>Performance-Optimized Configuration:</h4>
 * <pre>{@code
 * ProgressConfig config = new ProgressConfig.Builder()
 *     .setUpdateIntervalMs(1000)             // Update every second
 *     .setBatchSize(50)                      // Larger batches
 *     .setEnableDetailedReporting(false)     // Minimal overhead
 *     .build();
 * }</pre>
 * 
 * <h4>Using with ProgressReporter:</h4>
 * <pre>{@code
 * ProgressConfig config = new ProgressConfig.Builder()
 *     .setUpdateIntervalMs(200)
 *     .build();
 * 
 * ProgressReporter reporter = new ProgressReporter(
 *     config.getUpdateIntervalMs(),
 *     2  // Min progress delta percentage
 * );
 * 
 * reporter.initialize(10000, 10, 15);
 * 
 * for (Tile tile : tiles) {
 *     boolean success = processTile(tile);
 *     if (reporter.updateProgress(tile.getZoomLevel(), success)) {
 *         // Update UI only when threshold is met
 *         updateProgressBar(reporter.getProgressPercentage());
 *     }
 * }
 * }</pre>
 * 
 * <h4>Batch Progress Updates:</h4>
 * <pre>{@code
 * ProgressConfig config = new ProgressConfig.Builder()
 *     .setBatchSize(100)                     // Process 100 items per batch
 *     .build();
 * 
 * ProgressReporter reporter = new ProgressReporter();
 * 
 * // Process tiles in batches
 * for (List<Tile> batch : tileBatches) {
 *     int successCount = processBatch(batch);
 *     reporter.updateProgressBulk(
 *         batch.size(),
 *         currentZoom,
 *         successCount
 *     );
 * }
 * }</pre>
 * 
 * @author osmdroid
 * @since 6.2.0
 * @see ProgressReporter
 */
public class ProgressConfig {
    
    final long updateIntervalMs;
    final int batchSize;
    final boolean enableDetailedReporting;
    
    /**
     * Creates a ProgressConfig with default values.
     */
    public ProgressConfig() {
        this(500L, 10, false);
    }
    
    /**
     * Creates a ProgressConfig with specified values.
     * 
     * @param updateIntervalMs Minimum interval between progress updates in milliseconds
     * @param batchSize Number of operations to batch before reporting
     * @param enableDetailedReporting Whether to include detailed statistics
     */
    public ProgressConfig(long updateIntervalMs, int batchSize, 
                         boolean enableDetailedReporting) {
        this.updateIntervalMs = updateIntervalMs;
        this.batchSize = batchSize;
        this.enableDetailedReporting = enableDetailedReporting;
    }
    
    public long getUpdateIntervalMs() {
        return updateIntervalMs;
    }
    
    public int getBatchSize() {
        return batchSize;
    }
    
    public boolean isEnableDetailedReporting() {
        return enableDetailedReporting;
    }
    
    /**
     * Builder for ProgressConfig.
     */
    public static class Builder {
        private long updateIntervalMs = 500L;
        private int batchSize = 10;
        private boolean enableDetailedReporting = false;
        
        public Builder setUpdateIntervalMs(long updateIntervalMs) {
            this.updateIntervalMs = updateIntervalMs;
            return this;
        }
        
        public Builder setBatchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }
        
        public Builder setEnableDetailedReporting(boolean enableDetailedReporting) {
            this.enableDetailedReporting = enableDetailedReporting;
            return this;
        }
        
        public ProgressConfig build() {
            return new ProgressConfig(
                updateIntervalMs,
                batchSize,
                enableDetailedReporting
            );
        }
    }
}
