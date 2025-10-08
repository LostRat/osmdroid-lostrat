package org.osmdroid.tileprovider.cachemanager;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for comparing performance between different CacheManager configurations.
 * Useful for validating optimization benefits and making informed configuration decisions.
 * 
 * @since 6.2.0
 */
public class PerformanceComparator {
    private static final String TAG = "PerformanceComparator";
    
    private final List<PerformanceMetric> metrics;
    private long startTime;
    private String currentOperation;
    
    /**
     * Creates a new PerformanceComparator.
     */
    public PerformanceComparator() {
        this.metrics = new ArrayList<>();
    }
    
    /**
     * Starts timing an operation.
     * 
     * @param operationName The name of the operation being timed
     */
    public void startOperation(String operationName) {
        this.currentOperation = operationName;
        this.startTime = System.nanoTime();
        Log.d(TAG, "Started timing: " + operationName);
    }
    
    /**
     * Ends timing the current operation and records the metric.
     * 
     * @param itemsProcessed The number of items processed during the operation
     */
    public void endOperation(int itemsProcessed) {
        if (currentOperation == null) {
            Log.w(TAG, "endOperation called without startOperation");
            return;
        }
        
        long endTime = System.nanoTime();
        long durationNanos = endTime - startTime;
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(durationNanos);
        
        PerformanceMetric metric = new PerformanceMetric(
            currentOperation,
            durationMillis,
            itemsProcessed
        );
        
        metrics.add(metric);
        Log.d(TAG, "Completed: " + metric);
        
        currentOperation = null;
        startTime = 0;
    }
    
    /**
     * Records a cache hit for performance analysis.
     * 
     * @param cacheType The type of cache (e.g., "GroundResolution", "TileCoordinate")
     */
    public void recordCacheHit(String cacheType) {
        Log.v(TAG, "Cache hit: " + cacheType);
    }
    
    /**
     * Records a cache miss for performance analysis.
     * 
     * @param cacheType The type of cache
     */
    public void recordCacheMiss(String cacheType) {
        Log.v(TAG, "Cache miss: " + cacheType);
    }
    
    /**
     * Gets all recorded metrics.
     * 
     * @return List of performance metrics
     */
    public List<PerformanceMetric> getMetrics() {
        return new ArrayList<>(metrics);
    }
    
    /**
     * Gets metrics for a specific operation.
     * 
     * @param operationName The operation name
     * @return List of metrics for that operation
     */
    public List<PerformanceMetric> getMetricsForOperation(String operationName) {
        List<PerformanceMetric> result = new ArrayList<>();
        for (PerformanceMetric metric : metrics) {
            if (metric.operationName.equals(operationName)) {
                result.add(metric);
            }
        }
        return result;
    }
    
    /**
     * Calculates average duration for a specific operation.
     * 
     * @param operationName The operation name
     * @return Average duration in milliseconds, or 0 if no metrics found
     */
    public long getAverageDuration(String operationName) {
        List<PerformanceMetric> operationMetrics = getMetricsForOperation(operationName);
        if (operationMetrics.isEmpty()) {
            return 0;
        }
        
        long totalDuration = 0;
        for (PerformanceMetric metric : operationMetrics) {
            totalDuration += metric.durationMillis;
        }
        
        return totalDuration / operationMetrics.size();
    }
    
    /**
     * Calculates throughput (items per second) for a specific operation.
     * 
     * @param operationName The operation name
     * @return Throughput in items per second, or 0 if no metrics found
     */
    public double getThroughput(String operationName) {
        List<PerformanceMetric> operationMetrics = getMetricsForOperation(operationName);
        if (operationMetrics.isEmpty()) {
            return 0;
        }
        
        long totalItems = 0;
        long totalDuration = 0;
        
        for (PerformanceMetric metric : operationMetrics) {
            totalItems += metric.itemsProcessed;
            totalDuration += metric.durationMillis;
        }
        
        if (totalDuration == 0) {
            return 0;
        }
        
        // Convert to items per second
        return (totalItems * 1000.0) / totalDuration;
    }
    
