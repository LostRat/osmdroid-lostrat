package org.osmdroid.tileprovider.cachemanager;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Random;

/**
 * Configurable retry policy for handling failed tile operations with exponential backoff and jitter.
 * Implements intelligent retry strategies based on error types and attempt counts.
 * 
 * @author CacheManager Optimization Team
 * @since 6.2.0
 */
public class RetryPolicy {
    
    /**
     * Error categories for different retry strategies
     */
    public enum ErrorCategory {
        /** Network-related errors (timeouts, connection failures) - retryable */
        NETWORK,
        /** I/O errors (disk full, permission denied) - may be retryable */
        IO,
        /** Server errors (5xx responses) - retryable with backoff */
        SERVER,
        /** Client errors (4xx responses) - generally not retryable */
        CLIENT,
        /** Unknown or uncategorized errors - use default retry strategy */
        UNKNOWN
    }
    
    private final int maxRetries;
    private final long baseDelayMs;
    private final double backoffMultiplier;
    private final long maxDelayMs;
    private final double jitterFactor;
    private final Random random;
    
    /**
     * Creates a retry policy with default settings.
     * Default: 3 retries, 1000ms base delay, 2.0x backoff, 30000ms max delay, 0.1 jitter
     */
    public RetryPolicy() {
        this(3, 1000, 2.0, 30000, 0.1);
    }
    
    /**
     * Creates a retry policy from RetryConfig.
     * 
     * @param config Retry configuration
     * @since 6.2.0
     */
    public RetryPolicy(RetryConfig config) {
        this(config.getMaxRetries(),
             config.getBaseDelayMs(),
             config.getBackoffMultiplier(),
             config.getMaxDelayMs(),
             config.getJitterFactor());
    }
    
