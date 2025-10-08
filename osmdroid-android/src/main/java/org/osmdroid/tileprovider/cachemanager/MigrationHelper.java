package org.osmdroid.tileprovider.cachemanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Helper class for migrating from legacy CacheManager configurations to
 * the new optimized configuration system.
 * 
 * Provides utilities for:
 * - Migrating configuration settings
 * - Gradual feature adoption
 * - Performance comparison
 * - Backward compatibility support
 * 
 * @since 6.2.0
 */
public class MigrationHelper {
    private static final String TAG = "MigrationHelper";
    private static final String PREFS_NAME = "osmdroid_cache_manager_migration";
    private static final String KEY_MIGRATION_VERSION = "migration_version";
    private static final String KEY_OPTIMIZATIONS_ENABLED = "optimizations_enabled";
    private static final int CURRENT_MIGRATION_VERSION = 1;
    
    private final Context context;
    private final APIVersionAdapter apiAdapter;
    
    /**
     * Creates a new MigrationHelper.
     * 
     * @param context The application context
     */
    public MigrationHelper(Context context) {
        this(context, new APIVersionAdapter());
    }
    
    /**
     * Creates a new MigrationHelper with a specific API adapter.
     * 
     * @param context The application context
     * @param apiAdapter The API version adapter
     */
    public MigrationHelper(Context context, APIVersionAdapter apiAdapter) {
        this.context = context;
        this.apiAdapter = apiAdapter;
    }
    
    /**
     * Creates a default configuration for legacy code that doesn't specify configuration.
     * This provides sensible defaults while enabling optimizations where appropriate.
     * 
     * @return A default CacheManagerConfig
     */
    public CacheManagerConfig createDefaultConfig() {
        Log.i(TAG, "Creating default configuration for API level " + apiAdapter.getCurrentApiLevel());
        
        // Create default configurations for each component
        ThreadPoolConfig threadPoolConfig = createDefaultThreadPoolConfig();
        CacheConfig cacheConfig = createDefaultCacheConfig();
        RetryConfig retryConfig = createDefaultRetryConfig();
        ProgressConfig progressConfig = createDefaultProgressConfig();
        
        return new CacheManagerConfig(threadPoolConfig, cacheConfig, retryConfig, progressConfig);
    }
    
    /**
     * Creates a conservative configuration that prioritizes compatibility over performance.
     * Useful for gradual migration or when stability is critical.
     * 
     * @return A conservative CacheManagerConfig
     */
    public CacheManagerConfig createConservativeConfig() {
        Log.i(TAG, "Creating conservative configuration");
        
        ThreadPoolConfig threadPoolConfig = new ThreadPoolConfig(
            2, // Small core pool
            4, // Small max pool
            60L, // 1 minute keep-alive (seconds)
            false, // Don't use work-stealing pool
            1, // Small retry pool
            30L // 30 second shutdown timeout
        );
        
        CacheConfig cacheConfig = new CacheConfig(
            50, // Small ground resolution cache
            50, // Small tile coordinate cache
            25, // Small bounding box cache
            300000L // 5 minute eviction interval
        );
        
        RetryConfig retryConfig = new RetryConfig(
            2, // Only 2 retries
            1000L, // 1 second base delay
            2.0, // Standard backoff
            10000L, // 10 second max delay
            0.1 // Minimal jitter
        );
        
        ProgressConfig progressConfig = new ProgressConfig(
            1000L, // 1 second update interval
            10, // 10 tile batch size
            false // Standard reporting
        );
        
        return new CacheManagerConfig(threadPoolConfig, cacheConfig, retryConfig, progressConfig);
    }
    
