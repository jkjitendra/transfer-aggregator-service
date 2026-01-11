package com.arcube.transferaggregator.observability;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter to set up MDC context for request tracing.
 * Adds requestId, method, path, and timing to all log entries.
 */
@Slf4j
@Component
@Order(1)
public class RequestLoggingFilter implements Filter {
    
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_METHOD = "method";
    private static final String MDC_PATH = "path";
    private static final String MDC_DURATION_MS = "durationMs";
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        long startTime = System.currentTimeMillis();
        
        // Extract or generate request ID
        String requestId = httpRequest.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }
        
        // Set MDC context
        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_METHOD, httpRequest.getMethod());
        MDC.put(MDC_PATH, httpRequest.getRequestURI());
        
        // Add request ID to response header
        httpResponse.setHeader(REQUEST_ID_HEADER, requestId);
        
        try {
            if (log.isDebugEnabled()) {
                log.debug(">>> {} {} started", httpRequest.getMethod(), httpRequest.getRequestURI());
            }
            
            chain.doFilter(request, response);
            
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            MDC.put(MDC_DURATION_MS, String.valueOf(duration));
            
            log.info("<<< {} {} completed status={} duration={}ms",
                httpRequest.getMethod(), httpRequest.getRequestURI(), 
                httpResponse.getStatus(), duration);
            
            MDC.clear();
        }
    }
}
