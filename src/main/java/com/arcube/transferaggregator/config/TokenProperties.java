package com.arcube.transferaggregator.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Security configuration properties for token signing */
@Data
@Component
@ConfigurationProperties(prefix = "security.token")
public class TokenProperties {
    
    private static final String RETIRED_DEMO_SECRET = "57fd0a434be5ba1dc468f69ee204c70328ceb8cac56d6f3e70cd39c49ce5f7d4";
    private static final String DEMO_SECRET = "demo-only-key-do-not-use-in-production-1234567890";

    /** HMAC-SHA256 secret key for signing new tokens. */
    @ToString.Exclude
    private String secret;
    private String activeKeyId = "v1";
    @ToString.Exclude
    private Map<String, String> previousKeys = new HashMap<>();
    
    @PostConstruct
    public void validate() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32
                || RETIRED_DEMO_SECRET.equals(secret) || DEMO_SECRET.equals(secret)) {
            throw new IllegalStateException("TOKEN_SECRET must be an external 32+ byte secret, not a retired demo key");
        }
        if (activeKeyId == null || !activeKeyId.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalStateException("TOKEN_ACTIVE_KEY_ID must be 1-32 URL-safe characters");
        }
        for (var entry : previousKeys.entrySet()) {
            if (!entry.getKey().matches("[A-Za-z0-9_-]{1,32}") || entry.getKey().equals(activeKeyId)
                    || entry.getValue() == null || entry.getValue().getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException("Invalid previous token verification key");
            }
        }
    }
}