    /**
     * Creates an aggressive configuration that maximizes performance.
     * Only recommended for modern devices with sufficient resources.
     * 
     * @return An aggressive CacheManagerConfig
     */
    public CacheManagerConfig createAggressiveConfig() {
        if (apiAdapter.getCurrentApiLevel() < APIVersionAdapter.API_LEVEL_NOUGAT) {
            Log.w(TAG, "Aggressive config requested but API level is low, using default instead");
            return createDefaultConfig();
        }
        
        Log.i(TAG, "Creating aggressive configuration");
        
        int processors = Runtime.getRuntime().availableProcessors();
        
        ThreadPoolConfig threadPoolConfig = new ThreadPoolConfig(
            processors, // Use all processors
            processors * 2, // Allow thread expansion
            30L, // 30 second keep-alive (seconds)
            true, // Use work-stealing pool
            2, // Standard retry pool
            30L // 30 second shutdown timeout
        );
        
        CacheConfig cacheConfig = new CacheConfig(
            500, // Large ground resolution cache
            500, // Large tile coordinate cache
            250, // Large bounding box cache
            600000L // 10 minute eviction interval
        );
        
        RetryConfig retryConfig = new RetryConfig(
            5, // More retries
            500L, // Shorter base delay
            1.5, // Gentler backoff
            30000L, // 30 second max delay
            0.2 // More jitter
        );
        
        ProgressConfig progressConfig = new ProgressConfig(
            500L, // 0.5 second update interval
            50, // 50 tile batch size
            true // Detailed reporting for aggressive config
        );
        
        return new CacheManagerConfig(threadPoolConfig, cacheConfig, retryConfig, progressConfig);
    }
    
    /**
     * Migrates from a legacy configuration to the new system.
     * Attempts to preserve existing behavior while enabling new features.
     * 
     * @param legacyThreadCount The legacy thread count (if any)
     * @return A migrated CacheManagerConfig
     */
    public CacheManagerConfig migrateFromLegacy(int legacyThreadCount) {
        Log.i(TAG, "Migrating from legacy configuration with thread count: " + legacyThreadCount);
        
        CacheManagerConfig defaultConfig = createDefaultConfig();
        
        if (legacyThreadCount > 0) {
            // Preserve the legacy thread count
            ThreadPoolConfig threadPoolConfig = new ThreadPoolConfig(
                legacyThreadCount,
                legacyThreadCount * 2,
                defaultConfig.getThreadPoolConfig().getKeepAliveTimeSeconds(),
                apiAdapter.isForkJoinPoolRecommended(),
                defaultConfig.getThreadPoolConfig().getRetryPoolSize(),
                defaultConfig.getThreadPoolConfig().getShutdownTimeoutSeconds()
            );
            
            return new CacheManagerConfig(
                threadPoolConfig,
                defaultConfig.getCacheConfig(),
                defaultConfig.getRetryConfig(),
                defaultConfig.getProgressConfig()
            );
        }
        
        return defaultConfig;
    }
    