    /**
     * Compares two sets of metrics and generates a comparison report.
     * 
     * @param baseline The baseline metrics (e.g., from unoptimized code)
     * @param optimized The optimized metrics
     * @return A ComparisonReport
     */
    public static ComparisonReport compare(List<PerformanceMetric> baseline, 
                                          List<PerformanceMetric> optimized) {
        if (baseline.isEmpty() || optimized.isEmpty()) {
            Log.w(TAG, "Cannot compare empty metric lists");
            return new ComparisonReport(0, 0, 0, 0);
        }
        
        long baselineTotalDuration = 0;
        long baselineTotalItems = 0;
        for (PerformanceMetric metric : baseline) {
            baselineTotalDuration += metric.durationMillis;
            baselineTotalItems += metric.itemsProcessed;
        }
        
        long optimizedTotalDuration = 0;
        long optimizedTotalItems = 0;
        for (PerformanceMetric metric : optimized) {
            optimizedTotalDuration += metric.durationMillis;
            optimizedTotalItems += metric.itemsProcessed;
        }
        
        double baselineThroughput = baselineTotalDuration > 0 ? 
            (baselineTotalItems * 1000.0) / baselineTotalDuration : 0;
        double optimizedThroughput = optimizedTotalDuration > 0 ? 
            (optimizedTotalItems * 1000.0) / optimizedTotalDuration : 0;
        
        double speedupFactor = baselineTotalDuration > 0 ? 
            (double) baselineTotalDuration / optimizedTotalDuration : 0;
        double throughputImprovement = baselineThroughput > 0 ? 
            ((optimizedThroughput - baselineThroughput) / baselineThroughput) * 100 : 0;
        
        return new ComparisonReport(
            speedupFactor,
            throughputImprovement,
            baselineThroughput,
            optimizedThroughput
        );
    }
    
    /**
     * Generates a summary report of all metrics.
     * 
     * @return A formatted summary string
     */
    public String generateReport() {
        if (metrics.isEmpty()) {
            return "No performance metrics recorded";
        }
        
        StringBuilder report = new StringBuilder();
        report.append("Performance Report\n");
        report.append("==================\n\n");
        
        long totalDuration = 0;
        int totalItems = 0;
        
        for (PerformanceMetric metric : metrics) {
            report.append(metric.toString()).append("\n");
            totalDuration += metric.durationMillis;
            totalItems += metric.itemsProcessed;
        }
        
        report.append("\nSummary:\n");
        report.append("  Total Duration: ").append(totalDuration).append(" ms\n");
        report.append("  Total Items: ").append(totalItems).append("\n");
        
        if (totalDuration > 0) {
            double throughput = (totalItems * 1000.0) / totalDuration;
            report.append("  Overall Throughput: ").append(String.format("%.2f", throughput))
                  .append(" items/sec\n");
        }
        
        return report.toString();
    }
    
    /**
     * Clears all recorded metrics.
     */
    public void reset() {
        metrics.clear();
        currentOperation = null;
        startTime = 0;
        Log.d(TAG, "Performance metrics reset");
    }
    
    /**
     * A single performance metric.
     */
    public static class PerformanceMetric {
        public final String operationName;
        public final long durationMillis;
        public final int itemsProcessed;
        public final long timestamp;
        
        PerformanceMetric(String operationName, long durationMillis, int itemsProcessed) {
            this.operationName = operationName;
            this.durationMillis = durationMillis;
            this.itemsProcessed = itemsProcessed;
            this.timestamp = System.currentTimeMillis();
        }
        
        public double getThroughput() {
            if (durationMillis == 0) {
                return 0;
            }
            return (itemsProcessed * 1000.0) / durationMillis;
        }
        
        @Override
        public String toString() {
            return String.format("%s: %d ms, %d items (%.2f items/sec)",
                operationName, durationMillis, itemsProcessed, getThroughput());
        }
    }
    
    /**
     * A comparison report between two sets of metrics.
     */
    public static class ComparisonReport {
        public final double speedupFactor;
        public final double throughputImprovementPercent;
        public final double baselineThroughput;
        public final double optimizedThroughput;
        
        ComparisonReport(double speedupFactor, double throughputImprovementPercent,
                        double baselineThroughput, double optimizedThroughput) {
            this.speedupFactor = speedupFactor;
            this.throughputImprovementPercent = throughputImprovementPercent;
            this.baselineThroughput = baselineThroughput;
            this.optimizedThroughput = optimizedThroughput;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Performance Comparison:\n" +
                "  Speedup Factor: %.2fx\n" +
                "  Throughput Improvement: %.1f%%\n" +
                "  Baseline Throughput: %.2f items/sec\n" +
                "  Optimized Throughput: %.2f items/sec",
                speedupFactor,
                throughputImprovementPercent,
                baselineThroughput,
                optimizedThroughput
            );
        }
        
        public boolean isImprovement() {
            return speedupFactor > 1.0 || throughputImprovementPercent > 0;
        }
    }
}
