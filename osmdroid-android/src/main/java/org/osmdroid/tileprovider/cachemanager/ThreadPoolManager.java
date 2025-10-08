package org.osmdroid.tileprovider.cachemanager;

import android.os.Build;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages thread pools for CacheManager operations with API-aware optimizations.
 * Uses work-stealing ForkJoinPool on API 24+ for better multi-core utilization.
 * 
 * @author osmdroid
 */
public class ThreadPoolManager {
    
    private final ExecutorService primaryExecutor;
    private final ExecutorService bulkOperationExecutor;
    private final ScheduledExecutorService retryExecutor;
    private final ThreadPoolConfig config;
    
    /**
     * Creates a ThreadPoolManager with default configuration.
     */
    public ThreadPoolManager() {
        this(new ThreadPoolConfig());
    }
    
    /**
     * Creates a ThreadPoolManager with specified configuration.
     * 
     * @param config Thread pool configuration
     */
    public ThreadPoolManager(ThreadPoolConfig config) {
        this.config = config;
        this.primaryExecutor = createPrimaryExecutor();
        this.bulkOperationExecutor = createBulkOperationExecutor();
        this.retryExecutor = Executors.newScheduledThreadPool(
            config.retryPoolSize,
            new NamedThreadFactory("CacheManager-Retry")
        );
    }
    
    /**
     * Creates the primary executor based on API level and configuration.
     */
    private ExecutorService createPrimaryExecutor() {
        if (config.useWorkStealingPool && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Use ForkJoinPool for API 24+ for better work-stealing
            return new ForkJoinPool(
                config.corePoolSize,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null,
                true // async mode
            );
        } else {
            // Use traditional thread pool for older devices
            return Executors.newFixedThreadPool(
                config.corePoolSize,
                new NamedThreadFactory("CacheManager-Primary")
            );
        }
    }
    
    /**
     * Creates the bulk operation executor for large batch operations.
     */
    private ExecutorService createBulkOperationExecutor() {
        if (config.useWorkStealingPool && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return new ForkJoinPool(
                config.maximumPoolSize,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null,
                true
            );
        } else {
            return Executors.newFixedThreadPool(
                config.maximumPoolSize,
                new NamedThreadFactory("CacheManager-Bulk")
            );
        }
    }
    
    /**
     * Gets the optimal executor for the specified operation type.
     * 
     * @param type Type of operation
     * @return Appropriate executor service
     */
    public ExecutorService getOptimalExecutor(OperationType type) {
        switch (type) {
            case BULK_OPERATION:
                return bulkOperationExecutor;
            case PRIMARY:
            default:
                return primaryExecutor;
        }
    }
    
    /**
     * Gets the primary executor service.
     */
    public ExecutorService getPrimaryExecutor() {
        return primaryExecutor;
    }
    
    /**
     * Gets the bulk operation executor service.
     */
    public ExecutorService getBulkOperationExecutor() {
        return bulkOperationExecutor;
    }
    
    /**
     * Gets the scheduled executor for retry operations.
     */
    public ScheduledExecutorService getRetryExecutor() {
        return retryExecutor;
    }
    
    /**
     * Shuts down all thread pools gracefully.
     */
    public void shutdown() {
        shutdownExecutor(primaryExecutor, "Primary");
        shutdownExecutor(bulkOperationExecutor, "Bulk");
        shutdownExecutor(retryExecutor, "Retry");
    }
    
    /**
     * Shuts down all thread pools immediately.
     */
    public void shutdownNow() {
        primaryExecutor.shutdownNow();
        bulkOperationExecutor.shutdownNow();
        retryExecutor.shutdownNow();
    }
    
    /**
     * Helper method to shutdown an executor gracefully.
     */
    private void shutdownExecutor(ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(config.shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(config.shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                    // Log warning if executor doesn't terminate
                    android.util.Log.w("ThreadPoolManager", 
                        name + " executor did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Checks if the manager is using work-stealing pools.
     */
    public boolean isUsingWorkStealingPool() {
        return config.useWorkStealingPool && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }
    
    /**
     * Gets the current configuration.
     */
    public ThreadPoolConfig getConfig() {
        return config;
    }
    
    /**
     * Operation types for executor selection.
     */
    public enum OperationType {
        PRIMARY,
        BULK_OPERATION
    }
    
    /**
     * Named thread factory for better debugging.
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        
        NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }
        
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
}
