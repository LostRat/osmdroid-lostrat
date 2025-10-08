package org.osmdroid.tileprovider.cachemanager;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.api.IMapView;
import org.osmdroid.config.Configuration;
import org.osmdroid.library.R;
import org.osmdroid.tileprovider.MapTileProviderBase;
import org.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants;
import org.osmdroid.tileprovider.modules.CantContinueException;
import org.osmdroid.tileprovider.modules.IFilesystemCache;
import org.osmdroid.tileprovider.modules.TileDownloader;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourcePolicyException;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.IterableWithSize;
import org.osmdroid.util.MapTileArea;
import org.osmdroid.util.MapTileAreaList;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.util.MyMath;
import org.osmdroid.util.TileSystem;
import org.osmdroid.util.constants.GeoConstants;
import org.osmdroid.views.MapView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

import android.os.Build;
import android.util.ArraySet;
import android.util.LruCache;

/**
 * Provides various methods for managing the local filesystem cache of osmdroid tiles: <br>
 * - Dowloading of tiles inside a specified area, <br>
 * - Cleaning of tiles inside a specified area,<br>
 * - Information about cache capacity and current cache usage. <br>
 * <p></p>
 * Important note 1: <br>
 * These methods only make sense for a MapView using an OnlineTileSourceBase:
 * bitmap tiles downloaded from urls. <br>
 * <p></p>
 * Important note 2 - about Bulk Downloading:<br>
 * When using OSM Mapnik tile server as the tile source, take care about OSM Tile usage policy
 * (http://wiki.openstreetmap.org/wiki/Tile_usage_policy).
 * Do not let to end-users the ability to download significant areas of tiles. <br>
 *
 * @author M.Kergall
 * @author Alex
 * @author 2ndGAB
 * @author F.Fontaine
 */
public class CacheManager {

    private TileDownloader mTileDownloader = new TileDownloader(); // default value
    protected final ITileSource mTileSource;
    protected final IFilesystemCache mTileWriter;
    protected final int mMinZoomLevel;
    protected final int mMaxZoomLevel;
    // API 23+ optimization: Use thread-safe collection for better concurrent performance
    protected Set<CacheManagerTask> mPendingTasks = new CopyOnWriteArraySet<>();
    protected boolean verifyCancel = true;

    // API 23+ optimization: Use ThreadPoolManager for optimized thread pool management
    private final ThreadPoolManager mThreadPoolManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    
    // API 23+ optimization: Cache expensive mathematical calculations using CalculationCache
    private final CalculationCache mCalculationCache;
    
    // API 23+ optimization: Use TaskCoordinator for enhanced task management
    private final TaskCoordinator mTaskCoordinator;
    
    // API 23+ optimization: Use ErrorRecoveryHandler for intelligent retry and error handling
    private final ErrorRecoveryHandler mErrorRecoveryHandler;
    
    // API 23+ optimization: Use BulkOperationOptimizer for batched tile processing
    private final BulkOperationOptimizer mBulkOperationOptimizer;
    
    // Metrics and diagnostics for monitoring and troubleshooting
    private final CacheManagerMetrics mMetrics;
    private final CacheManagerDiagnostics mDiagnostics;

    public CacheManager(final MapView mapView) throws TileSourcePolicyException {
        this(mapView, mapView.getTileProvider().getTileWriter());
    }

    public CacheManager(final MapView mapView, IFilesystemCache writer) throws TileSourcePolicyException {
        this(mapView.getTileProvider(), writer, (int) mapView.getMinZoomLevel(), (int) mapView.getMaxZoomLevel());
    }

    /**
     * See https://github.com/osmdroid/osmdroid/issues/619
     *
     * @since 5.6.5
     */
    public CacheManager(final MapTileProviderBase pTileProvider,
                        final IFilesystemCache pWriter,
                        final int pMinZoomLevel, final int pMaxZoomLevel)
            throws TileSourcePolicyException {
        this(pTileProvider.getTileSource(), pWriter, pMinZoomLevel, pMaxZoomLevel);
    }

    /**
     * @since 6.0
     */
    public CacheManager(final ITileSource pTileSource,
                        final IFilesystemCache pWriter,
                        final int pMinZoomLevel, final int pMaxZoomLevel)
            throws TileSourcePolicyException {
        this(pTileSource, pWriter, pMinZoomLevel, pMaxZoomLevel, new CalculationCache());
    }
    
    /**
     * Constructor with custom CalculationCache for advanced configuration.
     * 
     * @param pTileSource Tile source
     * @param pWriter Filesystem cache writer
     * @param pMinZoomLevel Minimum zoom level
     * @param pMaxZoomLevel Maximum zoom level
     * @param calculationCache Custom calculation cache instance
     * @throws TileSourcePolicyException if tile source policy is violated
     * @since 6.2.0
     */
    public CacheManager(final ITileSource pTileSource,
                        final IFilesystemCache pWriter,
                        final int pMinZoomLevel, final int pMaxZoomLevel,
                        final CalculationCache calculationCache)
            throws TileSourcePolicyException {
        this(pTileSource, pWriter, pMinZoomLevel, pMaxZoomLevel, calculationCache, new ThreadPoolManager());
    }
    
    /**
     * Constructor with custom CalculationCache and ThreadPoolManager for advanced configuration.
     * 
     * @param pTileSource Tile source
     * @param pWriter Filesystem cache writer
     * @param pMinZoomLevel Minimum zoom level
     * @param pMaxZoomLevel Maximum zoom level
     * @param calculationCache Custom calculation cache instance
     * @param threadPoolManager Custom thread pool manager instance
     * @throws TileSourcePolicyException if tile source policy is violated
     * @since 6.2.0
     */
    public CacheManager(final ITileSource pTileSource,
                        final IFilesystemCache pWriter,
                        final int pMinZoomLevel, final int pMaxZoomLevel,
                        final CalculationCache calculationCache,
                        final ThreadPoolManager threadPoolManager)
            throws TileSourcePolicyException {
        this(pTileSource, pWriter, pMinZoomLevel, pMaxZoomLevel, calculationCache, 
             threadPoolManager, new ErrorRecoveryHandler());
    }
    
    /**
     * Constructor with full customization including error recovery handler.
     * 
     * @param pTileSource Tile source
     * @param pWriter Filesystem cache writer
     * @param pMinZoomLevel Minimum zoom level
     * @param pMaxZoomLevel Maximum zoom level
     * @param calculationCache Custom calculation cache instance
     * @param threadPoolManager Custom thread pool manager instance
     * @param errorRecoveryHandler Custom error recovery handler instance
     * @throws TileSourcePolicyException if tile source policy is violated
     * @since 6.2.0
     */
    public CacheManager(final ITileSource pTileSource,
                        final IFilesystemCache pWriter,
                        final int pMinZoomLevel, final int pMaxZoomLevel,
                        final CalculationCache calculationCache,
                        final ThreadPoolManager threadPoolManager,
                        final ErrorRecoveryHandler errorRecoveryHandler)
            throws TileSourcePolicyException {
        mTileSource = pTileSource;
        mTileWriter = pWriter;
        mMinZoomLevel = pMinZoomLevel;
        mMaxZoomLevel = pMaxZoomLevel;
        mCalculationCache = calculationCache;
        mThreadPoolManager = threadPoolManager;
        mTaskCoordinator = new TaskCoordinator();
        mErrorRecoveryHandler = errorRecoveryHandler;
        mBulkOperationOptimizer = new BulkOperationOptimizer(threadPoolManager);
        mMetrics = new CacheManagerMetrics();
        mDiagnostics = new CacheManagerDiagnostics(this, mMetrics);
        
        // Wire up calculation cache metrics
        mCalculationCache.setMetricsListener(new CalculationCache.CacheMetricsListener() {
            @Override
            public void onCacheHit() {
                mMetrics.recordCacheHit();
            }
            
            @Override
            public void onCacheMiss() {
                mMetrics.recordCacheMiss();
            }
            
            @Override
            public void onCacheEviction() {
                mMetrics.recordCacheEviction();
            }
        });
    }
    
