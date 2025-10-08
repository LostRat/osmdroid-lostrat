package org.osmdroid.tileprovider.cachemanager;

import android.graphics.Point;
import android.util.LruCache;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.TileSystem;

import java.util.List;

/**
 * LRU cache for expensive mathematical operations in CacheManager.
 * Caches ground resolution, tile coordinates, and bounding box calculations
 * to improve performance for repeated operations.
 * 
 * Thread-safe implementation using LruCache.
 * 
 * @author osmdroid
 */
public class CalculationCache {
    
    /**
     * Listener interface for cache hit/miss events.
     */
    public interface CacheMetricsListener {
        void onCacheHit();
        void onCacheMiss();
        void onCacheEviction();
    }
    
    private final LruCache<String, Double> groundResolutionCache;
    private final LruCache<String, Point> tileCoordinateCache;
    private final LruCache<String, BoundingBox> boundingBoxCache;
    private CacheMetricsListener metricsListener;
    
    /**
     * Creates a CalculationCache with default cache sizes.
     */
    public CalculationCache() {
        this(1000, 2000, 500);
    }
    
    /**
     * Creates a CalculationCache with specified cache sizes.
     * 
     * @param groundResolutionCacheSize Maximum number of ground resolution entries
     * @param tileCoordinateCacheSize Maximum number of tile coordinate entries
     * @param boundingBoxCacheSize Maximum number of bounding box entries
     */
    public CalculationCache(int groundResolutionCacheSize, 
                           int tileCoordinateCacheSize,
                           int boundingBoxCacheSize) {
        this.groundResolutionCache = new LruCache<>(groundResolutionCacheSize);
        this.tileCoordinateCache = new LruCache<>(tileCoordinateCacheSize);
        this.boundingBoxCache = new LruCache<>(boundingBoxCacheSize);
    }
    
    /**
     * Creates a CalculationCache from CacheConfig.
     * 
     * @param config Cache configuration
     * @since 6.2.0
     */
    public CalculationCache(CacheConfig config) {
        this(config.getGroundResolutionCacheSize(),
             config.getTileCoordinateCacheSize(),
             config.getBoundingBoxCacheSize());
    }
    
    /**
     * Gets cached ground resolution or calculates and caches it.
     * 
     * @param latitude Latitude in degrees
     * @param zoomLevel Zoom level
     * @return Ground resolution in meters per pixel
     */
    public double getCachedGroundResolution(double latitude, int zoomLevel) {
        String key = latitude + ":" + zoomLevel;
        Double cached = groundResolutionCache.get(key);
        if (cached != null) {
            if (metricsListener != null) {
                metricsListener.onCacheHit();
            }
            return cached;
        }
        
        if (metricsListener != null) {
            metricsListener.onCacheMiss();
        }
        
        double resolution = TileSystem.GroundResolution(latitude, zoomLevel);
        groundResolutionCache.put(key, resolution);
        return resolution;
    }
    
    /**
     * Gets cached tile coordinates or calculates and caches them.
     * Requires a TileSystem instance to perform the calculation.
     * 
     * @param tileSystem TileSystem instance for coordinate conversion
     * @param longitude Longitude in degrees
     * @param latitude Latitude in degrees
     * @param zoomLevel Zoom level
     * @return Point containing tile X and Y coordinates
     */
    public Point getCachedTileCoordinates(TileSystem tileSystem, double longitude, 
                                         double latitude, int zoomLevel) {
        String key = longitude + ":" + latitude + ":" + zoomLevel;
        Point cached = tileCoordinateCache.get(key);
        if (cached != null) {
            if (metricsListener != null) {
                metricsListener.onCacheHit();
            }
            return new Point(cached.x, cached.y);
        }
        
        if (metricsListener != null) {
            metricsListener.onCacheMiss();
        }
        
        // Calculate tile coordinates using TileSystem
        Point result = new Point(
            tileSystem.getTileXFromLongitude(longitude, zoomLevel),
            tileSystem.getTileYFromLatitude(latitude, zoomLevel)
        );
        
        // Cache a copy
        tileCoordinateCache.put(key, new Point(result.x, result.y));
        return result;
    }
    
