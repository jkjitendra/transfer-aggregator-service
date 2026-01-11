package com.arcube.transferaggregator.resilience;

import com.arcube.transferaggregator.config.AggregatorProperties;
import com.arcube.transferaggregator.config.AggregatorProperties.ResilienceProperties;
import com.arcube.transferaggregator.exception.ServiceBusyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierBulkheadTest {
    
    @Mock
    AggregatorProperties properties;
    @Mock
    ResilienceProperties resilienceProperties;
    
    @Test
    void shouldAllowRequestsWithinLimit() {
        when(properties.getResilience()).thenReturn(resilienceProperties);
        when(resilienceProperties.getMaxConcurrentCalls()).thenReturn(2);
        
        SupplierBulkhead bulkhead = new SupplierBulkhead(properties);
        
        assertThat(bulkhead.execute(() -> "success")).isEqualTo("success");
    }
    
    @Test
    void shouldRejectExcessiveRequests() throws InterruptedException {
        when(properties.getResilience()).thenReturn(resilienceProperties);
        when(resilienceProperties.getMaxConcurrentCalls()).thenReturn(1);
        
        SupplierBulkhead bulkhead = new SupplierBulkhead(properties);
        CountDownLatch latch = new CountDownLatch(1);
        
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            // Occupy the single permit
            Future<?> f1 = executor.submit(() -> bulkhead.execute(() -> {
                try { 
                    latch.await(); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "ok";
            }));
            
            // Wait for f1 to start
            Thread.sleep(100);
            
            // Try to acquire another permit - should fail immediately (after 500ms timeout logic)
            // Note: In unit test without mocking time, this waits 500ms.
            assertThatThrownBy(() -> bulkhead.execute(() -> "fail"))
                .isInstanceOf(ServiceBusyException.class);
                
            latch.countDown();
        }
    }
}
