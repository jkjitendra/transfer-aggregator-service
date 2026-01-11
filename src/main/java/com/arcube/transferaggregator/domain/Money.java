package com.arcube.transferaggregator.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Represents a monetary amount with currency
public record Money(BigDecimal value, String currency, String display) {
    
    public Money {
        if (value != null) {
            value = value.setScale(2, RoundingMode.HALF_UP);
        }
        if (display == null && value != null && currency != null) {
            display = currency + " " + value.toPlainString();
        }
    }
    
    public static Money of(double value, String currency) {
        return new Money(BigDecimal.valueOf(value), currency, null);
    }
    
    public static Money of(BigDecimal value, String currency) {
        return new Money(value, currency, null);
    }
}
