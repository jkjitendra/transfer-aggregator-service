package com.arcube.transferaggregator.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Security configuration properties for token signing */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "security.token")
public class TokenProperties {
    
    /** HMAC-SHA256 secret key for signing tokens. Must be at least 32 bytes (256 bits) */
    private String secret;
    
    @PostConstruct
    public void validate() {
        if (secret == null || secret.isBlank()) {
            log.warn("TOKEN_SECRET not configured - using insecure default for development ONLY");
            secret = "INSECURE-DEV-ONLY-" + System.currentTimeMillis();
        } else if (secret.length() < 32) {
            throw new IllegalStateException("TOKEN_SECRET must be at least 32 characters for HMAC-SHA256 security");
        }
    }
}

