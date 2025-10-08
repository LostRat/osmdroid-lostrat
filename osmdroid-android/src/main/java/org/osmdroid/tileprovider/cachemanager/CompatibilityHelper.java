package org.osmdroid.tileprovider.cachemanager;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Helper class providing compatibility wrappers and fallback mechanisms
 * for CacheManager optimizations across different Android API levels.
 * 
 * This class ensures that optimizations gracefully degrade on older devices
 * while maintaining full functionality.
 * 
 * @since 6.2.0
 */
public class CompatibilityHelper {
    private static final String TAG = "CompatibilityHelper";
    
    private final APIVersionAdapter apiAdapter;
    
    /**
     * Creates a new CompatibilityHelper with the current API level.
     */
    public CompatibilityHelper() {
        this(new APIVersionAdapter());
    }
    
    /**
     * Creates a new CompatibilityHelper with a specific API adapter.
     * 
     * @param apiAdapter The API version adapter to use
     */
    public CompatibilityHelper(APIVersionAdapter apiAdapter) {
        this.apiAdapter = apiAdapter;
    }
    
    /**
     * Creates an optimal thread pool based on API level and configuration.
     * Falls back to fixed thread pool on older devices.
     * 
     * @param config The thread pool configuration
     * @return An ExecutorService configured for the current API level
     */
    public ExecutorService createOptimalThreadPool(ThreadPoolConfig config) {
        if (config.isUseWorkStealingPool() && apiAdapter.isForkJoinPoolRecommended()) {
            try {
                // Use ForkJoinPool for API 24+
                return createWorkStealingPool(config.getCorePoolSize());
            } catch (Exception e) {
                Log.w(TAG, "Failed to create ForkJoinPool, falling back to fixed thread pool", e);
                return createFixedThreadPool(config);
            }
        } else {
            // Use traditional fixed thread pool for older APIs
            return createFixedThreadPool(config);
        }
    }
    
    /**
     * Creates a work-stealing thread pool (API 24+).
     * 
     * @param parallelism The level of parallelism
     * @return A work-stealing ExecutorService
     */
    private ExecutorService createWorkStealingPool(int parallelism) {
        if (parallelism <= 0) {
            return Executors.newWorkStealingPool();
        } else {
            return Executors.newWorkStealingPool(parallelism);
        }
    }
    
    /**
     * Creates a fixed thread pool with proper configuration.
     * 
     * @param config The thread pool configuration
     * @return A fixed thread pool ExecutorService
     */
    private ExecutorService createFixedThreadPool(ThreadPoolConfig config) {
        int poolSize = config.getCorePoolSize();
        if (poolSize <= 0) {
            poolSize = Runtime.getRuntime().availableProcessors();
        }
        return Executors.newFixedThreadPool(poolSize);
    }
    
    /**
     * Creates an optimal set for the given expected size.
     * Uses API-appropriate collection types with fallback.
     * 
     * @param expectedSize The expected size of the set
     * @param <T> The type of elements in the set
     * @return An optimal Set implementation
     */
    public <T> Set<T> createOptimalSet(int expectedSize) {
        try {
            return CollectionFactory.createOptimalSet(expectedSize);
        } catch (Exception e) {
            Log.w(TAG, "Failed to create optimal set, falling back to HashSet", e);
            return new HashSet<>(Math.max(expectedSize, 16));
        }
    }
    
    /**
     * Creates a thread-safe set with API-appropriate implementation.
     * Falls back to CopyOnWriteArraySet on older devices.
     * 
     * @param <T> The type of elements in the set
     * @return A thread-safe Set implementation
     */
    public <T> Set<T> createThreadSafeSet() {
        if (apiAdapter.isConcurrentHashMapKeySetAvailable()) {
            try {
                return CollectionFactory.createOptimalConcurrentSet();
            } catch (Exception e) {
                Log.w(TAG, "Failed to create ConcurrentHashMap.newKeySet(), falling back", e);
            }
        }
        // Fallback to CopyOnWriteArraySet for older APIs
        return new CopyOnWriteArraySet<>();
    }
    
