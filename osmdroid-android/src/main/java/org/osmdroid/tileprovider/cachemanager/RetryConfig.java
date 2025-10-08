package org.osmdroid.tileprovider.cachemanager;

/**
 * Configuration for retry policies with exponential backoff and jitter.
 * <p>
 * Controls how failed operations are retried, including the number of attempts,
 * delay between retries, and backoff strategy. The jitter factor helps prevent
 * "thundering herd" problems when many clients retry simultaneously.
 * </p>
 * 
 * <h3>Usage Examples:</h3>
 * 
 * <h4>Default Configuration (Recommended):</h4>
 * <pre>{@code
 * // 3 retries, 1 second base delay, 2x backoff, 30 second max
 * RetryConfig config = new RetryConfig();
 * // Retry delays: 1s, 2s, 4s (with 10% jitter)
 * }</pre>
 * 
 * <h4>Aggressive Retry Configuration:</h4>
 * <pre>{@code
 * RetryConfig config = new RetryConfig.Builder()
 *     .setMaxRetries(5)                      // More retry attempts
 *     .setBaseDelayMs(500)                   // Faster initial retry
 *     .setBackoffMultiplier(1.5)             // Gentler backoff
 *     .setMaxDelayMs(60000)                  // 1 minute max delay
 *     .setJitterFactor(0.2)                  // More randomization
 *     .build();
 * // Retry delays: 0.5s, 0.75s, 1.125s, 1.69s, 2.53s (with 20% jitter)
 * }</pre>
 * 
 * <h4>Conservative Retry Configuration:</h4>
 * <pre>{@code
 * RetryConfig config = new RetryConfig.Builder()
 *     .setMaxRetries(2)                      // Fewer retries
 *     .setBaseDelayMs(2000)                  // Longer initial delay
 *     .setBackoffMultiplier(3.0)             // Aggressive backoff
 *     .build();
 * // Retry delays: 2s, 6s (with 10% jitter)
 * }</pre>
 * 
 * <h4>No Retry Configuration:</h4>
 * <pre>{@code
 * RetryConfig config = new RetryConfig.Builder()
 *     .setMaxRetries(0)                      // Disable retries
 *     .build();
 * }</pre>
 * 
 * <h4>Using with RetryPolicy:</h4>
 * <pre>{@code
 * RetryConfig config = new RetryConfig.Builder()
 *     .setMaxRetries(3)
 *     .setBaseDelayMs(1000)
 *     .build();
 * 
 * RetryPolicy policy = new RetryPolicy(config);
 * 
 * for (int attempt = 1; attempt <= config.getMaxRetries() + 1; attempt++) {
 *     try {
 *         performOperation();
 *         break; // Success
 *     } catch (Exception e) {
 *         if (policy.shouldRetry(e, attempt)) {
 *             long delay = policy.calculateDelay(attempt);
 *             Thread.sleep(delay);
 *         } else {
 *             throw e; // Give up
 *         }
 *     }
 * }
 * }</pre>
 * 
 * @author osmdroid
 * @since 6.2.0
 * @see RetryPolicy
 */
public class RetryConfig {
    
    final int maxRetries;
    final long baseDelayMs;
    final double backoffMultiplier;
    final long maxDelayMs;
    final double jitterFactor;
    
    /**
     * Creates a RetryConfig with default values.
     */
    public RetryConfig() {
        this(3, 1000L, 2.0, 30000L, 0.1);
    }
    
    /**
     * Creates a RetryConfig with specified values.
     * 
     * @param maxRetries Maximum number of retry attempts
     * @param baseDelayMs Base delay in milliseconds
     * @param backoffMultiplier Multiplier for exponential backoff
     * @param maxDelayMs Maximum delay in milliseconds
     * @param jitterFactor Jitter factor (0.0 to 1.0) to randomize delays
     */
    public RetryConfig(int maxRetries, long baseDelayMs, double backoffMultiplier,
                      long maxDelayMs, double jitterFactor) {
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
        this.backoffMultiplier = backoffMultiplier;
        this.maxDelayMs = maxDelayMs;
        this.jitterFactor = jitterFactor;
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public long getBaseDelayMs() {
        return baseDelayMs;
    }
    
    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }
    
    public long getMaxDelayMs() {
        return maxDelayMs;
    }
    
    public double getJitterFactor() {
        return jitterFactor;
    }
    
    /**
     * Builder for RetryConfig.
     */
    public static class Builder {
        private int maxRetries = 3;
        private long baseDelayMs = 1000L;
        private double backoffMultiplier = 2.0;
        private long maxDelayMs = 30000L;
        private double jitterFactor = 0.1;
        
        public Builder setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        public Builder setBaseDelayMs(long baseDelayMs) {
            this.baseDelayMs = baseDelayMs;
            return this;
        }
        
        public Builder setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }
        
        public Builder setMaxDelayMs(long maxDelayMs) {
            this.maxDelayMs = maxDelayMs;
            return this;
        }
        
        public Builder setJitterFactor(double jitterFactor) {
            this.jitterFactor = jitterFactor;
            return this;
        }
        
        public RetryConfig build() {
            return new RetryConfig(
                maxRetries,
                baseDelayMs,
                backoffMultiplier,
                maxDelayMs,
                jitterFactor
            );
        }
    }
}
