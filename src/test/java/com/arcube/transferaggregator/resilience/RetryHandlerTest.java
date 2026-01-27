package com.arcube.transferaggregator.resilience;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryHandlerTest {
    
    @Test
    void shouldRetryOnTransientFailure() {
        RetryHandler handler = new RetryHandler();
        final int[] attempts = {0};
        
        String result = handler.executeWithRetry(() -> {
            attempts[0]++;
            if (attempts[0] < 2) {
                throw new RuntimeException("SocketTimeoutException"); // Simulated retryable name match
            }
            return "success";
        }, 2);
        
        assertThat(result).isEqualTo("success");
        assertThat(attempts[0]).isEqualTo(2);
    }
    
    @Test
    void shouldFailAfterMaxRetries() {
        RetryHandler handler = new RetryHandler();
        
        assertThatThrownBy(() -> handler.executeWithRetry(() -> {
            throw new RuntimeException("Connection refused");
        }, 2))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Connection refused");
    }
    
    @Test
    void shouldNotRetryNonTransientErrors() {
        RetryHandler handler = new RetryHandler();
        final int[] attempts = {0};
        
        assertThatThrownBy(() -> handler.executeWithRetry(() -> {
            attempts[0]++;
            throw new IllegalArgumentException("Bad argument");
        }, 2))
            .isInstanceOf(IllegalArgumentException.class);
            
        assertThat(attempts[0]).isEqualTo(1); // No retry
    }

    @Test
    void executeWithRetryUsesDefaultSettings() {
        RetryHandler handler = new RetryHandler();
        final int[] attempts = {0};

        String result = handler.executeWithRetry(() -> {
            attempts[0]++;
            if (attempts[0] < 2) {
                throw new RuntimeException("timeout");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts[0]).isEqualTo(2);
    }

    @Test
    void treatsNullMessageAsNonRetryableByMessage() {
        RetryHandler handler = new RetryHandler();

        assertThatThrownBy(() -> handler.executeWithRetry(() -> {
            throw new RuntimeException((String) null);
        }, 1)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void retriesOnClassNameHints() {
        RetryHandler handler = new RetryHandler();
        final int[] attempts = {0};

        String result = handler.executeWithRetry(() -> {
            attempts[0]++;
            if (attempts[0] == 1) {
                throw new TimeoutException();
            }
            return "ok";
        }, 1);

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void retriesOnMessageHints() {
        RetryHandler handler = new RetryHandler();
        final int[] attempts = {0};

        String result = handler.executeWithRetry(() -> {
            attempts[0]++;
            if (attempts[0] == 1) {
                throw new RuntimeException("Service temporarily unavailable (503)");
            }
            return "ok";
        }, 1);

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void retriesOnMessage502() {
        RetryHandler handler = new RetryHandler();
        final int[] attempts = {0};

        String result = handler.executeWithRetry(() -> {
            attempts[0]++;
            if (attempts[0] == 1) {
                throw new RuntimeException("502");
            }
            return "ok";
        }, 1);

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void retriesOnConnectAndIoExceptionNames() {
        RetryHandler handler = new RetryHandler();
        final int[] attempts = {0};

        String result = handler.executeWithRetry(() -> {
            attempts[0]++;
            if (attempts[0] == 1) {
                throw new ConnectFailure();
            }
            return "ok";
        }, 1);

        assertThat(result).isEqualTo("ok");

        final int[] attempts2 = {0};
        String result2 = handler.executeWithRetry(() -> {
            attempts2[0]++;
            if (attempts2[0] == 1) {
                throw new IOExceptionFailure();
            }
            return "ok";
        }, 1);

        assertThat(result2).isEqualTo("ok");
    }

    @Test
    void sleepInterruptedThrowsRuntime() throws Exception {
        RetryHandler handler = new RetryHandler();

        Thread t = new Thread(() -> {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> handler.executeWithRetry(() -> {
                throw new RuntimeException("timeout");
            }, 1)).isInstanceOf(RuntimeException.class)
                .hasMessage("Retry interrupted");
        });

        t.start();
        t.join();
    }

    private static final class TimeoutException extends RuntimeException {
        private TimeoutException() {
            super("timeout");
        }
    }

    private static final class ConnectFailure extends RuntimeException {
        private ConnectFailure() {
            super("connect");
        }
    }

    private static final class IOExceptionFailure extends RuntimeException {
        private IOExceptionFailure() {
            super("io");
        }
    }
}
