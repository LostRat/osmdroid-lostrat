package org.osmdroid.tileprovider.cachemanager;

/**
 * Centralized configuration manager for CacheManager optimizations.
 * Provides a single point of configuration for all optimization features.
 * 
 * @author osmdroid
 */
public class ConfigurationManager {
    
    private final CacheManagerConfig config;
    
    /**
     * Creates a ConfigurationManager with default configuration.
     */
    public ConfigurationManager() {
        this(new CacheManagerConfig());
    }
    
    /**
     * Creates a ConfigurationManager with specified configuration.
     * 
     * @param config CacheManager configuration
     */
    public ConfigurationManager(CacheManagerConfig config) {
        this.config = config;
    }
    
    /**
     * Gets the thread pool configuration.
     */
    public ThreadPoolConfig getThreadPoolConfig() {
        return config.threadPoolConfig;
    }
    
    /**
     * Gets the cache configuration.
     */
    public CacheConfig getCacheConfig() {
        return config.cacheConfig;
    }
    
    /**
     * Gets the retry configuration.
     */
    public RetryConfig getRetryConfig() {
        return config.retryConfig;
    }
    
    /**
     * Gets the progress reporting configuration.
     */
    public ProgressConfig getProgressConfig() {
        return config.progressConfig;
    }
    
    /**
     * Gets the complete configuration.
     */
    public CacheManagerConfig getConfig() {
        return config;
    }
    
    /**
     * Validates the configuration.
     * 
     * @throws IllegalArgumentException if configuration is invalid
     */
    public void validate() {
        if (config.threadPoolConfig.corePoolSize <= 0) {
            throw new IllegalArgumentException("Core pool size must be positive");
        }
        if (config.threadPoolConfig.maximumPoolSize < config.threadPoolConfig.corePoolSize) {
            throw new IllegalArgumentException(
                "Maximum pool size must be >= core pool size");
        }
        if (config.cacheConfig.groundResolutionCacheSize <= 0) {
            throw new IllegalArgumentException(
                "Ground resolution cache size must be positive");
        }
        if (config.cacheConfig.tileCoordinateCacheSize <= 0) {
            throw new IllegalArgumentException(
                "Tile coordinate cache size must be positive");
        }
        if (config.retryConfig.maxRetries < 0) {
            throw new IllegalArgumentException("Max retries must be non-negative");
        }
        if (config.progressConfig.updateIntervalMs <= 0) {
            throw new IllegalArgumentException(
                "Progress update interval must be positive");
        }
    }
}
