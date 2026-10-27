package com.arcube.transferaggregator.routing;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "routing.osrm")
public class OsrmProperties {
    private boolean enabled;
    private String baseUrl = "http://localhost:5000";
    private Duration timeout = Duration.ofSeconds(2);
}
