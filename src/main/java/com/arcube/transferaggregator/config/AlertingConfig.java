package com.arcube.transferaggregator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for alerting thresholds and email settings.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "transfer.aggregator.alerting")
public class AlertingConfig {

    private boolean enabled = false;
    
    // DLQ Thresholds
    private int dlqWarningThreshold = 10;
    private int dlqCriticalThreshold = 50;
    
    // Error Rate Thresholds (per minute)
    private int errorRateWarningThreshold = 5;
    private int errorRateCriticalThreshold = 20;
    
    // Check interval in seconds
    private int checkIntervalSeconds = 60;
    
    // Email configuration
    private EmailConfig email = new EmailConfig();
    
    @Data
    public static class EmailConfig {
        private boolean enabled = false;
        private String from;
        private List<String> recipients;
        private String subjectPrefix;
    }
}