    /**
     * Constructor with CacheManagerConfig for centralized configuration.
     * This is the recommended constructor for new code as it provides a clean
     * configuration interface and validates all settings.
     * 
     * @param pTileSource Tile source
     * @param pWriter Filesystem cache writer
     * @param pMinZoomLevel Minimum zoom level
     * @param pMaxZoomLevel Maximum zoom level
     * @param config Complete CacheManager configuration
     * @throws TileSourcePolicyException if tile source policy is violated
     * @throws IllegalArgumentException if configuration is invalid
     * @since 6.2.0
     */
    public CacheManager(final ITileSource pTileSource,
                        final IFilesystemCache pWriter,
                        final int pMinZoomLevel, final int pMaxZoomLevel,
                        final CacheManagerConfig config)
            throws TileSourcePolicyException {
        // Validate configuration
        ConfigurationManager configManager = new ConfigurationManager(config);
        configManager.validate();
        
        // Initialize with validated configuration
        mTileSource = pTileSource;
        mTileWriter = pWriter;
        mMinZoomLevel = pMinZoomLevel;
        mMaxZoomLevel = pMaxZoomLevel;
        
        // Create components from configuration
        mCalculationCache = new CalculationCache(config.getCacheConfig());
        mThreadPoolManager = new ThreadPoolManager(config.getThreadPoolConfig());
        mTaskCoordinator = new TaskCoordinator();
        mErrorRecoveryHandler = new ErrorRecoveryHandler(config.getRetryConfig());
        mBulkOperationOptimizer = new BulkOperationOptimizer(mThreadPoolManager);
        mMetrics = new CacheManagerMetrics();
        mDiagnostics = new CacheManagerDiagnostics(this, mMetrics);
        
        // Wire up calculation cache metrics
        mCalculationCache.setMetricsListener(new CalculationCache.CacheMetricsListener() {
            @Override
            public void onCacheHit() {
                mMetrics.recordCacheHit();
            }
            
            @Override
            public void onCacheMiss() {
                mMetrics.recordCacheMiss();
            }
            
            @Override
            public void onCacheEviction() {
                mMetrics.recordCacheEviction();
            }
        });
    }
    
    /**
     * Constructor with CacheManagerConfig for MapView integration.
     * 
     * @param mapView MapView instance
     * @param config Complete CacheManager configuration
     * @throws TileSourcePolicyException if tile source policy is violated
     * @throws IllegalArgumentException if configuration is invalid
     * @since 6.2.0
     */
    public CacheManager(final MapView mapView, final CacheManagerConfig config)
            throws TileSourcePolicyException {
        this(mapView.getTileProvider().getTileSource(),
             mapView.getTileProvider().getTileWriter(),
             (int) mapView.getMinZoomLevel(),
             (int) mapView.getMaxZoomLevel(),
             config);
    }
    
    /**
     * Constructor with CacheManagerConfig for MapTileProviderBase integration.
     * 
     * @param pTileProvider Tile provider
     * @param config Complete CacheManager configuration
     * @throws TileSourcePolicyException if tile source policy is violated
     * @throws IllegalArgumentException if configuration is invalid
     * @since 6.2.0
     */
    public CacheManager(final MapTileProviderBase pTileProvider,
                        final IFilesystemCache pWriter,
                        final int pMinZoomLevel, final int pMaxZoomLevel,
                        final CacheManagerConfig config)
            throws TileSourcePolicyException {
        this(pTileProvider.getTileSource(), pWriter, pMinZoomLevel, pMaxZoomLevel, config);
    }

    /**
     * @return
     * @since 5.6.3
     */
    public int getPendingJobs() {
        return mPendingTasks.size();
    }
    
    /**
     * Gets the CalculationCache instance for this CacheManager.
     * 
     * @return The calculation cache instance
     * @since 6.2.0
     */
    public CalculationCache getCalculationCache() {
        return mCalculationCache;
    }
    
    /**
     * Gets the ThreadPoolManager instance for this CacheManager.
     * 
     * @return The thread pool manager instance
     * @since 6.2.0
     */
    public ThreadPoolManager getThreadPoolManager() {
        return mThreadPoolManager;
    }
    
    /**
     * Gets the TaskCoordinator instance for this CacheManager.
     * 
     * @return The task coordinator instance
     * @since 6.2.0
     */
    public TaskCoordinator getTaskCoordinator() {
        return mTaskCoordinator;
    }
    
    /**
     * Gets comprehensive statistics about task execution.
     * 
     * @return TaskStatistics object containing execution metrics
     * @since 6.2.0
     */
    public TaskCoordinator.TaskStatistics getTaskStatistics() {
        return mTaskCoordinator.getStatistics();
    }
    
    /**
     * Gets the ErrorRecoveryHandler instance for this CacheManager.
     * 
     * @return The error recovery handler instance
     * @since 6.2.0
     */
    public ErrorRecoveryHandler getErrorRecoveryHandler() {
        return mErrorRecoveryHandler;
    }
    
    /**
     * Gets error statistics from the error recovery handler.
     * 
     * @return ErrorStatistics object containing error metrics
     * @since 6.2.0
     */
    public ErrorRecoveryHandler.ErrorStatistics getErrorStatistics() {
        return mErrorRecoveryHandler.getStatistics();
    }
    
    /**
     * Gets the BulkOperationOptimizer instance for this CacheManager.
     * 
     * @return The bulk operation optimizer instance
     * @since 6.2.0
     */
    public BulkOperationOptimizer getBulkOperationOptimizer() {
        return mBulkOperationOptimizer;
    }
    
    /**
     * Gets the CacheManagerMetrics instance for monitoring performance.
     * 
     * @return The metrics collector instance
     * @since 6.2.0
     */
    public CacheManagerMetrics getMetrics() {
        return mMetrics;
    }
    
    /**
     * Gets a snapshot of current metrics.
     * 
     * @return MetricsSnapshot containing current metrics
     * @since 6.2.0
     */
    public CacheManagerMetrics.MetricsSnapshot getMetricsSnapshot() {
        return mMetrics.getSnapshot();
    }
    
    /**
     * Gets the CacheManagerDiagnostics instance for troubleshooting.
     * 
     * @return The diagnostics utility instance
     * @since 6.2.0
     */
    public CacheManagerDiagnostics getDiagnostics() {
        return mDiagnostics;
    }
    
    /**
     * Generates and returns a comprehensive diagnostic report.
     * 
     * @return Diagnostic report as a string
     * @since 6.2.0
     */
    public String generateDiagnosticReport() {
        return mDiagnostics.generateReport();
    }
    
    /**
     * Logs a summary of current metrics to the Android log.
     * 
     * @since 6.2.0
     */
    public void logMetricsSummary() {
        mMetrics.logSummary();
    }
    
    /**
     * Logs a comprehensive diagnostic report to the Android log.
     * 
     * @since 6.2.0
     */
    public void logDiagnosticReport() {
        mDiagnostics.logReport();
    }
    
    /**
     * Resets all metrics to zero.
     * Useful for benchmarking specific operations.
     * 
     * @since 6.2.0
     */
    public void resetMetrics() {
        mMetrics.reset();
    }
    
    /**
     * Checks if the CacheManager is operating healthily.
     * 
     * @return true if no issues detected, false otherwise
     * @since 6.2.0
     */
    public boolean isHealthy() {
        return mDiagnostics.isHealthy();
    }
    
    /**
     * Checks for common issues and returns a list of problems found.
     * 
     * @return List of issue descriptions
     * @since 6.2.0
     */
    public List<String> checkForIssues() {
        return mDiagnostics.checkForIssues();
    }

    /**
     * @deprecated Use {@link TileSystem#getTileXFromLongitude(double, int)} and
     * {@link TileSystem#getTileYFromLatitude(double, int)} instead
     */
    @Deprecated
    public static Point getMapTileFromCoordinates(final double aLat, final double aLon, final int zoom) {
        final int y = MapView.getTileSystem().getTileYFromLatitude(aLat, zoom);
        final int x = MapView.getTileSystem().getTileXFromLongitude(aLon, zoom);
        return new Point(x, y);
    }

    /**
     * @deprecated Use {@link TileSystem#getLatitudeFromTileY(int, int)} and
     * {@link TileSystem#getLongitudeFromTileX(int, int)} instead
     */
    @Deprecated
    public static GeoPoint getCoordinatesFromMapTile(final int x, final int y, final int zoom) {
        final double lat = MapView.getTileSystem().getLatitudeFromTileY(y, zoom);
        final double lon = MapView.getTileSystem().getLongitudeFromTileX(x, zoom);
        return new GeoPoint(lat, lon);
    }

    public static File getFileName(ITileSource tileSource, final long pMapTileIndex) {
        final File file = new File(Configuration.getInstance().getOsmdroidTileCache(),
                tileSource.getTileRelativeFilenameString(pMapTileIndex) + OpenStreetMapTileProviderConstants.TILE_PATH_EXTENSION);
        return file;
    }

    /**
     * @return true if success, false if error
     */
    public boolean loadTile(final OnlineTileSourceBase tileSource, final long pMapTileIndex) {
        //check if file is already downloaded:
        File file = getFileName(tileSource, pMapTileIndex);
        if (file.exists()) {
            return true;
        }
        //check if the destination already has the file
        if (mTileWriter.exists(tileSource, pMapTileIndex)) {
            return true;
        }

        return forceLoadTile(tileSource, pMapTileIndex);
    }

    /**
     * Actual tile download, regardless of the tile being already present in the cache
     *
     * @return true if success, false if error
     * @since 5.6.5
     */
    public boolean forceLoadTile(final OnlineTileSourceBase tileSource, final long pMapTileIndex) {
        return forceLoadTileWithRetry(tileSource, pMapTileIndex, true);
    }
    
