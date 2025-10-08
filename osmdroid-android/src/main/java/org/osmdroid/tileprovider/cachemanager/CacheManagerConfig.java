package org.osmdroid.tileprovider.cachemanager;

/**
 * Complete configuration for CacheManager optimizations.
 * <p>
 * This class provides a centralized configuration for all CacheManager optimization features
 * including thread pool management, calculation caching, retry policies, and progress reporting.
 * </p>
 * 
 * <h3>Usage Examples:</h3>
 * 
 * <h4>Default Configuration (Recommended):</h4>
 * <pre>{@code
 * // Uses sensible defaults optimized for most devices
 * CacheManagerConfig config = new CacheManagerConfig();
 * CacheManager cacheManager = new CacheManager(mapView, config);
 * }</pre>
 * 
 * <h4>Custom Configuration:</h4>
 * <pre>{@code
 * CacheManagerConfig config = new CacheManagerConfig.Builder()
 *     .setThreadPoolConfig(new ThreadPoolConfig.Builder()
 *         .setCorePoolSize(4)
 *         .setMaximumPoolSize(8)
 *         .build())
 *     .setCacheConfig(new CacheConfig.Builder()
 *         .setGroundResolutionCacheSize(2000)
 *         .build())
 *     .setRetryConfig(new RetryConfig.Builder()
 *         .setMaxRetries(5)
 *         .build())
 *     .build();
 * 
 * CacheManager cacheManager = new CacheManager(mapView, config);
 * }</pre>
 * 
 * <h4>Memory-Constrained Configuration:</h4>
 * <pre>{@code
 * CacheManagerConfig config = new CacheManagerConfig.Builder()
 *     .setThreadPoolConfig(new ThreadPoolConfig.Builder()
 *         .setCorePoolSize(2)
 *         .setMaximumPoolSize(4)
 *         .setUseWorkStealingPool(false)
 *         .build())
 *     .setCacheConfig(new CacheConfig.Builder()
 *         .setGroundResolutionCacheSize(250)
 *         .setTileCoordinateCacheSize(500)
 *         .build())
 *     .build();
 * }</pre>
 * 
 * @author osmdroid
 * @since 6.2.0
 * @see ThreadPoolConfig
 * @see CacheConfig
 * @see RetryConfig
 * @see ProgressConfig
 */
public class CacheManagerConfig {
    
    final ThreadPoolConfig threadPoolConfig;
    final CacheConfig cacheConfig;
    final RetryConfig retryConfig;
    final ProgressConfig progressConfig;
    
    /**
     * Creates a CacheManagerConfig with default values.
     */
    public CacheManagerConfig() {
        this(
            new ThreadPoolConfig(),
            new CacheConfig(),
            new RetryConfig(),
            new ProgressConfig()
        );
    }
    
    /**
     * Creates a CacheManagerConfig with specified values.
     * 
     * @param threadPoolConfig Thread pool configuration
     * @param cacheConfig Cache configuration
     * @param retryConfig Retry configuration
     * @param progressConfig Progress reporting configuration
     */
    public CacheManagerConfig(ThreadPoolConfig threadPoolConfig,
                             CacheConfig cacheConfig,
                             RetryConfig retryConfig,
                             ProgressConfig progressConfig) {
        this.threadPoolConfig = threadPoolConfig;
        this.cacheConfig = cacheConfig;
        this.retryConfig = retryConfig;
        this.progressConfig = progressConfig;
    }
    
    public ThreadPoolConfig getThreadPoolConfig() {
        return threadPoolConfig;
    }
    
    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }
    
    public RetryConfig getRetryConfig() {
        return retryConfig;
    }
    
    public ProgressConfig getProgressConfig() {
        return progressConfig;
    }
    
    /**
     * Builder for CacheManagerConfig.
     */
    public static class Builder {
        private ThreadPoolConfig threadPoolConfig = new ThreadPoolConfig();
        private CacheConfig cacheConfig = new CacheConfig();
        private RetryConfig retryConfig = new RetryConfig();
        private ProgressConfig progressConfig = new ProgressConfig();
        
        public Builder setThreadPoolConfig(ThreadPoolConfig threadPoolConfig) {
            this.threadPoolConfig = threadPoolConfig;
            return this;
        }
        
        public Builder setCacheConfig(CacheConfig cacheConfig) {
            this.cacheConfig = cacheConfig;
            return this;
        }
        
        public Builder setRetryConfig(RetryConfig retryConfig) {
            this.retryConfig = retryConfig;
            return this;
        }
        
        public Builder setProgressConfig(ProgressConfig progressConfig) {
            this.progressConfig = progressConfig;
            return this;
        }
        
        public CacheManagerConfig build() {
            return new CacheManagerConfig(
                threadPoolConfig,
                cacheConfig,
                retryConfig,
                progressConfig
            );
        }
    }
}
