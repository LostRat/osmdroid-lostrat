package org.osmdroid.tileprovider.cachemanager;

import android.os.Build;
import android.util.ArraySet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating optimal collection types based on Android API level.
 * Uses ArraySet on API 23+ and ConcurrentHashMap.newKeySet() on API 24+ for better performance.
 * 
 * @author osmdroid
 */
public class CollectionFactory {
    
    /**
     * Creates an optimal Set implementation based on API level and expected size.
     * 
     * @param expectedSize Expected number of elements
     * @param <T> Element type
     * @return Optimal Set implementation
     */
    public static <T> Set<T> createOptimalSet(int expectedSize) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // API 23+: Use ArraySet for memory efficiency
            return new ArraySet<>(expectedSize);
        } else {
            // Older APIs: Use HashSet with initial capacity
            return new HashSet<>(calculateInitialCapacity(expectedSize));
        }
    }
    
    /**
     * Creates an optimal Set implementation with default size.
     * 
     * @param <T> Element type
     * @return Optimal Set implementation
     */
    public static <T> Set<T> createOptimalSet() {
        return createOptimalSet(16);
    }
    
    /**
     * Creates an optimal thread-safe Set implementation based on API level.
     * 
     * @param <T> Element type
     * @return Optimal thread-safe Set implementation
     */
    public static <T> Set<T> createOptimalConcurrentSet() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // API 24+: Use ConcurrentHashMap.newKeySet() for better concurrency
            return ConcurrentHashMap.newKeySet();
        } else {
            // Older APIs: Use Collections.synchronizedSet with HashSet
            return Collections.synchronizedSet(new HashSet<T>());
        }
    }
    
    /**
     * Creates an optimal thread-safe Set with expected size.
     * 
     * @param expectedSize Expected number of elements
     * @param <T> Element type
     * @return Optimal thread-safe Set implementation
     */
    public static <T> Set<T> createOptimalConcurrentSet(int expectedSize) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // API 24+: Use ConcurrentHashMap.newKeySet() with initial capacity
            return ConcurrentHashMap.newKeySet(calculateInitialCapacity(expectedSize));
        } else {
            // Older APIs: Use Collections.synchronizedSet with HashSet
            return Collections.synchronizedSet(
                new HashSet<T>(calculateInitialCapacity(expectedSize))
            );
        }
    }
    
    /**
     * Creates an optimal Map implementation based on API level and expected size.
     * 
     * @param expectedSize Expected number of entries
     * @param <K> Key type
     * @param <V> Value type
     * @return Optimal Map implementation
     */
    public static <K, V> Map<K, V> createOptimalMap(int expectedSize) {
        // HashMap is generally optimal for non-concurrent use
        return new HashMap<>(calculateInitialCapacity(expectedSize));
    }
    
    /**
     * Creates an optimal Map implementation with default size.
     * 
     * @param <K> Key type
     * @param <V> Value type
     * @return Optimal Map implementation
     */
    public static <K, V> Map<K, V> createOptimalMap() {
        return createOptimalMap(16);
    }
    
    /**
     * Creates an optimal thread-safe Map implementation.
     * 
     * @param <K> Key type
     * @param <V> Value type
     * @return Optimal thread-safe Map implementation
     */
    public static <K, V> Map<K, V> createOptimalConcurrentMap() {
        return new ConcurrentHashMap<>();
    }
    
    /**
     * Creates an optimal thread-safe Map with expected size.
     * 
     * @param expectedSize Expected number of entries
     * @param <K> Key type
     * @param <V> Value type
     * @return Optimal thread-safe Map implementation
     */
    public static <K, V> Map<K, V> createOptimalConcurrentMap(int expectedSize) {
        return new ConcurrentHashMap<>(calculateInitialCapacity(expectedSize));
    }
    
    /**
     * Creates an optimal List implementation with expected size.
     * 
     * @param expectedSize Expected number of elements
     * @param <T> Element type
     * @return Optimal List implementation
     */
    public static <T> List<T> createOptimalList(int expectedSize) {
        return new ArrayList<>(expectedSize);
    }
    
    /**
     * Creates an optimal List implementation with default size.
     * 
     * @param <T> Element type
     * @return Optimal List implementation
     */
    public static <T> List<T> createOptimalList() {
        return new ArrayList<>();
    }
    
    /**
     * Calculates initial capacity for collections to minimize resizing.
     * Takes into account the load factor of HashMap/HashSet (0.75).
     * 
     * @param expectedSize Expected number of elements
     * @return Initial capacity
     */
    private static int calculateInitialCapacity(int expectedSize) {
        // Add 25% overhead to account for 0.75 load factor
        return (int) (expectedSize / 0.75f) + 1;
    }
    
    /**
     * Gets information about which collection types are being used.
     * Useful for debugging and performance analysis.
     * 
     * @return CollectionInfo describing the optimal collections for this device
     */
    public static CollectionInfo getCollectionInfo() {
        return new CollectionInfo(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        );
    }
    
    /**
     * Information about collection optimizations available on this device.
     */
    public static class CollectionInfo {
        public final boolean supportsArraySet;
        public final boolean supportsConcurrentHashMapKeySet;
        
        CollectionInfo(boolean supportsArraySet, boolean supportsConcurrentHashMapKeySet) {
            this.supportsArraySet = supportsArraySet;
            this.supportsConcurrentHashMapKeySet = supportsConcurrentHashMapKeySet;
        }
        
        @Override
        public String toString() {
            return "CollectionInfo{" +
                   "supportsArraySet=" + supportsArraySet +
                   ", supportsConcurrentHashMapKeySet=" + supportsConcurrentHashMapKeySet +
                   ", apiLevel=" + Build.VERSION.SDK_INT +
                   '}';
        }
    }
}