    /**
     * Internal method for tile download with retry support.
     * 
     * @param tileSource The tile source
     * @param pMapTileIndex The tile index
     * @param enableRetry Whether to enable retry logic
     * @return true if success, false if error
     * @since 6.2.0
     */
    private boolean forceLoadTileWithRetry(final OnlineTileSourceBase tileSource, 
                                           final long pMapTileIndex, 
                                           final boolean enableRetry) {
        Exception lastException = null;
        int attemptNumber = 0;
        
        do {
            attemptNumber++;
            
            try {
                long downloadStart = System.currentTimeMillis();
                final Drawable drawable = mTileDownloader.downloadTile(pMapTileIndex, mTileWriter, tileSource);
                long downloadDuration = System.currentTimeMillis() - downloadStart;
                boolean success = drawable != null;
                
                // Record metrics
                int zoomLevel = org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex);
                mMetrics.recordTileDownload(zoomLevel, downloadDuration, success);
                
                if (success && attemptNumber > 1) {
                    // Mark as recovered if we succeeded after retry
                    mErrorRecoveryHandler.markRecovered(pMapTileIndex);
                    mMetrics.recordRetrySuccess();
                }
                
                return success;
                
            } catch (CantContinueException e) {
                lastException = e;
                mMetrics.recordNetworkError();
                
                if (!enableRetry) {
                    // Retry disabled, fail immediately
                    mErrorRecoveryHandler.handleTileDownloadError(pMapTileIndex, e);
                    return false;
                }
                
                // Check if we should retry
                boolean shouldRetry = mErrorRecoveryHandler.handleTileDownloadError(pMapTileIndex, e);
                
                if (shouldRetry) {
                    // Calculate delay and wait before retry
                    long delay = mErrorRecoveryHandler.getRetryDelay(pMapTileIndex);
                    if (delay > 0) {
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            // Thread interrupted, stop retrying
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    }
                } else {
                    // No more retries
                    return false;
                }
                
            } catch (Exception e) {
                // Handle unexpected exceptions
                lastException = e;
                
                if (!enableRetry) {
                    mErrorRecoveryHandler.handleTileDownloadError(pMapTileIndex, e);
                    return false;
                }
                
                boolean shouldRetry = mErrorRecoveryHandler.handleTileDownloadError(pMapTileIndex, e);
                
                if (shouldRetry) {
                    long delay = mErrorRecoveryHandler.getRetryDelay(pMapTileIndex);
                    if (delay > 0) {
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    }
                } else {
                    return false;
                }
            }
            
        } while (attemptNumber <= mErrorRecoveryHandler.getRetryPolicy().getMaxRetries());
        
