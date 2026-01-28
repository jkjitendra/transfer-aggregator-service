package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.config.AlertingConfig;
import com.arcube.transferaggregator.observability.ErrorMetrics;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AlertingServiceTest {

    @Test
    void sendsDlqAlertWhenThresholdExceeded() throws Exception {
        ErrorMetrics metrics = new ErrorMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        metrics.init();
        metrics.incrementDlqSize("main");

        AlertingConfig config = new AlertingConfig();
        config.setEnabled(true);
        config.setDlqWarningThreshold(1);
        config.setDlqCriticalThreshold(10);
        config.getEmail().setEnabled(true);
        config.getEmail().setFrom("alerts@example.com");
        config.getEmail().setRecipients(List.of("ops@example.com"));
        config.getEmail().setSubjectPrefix("[Test]");

        JavaMailSender mailSender = mock(JavaMailSender.class);
        AlertingService service = new AlertingService(metrics, config, mailSender);
        service.checkAlerts();

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).contains("DLQ Size Alert");
    }

    @Test
    void suppressesAlertDuringCooldown() throws Exception {
        ErrorMetrics metrics = new ErrorMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        metrics.init();
        metrics.incrementDlqSize("main");

        AlertingConfig config = new AlertingConfig();
        config.setEnabled(true);
        config.setDlqWarningThreshold(1);
        config.getEmail().setEnabled(true);
        config.getEmail().setFrom("alerts@example.com");
        config.getEmail().setRecipients(List.of("ops@example.com"));
        config.getEmail().setSubjectPrefix("[Test]");

        JavaMailSender mailSender = mock(JavaMailSender.class);
        AlertingService service = new AlertingService(metrics, config, mailSender);

        setAtomicInstant(service, "lastDlqAlert", Instant.now());
        service.checkAlerts();

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendsCriticalDlqAlertAndLogsCriticalBranch() throws Exception {
        ErrorMetrics metrics = new ErrorMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        metrics.init();
        metrics.incrementDlqSize("main");
        metrics.incrementDlqSize("main");

        AlertingConfig config = new AlertingConfig();
        config.setEnabled(true);
        config.setDlqWarningThreshold(1);
        config.setDlqCriticalThreshold(2);
        config.getEmail().setEnabled(true);
        config.getEmail().setFrom("alerts@example.com");
        config.getEmail().setRecipients(List.of("ops@example.com"));
        config.getEmail().setSubjectPrefix("[Test]");

        JavaMailSender mailSender = mock(JavaMailSender.class);
        AlertingService service = new AlertingService(metrics, config, mailSender);
        service.checkAlerts();

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendsErrorRateAlertWhenRateHigh() throws Exception {
        ErrorMetrics metrics = new ErrorMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        metrics.init();
        metrics.recordSearchError("S1", "err");
        metrics.recordBookingError("S1", "err");

        AlertingConfig config = new AlertingConfig();
        config.setEnabled(true);
        config.setErrorRateWarningThreshold(1);
        config.setErrorRateCriticalThreshold(10);
        config.getEmail().setEnabled(true);
        config.getEmail().setFrom("alerts@example.com");
        config.getEmail().setRecipients(List.of("ops@example.com"));
        config.getEmail().setSubjectPrefix("[Test]");

        JavaMailSender mailSender = mock(JavaMailSender.class);
        AlertingService service = new AlertingService(metrics, config, mailSender);

        setPrivate(service, "lastCheckTime", Instant.now().minusSeconds(60));
        setPrivate(service, "previousSearchErrors", 0.0);
        setPrivate(service, "previousBookingErrors", 0.0);

        service.checkAlerts();

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void suppressesErrorRateAlertDuringCooldown() throws Exception {
        ErrorMetrics metrics = new ErrorMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        metrics.init();
        metrics.recordSearchError("S1", "err");

        AlertingConfig config = new AlertingConfig();
        config.setEnabled(true);
        config.setErrorRateWarningThreshold(1);
        config.getEmail().setEnabled(true);
        config.getEmail().setRecipients(List.of("ops@example.com"));
        config.getEmail().setSubjectPrefix("[Test]");

        JavaMailSender mailSender = mock(JavaMailSender.class);
        AlertingService service = new AlertingService(metrics, config, mailSender);

        setAtomicInstant(service, "lastErrorAlert", Instant.now());
        setPrivate(service, "lastCheckTime", Instant.now().minusSeconds(60));
        setPrivate(service, "previousSearchErrors", 0.0);
        setPrivate(service, "previousBookingErrors", 0.0);

        service.checkAlerts();
        verifyNoInteractions(mailSender);
    }

    @Test
    void doesNotSendEmailWhenEmailDisabled() {
        ErrorMetrics metrics = new ErrorMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        metrics.init();
        metrics.incrementDlqSize("main");

        AlertingConfig config = new AlertingConfig();
        config.setEnabled(true);
        config.setDlqWarningThreshold(1);
        config.getEmail().setEnabled(false);
        config.getEmail().setFrom("alerts@example.com");
        config.getEmail().setRecipients(List.of("ops@example.com"));
        config.getEmail().setSubjectPrefix("[Test]");

        JavaMailSender mailSender = mock(JavaMailSender.class);
        AlertingService service = new AlertingService(metrics, config, mailSender);

        service.checkAlerts();
        verifyNoInteractions(mailSender);
    }

    @Test
    void doesNotSendEmailWhenRecipientsEmpty() {
        ErrorMetrics metrics = new ErrorMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        metrics.init();
        metrics.incrementDlqSize("main");

        AlertingConfig config = new AlertingConfig();
        config.setEnabled(true);
        config.setDlqWarningThreshold(1);
        config.getEmail().setEnabled(true);
        config.getEmail().setFrom("alerts@example.com");
        config.getEmail().setRecipients(List.of());
        config.getEmail().setSubjectPrefix("[Test]");

        JavaMailSender mailSender = mock(JavaMailSender.class);
        AlertingService service = new AlertingService(metrics, config, mailSender);

        service.checkAlerts();
        verifyNoInteractions(mailSender);
    }

    @Test
    void sendsCriticalErrorRateAlertWhenRateHigh() throws Exception {
        ErrorMetrics metrics = new ErrorMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        metrics.init();
        metrics.recordSearchError("S1", "err");
        metrics.recordBookingError("S1", "err");

        AlertingConfig config = new AlertingConfig();
        config.setEnabled(true);
        config.setErrorRateWarningThreshold(1);
        config.setErrorRateCriticalThreshold(1);
        config.getEmail().setEnabled(true);
        config.getEmail().setFrom("alerts@example.com");
        config.getEmail().setRecipients(List.of("ops@example.com"));
        config.getEmail().setSubjectPrefix("[Test]");

        JavaMailSender mailSender = mock(JavaMailSender.class);
        AlertingService service = new AlertingService(metrics, config, mailSender);

        setPrivate(service, "lastCheckTime", Instant.now().minusSeconds(60));
        setPrivate(service, "previousSearchErrors", 0.0);
        setPrivate(service, "previousBookingErrors", 0.0);

        service.checkAlerts();

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void doesNotSendErrorRateAlertWhenBelowWarning() throws Exception {
        ErrorMetrics metrics = new ErrorMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        metrics.init();

        AlertingConfig config = new AlertingConfig();
        config.setEnabled(true);
        config.setErrorRateWarningThreshold(10);
        config.setErrorRateCriticalThreshold(20);
        config.getEmail().setEnabled(true);
        config.getEmail().setRecipients(List.of("ops@example.com"));
        config.getEmail().setSubjectPrefix("[Test]");

        JavaMailSender mailSender = mock(JavaMailSender.class);
        AlertingService service = new AlertingService(metrics, config, mailSender);

        setPrivate(service, "lastCheckTime", Instant.now().minusSeconds(60));
        setPrivate(service, "previousSearchErrors", 0.0);
        setPrivate(service, "previousBookingErrors", 0.0);

        service.checkAlerts();
        verifyNoInteractions(mailSender);
    }

    @Test
    void getDlqStatusReflectsThresholds() {
        ErrorMetrics metrics = new ErrorMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        metrics.init();

        AlertingConfig config = new AlertingConfig();
        config.setEnabled(true);
        config.setDlqWarningThreshold(1);
        config.setDlqCriticalThreshold(2);

        JavaMailSender mailSender = mock(JavaMailSender.class);
        AlertingService service = new AlertingService(metrics, config, mailSender);

        AlertingService.AlertStatus ok = service.getDlqStatus();
        assertThat(ok.level()).isEqualTo(AlertingService.AlertLevel.OK);

        metrics.incrementDlqSize("main");
        AlertingService.AlertStatus warning = service.getDlqStatus();
        assertThat(warning.level()).isEqualTo(AlertingService.AlertLevel.WARNING);

        metrics.incrementDlqSize("main");
        AlertingService.AlertStatus critical = service.getDlqStatus();
        assertThat(critical.level()).isEqualTo(AlertingService.AlertLevel.CRITICAL);
    }

    @Test
    void doesNotSendEmailWhenRecipientsMissing() throws Exception {
        ErrorMetrics metrics = new ErrorMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        metrics.init();
        metrics.incrementDlqSize("main");

        AlertingConfig config = new AlertingConfig();
        config.setEnabled(true);
        config.setDlqWarningThreshold(1);
        config.getEmail().setEnabled(true);
        config.getEmail().setFrom("alerts@example.com");
        config.getEmail().setRecipients(null);
        config.getEmail().setSubjectPrefix("[Test]");

        JavaMailSender mailSender = mock(JavaMailSender.class);
        AlertingService service = new AlertingService(metrics, config, mailSender);

        service.checkAlerts();
        verifyNoInteractions(mailSender);
    }

    @Test
    void swallowsEmailSendException() throws Exception {
        ErrorMetrics metrics = new ErrorMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        metrics.init();
        metrics.incrementDlqSize("main");

        AlertingConfig config = new AlertingConfig();
        config.setEnabled(true);
        config.setDlqWarningThreshold(1);
        config.getEmail().setEnabled(true);
        config.getEmail().setFrom("alerts@example.com");
        config.getEmail().setRecipients(List.of("ops@example.com"));
        config.getEmail().setSubjectPrefix("[Test]");

        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new RuntimeException("mail down")).when(mailSender).send(any(SimpleMailMessage.class));

        AlertingService service = new AlertingService(metrics, config, mailSender);
        service.checkAlerts();

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    private static void setPrivate(Object target, String fieldName, Object value) throws Exception {
        Field field = AlertingService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setAtomicInstant(Object target, String fieldName, Instant value) throws Exception {
        Field field = AlertingService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.atomic.AtomicReference<Instant> ref =
            (java.util.concurrent.atomic.AtomicReference<Instant>) field.get(target);
        ref.set(value);
    }
}
