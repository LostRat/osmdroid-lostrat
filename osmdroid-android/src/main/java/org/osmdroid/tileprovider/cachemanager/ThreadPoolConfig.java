package org.osmdroid.tileprovider.cachemanager;

/**
 * Configuration for ThreadPoolManager thread pool behavior.
 * <p>
 * Controls thread pool sizes, work-stealing behavior, and shutdown timeouts.
 * The default configuration automatically adapts to device capabilities.
 * </p>
 * 
 * <h3>Usage Examples:</h3>
 * 
 * <h4>Default Configuration (Adapts to Device):</h4>
 * <pre>{@code
 * // Automatically uses CPU count for optimal performance
 * ThreadPoolConfig config = new ThreadPoolConfig();
 * // Core pool size: max(2, CPU count)
 * // Max pool size: max(4, CPU count * 2)
 * }</pre>
 * 
 * <h4>Custom Configuration:</h4>
 * <pre>{@code
 * ThreadPoolConfig config = new ThreadPoolConfig.Builder()
 *     .setCorePoolSize(4)                    // Primary operations
 *     .setMaximumPoolSize(8)                 // Bulk operations
 *     .setUseWorkStealingPool(true)          // Enable ForkJoinPool on API 24+
 *     .setRetryPoolSize(2)                   // Retry operations
 *     .setShutdownTimeoutSeconds(30)         // Graceful shutdown timeout
 *     .build();
 * }</pre>
 * 
 * <h4>Low-End Device Configuration:</h4>
 * <pre>{@code
 * ThreadPoolConfig config = new ThreadPoolConfig.Builder()
 *     .setCorePoolSize(2)
 *     .setMaximumPoolSize(4)
 *     .setUseWorkStealingPool(false)         // Disable for simplicity
 *     .build();
 * }</pre>
 * 
 * <h4>High-End Device Configuration:</h4>
 * <pre>{@code
 * ThreadPoolConfig config = new ThreadPoolConfig.Builder()
 *     .setCorePoolSize(8)
 *     .setMaximumPoolSize(16)
 *     .setUseWorkStealingPool(true)          // Maximize parallelism
 *     .build();
 * }</pre>
 * 
 * @author osmdroid
 * @since 6.2.0
 * @see ThreadPoolManager
 */
public class ThreadPoolConfig {
    
    final int corePoolSize;
    final int maximumPoolSize;
    final long keepAliveTimeSeconds;
    final boolean useWorkStealingPool;
    final int retryPoolSize;
    final long shutdownTimeoutSeconds;
    
    /**
     * Creates a ThreadPoolConfig with default values.
     */
    public ThreadPoolConfig() {
        this(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            Math.max(4, Runtime.getRuntime().availableProcessors() * 2),
            60L,
            true,
            2,
            30L
        );
    }
    
    /**
     * Creates a ThreadPoolConfig with specified values.
     * 
     * @param corePoolSize Core pool size for primary executor
     * @param maximumPoolSize Maximum pool size for bulk operations
     * @param keepAliveTimeSeconds Keep alive time for idle threads
     * @param useWorkStealingPool Whether to use ForkJoinPool on API 24+
     * @param retryPoolSize Size of retry executor pool
     * @param shutdownTimeoutSeconds Timeout for graceful shutdown
     */
    public ThreadPoolConfig(int corePoolSize, int maximumPoolSize, 
                           long keepAliveTimeSeconds, boolean useWorkStealingPool,
                           int retryPoolSize, long shutdownTimeoutSeconds) {
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.keepAliveTimeSeconds = keepAliveTimeSeconds;
        this.useWorkStealingPool = useWorkStealingPool;
        this.retryPoolSize = retryPoolSize;
        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
    }
    
    public int getCorePoolSize() {
        return corePoolSize;
    }
    
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }
    
    public long getKeepAliveTimeSeconds() {
        return keepAliveTimeSeconds;
    }
    
    public boolean isUseWorkStealingPool() {
        return useWorkStealingPool;
    }
    
    public int getRetryPoolSize() {
        return retryPoolSize;
    }
    
    public long getShutdownTimeoutSeconds() {
        return shutdownTimeoutSeconds;
    }
    
    /**
     * Builder for ThreadPoolConfig.
     */
    public static class Builder {
        private int corePoolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        private int maximumPoolSize = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
        private long keepAliveTimeSeconds = 60L;
        private boolean useWorkStealingPool = true;
        private int retryPoolSize = 2;
        private long shutdownTimeoutSeconds = 30L;
        
        public Builder setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
            return this;
        }
        
        public Builder setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
            return this;
        }
        
        public Builder setKeepAliveTimeSeconds(long keepAliveTimeSeconds) {
            this.keepAliveTimeSeconds = keepAliveTimeSeconds;
            return this;
        }
        
        public Builder setUseWorkStealingPool(boolean useWorkStealingPool) {
            this.useWorkStealingPool = useWorkStealingPool;
            return this;
        }
        
        public Builder setRetryPoolSize(int retryPoolSize) {
            this.retryPoolSize = retryPoolSize;
            return this;
        }
        
        public Builder setShutdownTimeoutSeconds(long shutdownTimeoutSeconds) {
            this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
            return this;
        }
        
        public ThreadPoolConfig build() {
            return new ThreadPoolConfig(
                corePoolSize,
                maximumPoolSize,
                keepAliveTimeSeconds,
                useWorkStealingPool,
                retryPoolSize,
                shutdownTimeoutSeconds
            );
        }
    }
}
