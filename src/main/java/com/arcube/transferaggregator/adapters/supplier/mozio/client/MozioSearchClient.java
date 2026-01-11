package com.arcube.transferaggregator.adapters.supplier.mozio.client;

import com.arcube.transferaggregator.adapters.supplier.mozio.MozioConfig;
import com.arcube.transferaggregator.adapters.supplier.mozio.dto.MozioSearchRequest;
import com.arcube.transferaggregator.adapters.supplier.mozio.dto.MozioSearchResponse;
import com.arcube.transferaggregator.adapters.supplier.mozio.dto.MozioSearchResponse.MozioResult;
import com.arcube.transferaggregator.resilience.RateLimiter;
import com.arcube.transferaggregator.resilience.RetryHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

// HTTP client for Mozio Search API - handles initial search and polling
@Slf4j
@Component
public class MozioSearchClient {
    
    private final WebClient webClient;
    private final MozioConfig config;
    private final RateLimiter rateLimiter;
    private final RetryHandler retryHandler;
    
    // Sets up WebClient with Mozio base URL and API key
    public MozioSearchClient(WebClient.Builder webClientBuilder, MozioConfig config, 
                             RateLimiter rateLimiter, RetryHandler retryHandler) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Mozio baseUrl is not configured");
        }
        if (!baseUrl.startsWith("https://") && !baseUrl.contains("localhost")) {
            log.warn("SECURITY: Mozio baseUrl should use HTTPS in production: {}", baseUrl);
        }
        
        this.webClient = webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("API-KEY", config.getApiKey())
            .build();
        this.config = config;
        this.rateLimiter = rateLimiter;
        this.retryHandler = retryHandler;
    }
    
    public record SearchResult(String searchId, List<MozioResult> results, boolean complete, boolean timedOut, Instant expiresAt) {}
    
    // Main search method - sends initial request then polls until complete or timeout
    public SearchResult search(MozioSearchRequest request, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        
        // Send initial search request to Mozio
        MozioSearchResponse initialResponse = sendInitialSearch(request);
        
        if (initialResponse == null || initialResponse.getSearchId() == null) {
            throw new RuntimeException("Failed to start Mozio search");
        }
        
        String searchId = initialResponse.getSearchId();
        List<MozioResult> allResults = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        
        // Collect results from initial response
        if (initialResponse.getResults() != null) {
            addResults(initialResponse.getResults(), allResults, seenIds);
        }
        
        // If no more results expected, return immediately
        if (!initialResponse.isMoreComing()) {
            return new SearchResult(searchId, allResults, true, false, calculateExpiry());
        }
        
        // Poll for additional results until complete or timeout
        boolean complete = pollForResults(searchId, deadline, allResults, seenIds);
        boolean timedOut = !complete && Instant.now().isAfter(deadline);
        
        return new SearchResult(searchId, allResults, complete, timedOut, calculateExpiry());
    }
    
    // Sends initial POST request to start the search
    private MozioSearchResponse sendInitialSearch(MozioSearchRequest request) {
        return retryHandler.executeWithRetry(() -> 
            webClient.post()
                .uri("/v2/search/")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MozioSearchResponse.class)
                .block(Duration.ofSeconds(config.getInitialRequestTimeoutSeconds()))
        );
    }
    
    // Polls Mozio API until moreComing=false or deadline reached
    private boolean pollForResults(String searchId, Instant deadline, 
                                   List<MozioResult> allResults, Set<String> seenIds) {
        boolean complete = false;
        
        while (!complete) {
            if (Instant.now().isAfter(deadline)) {
                break;
            }
            
            try {
                rateLimiter.acquirePollPermit(searchId);
                
                MozioSearchResponse pollResponse = retryHandler.executeWithRetry(() -> 
                    webClient.get()
                        .uri("/v2/search/{id}/poll/", searchId)
                        .retrieve()
                        .bodyToMono(MozioSearchResponse.class)
                        .block(Duration.ofSeconds(config.getPollResponseTimeoutSeconds()))
                );
                
                if (pollResponse != null) {
                    if (pollResponse.getResults() != null) {
                        addResults(pollResponse.getResults(), allResults, seenIds);
                    }
                    if (!pollResponse.isMoreComing()) {
                        complete = true;
                    }
                }
                
                if (!complete) {
                    Thread.sleep(config.getPollIntervalMs());
                }
                
            } catch (Exception e) {
                log.warn("Error polling Mozio search {}: {}", searchId, e.getMessage());
                break;
            }
        }
        
        return complete;
    }
    
    // Adds new results to list, deduping by resultId
    private void addResults(List<MozioResult> newResults, List<MozioResult> allResults, Set<String> seenIds) {
        for (MozioResult r : newResults) {
            if (r.getResultId() != null && seenIds.add(r.getResultId())) {
                allResults.add(r);
            }
        }
    }
    
    // Calculates when search results will expire
    private Instant calculateExpiry() {
        return Instant.now().plusSeconds(config.getSearchValidityMinutes() * 60L);
    }
}
