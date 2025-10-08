package org.osmdroid.tileprovider.cachemanager;

import android.os.Build;
import android.util.Log;

import org.osmdroid.api.IMapView;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Diagnostic utilities for troubleshooting CacheManager issues.
 * Provides detailed information about configuration, state, and performance.
 * 
 * @author osmdroid
 * @since 6.2.0
 */
public class CacheManagerDiagnostics {
    
    private static final String TAG = "CacheManagerDiag";
    
    private final CacheManager cacheManager;
    private final CacheManagerMetrics metrics;
    
    /**
     * Creates a new diagnostics instance.
     * 
     * @param cacheManager CacheManager to diagnose
     * @param metrics Metrics collector
     */
    public CacheManagerDiagnostics(CacheManager cacheManager, CacheManagerMetrics metrics) {
        this.cacheManager = cacheManager;
        this.metrics = metrics;
    }
    
    /**
     * Generates a comprehensive diagnostic report.
     * 
     * @return Diagnostic report as a string
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== CacheManager Diagnostic Report ===\n\n");
        
        // System information
        report.append("System Information:\n");
        report.append(getSystemInfo());
        report.append("\n");
        
        // CacheManager configuration
        report.append("CacheManager Configuration:\n");
        report.append(getConfigurationInfo());
        report.append("\n");
        
        // Current state
        report.append("Current State:\n");
        report.append(getStateInfo());
        report.append("\n");
        
        // Performance metrics
        report.append("Performance Metrics:\n");
        report.append(getMetricsInfo());
        report.append("\n");
        
        // Thread pool status
        report.append("Thread Pool Status:\n");
        report.append(getThreadPoolInfo());
        report.append("\n");
        
        // Cache status
        report.append("Cache Status:\n");
        report.append(getCacheInfo());
        report.append("\n");
        
        // Error analysis
        report.append("Error Analysis:\n");
        report.append(getErrorAnalysis());
        report.append("\n");
        
        // Recommendations
        report.append("Recommendations:\n");
        report.append(getRecommendations());
        report.append("\n");
        
        report.append("=== End of Report ===");
        return report.toString();
    }
    
    /**
     * Gets system information.
     */
    private String getSystemInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("  Android API Level: %d\n", Build.VERSION.SDK_INT));
        sb.append(String.format("  Device: %s %s\n", Build.MANUFACTURER, Build.MODEL));
        sb.append(String.format("  Available Processors: %d\n", Runtime.getRuntime().availableProcessors()));
        
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        
        sb.append(String.format("  Memory: %d MB used / %d MB total / %d MB max\n", 
            usedMemory, totalMemory, maxMemory));
        sb.append(String.format("  Memory Usage: %.1f%%\n", (usedMemory * 100.0) / maxMemory));
        
        return sb.toString();
    }
    
    /**
     * Gets CacheManager configuration information.
     */
    private String getConfigurationInfo() {
        StringBuilder sb = new StringBuilder();
        
        try {
            // Thread pool configuration
            ThreadPoolManager threadPoolManager = cacheManager.getThreadPoolManager();
            if (threadPoolManager != null) {
                sb.append("  Thread Pool: Configured\n");
                sb.append(String.format("    Work-stealing enabled: %s\n", 
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? "Yes" : "No"));
            } else {
                sb.append("  Thread Pool: Not configured\n");
            }
            
            // Calculation cache configuration
            CalculationCache calcCache = cacheManager.getCalculationCache();
            if (calcCache != null) {
                sb.append("  Calculation Cache: Configured\n");
            } else {
                sb.append("  Calculation Cache: Not configured\n");
            }
            
            // Error recovery configuration
            ErrorRecoveryHandler errorHandler = cacheManager.getErrorRecoveryHandler();
            if (errorHandler != null) {
                sb.append("  Error Recovery: Configured\n");
                RetryPolicy retryPolicy = errorHandler.getRetryPolicy();
                if (retryPolicy != null) {
                    sb.append(String.format("    Max retries: %d\n", retryPolicy.getMaxRetries()));
                    sb.append(String.format("    Base delay: %d ms\n", retryPolicy.getBaseDelayMs()));
                }
            } else {
                sb.append("  Error Recovery: Not configured\n");
            }
            
            // Bulk operation optimizer
            BulkOperationOptimizer bulkOptimizer = cacheManager.getBulkOperationOptimizer();
            if (bulkOptimizer != null) {
                sb.append("  Bulk Optimizer: Configured\n");
                sb.append(String.format("    Parallel processing: %s\n", 
                    bulkOptimizer.isParallelProcessingEnabled() ? "Enabled" : "Disabled"));
            } else {
                sb.append("  Bulk Optimizer: Not configured\n");
            }
            
        } catch (Exception e) {
            sb.append("  Error retrieving configuration: ").append(e.getMessage()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Gets current state information.
     */
    private String getStateInfo() {
        StringBuilder sb = new StringBuilder();
        
        try {
            sb.append(String.format("  Pending Jobs: %d\n", cacheManager.getPendingJobs()));
            
            TaskCoordinator coordinator = cacheManager.getTaskCoordinator();
            if (coordinator != null) {
                TaskCoordinator.TaskStatistics taskStats = coordinator.getStatistics();
                sb.append(String.format("  Active Tasks: %d\n", taskStats.getActiveTaskCount()));
                sb.append(String.format("  Total Tasks Created: %d\n", taskStats.getTotalTasksCreated()));
            }
            
        } catch (Exception e) {
            sb.append("  Error retrieving state: ").append(e.getMessage()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Gets performance metrics information.
     */
    private String getMetricsInfo() {
        StringBuilder sb = new StringBuilder();
        
        try {
            CacheManagerMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
            sb.append(String.format("  Total Tiles Processed: %d\n", snapshot.totalTilesProcessed));
            sb.append(String.format("  Total Tiles Downloaded: %d\n", snapshot.totalTilesDownloaded));
            sb.append(String.format("  Total Tiles Deleted: %d\n", snapshot.totalTilesDeleted));
            sb.append(String.format("  Average Processing Time: %.2f ms/tile\n", 
                snapshot.getAverageProcessingTimePerTile()));
            sb.append(String.format("  Average Download Time: %.2f ms/tile\n", 
                snapshot.getAverageDownloadTimePerTile()));
            sb.append(String.format("  Error Rate: %.2f%%\n", snapshot.getErrorRate() * 100));
            sb.append(String.format("  Task Success Rate: %.2f%%\n", snapshot.getTaskSuccessRate() * 100));
            
        } catch (Exception e) {
            sb.append("  Error retrieving metrics: ").append(e.getMessage()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Gets thread pool status information.
     */
    private String getThreadPoolInfo() {
        StringBuilder sb = new StringBuilder();
        
        try {
            ThreadPoolManager threadPoolManager = cacheManager.getThreadPoolManager();
            if (threadPoolManager != null) {
                ExecutorService primaryExecutor = threadPoolManager.getPrimaryExecutor();
                if (primaryExecutor instanceof ThreadPoolExecutor) {
                    ThreadPoolExecutor tpe = (ThreadPoolExecutor) primaryExecutor;
                    sb.append(String.format("  Primary Executor:\n"));
                    sb.append(String.format("    Active Threads: %d\n", tpe.getActiveCount()));
                    sb.append(String.format("    Pool Size: %d\n", tpe.getPoolSize()));
                    sb.append(String.format("    Core Pool Size: %d\n", tpe.getCorePoolSize()));
                    sb.append(String.format("    Max Pool Size: %d\n", tpe.getMaximumPoolSize()));
                    sb.append(String.format("    Queue Size: %d\n", tpe.getQueue().size()));
                    sb.append(String.format("    Completed Tasks: %d\n", tpe.getCompletedTaskCount()));
                } else {
                    sb.append("  Primary Executor: ForkJoinPool or custom implementation\n");
                }
                
                CacheManagerMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
                sb.append(String.format("  Peak Thread Count: %d\n", snapshot.peakThreadCount));
                sb.append(String.format("  Total Thread Execution Time: %d ms\n", 
                    snapshot.totalThreadExecutionTimeMs));
            } else {
                sb.append("  Thread Pool Manager: Not available\n");
            }
            
        } catch (Exception e) {
            sb.append("  Error retrieving thread pool info: ").append(e.getMessage()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Gets cache status information.
     */
    private String getCacheInfo() {
        StringBuilder sb = new StringBuilder();
        
        try {
            CacheManagerMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
            sb.append(String.format("  Cache Hits: %d\n", snapshot.cacheHits));
            sb.append(String.format("  Cache Misses: %d\n", snapshot.cacheMisses));
            sb.append(String.format("  Cache Hit Ratio: %.2f%%\n", snapshot.getCacheHitRatio() * 100));
            sb.append(String.format("  Cache Evictions: %d\n", snapshot.cacheEvictions));
            
            CalculationCache calcCache = cacheManager.getCalculationCache();
            if (calcCache != null) {
                CalculationCache.CacheStatistics cacheStats = calcCache.getStatistics();
                sb.append(String.format("  Ground Resolution Cache Hit Ratio: %.2f%%\n", 
                    cacheStats.getGroundResolutionHitRatio() * 100));
                sb.append(String.format("  Tile Coordinate Cache Hit Ratio: %.2f%%\n", 
                    cacheStats.getTileCoordinateHitRatio() * 100));
                sb.append(String.format("  Bounding Box Cache Hit Ratio: %.2f%%\n", 
                    cacheStats.getBoundingBoxHitRatio() * 100));
            }
            
        } catch (Exception e) {
            sb.append("  Error retrieving cache info: ").append(e.getMessage()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Gets error analysis information.
     */
    private String getErrorAnalysis() {
        StringBuilder sb = new StringBuilder();
        
        try {
            CacheManagerMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
            sb.append(String.format("  Total Errors: %d\n", snapshot.totalErrors));
            sb.append(String.format("  Network Errors: %d (%.1f%%)\n", 
                snapshot.networkErrors, 
                snapshot.totalErrors > 0 ? (snapshot.networkErrors * 100.0 / snapshot.totalErrors) : 0));
            sb.append(String.format("  I/O Errors: %d (%.1f%%)\n", 
                snapshot.ioErrors,
                snapshot.totalErrors > 0 ? (snapshot.ioErrors * 100.0 / snapshot.totalErrors) : 0));
            sb.append(String.format("  Retry Successes: %d\n", snapshot.retrySuccesses));
            sb.append(String.format("  Failed Tasks: %d\n", snapshot.failedTasks));
            sb.append(String.format("  Cancelled Tasks: %d\n", snapshot.cancelledTasks));
            
            ErrorRecoveryHandler errorHandler = cacheManager.getErrorRecoveryHandler();
            if (errorHandler != null) {
                ErrorRecoveryHandler.ErrorStatistics errorStats = errorHandler.getStatistics();
                sb.append(String.format("  Retried Errors: %d\n", errorStats.getRetriedErrors()));
                sb.append(String.format("  Recovered Errors: %d\n", errorStats.getRecoveredErrors()));
                sb.append(String.format("  Total Errors: %d\n", errorStats.getTotalErrors()));
            }
            
        } catch (Exception e) {
            sb.append("  Error retrieving error analysis: ").append(e.getMessage()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Generates recommendations based on current state and metrics.
     */
    private String getRecommendations() {
        StringBuilder sb = new StringBuilder();
        List<String> recommendations = new ArrayList<>();
        
        try {
            CacheManagerMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
            
            // Check error rate
            if (snapshot.getErrorRate() > 0.1) {
                recommendations.add("High error rate detected (>10%). Check network connectivity and tile source availability.");
            }
            
            // Check cache hit ratio
            if (snapshot.getCacheHitRatio() < 0.5 && snapshot.cacheHits + snapshot.cacheMisses > 100) {
                recommendations.add("Low cache hit ratio (<50%). Consider increasing cache sizes in CacheConfig.");
            }
            
            // Check memory usage
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = (runtime.totalMemory() - runtime.freeMemory());
            long maxMemory = runtime.maxMemory();
            double memoryUsage = (usedMemory * 100.0) / maxMemory;
            
            if (memoryUsage > 80) {
                recommendations.add("High memory usage (>80%). Consider reducing cache sizes or batch sizes.");
            }
            
            // Check thread pool utilization
            ThreadPoolManager threadPoolManager = cacheManager.getThreadPoolManager();
            if (threadPoolManager != null) {
                ExecutorService primaryExecutor = threadPoolManager.getPrimaryExecutor();
                if (primaryExecutor instanceof ThreadPoolExecutor) {
                    ThreadPoolExecutor tpe = (ThreadPoolExecutor) primaryExecutor;
                    if (tpe.getQueue().size() > 100) {
                        recommendations.add("Large task queue detected. Consider increasing thread pool size.");
                    }
                }
            }
            
            // Check for API level optimizations
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                BulkOperationOptimizer bulkOptimizer = cacheManager.getBulkOperationOptimizer();
                if (bulkOptimizer == null || !bulkOptimizer.isParallelProcessingEnabled()) {
                    recommendations.add("Device supports parallel processing (API 24+) but it's not enabled. Enable for better performance.");
                }
            }
            
            // Check retry configuration
            ErrorRecoveryHandler errorHandler = cacheManager.getErrorRecoveryHandler();
            if (errorHandler != null && snapshot.networkErrors > snapshot.retrySuccesses * 2) {
                recommendations.add("Many network errors without successful retries. Consider adjusting retry policy.");
            }
            
            // Check task success rate
            if (snapshot.getTaskSuccessRate() < 0.8 && snapshot.completedTasks + snapshot.failedTasks > 10) {
                recommendations.add("Low task success rate (<80%). Review error logs and tile source configuration.");
            }
            
            if (recommendations.isEmpty()) {
                sb.append("  No issues detected. CacheManager is operating normally.\n");
            } else {
                for (int i = 0; i < recommendations.size(); i++) {
                    sb.append(String.format("  %d. %s\n", i + 1, recommendations.get(i)));
                }
            }
            
        } catch (Exception e) {
            sb.append("  Error generating recommendations: ").append(e.getMessage()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Logs the diagnostic report.
     */
    public void logReport() {
        String report = generateReport();
        // Split by lines and log each line separately to avoid truncation
        String[] lines = report.split("\n");
        for (String line : lines) {
            Log.i(TAG, line);
        }
    }
    
    /**
     * Checks for common issues and returns a list of problems found.
     * 
     * @return List of issue descriptions
     */
    public List<String> checkForIssues() {
        List<String> issues = new ArrayList<>();
        
        try {
            CacheManagerMetrics.MetricsSnapshot snapshot = metrics.getSnapshot();
            
            // Check for high error rate
            if (snapshot.getErrorRate() > 0.2) {
                issues.add("Critical: Very high error rate (>20%)");
            }
            
            // Check for memory pressure
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = (runtime.totalMemory() - runtime.freeMemory());
            long maxMemory = runtime.maxMemory();
            double memoryUsage = (usedMemory * 100.0) / maxMemory;
            
            if (memoryUsage > 90) {
                issues.add("Critical: Very high memory usage (>90%)");
            }
            
            // Check for stalled tasks
            if (snapshot.activeTasks > 0 && snapshot.totalTilesProcessed == 0) {
                issues.add("Warning: Active tasks but no tiles processed - possible stall");
            }
            
            // Check for excessive failures
            if (snapshot.failedTasks > snapshot.completedTasks && snapshot.failedTasks > 5) {
                issues.add("Warning: More failed tasks than completed tasks");
            }
            
        } catch (Exception e) {
            issues.add("Error checking for issues: " + e.getMessage());
        }
        
        return issues;
    }
    
    /**
     * Performs a health check and returns true if everything is OK.
     * 
     * @return true if healthy, false if issues detected
     */
    public boolean isHealthy() {
        return checkForIssues().isEmpty();
    }
}