    /**
     * Performs parallel cancellation of tasks if supported, otherwise sequential.
     * 
     * @param tasks The collection of tasks to cancel
     * @param <T> The type of tasks (must have cancel method)
     */
    public <T extends Cancellable> void cancelTasks(Collection<T> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        
        if (apiAdapter.isParallelStreamRecommended() && tasks.size() > 10) {
            try {
                // Use parallel streams for API 24+
                cancelTasksParallel(tasks);
                return;
            } catch (Exception e) {
                Log.w(TAG, "Parallel cancellation failed, falling back to sequential", e);
            }
        }
        
        // Fallback to sequential cancellation
        cancelTasksSequential(tasks);
    }
    
    /**
     * Cancels tasks in parallel using streams (API 24+).
     * 
     * @param tasks The tasks to cancel
     * @param <T> The task type
     */
    private <T extends Cancellable> void cancelTasksParallel(Collection<T> tasks) {
        tasks.parallelStream().forEach(task -> {
            try {
                task.cancel();
            } catch (Exception e) {
                Log.w(TAG, "Error cancelling task", e);
            }
        });
    }
    
    /**
     * Cancels tasks sequentially.
     * 
     * @param tasks The tasks to cancel
     * @param <T> The task type
     */
    private <T extends Cancellable> void cancelTasksSequential(Collection<T> tasks) {
        for (T task : tasks) {
            try {
                task.cancel();
            } catch (Exception e) {
                Log.w(TAG, "Error cancelling task", e);
            }
        }
    }
    
    /**
     * Creates a synchronized list wrapper if needed.
     * 
     * @param list The list to wrap
     * @param <T> The element type
     * @return A thread-safe list
     */
    public <T> List<T> createSynchronizedList(List<T> list) {
        if (list == null) {
            list = new ArrayList<>();
        }
        return Collections.synchronizedList(list);
    }
    
    /**
     * Checks if optimizations should be enabled based on API level and configuration.
     * 
     * @param config The cache manager configuration
     * @return true if optimizations should be enabled, false otherwise
     */
    public boolean shouldEnableOptimizations(CacheManagerConfig config) {
        // Always enable basic optimizations
        if (apiAdapter.getCurrentApiLevel() >= APIVersionAdapter.API_LEVEL_MARSHMALLOW) {
            return true;
        }
        
        // For older APIs, check if user explicitly enabled optimizations
        // (This would be a configuration option if needed)
        return false;
    }
    
    /**
     * Gets a safe thread pool size based on device capabilities.
     * 
     * @param requestedSize The requested pool size
     * @return A safe pool size for the current device
     */
    public int getSafeThreadPoolSize(int requestedSize) {
        int processors = Runtime.getRuntime().availableProcessors();
        
        if (requestedSize <= 0) {
            // Default to number of processors
            return processors;
        }
        
        // Cap at 2x processors to avoid excessive thread creation
        int maxSize = processors * 2;
        return Math.min(requestedSize, maxSize);
    }
    
    /**
     * Gets a safe cache size based on available memory.
     * 
     * @param requestedSize The requested cache size
     * @return A safe cache size for the current device
     */
    public int getSafeCacheSize(int requestedSize) {
        if (requestedSize <= 0) {
            return 100; // Default cache size
        }
        
        // Cap at reasonable maximum to prevent memory issues
        int maxSize = 1000;
        return Math.min(requestedSize, maxSize);
    }
    
    /**
     * Wraps a deprecated method call with logging and migration guidance.
     * 
     * @param methodName The name of the deprecated method
     * @param replacement The recommended replacement
     */
    public void logDeprecatedMethodUsage(String methodName, String replacement) {
        Log.w(TAG, "Deprecated method called: " + methodName);
        Log.w(TAG, "Please migrate to: " + replacement);
    }
    
    /**
     * Gets the API version adapter.
     * 
     * @return The API version adapter
     */
    public APIVersionAdapter getApiAdapter() {
        return apiAdapter;
    }
    
    /**
     * Interface for cancellable tasks.
     */
    public interface Cancellable {
        void cancel();
    }
}
