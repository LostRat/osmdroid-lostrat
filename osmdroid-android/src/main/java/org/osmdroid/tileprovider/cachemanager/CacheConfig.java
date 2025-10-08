package org.osmdroid.tileprovider.cachemanager;

/**
 * Configuration for CalculationCache LRU cache sizes.
 * <p>
 * Controls the maximum number of cached entries for ground resolution calculations,
 * tile coordinate conversions, and bounding box computations. Larger caches improve
 * performance but consume more memory.
 * </p>
 * 
 * <h3>Usage Examples:</h3>
 * 
 * <h4>Default Configuration:</h4>
 * <pre>{@code
 * // Balanced configuration for most use cases
 * CacheConfig config = new CacheConfig();
 * // Ground resolution: 1000 entries
 * // Tile coordinates: 2000 entries
 * // Bounding boxes: 500 entries
 * }</pre>
 * 
 * <h4>High-Performance Configuration:</h4>
 * <pre>{@code
 * CacheConfig config = new CacheConfig.Builder()
 *     .setGroundResolutionCacheSize(5000)    // More cached calculations
 *     .setTileCoordinateCacheSize(10000)     // Larger coordinate cache
 *     .setBoundingBoxCacheSize(2000)         // More bounding boxes
 *     .build();
 * }</pre>
 * 
 * <h4>Memory-Constrained Configuration:</h4>
 * <pre>{@code
 * CacheConfig config = new CacheConfig.Builder()
 *     .setGroundResolutionCacheSize(250)     // Smaller caches
 *     .setTileCoordinateCacheSize(500)
 *     .setBoundingBoxCacheSize(100)
 *     .build();
 * }</pre>
 * 
 * <h4>Monitoring Cache Performance:</h4>
 * <pre>{@code
 * CalculationCache cache = new CalculationCache(config);
 * 
 * // After some operations...
 * CalculationCache.CacheStatistics stats = cache.getStatistics();
 * double hitRatio = stats.getGroundResolutionHitRatio();
 * 
 * if (hitRatio < 0.7) {
 *     // Consider increasing cache size
 *     Log.w("Cache", "Low hit ratio: " + hitRatio);
 * }
 * }</pre>
 * 
 * @author osmdroid
 * @since 6.2.0
 * @see CalculationCache
 */
public class CacheConfig {
    
    final int groundResolutionCacheSize;
    final int tileCoordinateCacheSize;
    final int boundingBoxCacheSize;
    final long cacheEvictionIntervalMs;
    
    /**
     * Creates a CacheConfig with default values.
     */
    public CacheConfig() {
        this(1000, 2000, 500, 300000L); // 5 minutes eviction interval
    }
    
    /**
     * Creates a CacheConfig with specified values.
     * 
     * @param groundResolutionCacheSize Maximum ground resolution cache entries
     * @param tileCoordinateCacheSize Maximum tile coordinate cache entries
     * @param boundingBoxCacheSize Maximum bounding box cache entries
     * @param cacheEvictionIntervalMs Interval for cache eviction in milliseconds
     */
    public CacheConfig(int groundResolutionCacheSize,
                      int tileCoordinateCacheSize,
                      int boundingBoxCacheSize,
                      long cacheEvictionIntervalMs) {
        this.groundResolutionCacheSize = groundResolutionCacheSize;
        this.tileCoordinateCacheSize = tileCoordinateCacheSize;
        this.boundingBoxCacheSize = boundingBoxCacheSize;
        this.cacheEvictionIntervalMs = cacheEvictionIntervalMs;
    }
    
    public int getGroundResolutionCacheSize() {
        return groundResolutionCacheSize;
    }
    
    public int getTileCoordinateCacheSize() {
        return tileCoordinateCacheSize;
    }
    
    public int getBoundingBoxCacheSize() {
        return boundingBoxCacheSize;
    }
    
    public long getCacheEvictionIntervalMs() {
        return cacheEvictionIntervalMs;
    }
    
    /**
     * Builder for CacheConfig.
     */
    public static class Builder {
        private int groundResolutionCacheSize = 1000;
        private int tileCoordinateCacheSize = 2000;
        private int boundingBoxCacheSize = 500;
        private long cacheEvictionIntervalMs = 300000L;
        
        public Builder setGroundResolutionCacheSize(int groundResolutionCacheSize) {
            this.groundResolutionCacheSize = groundResolutionCacheSize;
            return this;
        }
        
        public Builder setTileCoordinateCacheSize(int tileCoordinateCacheSize) {
            this.tileCoordinateCacheSize = tileCoordinateCacheSize;
            return this;
        }
        
        public Builder setBoundingBoxCacheSize(int boundingBoxCacheSize) {
            this.boundingBoxCacheSize = boundingBoxCacheSize;
            return this;
        }
        
        public Builder setCacheEvictionIntervalMs(long cacheEvictionIntervalMs) {
            this.cacheEvictionIntervalMs = cacheEvictionIntervalMs;
            return this;
        }
        
        public CacheConfig build() {
            return new CacheConfig(
                groundResolutionCacheSize,
                tileCoordinateCacheSize,
                boundingBoxCacheSize,
                cacheEvictionIntervalMs
            );
        }
    }
}