    /**
     * Creates a retry policy with custom settings.
     * 
     * @param maxRetries Maximum number of retry attempts (0 = no retries)
     * @param baseDelayMs Base delay in milliseconds before first retry
     * @param backoffMultiplier Multiplier for exponential backoff (e.g., 2.0 doubles delay each time)
     * @param maxDelayMs Maximum delay in milliseconds between retries
     * @param jitterFactor Random jitter factor (0.0-1.0) to prevent thundering herd
     */
    public RetryPolicy(int maxRetries, long baseDelayMs, double backoffMultiplier, 
                      long maxDelayMs, double jitterFactor) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be non-negative");
        }
        if (baseDelayMs < 0) {
            throw new IllegalArgumentException("baseDelayMs must be non-negative");
        }
        if (backoffMultiplier < 1.0) {
            throw new IllegalArgumentException("backoffMultiplier must be >= 1.0");
        }
        if (maxDelayMs < baseDelayMs) {
            throw new IllegalArgumentException("maxDelayMs must be >= baseDelayMs");
        }
        if (jitterFactor < 0.0 || jitterFactor > 1.0) {
            throw new IllegalArgumentException("jitterFactor must be between 0.0 and 1.0");
        }
        
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
        this.backoffMultiplier = backoffMultiplier;
        this.maxDelayMs = maxDelayMs;
        this.jitterFactor = jitterFactor;
        this.random = new Random();
    }
    
    /**
     * Calculates the delay before the next retry attempt using exponential backoff with jitter.
     * 
     * @param attemptNumber The current attempt number (1-based)
     * @return Delay in milliseconds before next retry
     */
    public long calculateDelay(int attemptNumber) {
        if (attemptNumber <= 0) {
            return 0;
        }
        
        // Calculate exponential backoff: baseDelay * (backoffMultiplier ^ (attemptNumber - 1))
        double exponentialDelay = baseDelayMs * Math.pow(backoffMultiplier, attemptNumber - 1);
        
        // Cap at maximum delay
        long cappedDelay = Math.min((long) exponentialDelay, maxDelayMs);
        
        // Add jitter: random value between (1 - jitterFactor) and (1 + jitterFactor)
        double jitter = 1.0 + (random.nextDouble() * 2.0 - 1.0) * jitterFactor;
        long delayWithJitter = (long) (cappedDelay * jitter);
        
        // Ensure non-negative result
        return Math.max(0, delayWithJitter);
    }
    
    /**
     * Determines if an operation should be retried based on the exception and attempt number.
     * 
     * @param exception The exception that occurred
     * @param attemptNumber The current attempt number (1-based)
     * @return true if the operation should be retried, false otherwise
     */
    public boolean shouldRetry(Exception exception, int attemptNumber) {
        // Don't retry if we've exceeded max retries
        if (attemptNumber > maxRetries) {
            return false;
        }
        
        // Categorize the error and determine if it's retryable
        ErrorCategory category = categorizeError(exception);
        return isRetryableCategory(category);
    }
    
    /**
     * Categorizes an exception into an error category.
     * 
     * @param exception The exception to categorize
     * @return The error category
     */
    public ErrorCategory categorizeError(Exception exception) {
        if (exception == null) {
            return ErrorCategory.UNKNOWN;
        }
        
        // Network errors - typically retryable
        if (exception instanceof SocketTimeoutException ||
            exception instanceof UnknownHostException ||
            exception.getClass().getSimpleName().contains("Network") ||
            exception.getClass().getSimpleName().contains("Connection")) {
            return ErrorCategory.NETWORK;
        }
        
        // I/O errors - may be retryable depending on cause
        if (exception instanceof IOException) {
            String message = exception.getMessage();
            if (message != null) {
                // Disk full or permission errors are generally not retryable
                if (message.contains("No space left") || 
                    message.contains("Permission denied") ||
                    message.contains("Read-only")) {
                    return ErrorCategory.IO;
                }
            }
            // Other I/O errors might be transient
            return ErrorCategory.IO;
        }
        
        // Check exception message for HTTP status codes
        String message = exception.getMessage();
        if (message != null) {
            // Server errors (5xx) - retryable
            if (message.contains("500") || message.contains("502") || 
                message.contains("503") || message.contains("504")) {
                return ErrorCategory.SERVER;
            }
            // Client errors (4xx) - generally not retryable
            if (message.contains("400") || message.contains("401") || 
                message.contains("403") || message.contains("404")) {
                return ErrorCategory.CLIENT;
            }
        }
        
        return ErrorCategory.UNKNOWN;
    }
    
    /**
     * Determines if an error category is retryable.
     * 
     * @param category The error category
     * @return true if errors in this category should be retried
     */
    public boolean isRetryableCategory(ErrorCategory category) {
        switch (category) {
            case NETWORK:
            case SERVER:
                return true;
            case IO:
                // I/O errors are conditionally retryable
                return true;
            case CLIENT:
                // Client errors (4xx) are generally not retryable
                return false;
            case UNKNOWN:
            default:
                // Unknown errors: retry with caution
                return true;
        }
    }
    
    /**
     * Gets the maximum number of retry attempts.
     * 
     * @return Maximum retry attempts
     */
    public int getMaxRetries() {
        return maxRetries;
    }
    
    /**
     * Gets the base delay in milliseconds.
     * 
     * @return Base delay in milliseconds
     */
    public long getBaseDelayMs() {
        return baseDelayMs;
    }
    
    /**
     * Gets the backoff multiplier.
     * 
     * @return Backoff multiplier
     */
    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }
    
    /**
     * Gets the maximum delay in milliseconds.
     * 
     * @return Maximum delay in milliseconds
     */
    public long getMaxDelayMs() {
        return maxDelayMs;
    }
    
    /**
     * Gets the jitter factor.
     * 
     * @return Jitter factor (0.0-1.0)
     */
    public double getJitterFactor() {
        return jitterFactor;
    }
    
    /**
     * Creates a builder for constructing RetryPolicy instances.
     * 
     * @return A new RetryPolicy.Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for creating RetryPolicy instances with fluent API.
     */
    public static class Builder {
        private int maxRetries = 3;
        private long baseDelayMs = 1000;
        private double backoffMultiplier = 2.0;
        private long maxDelayMs = 30000;
        private double jitterFactor = 0.1;
        
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        public Builder baseDelayMs(long baseDelayMs) {
            this.baseDelayMs = baseDelayMs;
            return this;
        }
        
        public Builder backoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }
        
        public Builder maxDelayMs(long maxDelayMs) {
            this.maxDelayMs = maxDelayMs;
            return this;
        }
        
        public Builder jitterFactor(double jitterFactor) {
            this.jitterFactor = jitterFactor;
            return this;
        }
        
        public RetryPolicy build() {
            return new RetryPolicy(maxRetries, baseDelayMs, backoffMultiplier, 
                                  maxDelayMs, jitterFactor);
        }
    }
}