    /**
     * Checks if migration is needed based on stored preferences.
     * 
     * @return true if migration is needed, false otherwise
     */
    public boolean isMigrationNeeded() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int storedVersion = prefs.getInt(KEY_MIGRATION_VERSION, 0);
        return storedVersion < CURRENT_MIGRATION_VERSION;
    }
    
    /**
     * Marks migration as complete.
     */
    public void markMigrationComplete() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putInt(KEY_MIGRATION_VERSION, CURRENT_MIGRATION_VERSION)
            .apply();
        Log.i(TAG, "Migration marked as complete");
    }
    
    /**
     * Enables or disables optimizations globally.
     * 
     * @param enabled true to enable optimizations, false to disable
     */
    public void setOptimizationsEnabled(boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putBoolean(KEY_OPTIMIZATIONS_ENABLED, enabled)
            .apply();
        Log.i(TAG, "Optimizations " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Checks if optimizations are enabled.
     * 
     * @return true if optimizations are enabled, false otherwise
     */
    public boolean areOptimizationsEnabled() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Default to true on supported devices
        boolean defaultValue = apiAdapter.getCurrentApiLevel() >= APIVersionAdapter.API_LEVEL_MARSHMALLOW;
        return prefs.getBoolean(KEY_OPTIMIZATIONS_ENABLED, defaultValue);
    }
    
    /**
     * Creates a configuration based on device capabilities and user preferences.
     * 
     * @return An appropriate CacheManagerConfig
     */
    public CacheManagerConfig createAdaptiveConfig() {
        if (!areOptimizationsEnabled()) {
            Log.i(TAG, "Optimizations disabled, using conservative config");
            return createConservativeConfig();
        }
        
        if (apiAdapter.getCurrentApiLevel() >= APIVersionAdapter.API_LEVEL_NOUGAT) {
            Log.i(TAG, "Modern device detected, using default config with optimizations");
            return createDefaultConfig();
        } else if (apiAdapter.getCurrentApiLevel() >= APIVersionAdapter.API_LEVEL_MARSHMALLOW) {
            Log.i(TAG, "Mid-range device detected, using balanced config");
            return createDefaultConfig();
        } else {
            Log.i(TAG, "Older device detected, using conservative config");
            return createConservativeConfig();
        }
    }
    
    // Private helper methods for creating default configurations
    
    private ThreadPoolConfig createDefaultThreadPoolConfig() {
        int processors = Runtime.getRuntime().availableProcessors();
        int corePoolSize = Math.max(2, processors / 2);
        int maxPoolSize = processors;
        
        return new ThreadPoolConfig(
            corePoolSize,
            maxPoolSize,
            60L, // 1 minute keep-alive (seconds)
            apiAdapter.isForkJoinPoolRecommended(),
            2, // Standard retry pool size
            30L // 30 second shutdown timeout
        );
    }
    
    private CacheConfig createDefaultCacheConfig() {
        return new CacheConfig(
            200, // Ground resolution cache
            200, // Tile coordinate cache
            100, // Bounding box cache
            300000L // 5 minute eviction interval
        );
    }
    
    private RetryConfig createDefaultRetryConfig() {
        return new RetryConfig(
            3, // 3 retries
            1000L, // 1 second base delay
            2.0, // Exponential backoff
            15000L, // 15 second max delay
            0.1 // 10% jitter
        );
    }
    
    private ProgressConfig createDefaultProgressConfig() {
        return new ProgressConfig(
            500L, // 0.5 second update interval
            20, // 20 tile batch size
            false // Standard reporting
        );
    }
    
    /**
     * Gets migration information for logging and debugging.
     * 
     * @return A MigrationInfo object
     */
    public MigrationInfo getMigrationInfo() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int storedVersion = prefs.getInt(KEY_MIGRATION_VERSION, 0);
        boolean optimizationsEnabled = areOptimizationsEnabled();
        
        return new MigrationInfo(
            storedVersion,
            CURRENT_MIGRATION_VERSION,
            isMigrationNeeded(),
            optimizationsEnabled,
            apiAdapter.getCurrentApiLevel()
        );
    }
    
    /**
     * Information about the current migration state.
     */
    public static class MigrationInfo {
        public final int currentVersion;
        public final int targetVersion;
        public final boolean migrationNeeded;
        public final boolean optimizationsEnabled;
        public final int apiLevel;
        
        MigrationInfo(int currentVersion, int targetVersion, boolean migrationNeeded,
                     boolean optimizationsEnabled, int apiLevel) {
            this.currentVersion = currentVersion;
            this.targetVersion = targetVersion;
            this.migrationNeeded = migrationNeeded;
            this.optimizationsEnabled = optimizationsEnabled;
            this.apiLevel = apiLevel;
        }
        
        @Override
        public String toString() {
            return "MigrationInfo{" +
                   "currentVersion=" + currentVersion +
                   ", targetVersion=" + targetVersion +
                   ", migrationNeeded=" + migrationNeeded +
                   ", optimizationsEnabled=" + optimizationsEnabled +
                   ", apiLevel=" + apiLevel +
                   '}';
        }
    }
}