    /**
     * Gets cached bounding box or calculates and caches it.
     * 
     * @param points List of GeoPoints
     * @return BoundingBox containing all points
     */
    public BoundingBox getCachedBoundingBox(List<GeoPoint> points) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        
        // Create a simple hash based on first, last, and middle points
        String key = createBoundingBoxKey(points);
        BoundingBox cached = boundingBoxCache.get(key);
        if (cached != null) {
            if (metricsListener != null) {
                metricsListener.onCacheHit();
            }
            return cached;
        }
        
        if (metricsListener != null) {
            metricsListener.onCacheMiss();
        }
        
        BoundingBox bbox = BoundingBox.fromGeoPoints(points);
        boundingBoxCache.put(key, bbox);
        return bbox;
    }
    
    /**
     * Creates a cache key for a list of GeoPoints.
     * Uses first, last, and middle points to create a reasonably unique key.
     */
    private String createBoundingBoxKey(List<GeoPoint> points) {
        int size = points.size();
        GeoPoint first = points.get(0);
        GeoPoint last = points.get(size - 1);
        GeoPoint middle = points.get(size / 2);
        
        return first.getLatitude() + ":" + first.getLongitude() + ":" +
               middle.getLatitude() + ":" + middle.getLongitude() + ":" +
               last.getLatitude() + ":" + last.getLongitude() + ":" + size;
    }
    
    /**
     * Sets the metrics listener for cache hit/miss tracking.
     * 
     * @param listener Metrics listener or null to disable
     * @since 6.2.0
     */
    public void setMetricsListener(CacheMetricsListener listener) {
        this.metricsListener = listener;
    }
    
    /**
     * Clears all caches.
     */
    public void clear() {
        groundResolutionCache.evictAll();
        tileCoordinateCache.evictAll();
        boundingBoxCache.evictAll();
    }
    
    /**
     * Gets cache statistics for monitoring.
     * 
     * @return CacheStatistics object with hit/miss information
     */
    public CacheStatistics getStatistics() {
        return new CacheStatistics(
            groundResolutionCache.hitCount(),
            groundResolutionCache.missCount(),
            tileCoordinateCache.hitCount(),
            tileCoordinateCache.missCount(),
            boundingBoxCache.hitCount(),
            boundingBoxCache.missCount()
        );
    }
    
    /**
     * Statistics for cache performance monitoring.
     */
    public static class CacheStatistics {
        public final long groundResolutionHits;
        public final long groundResolutionMisses;
        public final long tileCoordinateHits;
        public final long tileCoordinateMisses;
        public final long boundingBoxHits;
        public final long boundingBoxMisses;
        
        CacheStatistics(long grHits, long grMisses, long tcHits, long tcMisses,
                       long bbHits, long bbMisses) {
            this.groundResolutionHits = grHits;
            this.groundResolutionMisses = grMisses;
            this.tileCoordinateHits = tcHits;
            this.tileCoordinateMisses = tcMisses;
            this.boundingBoxHits = bbHits;
            this.boundingBoxMisses = bbMisses;
        }
        
        public double getGroundResolutionHitRatio() {
            long total = groundResolutionHits + groundResolutionMisses;
            return total == 0 ? 0.0 : (double) groundResolutionHits / total;
        }
        
        public double getTileCoordinateHitRatio() {
            long total = tileCoordinateHits + tileCoordinateMisses;
            return total == 0 ? 0.0 : (double) tileCoordinateHits / total;
        }
        
        public double getBoundingBoxHitRatio() {
            long total = boundingBoxHits + boundingBoxMisses;
            return total == 0 ? 0.0 : (double) boundingBoxHits / total;
        }
    }
}
