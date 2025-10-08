package org.osmdroid.tileprovider.cachemanager;

import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Optimizes bulk tile operations through intelligent batching and parallel processing.
 * Provides performance improvements for download and cleanup operations by:
 * - Batching tiles into optimal chunk sizes based on device capabilities
 * - Parallel processing of independent tile operations on API 24+ devices
 * - Adaptive batch sizing based on operation type and device resources
 * 
 * @author CacheManager Optimization Team
 * @since 6.2.0
 */
public class BulkOperationOptimizer {
    
    private static final String TAG = "BulkOperationOptimizer";
    
    // Default batch sizes optimized for different device capabilities
    private static final int DEFAULT_BATCH_SIZE_LOW_END = 50;
    private static final int DEFAULT_BATCH_SIZE_MID_RANGE = 100;
    private static final int DEFAULT_BATCH_SIZE_HIGH_END = 200;
    
    // Minimum tiles to justify parallel processing overhead
    private static final int MIN_TILES_FOR_PARALLEL = 100;
    
    private final ThreadPoolManager threadPoolManager;
    private final int batchSize;
    private final boolean enableParallelProcessing;
    
    /**
     * Creates a BulkOperationOptimizer with default settings based on device capabilities.
     * 
     * @param threadPoolManager Thread pool manager for parallel execution
     */
    public BulkOperationOptimizer(ThreadPoolManager threadPoolManager) {
        this(threadPoolManager, determineOptimalBatchSize(), shouldEnableParallelProcessing());
    }
    
    /**
     * Creates a BulkOperationOptimizer with custom settings.
     * 
     * @param threadPoolManager Thread pool manager for parallel execution
     * @param batchSize Number of tiles to process in each batch
     * @param enableParallelProcessing Whether to enable parallel batch processing
     */
    public BulkOperationOptimizer(ThreadPoolManager threadPoolManager, 
                                  int batchSize, 
                                  boolean enableParallelProcessing) {
        this.threadPoolManager = threadPoolManager;
        this.batchSize = Math.max(1, batchSize);
        this.enableParallelProcessing = enableParallelProcessing;
        
        Log.d(TAG, String.format("BulkOperationOptimizer initialized: batchSize=%d, parallel=%b", 
                this.batchSize, this.enableParallelProcessing));
    }
    
    /**
     * Determines optimal batch size based on device capabilities.
     * 
     * @return Recommended batch size for this device
     */
    private static int determineOptimalBatchSize() {
        // Get available processors
        int processors = Runtime.getRuntime().availableProcessors();
        
        // Get available memory (rough estimate)
        long maxMemory = Runtime.getRuntime().maxMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long availableMemory = maxMemory - (totalMemory - freeMemory);
        
        // Categorize device capability
        if (processors >= 8 && availableMemory > 512 * 1024 * 1024) {
            // High-end device: 8+ cores, 512MB+ available
            return DEFAULT_BATCH_SIZE_HIGH_END;
        } else if (processors >= 4 && availableMemory > 256 * 1024 * 1024) {
            // Mid-range device: 4+ cores, 256MB+ available
            return DEFAULT_BATCH_SIZE_MID_RANGE;
        } else {
            // Low-end device
            return DEFAULT_BATCH_SIZE_LOW_END;
        }
    }
    
    /**
     * Determines if parallel processing should be enabled based on API level and device capabilities.
     * 
     * @return true if parallel processing is recommended
     */
    private static boolean shouldEnableParallelProcessing() {
        // Require API 24+ for ForkJoinPool support
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }
        
