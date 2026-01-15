package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.config.AlertingConfig;
import com.arcube.transferaggregator.observability.ErrorMetrics;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service for monitoring and alerting on DLQ size and error rates.
 * Runs scheduled checks and sends email alerts when thresholds are exceeded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertingService {

    private final ErrorMetrics errorMetrics;
    private final AlertingConfig alertingConfig;
    private final JavaMailSender mailSender;

    // Track last alert times to avoid spam
    private final AtomicReference<Instant> lastDlqAlert = new AtomicReference<>(Instant.EPOCH);
    private final AtomicReference<Instant> lastErrorAlert = new AtomicReference<>(Instant.EPOCH);
    
    // Track previous error counts for rate calculation
    private double previousSearchErrors = 0;
    private double previousBookingErrors = 0;
    private Instant lastCheckTime = Instant.now();
    
    // Minimum interval between alerts (5 minutes)
    private static final long ALERT_COOLDOWN_SECONDS = 300;

    @PostConstruct
    public void init() {
        log.info("AlertingService initialized: enabled={}, dlqWarning={}, dlqCritical={}", 
            alertingConfig.isEnabled(),
            alertingConfig.getDlqWarningThreshold(),
            alertingConfig.getDlqCriticalThreshold());
    }

    /**
     * Scheduled check for DLQ size and error rates.
     * Runs every configured interval (default: 60 seconds).
     */
    @Scheduled(fixedDelayString = "${transfer.aggregator.alerting.check-interval-seconds:60}000")
    public void checkAlerts() {
        if (!alertingConfig.isEnabled()) {
            return;
        }

        log.debug("Running alerting check...");
        
        checkDlqSize();
        checkErrorRates();
    }

    private void checkDlqSize() {
        int dlqSize = errorMetrics.getTotalDlqSize();
        
        if (dlqSize >= alertingConfig.getDlqCriticalThreshold()) {
            sendDlqAlert(AlertLevel.CRITICAL, dlqSize);
        } else if (dlqSize >= alertingConfig.getDlqWarningThreshold()) {
            sendDlqAlert(AlertLevel.WARNING, dlqSize);
        }
    }

    private void checkErrorRates() {
        Instant now = Instant.now();
        long elapsedSeconds = now.getEpochSecond() - lastCheckTime.getEpochSecond();
        
        if (elapsedSeconds <= 0) return;
        
        // Calculate error rates (errors per minute)
        double currentSearchErrors = errorMetrics.getSearchErrorCount();
        double currentBookingErrors = errorMetrics.getBookingErrorCount();
        
        double searchErrorRate = ((currentSearchErrors - previousSearchErrors) / elapsedSeconds) * 60;
        double bookingErrorRate = ((currentBookingErrors - previousBookingErrors) / elapsedSeconds) * 60;
        double totalErrorRate = searchErrorRate + bookingErrorRate;
        
        // Update tracking
        previousSearchErrors = currentSearchErrors;
        previousBookingErrors = currentBookingErrors;
        lastCheckTime = now;
        
        if (totalErrorRate >= alertingConfig.getErrorRateCriticalThreshold()) {
            sendErrorRateAlert(AlertLevel.CRITICAL, totalErrorRate, searchErrorRate, bookingErrorRate);
        } else if (totalErrorRate >= alertingConfig.getErrorRateWarningThreshold()) {
            sendErrorRateAlert(AlertLevel.WARNING, totalErrorRate, searchErrorRate, bookingErrorRate);
        }
    }

    private void sendDlqAlert(AlertLevel level, int dlqSize) {
        if (!canSendAlert(lastDlqAlert)) {
            log.debug("DLQ alert suppressed due to cooldown");
            return;
        }
        
        String subject = String.format("%s %s: DLQ Size Alert", 
            alertingConfig.getEmail().getSubjectPrefix(), level);
        String body = String.format("""
            Alert Level: %s
            
            Dead Letter Queue size has exceeded threshold.
            
            Current DLQ Size: %d
            Warning Threshold: %d
            Critical Threshold: %d
            
            Please investigate failed messages in the DLQ.
            
            ---
            Transfer Aggregator Service
            Timestamp: %s
            """, 
            level, dlqSize,
            alertingConfig.getDlqWarningThreshold(),
            alertingConfig.getDlqCriticalThreshold(),
            Instant.now());
        
        sendAlert(subject, body, level);
        lastDlqAlert.set(Instant.now());
        log.warn("DLQ Alert [{}]: size={}, threshold={}", level, dlqSize, 
            level == AlertLevel.CRITICAL ? alertingConfig.getDlqCriticalThreshold() 
                                         : alertingConfig.getDlqWarningThreshold());
    }

    private void sendErrorRateAlert(AlertLevel level, double totalRate, 
                                     double searchRate, double bookingRate) {
        if (!canSendAlert(lastErrorAlert)) {
            log.debug("Error rate alert suppressed due to cooldown");
            return;
        }
        
        String subject = String.format("%s %s: Error Rate Alert", 
            alertingConfig.getEmail().getSubjectPrefix(), level);
        String body = String.format("""
            Alert Level: %s
            
            Error rate has exceeded threshold.
            
            Total Error Rate: %.2f/min
            - Search Errors: %.2f/min
            - Booking Errors: %.2f/min
            
            Warning Threshold: %d/min
            Critical Threshold: %d/min
            
            Please investigate the error logs.
            
            ---
            Transfer Aggregator Service
            Timestamp: %s
            """, 
            level, totalRate, searchRate, bookingRate,
            alertingConfig.getErrorRateWarningThreshold(),
            alertingConfig.getErrorRateCriticalThreshold(),
            Instant.now());
        
        sendAlert(subject, body, level);
        lastErrorAlert.set(Instant.now());
        log.warn("Error Rate Alert [{}]: total={}/min, search={}/min, booking={}/min", 
            level, totalRate, searchRate, bookingRate);
    }

    private void sendAlert(String subject, String body, AlertLevel level) {
        // Always log the alert
        if (level == AlertLevel.CRITICAL) {
            log.error("ALERT: {}\n{}", subject, body);
        } else {
            log.warn("ALERT: {}\n{}", subject, body);
        }
        
        // Send email if configured
        if (alertingConfig.getEmail().isEnabled() && 
            alertingConfig.getEmail().getRecipients() != null &&
            !alertingConfig.getEmail().getRecipients().isEmpty()) {
            
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(alertingConfig.getEmail().getFrom());
                message.setTo(alertingConfig.getEmail().getRecipients().toArray(new String[0]));
                message.setSubject(subject);
                message.setText(body);
                
                mailSender.send(message);
                log.info("Alert email sent to: {}", alertingConfig.getEmail().getRecipients());
            } catch (Exception e) {
                log.error("Failed to send alert email: {}", e.getMessage());
            }
        }
    }

    private boolean canSendAlert(AtomicReference<Instant> lastAlert) {
        long secondsSinceLastAlert = Instant.now().getEpochSecond() - lastAlert.get().getEpochSecond();
        return secondsSinceLastAlert >= ALERT_COOLDOWN_SECONDS;
    }

    /**
     * Manually trigger a DLQ check (for testing/admin endpoints).
     */
    public AlertStatus getDlqStatus() {
        int dlqSize = errorMetrics.getTotalDlqSize();
        AlertLevel level = AlertLevel.OK;
        
        if (dlqSize >= alertingConfig.getDlqCriticalThreshold()) {
            level = AlertLevel.CRITICAL;
        } else if (dlqSize >= alertingConfig.getDlqWarningThreshold()) {
            level = AlertLevel.WARNING;
        }
        
        return new AlertStatus(level, dlqSize, 
            alertingConfig.getDlqWarningThreshold(),
            alertingConfig.getDlqCriticalThreshold());
    }

    public enum AlertLevel {
        OK, WARNING, CRITICAL
    }

    public record AlertStatus(
        AlertLevel level,
        int currentValue,
        int warningThreshold,
        int criticalThreshold
    ) {}
}
