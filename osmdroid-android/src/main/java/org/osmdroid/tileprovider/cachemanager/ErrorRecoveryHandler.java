package org.osmdroid.tileprovider.cachemanager;

import android.util.Log;

import org.osmdroid.tileprovider.modules.CantContinueException;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles error recovery and logging for tile operations with categorization and statistics tracking.
 * Provides graceful degradation strategies for persistent failures.
 * 
 * @author CacheManager Optimization Team
 * @since 6.2.0
 */
public class ErrorRecoveryHandler {
    
    private static final String TAG = "ErrorRecoveryHandler";
    
    /**
     * Error statistics for monitoring and diagnostics
     */
    public static class ErrorStatistics {
        private final AtomicInteger totalErrors = new AtomicInteger(0);
        private final AtomicInteger networkErrors = new AtomicInteger(0);
        private final AtomicInteger ioErrors = new AtomicInteger(0);
        private final AtomicInteger serverErrors = new AtomicInteger(0);
        private final AtomicInteger clientErrors = new AtomicInteger(0);
        private final AtomicInteger unknownErrors = new AtomicInteger(0);
        private final AtomicInteger retriedErrors = new AtomicInteger(0);
        private final AtomicInteger recoveredErrors = new AtomicInteger(0);
        private final AtomicLong lastErrorTimestamp = new AtomicLong(0);
        
        public int getTotalErrors() {
            return totalErrors.get();
        }
        
        public int getNetworkErrors() {
            return networkErrors.get();
        }
        
        public int getIoErrors() {
            return ioErrors.get();
        }
        
        public int getServerErrors() {
            return serverErrors.get();
        }
        
        public int getClientErrors() {
            return clientErrors.get();
        }
        
        public int getUnknownErrors() {
            return unknownErrors.get();
        }
        
        public int getRetriedErrors() {
            return retriedErrors.get();
        }
        
        public int getRecoveredErrors() {
            return recoveredErrors.get();
        }
        
        public long getLastErrorTimestamp() {
            return lastErrorTimestamp.get();
        }
        
        void incrementTotal() {
            totalErrors.incrementAndGet();
            lastErrorTimestamp.set(System.currentTimeMillis());
        }
        
        void incrementCategory(RetryPolicy.ErrorCategory category) {
            switch (category) {
                case NETWORK:
                    networkErrors.incrementAndGet();
                    break;
                case IO:
                    ioErrors.incrementAndGet();
                    break;
                case SERVER:
                    serverErrors.incrementAndGet();
                    break;
                case CLIENT:
                    clientErrors.incrementAndGet();
                    break;
                case UNKNOWN:
                default:
                    unknownErrors.incrementAndGet();
                    break;
            }
        }
        
        void incrementRetried() {
            retriedErrors.incrementAndGet();
        }
        
        void incrementRecovered() {
            recoveredErrors.incrementAndGet();
        }
        
        public void reset() {
            totalErrors.set(0);
            networkErrors.set(0);
            ioErrors.set(0);
            serverErrors.set(0);
            clientErrors.set(0);
            unknownErrors.set(0);
            retriedErrors.set(0);
            recoveredErrors.set(0);
            lastErrorTimestamp.set(0);
        }
        
        @Override
        public String toString() {
            return String.format(
                "ErrorStatistics{total=%d, network=%d, io=%d, server=%d, client=%d, unknown=%d, retried=%d, recovered=%d}",
                getTotalErrors(), getNetworkErrors(), getIoErrors(), getServerErrors(),
                getClientErrors(), getUnknownErrors(), getRetriedErrors(), getRecoveredErrors()
            );
        }
    }
    
    private final RetryPolicy retryPolicy;
    private final ErrorStatistics statistics;
    private final ConcurrentHashMap<Long, Integer> tileRetryCount;
    private final boolean enableLogging;
    private final boolean enableDetailedLogging;
    
    /**
     * Creates an error recovery handler with default retry policy and logging enabled.
     */
    public ErrorRecoveryHandler() {
        this(new RetryPolicy(), true, false);
    }
    
    /**
     * Creates an error recovery handler with custom retry policy.
     * 
     * @param retryPolicy The retry policy to use
     */
    public ErrorRecoveryHandler(RetryPolicy retryPolicy) {
        this(retryPolicy, true, false);
    }
    
    /**
     * Creates an error recovery handler from RetryConfig.
     * 
     * @param retryConfig Retry configuration
     * @since 6.2.0
     */
    public ErrorRecoveryHandler(RetryConfig retryConfig) {
        this(new RetryPolicy(retryConfig), true, false);
    }
    
    /**
     * Creates an error recovery handler with custom settings.
     * 
     * @param retryPolicy The retry policy to use
     * @param enableLogging Whether to enable error logging
     * @param enableDetailedLogging Whether to enable detailed error logging (includes stack traces)
     */
    public ErrorRecoveryHandler(RetryPolicy retryPolicy, boolean enableLogging, boolean enableDetailedLogging) {
        this.retryPolicy = retryPolicy;
        this.statistics = new ErrorStatistics();
        this.tileRetryCount = new ConcurrentHashMap<>();
        this.enableLogging = enableLogging;
        this.enableDetailedLogging = enableDetailedLogging;
    }
    
