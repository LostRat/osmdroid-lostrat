package org.osmdroid.tileprovider.cachemanager;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.osmdroid.api.IMapView;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe progress reporter with efficient batched updates.
 * Implements configurable progress reporting intervals to minimize UI thread overhead
 * while providing accurate progress information.
 * 
 * @author CacheManager Optimization Team
 * @since 6.2.0
 */
public class ProgressReporter {
    
    private static final String TAG = "ProgressReporter";
    
    // Default configuration values
    private static final long DEFAULT_UPDATE_INTERVAL_MS = 100; // Update every 100ms
    private static final int DEFAULT_MIN_PROGRESS_DELTA = 1; // Update if progress changed by at least 1%
    
    // Configuration
    private final long updateIntervalMs;
    private final int minProgressDelta;
    private final Handler mainHandler;
    
    // Thread-safe progress tracking
    private final AtomicInteger currentProgress = new AtomicInteger(0);
    private final AtomicInteger totalItems = new AtomicInteger(0);
    private final AtomicInteger currentZoomLevel = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicLong lastUpdateTime = new AtomicLong(0);
    private final AtomicInteger lastReportedProgress = new AtomicInteger(0);
    
    // Statistics
    private final AtomicLong startTime = new AtomicLong(0);
    private final AtomicLong endTime = new AtomicLong(0);
    
    /**
     * Creates a ProgressReporter with default configuration.
     */
    public ProgressReporter() {
        this(DEFAULT_UPDATE_INTERVAL_MS, DEFAULT_MIN_PROGRESS_DELTA);
    }
    
