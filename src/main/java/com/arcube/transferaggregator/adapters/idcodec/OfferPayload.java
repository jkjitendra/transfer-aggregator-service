package com.arcube.transferaggregator.adapters.idcodec;

import java.time.Instant;


/** Payload encoded in offerId */
public record OfferPayload(
    String supplierCode,
    String searchId,
    String resultId,
    Instant expiresAt,
    Instant issuedAt
) {
    public static OfferPayload of(String supplierCode, String searchId, String resultId, Instant expiresAt) {
        return new OfferPayload(supplierCode, searchId, resultId, expiresAt, Instant.now());
    }
    
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
