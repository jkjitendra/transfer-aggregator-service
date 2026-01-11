package com.arcube.transferaggregator.resilience;

import com.arcube.transferaggregator.config.AggregatorProperties;
import com.arcube.transferaggregator.exception.RateLimitExceededException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Token bucket rate limiter for API calls.
 * 
 * Provides:
 * - Global search rate limit (requests per minute)
 * - Per-searchId poll rate limit (polls per minute per search)
 */
@Slf4j
@Component
public class RateLimiter {
    
    private final int searchRateLimit;
    private final int pollRateLimit;
    
    // Sliding window counters
    private final Cache<String, AtomicInteger> searchCounters;
    private final Cache<String, AtomicInteger> pollCounters;
    
    public RateLimiter(AggregatorProperties properties) {
        this.searchRateLimit = properties.getResilience().getSearchRateLimitPerMinute();
        this.pollRateLimit = properties.getResilience().getPollRateLimitPerMinute();
        
        // Counters expire after 1 minute (sliding window approximation)
        this.searchCounters = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();
        
        this.pollCounters = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();
        
        log.info("RateLimiter initialized: search={}/min, poll={}/min per searchId", 
            searchRateLimit, pollRateLimit);
    }
    
    /**
     * Check and consume a search request permit.
     * 
     * @param supplierCode Supplier code for tracking
     * @throws RateLimitExceededException if limit exceeded
     */
    public void acquireSearchPermit(String supplierCode) {
        String key = "search:" + supplierCode;
        AtomicInteger counter = Objects.requireNonNull(
            searchCounters.get(key, k -> new AtomicInteger(0)));
        
        int current = counter.incrementAndGet();
        if (current > searchRateLimit) {
            counter.decrementAndGet();
            log.warn("Search rate limit exceeded for {}: {}/{}", supplierCode, current - 1, searchRateLimit);
            throw new RateLimitExceededException(
                String.format("Search rate limit exceeded (%d/min). Retry in 60s.", searchRateLimit));
        }
        
        log.debug("Search permit acquired for {}: {}/{}", supplierCode, current, searchRateLimit);
    }
    
    /**
     * Check and consume a poll request permit for a specific search.
     * 
     * @param searchId The search ID being polled
     * @throws RateLimitExceededException if limit exceeded
     */
    public void acquirePollPermit(String searchId) {
        String key = "poll:" + searchId;
        AtomicInteger counter = Objects.requireNonNull(
            pollCounters.get(key, k -> new AtomicInteger(0)));
        
        int current = counter.incrementAndGet();
        if (current > pollRateLimit) {
            counter.decrementAndGet();
            log.warn("Poll rate limit exceeded for search {}: {}/{}", searchId, current - 1, pollRateLimit);
            throw new RateLimitExceededException(
                String.format("Poll rate limit exceeded for search (%d/min). Wait for results.", pollRateLimit));
        }
        
        log.debug("Poll permit acquired for search {}: {}/{}", searchId, current, pollRateLimit);
    }
}
