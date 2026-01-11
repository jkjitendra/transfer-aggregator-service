package com.arcube.transferaggregator.domain;

import lombok.Builder;
import java.util.List;

// Cancellation policy with tiered refunds
@Builder
public record CancellationPolicy(
    boolean cancellableOnline,
    boolean cancellableOffline,
    boolean amendable,
    List<CancellationTier> tiers
) {
    public record CancellationTier(int hoursNotice, int refundPercent) {}
    
    public boolean isFullyRefundable() {
        return tiers != null && tiers.stream().anyMatch(t -> t.refundPercent() == 100);
    }
}
