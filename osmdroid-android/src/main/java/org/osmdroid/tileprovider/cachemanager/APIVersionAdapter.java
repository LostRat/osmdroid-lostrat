package org.osmdroid.tileprovider.cachemanager;

import android.os.Build;
import android.util.Log;

/**
 * Adapter class for handling Android API version differences and providing
 * feature availability checking for CacheManager optimizations.
 * 
 * This class enables backward compatibility by detecting the current API level
 * and providing methods to check feature availability.
 * 
 * @since 6.2.0
 */
public class APIVersionAdapter {
    private static final String TAG = "APIVersionAdapter";
    
    // API level constants for feature detection
    public static final int API_LEVEL_MARSHMALLOW = 23;
    public static final int API_LEVEL_NOUGAT = 24;
    public static final int API_LEVEL_OREO = 26;
    
    private final int currentApiLevel;
    
    /**
     * Creates a new APIVersionAdapter using the current device API level.
     */
    public APIVersionAdapter() {
        this(Build.VERSION.SDK_INT);
    }
    
    /**
     * Creates a new APIVersionAdapter with a specific API level.
     * Useful for testing.
     * 
     * @param apiLevel The API level to use
     */
    public APIVersionAdapter(int apiLevel) {
        this.currentApiLevel = apiLevel;
        Log.d(TAG, "Initialized with API level: " + apiLevel);
    }
    
    /**
     * Gets the current API level.
     * 
     * @return The current Android API level
     */
    public int getCurrentApiLevel() {
        return currentApiLevel;
    }
    
    /**
     * Checks if ArraySet is available (API 23+).
     * ArraySet provides better memory efficiency for small to medium sets.
     * 
     * @return true if ArraySet is available, false otherwise
     */
    public boolean isArraySetAvailable() {
        return currentApiLevel >= API_LEVEL_MARSHMALLOW;
    }
    
    /**
     * Checks if ForkJoinPool is available (API 21+) and recommended (API 24+).
     * ForkJoinPool provides work-stealing thread pool for better parallelism.
     * 
     * @return true if ForkJoinPool is recommended, false otherwise
     */
    public boolean isForkJoinPoolRecommended() {
        return currentApiLevel >= API_LEVEL_NOUGAT;
    }
    
    /**
     * Checks if ConcurrentHashMap.newKeySet() is available (API 24+).
     * This provides a thread-safe set backed by ConcurrentHashMap.
     * 
     * @return true if ConcurrentHashMap.newKeySet() is available, false otherwise
     */
    public boolean isConcurrentHashMapKeySetAvailable() {
        return currentApiLevel >= API_LEVEL_NOUGAT;
    }
    
    /**
     * Checks if parallel stream operations are recommended (API 24+).
     * Parallel streams can improve performance on multi-core devices.
     * 
     * @return true if parallel streams are recommended, false otherwise
     */
    public boolean isParallelStreamRecommended() {
        return currentApiLevel >= API_LEVEL_NOUGAT;
    }
    
    /**
     * Checks if LRU cache optimizations are available (API 12+).
     * LruCache has been available since API 12, so this always returns true
     * for supported osmdroid versions.
     * 
     * @return true (always available in supported API levels)
     */
    public boolean isLruCacheAvailable() {
        return true; // Available since API 12, osmdroid minimum is API 16
    }
    
    /**
     * Checks if advanced thread pool features are available (API 24+).
     * This includes work-stealing pools and improved executor services.
     * 
     * @return true if advanced thread pool features are available, false otherwise
     */
    public boolean isAdvancedThreadPoolAvailable() {
        return currentApiLevel >= API_LEVEL_NOUGAT;
    }
    
    /**
     * Checks if memory-efficient collections should be used (API 23+).
     * This includes ArraySet and other optimized collection types.
     * 
     * @return true if memory-efficient collections are available, false otherwise
     */
    public boolean shouldUseMemoryEfficientCollections() {
        return currentApiLevel >= API_LEVEL_MARSHMALLOW;
    }
    
    /**
     * Gets the recommended thread pool type based on API level.
     * 
     * @return ThreadPoolType enum indicating the recommended pool type
     */
    public ThreadPoolType getRecommendedThreadPoolType() {
        if (currentApiLevel >= API_LEVEL_NOUGAT) {
            return ThreadPoolType.FORK_JOIN_POOL;
        } else {
            return ThreadPoolType.FIXED_THREAD_POOL;
        }
    }
    
    /**
     * Gets the recommended collection type for sets based on API level.
     * 
     * @return CollectionType enum indicating the recommended set type
     */
    public CollectionType getRecommendedSetType() {
        if (currentApiLevel >= API_LEVEL_NOUGAT) {
            return CollectionType.CONCURRENT_HASH_MAP_KEY_SET;
        } else if (currentApiLevel >= API_LEVEL_MARSHMALLOW) {
            return CollectionType.ARRAY_SET;
        } else {
            return CollectionType.HASH_SET;
        }
    }
    
    /**
     * Checks if a specific feature is available.
     * 
     * @param feature The feature to check
     * @return true if the feature is available, false otherwise
     */
    public boolean isFeatureAvailable(Feature feature) {
        switch (feature) {
            case ARRAY_SET:
                return isArraySetAvailable();
            case FORK_JOIN_POOL:
                return isForkJoinPoolRecommended();
            case CONCURRENT_HASHMAP_KEYSET:
                return isConcurrentHashMapKeySetAvailable();
            case PARALLEL_STREAMS:
                return isParallelStreamRecommended();
            case LRU_CACHE:
                return isLruCacheAvailable();
            case ADVANCED_THREAD_POOL:
                return isAdvancedThreadPoolAvailable();
            default:
                return false;
        }
    }
    
    /**
     * Logs feature availability information for debugging.
     */
    public void logFeatureAvailability() {
        Log.i(TAG, "Feature Availability Report (API " + currentApiLevel + "):");
        Log.i(TAG, "  ArraySet: " + isArraySetAvailable());
        Log.i(TAG, "  ForkJoinPool: " + isForkJoinPoolRecommended());
        Log.i(TAG, "  ConcurrentHashMap.newKeySet(): " + isConcurrentHashMapKeySetAvailable());
        Log.i(TAG, "  Parallel Streams: " + isParallelStreamRecommended());
        Log.i(TAG, "  Advanced Thread Pool: " + isAdvancedThreadPoolAvailable());
        Log.i(TAG, "  Recommended Thread Pool: " + getRecommendedThreadPoolType());
        Log.i(TAG, "  Recommended Set Type: " + getRecommendedSetType());
    }
    
    /**
     * Enum representing different thread pool types.
     */
    public enum ThreadPoolType {
        FIXED_THREAD_POOL,
        FORK_JOIN_POOL
    }
    
    /**
     * Enum representing different collection types.
     */
    public enum CollectionType {
        HASH_SET,
        ARRAY_SET,
        CONCURRENT_HASH_MAP_KEY_SET
    }
    
    /**
     * Enum representing features that may not be available on all API levels.
     */
    public enum Feature {
        ARRAY_SET,
        FORK_JOIN_POOL,
        CONCURRENT_HASHMAP_KEYSET,
        PARALLEL_STREAMS,
        LRU_CACHE,
        ADVANCED_THREAD_POOL
    }
}
