package org.osmdroid.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple object pool to reduce garbage collection pressure.
 * Thread-safe implementation for reusing objects.
 *
 * @param <T> the type of objects to pool
 * @since 6.1.22-lostrat
 */
public class ObjectPool<T> {
    private final List<T> mPool;
    private final int mMaxSize;
    private final PoolableFactory<T> mFactory;

    /**
     * Factory interface for creating and resetting pooled objects.
     */
    public interface PoolableFactory<T> {
        /**
         * Create a new instance of the pooled object.
         */
        T create();

        /**
         * Reset an object to its initial state before returning to pool.
         */
        void reset(T object);
    }

    /**
     * Create a new object pool.
     *
     * @param maxSize maximum number of objects to keep in pool
     * @param factory factory for creating and resetting objects
     */
    public ObjectPool(int maxSize, PoolableFactory<T> factory) {
        mMaxSize = maxSize;
        mFactory = factory;
        mPool = new ArrayList<>(maxSize);
    }

    /**
     * Acquire an object from the pool, or create a new one if pool is empty.
     *
     * @return an object ready for use
     */
    public synchronized T acquire() {
        if (mPool.isEmpty()) {
            return mFactory.create();
        }
        return mPool.remove(mPool.size() - 1);
    }

    /**
     * Return an object to the pool for reuse.
     * Object is reset before being added back to pool.
     * If pool is full, object is discarded.
     *
     * @param object the object to return (null is ignored)
     */
    public synchronized void release(T object) {
        if (object == null) return;
        if (mPool.size() < mMaxSize) {
            mFactory.reset(object);
            mPool.add(object);
        }
    }

    /**
     * Clear the pool and release all objects.
     */
    public synchronized void clear() {
        mPool.clear();
    }

    /**
     * Get current number of objects in pool.
     *
     * @return pool size
     */
    public synchronized int size() {
        return mPool.size();
    }
}

