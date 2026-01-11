package com.arcube.transferaggregator.domain;

import lombok.Builder;
import java.math.BigDecimal;

// Transport Provider information
@Builder
public record Provider(
    String name,
    String displayName,
    String logoUrl,
    BigDecimal rating,
    int ratingCount,
    String contactPhone
) {}