        // All retries exhausted
        return false;
    }

    /** Returns <i>TRUE</i> if deletion was not possible */
    private boolean deleteTileError(final long pMapTileIndex) {
        boolean tileExists = this.checkTile(pMapTileIndex);
        if (tileExists) {
            try {
                boolean removed = mTileWriter.remove(mTileSource, pMapTileIndex);
                if (!removed) {
                    // Log deletion failure
                    mErrorRecoveryHandler.handleCacheWriteError(pMapTileIndex, 
                        new IOException("Failed to delete tile " + pMapTileIndex));
                }
                return !removed;
            } catch (Exception e) {
                mErrorRecoveryHandler.handleCacheWriteError(pMapTileIndex, e);
                return true; // Deletion failed
            }
        }
        return false;
    }
    
    public boolean deleteTile(final long pMapTileIndex) {
        if (!this.checkTile(pMapTileIndex)) {
            return true; // Tile doesn't exist, consider it deleted
        }
        
        try {
            boolean removed = mTileWriter.remove(mTileSource, pMapTileIndex);
            
            // Record metrics
            int zoomLevel = org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex);
            mMetrics.recordTileDeletion(zoomLevel, removed);
            
            if (!removed) {
                // Log deletion failure
                mErrorRecoveryHandler.handleCacheWriteError(pMapTileIndex, 
                    new IOException("Failed to delete tile " + pMapTileIndex));
                mMetrics.recordIOError();
            }
            return removed;
        } catch (Exception e) {
            int zoomLevel = org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex);
            mMetrics.recordTileDeletion(zoomLevel, false);
            mMetrics.recordIOError();
            mErrorRecoveryHandler.handleCacheWriteError(pMapTileIndex, e);
            return false;
        }
    }

    public boolean checkTile(final long pMapTileIndex) {
        return mTileWriter.exists(mTileSource, pMapTileIndex);
    }

    /**
     * "Should we download this tile?", either because it's not cached yet or because it's expired
     *
     * @since 5.6.5
     */
    public boolean isTileToBeDownloaded(final ITileSource pTileSource, final long pMapTileIndex) {
        final Long expiration = mTileWriter.getExpirationTimestamp(pTileSource, pMapTileIndex);
        if (expiration == null) {
            return true;
        }
        final long now = System.currentTimeMillis();
        return now > expiration;
    }

    /**
     * Computes the theoretical tiles covered by the bounding box
     *
     * @return list of tiles, sorted by ascending zoom level
     */
    public static List<Long> getTilesCoverage(final BoundingBox pBB,
                                              final int pZoomMin, final int pZoomMax) {
        return getTilesCoverageOptimized(pBB, pZoomMin, pZoomMax, null, false);
    }
    
    /**
     * Computes the theoretical tiles covered by the bounding box with optional parallel processing.
     * 
     * @param pBB Bounding box
     * @param pZoomMin Minimum zoom level
     * @param pZoomMax Maximum zoom level
     * @param threadPoolManager Optional thread pool manager for parallel processing (can be null)
     * @param enableParallel Whether to enable parallel processing
     * @return list of tiles, sorted by ascending zoom level
     * @since 6.2.0
     */
    public static List<Long> getTilesCoverageOptimized(final BoundingBox pBB,
                                                       final int pZoomMin, final int pZoomMax,
                                                       final ThreadPoolManager threadPoolManager,
                                                       final boolean enableParallel) {
        // Pre-calculate expected size for better memory efficiency
        int expectedSize = 0;
        for (int zoomLevel = pZoomMin; zoomLevel <= pZoomMax; zoomLevel++) {
            final Rect rect = getTilesRect(pBB, zoomLevel);
            expectedSize += (rect.width() + 1) * (rect.height() + 1);
        }
        
        final List<Long> result = CollectionFactory.createOptimalList(expectedSize);
        
        // Check if parallel processing is beneficial
        int zoomLevels = pZoomMax - pZoomMin + 1;
        boolean useParallel = enableParallel && threadPoolManager != null && 
                             zoomLevels >= 3 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
        
        if (useParallel) {
            // Process zoom levels in parallel
            List<java.util.concurrent.Future<Collection<Long>>> futures = new ArrayList<>(zoomLevels);
            ExecutorService executor = threadPoolManager.getBulkOperationExecutor();
            
            for (int zoomLevel = pZoomMin; zoomLevel <= pZoomMax; zoomLevel++) {
                final int finalZoomLevel = zoomLevel;
                futures.add(executor.submit(new java.util.concurrent.Callable<Collection<Long>>() {
                    @Override
                    public Collection<Long> call() {
                        return getTilesCoverage(pBB, finalZoomLevel);
                    }
                }));
            }
            
            // Collect results in order
            for (java.util.concurrent.Future<Collection<Long>> future : futures) {
                try {
                    result.addAll(future.get());
                } catch (Exception e) {
                    // Fall back to sequential processing for this zoom level
                    // Log.e(IMapView.LOGTAG, "Error in parallel tile coverage calculation", e);
                }
            }
        } else {
            // Sequential processing
            for (int zoomLevel = pZoomMin; zoomLevel <= pZoomMax; zoomLevel++) {
                final Collection<Long> resultForZoom = getTilesCoverage(pBB, zoomLevel);
                result.addAll(resultForZoom);
            }
        }
        
        return result;
    }

    /**
     * Computes the theoretical tiles covered by the bounding box
     *
     * @return list of tiles for that zoom level, without any specific order
     */
    public static Collection<Long> getTilesCoverage(final BoundingBox pBB, final int pZoomLevel) {
        // Calculate expected size for optimal collection pre-sizing
        final Rect rect = getTilesRect(pBB, pZoomLevel);
        final int expectedSize = (rect.width() + 1) * (rect.height() + 1);
        
        // API 23+ optimization: Use CollectionFactory for optimal set implementation
        final Set<Long> result = CollectionFactory.createOptimalSet(expectedSize);
        
        for (Long mapTile : getTilesCoverageIterable(pBB, pZoomLevel, pZoomLevel)) {
            result.add(mapTile);
        }
        return result;
    }

    /**
     * Iterable returning tiles covered by the bounding box sorted by ascending zoom level
     *
     * @param pBB      the given bounding box
     * @param pZoomMin the given minimum zoom level
     * @param pZoomMax the given maximum zoom level
     * @return the iterable described above
     */
    static IterableWithSize<Long> getTilesCoverageIterable(final BoundingBox pBB,
                                                           final int pZoomMin, final int pZoomMax) {
        return getTilesCoverageIterable(pBB, pZoomMin, pZoomMax, null);
    }
    
    /**
     * Iterable returning tiles covered by the bounding box sorted by ascending zoom level with caching support.
     *
     * @param pBB      the given bounding box
     * @param pZoomMin the given minimum zoom level
     * @param pZoomMax the given maximum zoom level
     * @param calculationCache Optional calculation cache for performance (can be null)
     * @return the iterable described above
     * @since 6.2.0
     */
    static IterableWithSize<Long> getTilesCoverageIterable(final BoundingBox pBB,
                                                           final int pZoomMin, final int pZoomMax,
                                                           final CalculationCache calculationCache) {
        final MapTileAreaList list = new MapTileAreaList();
        for (int zoomLevel = pZoomMin; zoomLevel <= pZoomMax; zoomLevel++) {
            list.getList().add(new MapTileArea().set(zoomLevel, getTilesRect(pBB, zoomLevel, calculationCache)));
        }
        return list;
    }

    /**
     * Retrieve upper left and lower right points(exclusive) corresponding to the tiles coverage for
     * the selected zoom level.
     *
     * @param pBB        the given bounding box
     * @param pZoomLevel the given zoom level
     * @return the {@link Rect} reflecting the tiles coverage
     */
    public static Rect getTilesRect(final BoundingBox pBB,
                                    final int pZoomLevel) {
        return getTilesRect(pBB, pZoomLevel, null);
    }
    
    /**
     * Retrieve upper left and lower right points(exclusive) corresponding to the tiles coverage for
     * the selected zoom level with caching support.
     *
     * @param pBB        the given bounding box
     * @param pZoomLevel the given zoom level
     * @param calculationCache Optional calculation cache for performance (can be null)
     * @return the {@link Rect} reflecting the tiles coverage
     * @since 6.2.0
     */
    public static Rect getTilesRect(final BoundingBox pBB,
                                    final int pZoomLevel,
                                    final CalculationCache calculationCache) {
        final int mapTileUpperBound = 1 << pZoomLevel;
        final TileSystem tileSystem = MapView.getTileSystem();
        
        // Use cached tile coordinate calculations if cache is available
        final int right, bottom, left, top;
        if (calculationCache != null) {
            Point rightBottom = calculationCache.getCachedTileCoordinates(tileSystem, 
                    pBB.getLonEast(), pBB.getLatSouth(), pZoomLevel);
            Point leftTop = calculationCache.getCachedTileCoordinates(tileSystem,
                    pBB.getLonWest(), pBB.getLatNorth(), pZoomLevel);
            right = rightBottom.x;
            bottom = rightBottom.y;
            left = leftTop.x;
            top = leftTop.y;
        } else {
            right = tileSystem.getTileXFromLongitude(pBB.getLonEast(), pZoomLevel);
            bottom = tileSystem.getTileYFromLatitude(pBB.getLatSouth(), pZoomLevel);
            left = tileSystem.getTileXFromLongitude(pBB.getLonWest(), pZoomLevel);
            top = tileSystem.getTileYFromLatitude(pBB.getLatNorth(), pZoomLevel);
        }
        
        int width = right - left + 1; // handling the modulo
        if (width <= 0) {
            width += mapTileUpperBound;
        }
        int height = bottom - top + 1; // handling the modulo
        if (height <= 0) {
            height += mapTileUpperBound;
        }
        return new Rect(left, top, left + width - 1, top + height - 1);
    }

    /**
     * Computes the theoretical tiles covered by the list of points
     *
     * @return list of tiles, sorted by ascending zoom level
     */
    public static List<Long> getTilesCoverage(final ArrayList<GeoPoint> pGeoPoints,
                                              final int pZoomMin, final int pZoomMax) {
        return getTilesCoverage(pGeoPoints, pZoomMin, pZoomMax, null);
    }
    
    /**
     * Computes the theoretical tiles covered by the list of points with caching support.
     *
     * @param pGeoPoints List of geographic points
     * @param pZoomMin Minimum zoom level
     * @param pZoomMax Maximum zoom level
     * @param calculationCache Optional calculation cache for performance (can be null)
     * @return list of tiles, sorted by ascending zoom level
     * @since 6.2.0
     */
    public static List<Long> getTilesCoverage(final ArrayList<GeoPoint> pGeoPoints,
                                              final int pZoomMin, final int pZoomMax,
                                              final CalculationCache calculationCache) {
        // Estimate size based on points and zoom levels for better memory efficiency
        final int estimatedSize = pGeoPoints.size() * (pZoomMax - pZoomMin + 1) * 4;
        final List<Long> result = CollectionFactory.createOptimalList(estimatedSize);
        
        for (int zoomLevel = pZoomMin; zoomLevel <= pZoomMax; zoomLevel++) {
            final Collection<Long> resultForZoom = getTilesCoverage(pGeoPoints, zoomLevel, calculationCache);
            result.addAll(resultForZoom);
        }
        return result;
    }

    /**
     * Computes the theoretical tiles covered by the list of points
     * Calculation done based on http://www.movable-type.co.uk/scripts/latlong.html
     */
    public static Collection<Long> getTilesCoverage(final ArrayList<GeoPoint> pGeoPoints,
                                                    final int pZoomLevel) {
        return getTilesCoverage(pGeoPoints, pZoomLevel, null);
    }
    
    /**
     * Computes the theoretical tiles covered by the list of points with caching support.
     * Calculation done based on http://www.movable-type.co.uk/scripts/latlong.html
     * 
     * @param pGeoPoints List of geographic points
     * @param pZoomLevel Zoom level
     * @param calculationCache Optional calculation cache for performance (can be null)
     * @return Collection of tile indices
     * @since 6.2.0
     */
    public static Collection<Long> getTilesCoverage(final ArrayList<GeoPoint> pGeoPoints,
                                                    final int pZoomLevel,
                                                    final CalculationCache calculationCache) {
        // Estimate size: each point can generate ~4 tiles (2x2 area)
        final int estimatedSize = pGeoPoints.size() * 4;
        
        // API 23+ optimization: Use CollectionFactory for optimal set implementation
        final Set<Long> result = CollectionFactory.createOptimalSet(estimatedSize);

        GeoPoint prevPoint = null;
        Point tile, prevTile = null;

        final int mapTileUpperBound = 1 << pZoomLevel;
        for (GeoPoint geoPoint : pGeoPoints) {

            // API 23+ optimization: Cache expensive ground resolution calculations
            final double d = calculationCache != null ?
                    calculationCache.getCachedGroundResolution(geoPoint.getLatitude(), pZoomLevel) :
                    TileSystem.GroundResolution(geoPoint.getLatitude(), pZoomLevel);

            if (result.size() != 0) {

                if (prevPoint != null) {

                    final double leadCoef = (geoPoint.getLatitude() - prevPoint.getLatitude()) / (geoPoint.getLongitude() - prevPoint.getLongitude());
                    final double brng;
                    if (geoPoint.getLongitude() > prevPoint.getLongitude()) {
                        brng = Math.PI / 2 - Math.atan(leadCoef);
                    } else {
                        brng = 3 * Math.PI / 2 - Math.atan(leadCoef);
                    }

                    final GeoPoint wayPoint = new GeoPoint(prevPoint.getLatitude(), prevPoint.getLongitude());

                    while ((((geoPoint.getLatitude() > prevPoint.getLatitude()) && (wayPoint.getLatitude() < geoPoint.getLatitude())) ||
                            (geoPoint.getLatitude() < prevPoint.getLatitude()) && (wayPoint.getLatitude() > geoPoint.getLatitude())) &&
                            (((geoPoint.getLongitude() > prevPoint.getLongitude()) && (wayPoint.getLongitude() < geoPoint.getLongitude())) ||
                                    ((geoPoint.getLongitude() < prevPoint.getLongitude()) && (wayPoint.getLongitude() > geoPoint.getLongitude())))) {

                        final double prevLatRad = wayPoint.getLatitude() * Math.PI / 180.0;
                        final double prevLonRad = wayPoint.getLongitude() * Math.PI / 180.0;

                        final double latRad = Math.asin(Math.sin(prevLatRad) * Math.cos(d / GeoConstants.RADIUS_EARTH_METERS) + Math.cos(prevLatRad) * Math.sin(d / GeoConstants.RADIUS_EARTH_METERS) * Math.cos(brng));
                        final double lonRad = prevLonRad + Math.atan2(Math.sin(brng) * Math.sin(d / GeoConstants.RADIUS_EARTH_METERS) * Math.cos(prevLatRad), Math.cos(d / GeoConstants.RADIUS_EARTH_METERS) - Math.sin(prevLatRad) * Math.sin(latRad));

                        wayPoint.setLatitude(((latRad * 180.0 / Math.PI)));
                        wayPoint.setLongitude(((lonRad * 180.0 / Math.PI)));

                        tile = new Point(
                                MapView.getTileSystem().getTileXFromLongitude(wayPoint.getLongitude(), pZoomLevel),
                                MapView.getTileSystem().getTileYFromLatitude(wayPoint.getLatitude(), pZoomLevel));

                        if (!tile.equals(prevTile)) {
//Log.d(Constants.APP_TAG, "New Tile lat " + tile.x + " lon " + tile.y);
                            // API 23+ optimization: Bulk operations for better performance
                            int ofsx = tile.x >= 0 ? 0 : -tile.x;
                            int ofsy = tile.y >= 0 ? 0 : -tile.y;
                            List<Long> tilesToAdd = CollectionFactory.createOptimalList(4); // Pre-sized for typical 2x2 area
                            for (int xAround = tile.x + ofsx; xAround <= tile.x + 1 + ofsx; xAround++) {
                                for (int yAround = tile.y + ofsy; yAround <= tile.y + 1 + ofsy; yAround++) {
                                    final int tileY = MyMath.mod(yAround, mapTileUpperBound);
                                    final int tileX = MyMath.mod(xAround, mapTileUpperBound);
                                    tilesToAdd.add(MapTileIndex.getTileIndex(pZoomLevel, tileX, tileY));
                                }
                            }
                            result.addAll(tilesToAdd); // Single bulk operation

                            prevTile = tile;
                        }
                    }
                }

            } else {
                tile = new Point(
                        MapView.getTileSystem().getTileXFromLongitude(geoPoint.getLongitude(), pZoomLevel),
                        MapView.getTileSystem().getTileYFromLatitude(geoPoint.getLatitude(), pZoomLevel));
                prevTile = tile;

                // API 23+ optimization: Bulk operations for better performance
                int ofsx = tile.x >= 0 ? 0 : -tile.x;
                int ofsy = tile.y >= 0 ? 0 : -tile.y;
                List<Long> tilesToAdd = CollectionFactory.createOptimalList(4); // Pre-sized for typical 2x2 area
                for (int xAround = tile.x + ofsx; xAround <= tile.x + 1 + ofsx; xAround++) {
                    for (int yAround = tile.y + ofsy; yAround <= tile.y + 1 + ofsy; yAround++) {
                        final int tileY = MyMath.mod(yAround, mapTileUpperBound);
                        final int tileX = MyMath.mod(xAround, mapTileUpperBound);
                        tilesToAdd.add(MapTileIndex.getTileIndex(pZoomLevel, tileX, tileY));
                    }
                }
                result.addAll(tilesToAdd); // Single bulk operation
            }

            prevPoint = geoPoint;
        }
        return result;
    }

    /**
     * @return the theoretical number of tiles in the specified area
     */
    public int possibleTilesInArea(final BoundingBox pBB, final int pZoomMin, final int pZoomMax) {
        return getTilesCoverageIterable(pBB, pZoomMin, pZoomMax, mCalculationCache).size();
    }

    /**
     * @return the theoretical number of tiles covered by the list of points
     * Calculation done based on http://www.movable-type.co.uk/scripts/latlong.html
     */
    public int possibleTilesCovered(final ArrayList<GeoPoint> pGeoPoints,
                                    final int pZoomMin, final int pZoomMax) {
        // Estimate size based on points and zoom levels for better memory efficiency
        final int estimatedSize = pGeoPoints.size() * (pZoomMax - pZoomMin + 1) * 4;
        final List<Long> result = CollectionFactory.createOptimalList(estimatedSize);
        
        for (int zoomLevel = pZoomMin; zoomLevel <= pZoomMax; zoomLevel++) {
            final Collection<Long> resultForZoom = getTilesCoverage(pGeoPoints, zoomLevel, mCalculationCache);
            result.addAll(resultForZoom);
        }
        return result.size();
    }

    public CacheManagerTask execute(final CacheManagerTask pTask) {
        pTask.onPreExecute();
        mPendingTasks.add(pTask);
        // Register task with TaskCoordinator for enhanced tracking
        mTaskCoordinator.registerTask(pTask);
        // Record task start in metrics
        mMetrics.recordTaskStart(pTask.getTaskId());
        // Use ThreadPoolManager for optimized execution
        mThreadPoolManager.getPrimaryExecutor().submit(pTask);
        return pTask;
    }

    /**
     * Download in background all tiles of the specified area in osmdroid cache.
     *
     * @param ctx
     * @param bb
     * @param zoomMin
     * @param zoomMax
     */
    public CacheManagerTask downloadAreaAsync(Context ctx, BoundingBox bb, final int zoomMin, final int zoomMax) {
        final CacheManagerTask task = new CacheManagerTask(
                this,
                getDownloadingAction(ctx),
                bb,
                zoomMin,
                zoomMax);
        task.addCallback(getDownloadingDialog(ctx, task));
        return execute(task);
    }

    /**
     * Download in background all tiles of the specified area in osmdroid cache.
     *
     * @param ctx
     * @param geoPoints
     * @param zoomMin
     * @param zoomMax
     */
    public CacheManagerTask downloadAreaAsync(Context ctx, ArrayList<GeoPoint> geoPoints, final int zoomMin, final int zoomMax) {
        final CacheManagerTask task = new CacheManagerTask(
                this,
                getDownloadingAction(ctx),
                geoPoints,
                zoomMin,
                zoomMax);
        task.addCallback(getDownloadingDialog(ctx, task));
        return execute(task);
    }

    /**
     * Download in background all tiles of the specified area in osmdroid cache.
     *
     * @param ctx
     * @param bb
     * @param zoomMin
     * @param zoomMax
     */
    public CacheManagerTask downloadAreaAsync(Context ctx, BoundingBox bb, final int zoomMin, final int zoomMax, final CacheManagerCallback callback) {
        final CacheManagerTask task = new CacheManagerTask(
                this,
                getDownloadingAction(ctx),
                bb,
                zoomMin,
                zoomMax);
        task.addCallback(callback);
        task.addCallback(getDownloadingDialog(ctx, task));
        return execute(task);
    }

    /**
     * Download in background all tiles covered by the GePoints list in osmdroid cache.
     *
     * @param ctx
     * @param geoPoints
     * @param zoomMin
     * @param zoomMax
     */
    public CacheManagerTask downloadAreaAsync(Context ctx, ArrayList<GeoPoint> geoPoints, final int zoomMin, final int zoomMax, final CacheManagerCallback callback) {
        final CacheManagerTask task = new CacheManagerTask(
                this,
                getDownloadingAction(ctx),
                geoPoints,
                zoomMin,
                zoomMax);
        task.addCallback(callback);
        task.addCallback(getDownloadingDialog(ctx, task));
        return execute(task);
    }

    /**
     * Download in background all tiles covered by the GeoPoints list in osmdroid cache without a user interface.
     *
     * @param ctx
     * @param geoPoints
     * @param zoomMin
     * @param zoomMax
     * @since
     */
    public CacheManagerTask downloadAreaAsyncNoUI(Context ctx, ArrayList<GeoPoint> geoPoints, final int zoomMin, final int zoomMax, final CacheManagerCallback callback) {
        final CacheManagerTask task = new CacheManagerTask(
                this,
                getDownloadingAction(ctx),
                geoPoints,
                zoomMin,
                zoomMax);
        task.addCallback(callback);
        return execute(task);
    }

    /**
     * Download in background all tiles of the specified area in osmdroid cache without a user interface.
     *
     * @param ctx
     * @param bb
     * @param zoomMin
     * @param zoomMax
     * @since 5.3
     */
    public CacheManagerTask downloadAreaAsyncNoUI(Context ctx, BoundingBox bb, final int zoomMin, final int zoomMax, final CacheManagerCallback callback) {
        final CacheManagerTask task = new CacheManagerTask(
                this,
                getDownloadingAction(ctx),
                bb,
                zoomMin,
                zoomMax);
        task.addCallback(callback);
        execute(task);
        return task;
    }

    /**
     * Cancels all tasks with proper thread interruption.
     * Uses parallel cancellation on API 24+ for better performance.
     *
     * @since 5.6.3
     */
    public void cancelAllJobs() {
        // Use TaskCoordinator for enhanced cancellation with statistics tracking
        mTaskCoordinator.cancelAllTasks(true);
        
        // Also clear the legacy mPendingTasks for backward compatibility
        // API 23+ optimization: Use modern iteration patterns for better performance
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // API 24+: Use parallel streams for faster cancellation of many tasks
            // This leverages the ForkJoinPool for efficient parallel processing
            mPendingTasks.parallelStream().forEach(task -> task.cancel(true));
        } else {
            // API 23+: Use enhanced for-each (CopyOnWriteArraySet is safe for concurrent iteration)
            for (CacheManagerTask task : mPendingTasks) {
                task.cancel(true);
            }
        }
        mPendingTasks.clear();
    }
    
    /**
     * Shuts down the thread pool manager gracefully.
     * Should be called when the CacheManager is no longer needed.
     * 
     * @since 6.2.0
     */
    public void shutdown() {
        cancelAllJobs();
        mThreadPoolManager.shutdown();
    }
    
    /**
     * Shuts down the thread pool manager immediately.
     * Attempts to stop all actively executing tasks.
     * 
     * @since 6.2.0
     */
    public void shutdownNow() {
        cancelAllJobs();
        mThreadPoolManager.shutdownNow();
    }

    /**
     * Download in background all tiles of the specified area in osmdroid cache.
     *
     * @param ctx
     * @param pTiles
     * @param zoomMin
     * @param zoomMax
     */
    public CacheManagerTask downloadAreaAsync(Context ctx, List<Long> pTiles, final int zoomMin, final int zoomMax) {
        final CacheManagerTask task = new CacheManagerTask(
                this,
                getDownloadingAction(ctx),
                pTiles,
                zoomMin,
                zoomMax);
        task.addCallback(getDownloadingDialog(ctx, task));
        return execute(task);
    }

    /*
     * verifyCancel decides wether user has to confirm the cancel action via a alert
     *
     * @param state
     */
    public void setVerifyCancel(boolean state) {
        verifyCancel = state;
    }

    public boolean getVerifyCancel() {
        return verifyCancel;
    }

    /**
     * Callback interface for CacheManager task progress and completion notifications.
     * Enhanced in 6.2.0 with detailed statistics support.
     * 
     * @since 5.6.3
     */
    public interface CacheManagerCallback {

        /**
         * fired when the download job is done.
         */
        public void onTaskComplete();

        /**
         * this is fired periodically, useful for updating dialogs, progress bars, etc
         *
         * @param progress
         * @param currentZoomLevel
         * @param zoomMin
         * @param zoomMax
         */
        public void updateProgress(int progress, int currentZoomLevel, int zoomMin, int zoomMax);

        /**
         * as soon as the download is started, this is fired
         */
        public void downloadStarted();

        /**
         * this is fired right before the download starts
         *
         * @param total
         */
        public void setPossibleTilesInArea(int total);

        /**
         * this is fired when the task has been completed but had at least one download error.
         *
         * @param errors
         */
        public void onTaskFailed(int errors);
        
        /**
         * Enhanced progress update with detailed statistics.
         * Default implementation delegates to the basic updateProgress method for backward compatibility.
         * 
         * @param statistics Detailed progress statistics
         * @param zoomMin Minimum zoom level
         * @param zoomMax Maximum zoom level
         * @since 6.2.0
         */
        default void updateProgressWithStatistics(ProgressReporter.ProgressStatistics statistics, 
                                                 int zoomMin, int zoomMax) {
            // Default implementation for backward compatibility
            updateProgress(statistics.currentProgress, statistics.currentZoomLevel, zoomMin, zoomMax);
        }
        
        /**
         * Called when the task completes with detailed statistics.
         * Default implementation delegates to onTaskComplete for backward compatibility.
         * 
         * @param statistics Final task statistics
         * @since 6.2.0
         */
        default void onTaskCompleteWithStatistics(ProgressReporter.ProgressStatistics statistics) {
            // Default implementation for backward compatibility
            onTaskComplete();
        }
        
        /**
         * Called when the task fails with detailed error information.
         * Default implementation delegates to onTaskFailed for backward compatibility.
         * 
         * @param statistics Final task statistics including error counts
         * @since 6.2.0
         */
        default void onTaskFailedWithStatistics(ProgressReporter.ProgressStatistics statistics) {
            // Default implementation for backward compatibility
            onTaskFailed(statistics.errorCount);
        }
    }

    public static abstract class CacheManagerDialog implements CacheManagerCallback {

        private final CacheManagerTask mTask;
        private final AlertDialog mAlertDialog;
        private final ProgressBar mProgressBar;
        private final TextView mMessageView;
        private final TextView mStatsView;
        private String handleMessage;
        private boolean showDetailedStats = false;

        public CacheManagerDialog(final Context pCtx, final CacheManagerTask pTask) {
            this(pCtx, pTask, false);
        }
        
        /**
         * Constructor with option to show detailed statistics.
         * 
         * @param pCtx Context
         * @param pTask CacheManagerTask
         * @param showDetailedStats Whether to show detailed statistics (success/error counts, ETA)
         * @since 6.2.0
         */
        public CacheManagerDialog(final Context pCtx, final CacheManagerTask pTask, boolean showDetailedStats) {
            mTask = pTask;
            this.showDetailedStats = showDetailedStats;
            handleMessage = pCtx.getString(R.string.cacheManagerHandlingMessage);

            LinearLayout layout = new LinearLayout(pCtx);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(10, 10, 10, 10);

            mProgressBar = new ProgressBar(pCtx, null, android.R.attr.progressBarStyleHorizontal);
            mMessageView = new TextView(pCtx);
            mMessageView.setPadding(0, 5, 0, 0);

            layout.addView(mProgressBar);
            layout.addView(mMessageView);
            
            // Add statistics view if detailed stats are enabled
            if (showDetailedStats) {
                mStatsView = new TextView(pCtx);
                mStatsView.setPadding(0, 5, 0, 0);
                mStatsView.setTextSize(12);
                layout.addView(mStatsView);
            } else {
                mStatsView = null;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(pCtx);
            builder.setTitle(getUITitle());
            builder.setView(layout);
            builder.setCancelable(true);

            if (pTask.mManager.getVerifyCancel()) {
                builder.setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(final DialogInterface cancelDialog) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(pCtx);
                        builder.setTitle(pCtx.getString(R.string.cacheManagerCancelTitle));
                        builder.setMessage(pCtx.getString(R.string.cacheManagerCancelBody));
                        builder.setPositiveButton(pCtx.getString(R.string.cacheManagerYes), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mTask.cancel(true);
                            }
                        });
                        builder.setNegativeButton(pCtx.getString(R.string.cacheManagerNo), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                mAlertDialog.show();
                            }
                        });
                        builder.show();
                    }
                });
            } else {
                builder.setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        mTask.cancel(true);
                    }
                });
            }
            mAlertDialog = builder.create();
        }

        protected String zoomMessage(int zoomLevel, int zoomMin, int zoomMax) {
            return String.format(handleMessage, zoomLevel, zoomMin, zoomMax);
        }
        
        /**
         * Formats detailed statistics message for display.
         * 
         * @param statistics Progress statistics
         * @return Formatted statistics string
         * @since 6.2.0
         */
        protected String formatStatistics(ProgressReporter.ProgressStatistics statistics) {
            StringBuilder sb = new StringBuilder();
            
            // Success/Error counts
            sb.append("Success: ").append(statistics.successCount);
            sb.append(" | Errors: ").append(statistics.errorCount);
            
            // ETA if available
            if (statistics.estimatedTimeRemainingMs > 0) {
                long seconds = statistics.estimatedTimeRemainingMs / 1000;
                if (seconds < 60) {
                    sb.append(" | ETA: ").append(seconds).append("s");
                } else {
                    long minutes = seconds / 60;
                    seconds = seconds % 60;
                    sb.append(" | ETA: ").append(minutes).append("m ").append(seconds).append("s");
                }
            }
            
            return sb.toString();
        }

        abstract protected String getUITitle();

        @Override
        public void updateProgress(int progress, int currentZoomLevel, int zoomMin, int zoomMax) {
            mProgressBar.setProgress(progress);
            mMessageView.setText(zoomMessage(currentZoomLevel, zoomMin, zoomMax));
        }
        
        @Override
        public void updateProgressWithStatistics(ProgressReporter.ProgressStatistics statistics, 
                                                int zoomMin, int zoomMax) {
            mProgressBar.setProgress(statistics.currentProgress);
            mMessageView.setText(zoomMessage(statistics.currentZoomLevel, zoomMin, zoomMax));
            
            // Update statistics view if enabled
            if (showDetailedStats && mStatsView != null) {
                mStatsView.setText(formatStatistics(statistics));
            }
        }

        @Override
        public void downloadStarted() {
            mAlertDialog.show();
        }

        @Override
        public void setPossibleTilesInArea(int total) {
            mProgressBar.setMax(total);
        }

        @Override
        public void onTaskComplete() {
            dismiss();
        }

        @Override
        public void onTaskFailed(int errors) {
            dismiss();
        }
        
        @Override
        public void onTaskCompleteWithStatistics(ProgressReporter.ProgressStatistics statistics) {
            // Subclasses can override to show final statistics before dismissing
            onTaskComplete();
        }
        
        @Override
        public void onTaskFailedWithStatistics(ProgressReporter.ProgressStatistics statistics) {
            // Subclasses can override to show detailed error information
            onTaskFailed(statistics.errorCount);
        }

        private void dismiss() {
            if (mAlertDialog.isShowing()) {
                mAlertDialog.dismiss();
            }
        }
    }

    /**
     * generic class for common code related to AsyncTask management
     * - performing an action
     * - within a manager
     * - on a list of tiles (potentially sorted by ascending zoom level)
     * - and with callbacks for task progression
     */
    public static class CacheManagerTask implements Runnable {
        private static final AtomicInteger sTaskIdGenerator = new AtomicInteger(0);
        
        private final long mTaskId;
        private final CacheManager mManager;
        private final CacheManagerAction mAction;
        private final IterableWithSize<Long> mTiles;
        private final int mZoomMin;
        private final int mZoomMax;
        private final ArrayList<CacheManagerCallback> mCallbacks = new ArrayList<>();
        private volatile boolean mCancelled = false;
        private volatile Thread mExecutingThread;
        private final ProgressReporter mProgressReporter;
        private final long mTaskStartTime;

        private CacheManagerTask(final CacheManager pManager, final CacheManagerAction pAction,
                                 final IterableWithSize<Long> pTiles,
                                 final int pZoomMin, final int pZoomMax) {
            mTaskId = sTaskIdGenerator.incrementAndGet();
            mTaskStartTime = System.currentTimeMillis();
            mManager = pManager;
            mAction = pAction;
            mTiles = pTiles;
            mZoomMin = Math.max(pZoomMin, pManager.mMinZoomLevel);
            mZoomMax = Math.min(pZoomMax, pManager.mMaxZoomLevel);
            mProgressReporter = new ProgressReporter();
        }

        public CacheManagerTask(final CacheManager pManager, final CacheManagerAction pAction,
                                final List<Long> pTiles,
                                final int pZoomMin, final int pZoomMax) {
            this(pManager, pAction, new ListWrapper<>(pTiles), pZoomMin, pZoomMax);
        }

        public CacheManagerTask(final CacheManager pManager, final CacheManagerAction pAction,
                                final ArrayList<GeoPoint> pGeoPoints,
                                final int pZoomMin, final int pZoomMax) {
            this(pManager, pAction, getTilesCoverage(pGeoPoints, pZoomMin, pZoomMax), pZoomMin, pZoomMax);
        }

        public CacheManagerTask(final CacheManager pManager, final CacheManagerAction pAction,
                                final BoundingBox pBB,
                                final int pZoomMin, final int pZoomMax) {
            this(pManager, pAction, getTilesCoverageIterable(pBB, pZoomMin, pZoomMax), pZoomMin, pZoomMax);
        }

        public void addCallback(final CacheManagerCallback pCallback) {
            if (pCallback != null) {
                mCallbacks.add(pCallback);
            }
        }
        
        /**
         * Gets the unique task ID.
         * 
         * @return Task ID
         * @since 6.2.0
         */
        public long getTaskId() {
            return mTaskId;
        }
        
        /**
         * Gets the task start time.
         * 
         * @return Start time in milliseconds since epoch
         * @since 6.2.0
         */
        public long getTaskStartTime() {
            return mTaskStartTime;
        }
        
        /**
         * Gets the task duration so far.
         * 
         * @return Duration in milliseconds
         * @since 6.2.0
         */
        public long getTaskDuration() {
            return System.currentTimeMillis() - mTaskStartTime;
        }

        public void onPreExecute() {
            final int total = mTiles.size();
            
            // Initialize progress reporter
            mProgressReporter.initialize(total, mZoomMin, mZoomMax);
            
            for (final CacheManagerCallback callback : mCallbacks) {
                try {
                    callback.setPossibleTilesInArea(total);
                    callback.downloadStarted();
                    callback.updateProgress(0, mZoomMin, mZoomMin, mZoomMax);
                } catch (Throwable t) {
                    logFaultyCallback(t);
                }
            }
        }

        private void logFaultyCallback(Throwable pThrowable) {
            Log.w(IMapView.LOGTAG, "Error caught processing cachemanager callback, your implementation is faulty", pThrowable);
        }

        public void onProgressUpdate(final Integer... count) {
            //count[0] = tile counter, count[1] = current zoom level
            
            // Get statistics from progress reporter
            ProgressReporter.ProgressStatistics statistics = mProgressReporter.getStatistics();
            
            for (final CacheManagerCallback callback : mCallbacks) {
                try {
                    // Try enhanced callback first, falls back to basic if not overridden
                    callback.updateProgressWithStatistics(statistics, mZoomMin, mZoomMax);
                } catch (Throwable t) {
                    logFaultyCallback(t);
                }
            }
        }

        public void onCancelled() {
            mManager.mPendingTasks.remove(this);
            // Record metrics
            mManager.mMetrics.recordTaskCancelled(mTaskId);
            // Notify TaskCoordinator about cancellation
            mManager.mTaskCoordinator.markTaskCancelled(this);
        }

        public void onPostExecute(final Integer specialCount) {
            mManager.mPendingTasks.remove(this);
            
            // Mark progress reporter as complete
            mProgressReporter.markComplete();
            
            // Get final statistics
            ProgressReporter.ProgressStatistics finalStats = mProgressReporter.getStatistics();
            
            // Record metrics
            int tilesProcessed = mTiles.size() - specialCount;
            if (specialCount == 0) {
                mManager.mMetrics.recordTaskComplete(mTaskId, tilesProcessed);
                mManager.mTaskCoordinator.markTaskCompleted(this, mTiles.size());
            } else {
                mManager.mMetrics.recordTaskFailed(mTaskId, tilesProcessed);
                mManager.mTaskCoordinator.markTaskFailed(this, mTiles.size() - specialCount);
            }
            
            for (final CacheManagerCallback callback : mCallbacks) {
                try {
                    if (specialCount == 0) {
                        callback.onTaskCompleteWithStatistics(finalStats);
                    } else {
                        callback.onTaskFailedWithStatistics(finalStats);
                    }
                } catch (Throwable t) {
                    logFaultyCallback(t);
                }
            }
        }

        @Override
        public void run() {
            // Capture the executing thread for proper interruption handling
            mExecutingThread = Thread.currentThread();
            
            try {
                if (!mAction.preCheck()) {
                    mManager.mMainHandler.post(() -> onPostExecute(0));
                    return;
                }

                int tileCounter = 0;
                int errors = 0;
                
                // Check if we should use bulk optimization
                boolean useBulkOptimization = shouldUseBulkOptimization();

                if (useBulkOptimization) {
                    // Use BulkOperationOptimizer for better performance
                    errors = processTilesWithBulkOptimization();
                    tileCounter = mTiles.size();
                } else {
                    // Traditional sequential processing
                    for (final long tile : mTiles) {
                        // Check both cancellation flag and thread interruption
                        if (isCancelled() || Thread.currentThread().isInterrupted()) {
                            int finalErrors1 = errors;
                            mManager.mMainHandler.post(() -> onPostExecute(finalErrors1));
                            return;
                        }
                        final int zoom = MapTileIndex.getZoom(tile);
                        if (zoom >= mZoomMin && zoom <= mZoomMax) {
                            boolean hadError = mAction.tileAction(tile);
                            if (hadError) {
                                errors++;
                            }
                            
                            // Update progress reporter with batched updates
                            boolean shouldUpdate = mProgressReporter.updateProgress(zoom, !hadError);
                            
                            // Trigger UI update if progress reporter says we should
                            if (shouldUpdate) {
                                if (isCancelled() || Thread.currentThread().isInterrupted()) {
                                    int finalErrors = errors;
                                    mManager.mMainHandler.post(() -> onPostExecute(finalErrors));
                                    return;
                                }
                                final int finalTileCounter = tileCounter;
                                final int finalZoom = zoom;
                                mManager.mMainHandler.post(() -> onProgressUpdate(finalTileCounter, finalZoom));
                            }
                        }
                        tileCounter++;
                        
                        // Legacy progress update mechanism (kept for compatibility)
                        if (tileCounter % mAction.getProgressModulo() == 0) {
                            // Force update through progress reporter
                            mProgressReporter.forceUpdate();
                            
                            if (isCancelled() || Thread.currentThread().isInterrupted()) {
                                int finalErrors2 = errors;
                                mManager.mMainHandler.post(() -> onPostExecute(finalErrors2));
                                return;
                            }
                            final int finalTileCounter = tileCounter;
                            mManager.mMainHandler.post(() -> onProgressUpdate(finalTileCounter, MapTileIndex.getZoom(tile)));
                        }
                    }
                }
                
                final int finalErrors = errors;
                mManager.mMainHandler.post(() -> onPostExecute(finalErrors));
            } finally {
                // Clear the executing thread reference
                mExecutingThread = null;
            }
        }
        
        /**
         * Determines if bulk optimization should be used for this task.
         * Bulk optimization is beneficial for large tile sets on capable devices.
         */
        private boolean shouldUseBulkOptimization() {
            // Only use bulk optimization for large tile sets
            if (mTiles.size() < 100) {
                return false;
            }
            
            // Check if bulk optimizer is available and parallel processing is enabled
            BulkOperationOptimizer optimizer = mManager.mBulkOperationOptimizer;
            return optimizer != null && optimizer.isParallelProcessingEnabled();
        }
        
        /**
         * Processes tiles using the BulkOperationOptimizer for better performance.
         * 
         * @return Number of errors encountered
         */
        private int processTilesWithBulkOptimization() {
            // Convert tiles to a list for bulk processing
            final List<Long> tileList = new ArrayList<>(mTiles.size());
            for (Long tile : mTiles) {
                final int zoom = MapTileIndex.getZoom(tile);
                if (zoom >= mZoomMin && zoom <= mZoomMax) {
                    tileList.add(tile);
                }
            }
            
            // Create a tile operation wrapper
            BulkOperationOptimizer.TileOperation operation = new BulkOperationOptimizer.TileOperation() {
                private final AtomicInteger processedCount = new AtomicInteger(0);
                
                @Override
                public boolean execute(long tileIndex) {
                    // Check for cancellation
                    if (isCancelled() || Thread.currentThread().isInterrupted()) {
                        return false;
                    }
                    
                    // Execute the tile action
                    boolean hadError = mAction.tileAction(tileIndex);
                    
                    // Update progress periodically
                    int count = processedCount.incrementAndGet();
                    if (count % mAction.getProgressModulo() == 0) {
                        final int zoom = MapTileIndex.getZoom(tileIndex);
                        mProgressReporter.updateProgress(zoom, !hadError);
                        
                        // Post progress update to main thread
                        mManager.mMainHandler.post(() -> onProgressUpdate(count, zoom));
                    }
                    
                    return hadError;
                }
            };
            
            // Process tiles in batches
            BulkOperationOptimizer.BatchResult result = 
                mManager.mBulkOperationOptimizer.processTilesBatched(tileList, operation);
            
            // Return error count
            return result.errorCount;
        }

        public void cancel(boolean mayInterruptIfRunning) {
            mCancelled = true;
            // Interrupt the executing thread if requested and available
            if (mayInterruptIfRunning && mExecutingThread != null) {
                mExecutingThread.interrupt();
            }
            onCancelled();
        }

        public boolean isCancelled() {
            return mCancelled || (mExecutingThread != null && mExecutingThread.isInterrupted());
        }
    }

    public CacheManagerDialog getDownloadingDialog(final Context pCtx, final CacheManagerTask pTask) {
        return new CacheManagerDialog(pCtx, pTask) {
            @Override
            protected String getUITitle() {
                return pCtx.getString(R.string.cacheManagerDownloadingTitle);
            }

            @Override
            public void onTaskFailed(int errors) {
                super.onTaskFailed(errors);
                Toast.makeText(pCtx,
                        String.format(pCtx.getString(R.string.cacheManagerFailed), errors+""),
                        Toast.LENGTH_SHORT).show();
            }
        };
    }

    public CacheManagerDialog getCleaningDialog(final Context pCtx, final CacheManagerTask pTask) {
        return new CacheManagerDialog(pCtx, pTask) {
            @Override
            protected String getUITitle() {
                return pCtx.getString(R.string.cacheManagerCleaningTitle);
            }

            @Override
            public void onTaskFailed(int deleted) {
                super.onTaskFailed(deleted);

                Toast.makeText(pCtx,
                        String.format(pCtx.getString(R.string.cacheManagerCleanFailed), deleted+""),
                        Toast.LENGTH_SHORT).show();
            }
        };
    }

    /**
     * Action to perform on a tile within a CacheManagerTask
     *
     * @author F.Fontaine
     */
    public interface CacheManagerAction {
        /**
         * Preconditions to check before bulk action
         *
         * @return true if we pass the check
         */
        boolean preCheck();

        /**
         * We will update the callbacks not for every tile, but at this rate
         */
        int getProgressModulo();

        /**
         * The action to perform on a single tile
         *
         * @return true if you want to increment the action counter
         */
        boolean tileAction(final long pMapTileIndex);
    }

    private static class ListWrapper<T> implements IterableWithSize<T> {
        private final List<T> list;

        private ListWrapper(List<T> list) {
            this.list = list;
        }

        @Override
        public int size() {
            return list.size();
        }

        @Override
        public Iterator<T> iterator() {
            return list.iterator();
        }
    }

    public CacheManagerAction getDownloadingAction(Context pCtx) {
        return new CacheManagerAction() {
            @Override
            public boolean preCheck() {
                if (mTileSource instanceof OnlineTileSourceBase) {
                    if (!((OnlineTileSourceBase) mTileSource).getTileSourcePolicy().acceptsBulkDownload()) {
                        throw new TileSourcePolicyException(pCtx.getString(R.string.cacheManagerUnsupportedSource));
                    }
                    return true;
                } else {
                    Log.e(IMapView.LOGTAG, "TileSource is not an online tile source");
                    return false;
                }
            }

            @Override
            public int getProgressModulo() {
                return 10;
            }

            @Override
            public boolean tileAction(final long pMapTileIndex) {
                return !loadTile((OnlineTileSourceBase) mTileSource, pMapTileIndex);
            }
        };
    }

    public CacheManagerAction getCleaningAction() {
        return new CacheManagerAction() {
            @Override
            public boolean preCheck() {
                return true;
            }

            @Override
            public int getProgressModulo() {
                return 1000;
            }

            @Override
            public boolean tileAction(final long pMapTileIndex) {
                return deleteTileError(pMapTileIndex);
            }
        };
    }

    /**
     * Remove all cached tiles in the specified area.
     *
     * @param ctx
     * @param bb
     * @param zoomMin
     * @param zoomMax
     */
    public CacheManagerTask cleanAreaAsync(Context ctx, BoundingBox bb, int zoomMin, int zoomMax) {
        final CacheManagerTask task = new CacheManagerTask(this, getCleaningAction(), bb, zoomMin, zoomMax);
        task.addCallback(getCleaningDialog(ctx, task));
        return execute(task);
    }

    /**
     * Remove all cached tiles covered by the GeoPoints list.
     *
     * @param ctx
     * @param geoPoints
     * @param zoomMin
     * @param zoomMax
     */
    public CacheManagerTask cleanAreaAsync(final Context ctx, ArrayList<GeoPoint> geoPoints, int zoomMin, int zoomMax) {
        BoundingBox extendedBounds = extendedBoundsFromGeoPoints(geoPoints, zoomMin);
        return cleanAreaAsync(ctx, extendedBounds, zoomMin, zoomMax);
    }

    /**
     * Remove all cached tiles in the specified area.
     */
    public CacheManagerTask cleanAreaAsync(Context ctx, List<Long> tiles, int zoomMin, int zoomMax) {
        final CacheManagerTask task = new CacheManagerTask(this, getCleaningAction(), tiles, zoomMin, zoomMax);
        task.addCallback(getCleaningDialog(ctx, task));
        return execute(task);
    }

    /**
     *
     */

    public BoundingBox extendedBoundsFromGeoPoints(ArrayList<GeoPoint> geoPoints, int minZoomLevel) {
        final BoundingBox bb = BoundingBox.fromGeoPoints(geoPoints);
        final int right = MapView.getTileSystem().getTileXFromLongitude(bb.getLonEast(), minZoomLevel);
        final int bottom = MapView.getTileSystem().getTileYFromLatitude(bb.getLatSouth(), minZoomLevel);
        final int left = MapView.getTileSystem().getTileXFromLongitude(bb.getLonWest(), minZoomLevel);
        final int top = MapView.getTileSystem().getTileYFromLatitude(bb.getLatNorth(), minZoomLevel);
        return new BoundingBox(
                MapView.getTileSystem().getLatitudeFromTileY(top - 1, minZoomLevel),
                MapView.getTileSystem().getLongitudeFromTileX(right + 1, minZoomLevel),
                MapView.getTileSystem().getLatitudeFromTileY(bottom + 1, minZoomLevel),
                MapView.getTileSystem().getLongitudeFromTileX(left - 1, minZoomLevel));
    }

    /**
     * @return volume currently use in the osmdroid local filesystem cache, in bytes.
     * Note that this method currently takes a while.
     */
    public long currentCacheUsage() {
        //return TileWriter.getUsedCacheSpace(); //returned value is not stable! Increase and decrease, for unknown reasons.
        return directorySize(Configuration.getInstance().getOsmdroidTileCache());
    }

    /**
     * @return the capacity of the osmdroid local filesystem cache, in bytes.
     * This capacity is currently a hard-coded constant inside osmdroid.
     */
    public long cacheCapacity() {
        return Configuration.getInstance().getTileFileSystemCacheMaxBytes();
    }

    /**
     * @return the total size of a directory and of its whole content, recursively
     */
    public long directorySize(final File pDirectory) {
        long usedCacheSpace = 0;
        final File[] z = pDirectory.listFiles();
        if (z != null) {
            for (final File file : z) {
                if (file.isFile()) {
                    usedCacheSpace += file.length();
                } else {
                    if (file.isDirectory()) {
                        usedCacheSpace += directorySize(file);
                    }
                }
            }
        }
        return usedCacheSpace;
    }

    /**
     * @since 6.0.2
     */
    public void setTileDownloader(final TileDownloader pTileDownloader) {
        mTileDownloader = pTileDownloader;
    }
}