    /**
     * Creates a ProgressReporter with custom configuration.
     * 
     * @param updateIntervalMs Minimum time between progress updates in milliseconds
     * @param minProgressDelta Minimum progress change (in percentage points) to trigger update
     */
    public ProgressReporter(long updateIntervalMs, int minProgressDelta) {
        this.updateIntervalMs = updateIntervalMs;
        this.minProgressDelta = minProgressDelta;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Initializes the progress reporter for a new task.
     * 
     * @param total Total number of items to process
     * @param zoomMin Minimum zoom level
     * @param zoomMax Maximum zoom level
     */
    public void initialize(int total, int zoomMin, int zoomMax) {
        totalItems.set(total);
        currentProgress.set(0);
        currentZoomLevel.set(zoomMin);
        successCount.set(0);
        errorCount.set(0);
        lastUpdateTime.set(0);
        lastReportedProgress.set(0);
        startTime.set(System.currentTimeMillis());
        endTime.set(0);
    }
    
    /**
     * Updates progress for a single processed item.
     * Progress updates are batched based on configured intervals.
     * 
     * @param zoomLevel Current zoom level being processed
     * @param success Whether the item was processed successfully
     * @return true if a progress update was triggered
     */
    public boolean updateProgress(int zoomLevel, boolean success) {
        currentProgress.incrementAndGet();
        currentZoomLevel.set(zoomLevel);
        
        if (success) {
            successCount.incrementAndGet();
        } else {
            errorCount.incrementAndGet();
        }
        
        return shouldTriggerUpdate();
    }
    
    /**
     * Updates progress for multiple processed items (bulk operation).
     * 
     * @param count Number of items processed
     * @param zoomLevel Current zoom level being processed
     * @param successfulCount Number of successful items
     * @return true if a progress update was triggered
     */
    public boolean updateProgressBulk(int count, int zoomLevel, int successfulCount) {
        currentProgress.addAndGet(count);
        currentZoomLevel.set(zoomLevel);
        successCount.addAndGet(successfulCount);
        errorCount.addAndGet(count - successfulCount);
        
        return shouldTriggerUpdate();
    }
    
    /**
     * Determines if a progress update should be triggered based on time and progress delta.
     * 
     * @return true if update should be triggered
     */
    private boolean shouldTriggerUpdate() {
        long now = System.currentTimeMillis();
        long lastUpdate = lastUpdateTime.get();
        
        // Check time-based throttling
        if (now - lastUpdate < updateIntervalMs) {
            return false;
        }
        
        // Check progress delta
        int current = currentProgress.get();
        int total = totalItems.get();
        int lastReported = lastReportedProgress.get();
        
        if (total > 0) {
            int currentPercent = (current * 100) / total;
            int lastPercent = (lastReported * 100) / total;
            
            if (Math.abs(currentPercent - lastPercent) < minProgressDelta) {
                return false;
            }
        }
        
        // Update should be triggered
        if (lastUpdateTime.compareAndSet(lastUpdate, now)) {
            lastReportedProgress.set(current);
            return true;
        }
        
        return false;
    }
    
    /**
     * Forces a progress update regardless of throttling settings.
     * Useful for final updates or important milestones.
     */
    public void forceUpdate() {
        lastUpdateTime.set(0); // Reset to force next update
        lastReportedProgress.set(currentProgress.get());
    }
    
    /**
     * Marks the task as complete and records end time.
     */
    public void markComplete() {
        endTime.set(System.currentTimeMillis());
        forceUpdate();
    }
    
    /**
     * Gets the current progress count.
     * 
     * @return Current number of processed items
     */
    public int getCurrentProgress() {
        return currentProgress.get();
    }
    
    /**
     * Gets the total number of items.
     * 
     * @return Total items to process
     */
    public int getTotalItems() {
        return totalItems.get();
    }
    
    /**
     * Gets the current zoom level being processed.
     * 
     * @return Current zoom level
     */
    public int getCurrentZoomLevel() {
        return currentZoomLevel.get();
    }
    
    /**
     * Gets the number of successfully processed items.
     * 
     * @return Success count
     */
    public int getSuccessCount() {
        return successCount.get();
    }
    
    /**
     * Gets the number of items that failed processing.
     * 
     * @return Error count
     */
    public int getErrorCount() {
        return errorCount.get();
    }
    
    /**
     * Gets the progress percentage (0-100).
     * 
     * @return Progress percentage
     */
    public int getProgressPercentage() {
        int total = totalItems.get();
        if (total == 0) {
            return 0;
        }
        return (currentProgress.get() * 100) / total;
    }
    
    /**
     * Gets the elapsed time in milliseconds.
     * 
     * @return Elapsed time, or 0 if not started
     */
    public long getElapsedTimeMs() {
        long start = startTime.get();
        if (start == 0) {
            return 0;
        }
        
        long end = endTime.get();
        if (end == 0) {
            return System.currentTimeMillis() - start;
        }
        
        return end - start;
    }
    
    /**
     * Gets estimated time remaining in milliseconds based on current progress.
     * 
     * @return Estimated time remaining, or -1 if cannot be estimated
     */
    public long getEstimatedTimeRemainingMs() {
        int current = currentProgress.get();
        int total = totalItems.get();
        
        if (current == 0 || total == 0) {
            return -1;
        }
        
        long elapsed = getElapsedTimeMs();
        if (elapsed == 0) {
            return -1;
        }
        
        long estimatedTotal = (elapsed * total) / current;
        return estimatedTotal - elapsed;
    }
    
    /**
     * Gets comprehensive progress statistics.
     * 
     * @return ProgressStatistics object
     */
    public ProgressStatistics getStatistics() {
        return new ProgressStatistics(
            currentProgress.get(),
            totalItems.get(),
            currentZoomLevel.get(),
            successCount.get(),
            errorCount.get(),
            getElapsedTimeMs(),
            getEstimatedTimeRemainingMs(),
            getProgressPercentage()
        );
    }
    
    /**
     * Resets all progress tracking.
     */
    public void reset() {
        currentProgress.set(0);
        totalItems.set(0);
        currentZoomLevel.set(0);
        successCount.set(0);
        errorCount.set(0);
        lastUpdateTime.set(0);
        lastReportedProgress.set(0);
        startTime.set(0);
        endTime.set(0);
    }
    
    /**
     * Posts a runnable to the main UI thread.
     * Useful for updating UI components from background threads.
     * 
     * @param runnable Runnable to execute on main thread
     */
    public void postToMainThread(Runnable runnable) {
        if (runnable != null) {
            mainHandler.post(runnable);
        }
    }
    
    /**
     * Posts a delayed runnable to the main UI thread.
     * 
     * @param runnable Runnable to execute on main thread
     * @param delayMs Delay in milliseconds
     */
    public void postDelayedToMainThread(Runnable runnable, long delayMs) {
        if (runnable != null) {
            mainHandler.postDelayed(runnable, delayMs);
        }
    }
    
    /**
     * Immutable snapshot of progress statistics.
     */
    public static class ProgressStatistics {
        public final int currentProgress;
        public final int totalItems;
        public final int currentZoomLevel;
        public final int successCount;
        public final int errorCount;
        public final long elapsedTimeMs;
        public final long estimatedTimeRemainingMs;
        public final int progressPercentage;
        
        public ProgressStatistics(int currentProgress, int totalItems, int currentZoomLevel,
                                 int successCount, int errorCount, long elapsedTimeMs,
                                 long estimatedTimeRemainingMs, int progressPercentage) {
            this.currentProgress = currentProgress;
            this.totalItems = totalItems;
            this.currentZoomLevel = currentZoomLevel;
            this.successCount = successCount;
            this.errorCount = errorCount;
            this.elapsedTimeMs = elapsedTimeMs;
            this.estimatedTimeRemainingMs = estimatedTimeRemainingMs;
            this.progressPercentage = progressPercentage;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Progress: %d/%d (%d%%), Zoom: %d, Success: %d, Errors: %d, Elapsed: %dms, ETA: %dms",
                currentProgress, totalItems, progressPercentage, currentZoomLevel,
                successCount, errorCount, elapsedTimeMs, estimatedTimeRemainingMs
            );
        }
        
        /**
         * Gets the success rate as a percentage (0-100).
         * 
         * @return Success rate percentage
         */
        public int getSuccessRate() {
            int processed = successCount + errorCount;
            if (processed == 0) {
                return 100;
            }
            return (successCount * 100) / processed;
        }
        
        /**
         * Gets the average processing time per item in milliseconds.
         * 
         * @return Average time per item, or 0 if no items processed
         */
        public long getAverageTimePerItem() {
            if (currentProgress == 0) {
                return 0;
            }
            return elapsedTimeMs / currentProgress;
        }
        
        /**
         * Checks if the task is complete.
         * 
         * @return true if all items have been processed
         */
        public boolean isComplete() {
            return currentProgress >= totalItems && totalItems > 0;
        }
    }
}