    /**
     * Handles a tile download error and determines if retry should be attempted.
     * 
     * @param tileIndex The tile index that failed
     * @param error The exception that occurred
     * @return true if the operation should be retried, false otherwise
     */
    public boolean handleTileDownloadError(long tileIndex, Exception error) {
        // Update statistics
        statistics.incrementTotal();
        RetryPolicy.ErrorCategory category = retryPolicy.categorizeError(error);
        statistics.incrementCategory(category);
        
        // Get current retry count for this tile
        int attemptNumber = tileRetryCount.compute(tileIndex, (k, v) -> v == null ? 1 : v + 1);
        
        // Determine if we should retry
        boolean shouldRetry = retryPolicy.shouldRetry(error, attemptNumber);
        
        if (shouldRetry) {
            statistics.incrementRetried();
            
            if (enableLogging) {
                long delay = retryPolicy.calculateDelay(attemptNumber);
                Log.w(TAG, String.format(
                    "Tile download failed (attempt %d/%d) for tile %d, category: %s, retrying in %dms: %s",
                    attemptNumber, retryPolicy.getMaxRetries(), tileIndex, category, delay,
                    error.getMessage()
                ));
            }
            
            if (enableDetailedLogging) {
                Log.d(TAG, "Detailed error for tile " + tileIndex, error);
            }
        } else {
            // Max retries exceeded or non-retryable error
            if (enableLogging) {
                Log.e(TAG, String.format(
                    "Tile download permanently failed for tile %d after %d attempts, category: %s: %s",
                    tileIndex, attemptNumber, category, error.getMessage()
                ));
            }
            
            // Clean up retry count for this tile
            tileRetryCount.remove(tileIndex);
        }
        
        return shouldRetry;
    }
    
    /**
     * Handles a cache write error.
     * 
     * @param tileIndex The tile index that failed to write
     * @param error The exception that occurred
     */
    public void handleCacheWriteError(long tileIndex, Exception error) {
        statistics.incrementTotal();
        RetryPolicy.ErrorCategory category = retryPolicy.categorizeError(error);
        statistics.incrementCategory(category);
        
        if (enableLogging) {
            Log.e(TAG, String.format(
                "Cache write failed for tile %d, category: %s: %s",
                tileIndex, category, error.getMessage()
            ));
        }
        
        if (enableDetailedLogging) {
            Log.d(TAG, "Detailed cache write error for tile " + tileIndex, error);
        }
    }
    
    /**
     * Handles a network error.
     * 
     * @param error The exception that occurred
     */
    public void handleNetworkError(Exception error) {
        statistics.incrementTotal();
        statistics.incrementCategory(RetryPolicy.ErrorCategory.NETWORK);
        
        if (enableLogging) {
            Log.w(TAG, "Network error occurred: " + error.getMessage());
        }
        
        if (enableDetailedLogging) {
            Log.d(TAG, "Detailed network error", error);
        }
    }
    
    /**
     * Handles a memory error with graceful degradation.
     * 
     * @param error The OutOfMemoryError that occurred
     */
    public void handleMemoryError(OutOfMemoryError error) {
        if (enableLogging) {
            Log.e(TAG, "Memory error occurred - attempting graceful degradation: " + error.getMessage());
        }
        
        // Trigger garbage collection
        System.gc();
        
        // Clear retry counts to free memory
        int clearedEntries = tileRetryCount.size();
        tileRetryCount.clear();
        
        if (enableLogging) {
            Log.w(TAG, String.format(
                "Cleared %d retry count entries to free memory",
                clearedEntries
            ));
        }
    }
    
    /**
     * Marks a tile operation as successfully recovered after retry.
     * 
     * @param tileIndex The tile index that was recovered
     */
    public void markRecovered(long tileIndex) {
        Integer retryCount = tileRetryCount.remove(tileIndex);
        if (retryCount != null && retryCount > 1) {
            statistics.incrementRecovered();
            
            if (enableDetailedLogging) {
                Log.d(TAG, String.format(
                    "Tile %d successfully recovered after %d attempts",
                    tileIndex, retryCount
                ));
            }
        }
    }
    
    /**
     * Gets the current retry count for a specific tile.
     * 
     * @param tileIndex The tile index
     * @return The current retry count (0 if no retries yet)
     */
    public int getRetryCount(long tileIndex) {
        return tileRetryCount.getOrDefault(tileIndex, 0);
    }
    
    /**
     * Calculates the delay before the next retry for a specific tile.
     * 
     * @param tileIndex The tile index
     * @return Delay in milliseconds, or 0 if no retry is needed
     */
    public long getRetryDelay(long tileIndex) {
        int attemptNumber = getRetryCount(tileIndex);
        if (attemptNumber == 0) {
            return 0;
        }
        return retryPolicy.calculateDelay(attemptNumber);
    }
    
    /**
     * Gets the error statistics.
     * 
     * @return The error statistics
     */
    public ErrorStatistics getStatistics() {
        return statistics;
    }
    
    /**
     * Gets the retry policy.
     * 
     * @return The retry policy
     */
    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }
    
    /**
     * Resets all error statistics and retry counts.
     */
    public void reset() {
        statistics.reset();
        tileRetryCount.clear();
    }
    
    /**
     * Clears retry count for a specific tile.
     * 
     * @param tileIndex The tile index to clear
     */
    public void clearRetryCount(long tileIndex) {
        tileRetryCount.remove(tileIndex);
    }
    
    /**
     * Gets the total number of tiles currently being tracked for retry.
     * 
     * @return Number of tiles with retry counts
     */
    public int getTrackedTileCount() {
        return tileRetryCount.size();
    }
    
    /**
     * Checks if logging is enabled.
     * 
     * @return true if logging is enabled
     */
    public boolean isLoggingEnabled() {
        return enableLogging;
    }
    
    /**
     * Checks if detailed logging is enabled.
     * 
     * @return true if detailed logging is enabled
     */
    public boolean isDetailedLoggingEnabled() {
        return enableDetailedLogging;
    }
}
