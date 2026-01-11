package com.arcube.transferaggregator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/** Security configuration including CORS */
@Configuration
public class SecurityConfig {
    
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // In production, replace with specific allowed origins
        // config.setAllowedOrigins(List.of("https://your-frontend.com"));
        config.setAllowedOriginPatterns(List.of("*")); // Dev only - restrict in prod
        
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
            "Authorization", 
            "Content-Type", 
            "X-Request-Id", 
            "Idempotency-Key"
        ));
        config.setExposedHeaders(List.of(
            "X-Request-Id",
            "Retry-After"
        ));
        config.setAllowCredentials(false); // Set true if using cookies
        config.setMaxAge(3600L); // Cache preflight for 1 hour
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/v1/**", config);
        
        return new CorsFilter(source);
    }
}