        // Require at least 4 cores to benefit from parallelism
        int processors = Runtime.getRuntime().availableProcessors();
        return processors >= 4;
    }
    
    /**
     * Processes a collection of tiles in optimized batches.
     * 
     * @param tiles Collection of tile indices to process
     * @param operation Operation to perform on each tile
     * @return BatchResult containing success/failure counts
     */
    public BatchResult processTilesBatched(Collection<Long> tiles, TileOperation operation) {
        if (tiles == null || tiles.isEmpty()) {
            return new BatchResult(0, 0);
        }
        
        int totalTiles = tiles.size();
        Log.d(TAG, String.format("Processing %d tiles in batches of %d", totalTiles, batchSize));
        
        // Check if parallel processing is beneficial
        boolean useParallel = enableParallelProcessing && totalTiles >= MIN_TILES_FOR_PARALLEL;
        
        if (useParallel) {
            return processTilesParallel(tiles, operation);
        } else {
            return processTilesSequential(tiles, operation);
        }
    }
    
    /**
     * Processes tiles sequentially in batches (for smaller operations or older devices).
     */
    private BatchResult processTilesSequential(Collection<Long> tiles, TileOperation operation) {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        List<Long> batch = new ArrayList<>(batchSize);
        
        for (Long tile : tiles) {
            batch.add(tile);
            
            if (batch.size() >= batchSize) {
                processBatch(batch, operation, successCount, errorCount);
                batch.clear();
            }
        }
        
        // Process remaining tiles
        if (!batch.isEmpty()) {
            processBatch(batch, operation, successCount, errorCount);
        }
        
        return new BatchResult(successCount.get(), errorCount.get());
    }
    
    /**
     * Processes tiles in parallel batches (for larger operations on capable devices).
     */
    private BatchResult processTilesParallel(Collection<Long> tiles, TileOperation operation) {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        // Split tiles into batches
        List<List<Long>> batches = createBatches(tiles);
        
        Log.d(TAG, String.format("Processing %d batches in parallel", batches.size()));
        
        // Get executor for parallel processing
        ExecutorService executor = threadPoolManager.getBulkOperationExecutor();
        
        // Create tasks for each batch
        List<Future<BatchResult>> futures = new ArrayList<>(batches.size());
        
        for (List<Long> batch : batches) {
            Future<BatchResult> future = executor.submit(new Callable<BatchResult>() {
                @Override
                public BatchResult call() {
                    AtomicInteger batchSuccess = new AtomicInteger(0);
                    AtomicInteger batchErrors = new AtomicInteger(0);
                    processBatch(batch, operation, batchSuccess, batchErrors);
                    return new BatchResult(batchSuccess.get(), batchErrors.get());
                }
            });
            futures.add(future);
        }
        
        // Collect results from all batches
        for (Future<BatchResult> future : futures) {
            try {
                BatchResult result = future.get();
                successCount.addAndGet(result.successCount);
                errorCount.addAndGet(result.errorCount);
            } catch (Exception e) {
                Log.e(TAG, "Error processing batch in parallel", e);
                // Count all tiles in failed batch as errors
                errorCount.addAndGet(batchSize);
            }
        }
        
        return new BatchResult(successCount.get(), errorCount.get());
    }
    
    /**
     * Processes a single batch of tiles.
     */
    private void processBatch(List<Long> batch, TileOperation operation, 
                             AtomicInteger successCount, AtomicInteger errorCount) {
        for (Long tile : batch) {
            try {
                boolean success = operation.execute(tile);
                if (success) {
                    successCount.incrementAndGet();
                } else {
                    errorCount.incrementAndGet();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing tile " + tile, e);
                errorCount.incrementAndGet();
            }
        }
    }
    
    /**
     * Splits a collection of tiles into batches.
     */
    private List<List<Long>> createBatches(Collection<Long> tiles) {
        List<List<Long>> batches = new ArrayList<>();
        List<Long> currentBatch = new ArrayList<>(batchSize);
        
        for (Long tile : tiles) {
            currentBatch.add(tile);
            
            if (currentBatch.size() >= batchSize) {
                batches.add(currentBatch);
                currentBatch = new ArrayList<>(batchSize);
            }
        }
        
        // Add remaining tiles
        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }
        
        return batches;
    }
    
    /**
     * Gets the configured batch size.
     * 
     * @return Batch size for tile processing
     */
    public int getBatchSize() {
        return batchSize;
    }
    
    /**
     * Checks if parallel processing is enabled.
     * 
     * @return true if parallel processing is enabled
     */
    public boolean isParallelProcessingEnabled() {
        return enableParallelProcessing;
    }
    
    /**
     * Interface for tile operations that can be batched.
     */
    public interface TileOperation {
        /**
         * Executes an operation on a single tile.
         * 
         * @param tileIndex The tile index to process
         * @return true if operation succeeded, false otherwise
         */
        boolean execute(long tileIndex);
    }
    
    /**
     * Result of a batch operation.
     */
    public static class BatchResult {
        public final int successCount;
        public final int errorCount;
        
        public BatchResult(int successCount, int errorCount) {
            this.successCount = successCount;
            this.errorCount = errorCount;
        }
        
        public int getTotalProcessed() {
            return successCount + errorCount;
        }
        
        public double getSuccessRate() {
            int total = getTotalProcessed();
            return total > 0 ? (double) successCount / total : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("BatchResult{success=%d, errors=%d, rate=%.2f%%}", 
                    successCount, errorCount, getSuccessRate() * 100);
        }
    }
}
