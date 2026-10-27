package com.arcube.transferaggregator.adapters.idcodec;

import com.arcube.transferaggregator.config.TenantContext;
import com.arcube.transferaggregator.exception.InvalidTokenException;
import com.arcube.transferaggregator.exception.OfferExpiredException;
import com.arcube.transferaggregator.utils.HmacSigner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/** Codec for tenant-bound offer IDs: keyId.base64url(payload).base64url(signature). */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfferIdCodec {

    private final HmacSigner hmacSigner;
    private final ObjectMapper objectMapper;

    public String encode(OfferPayload payload) {
        try {
            var payloadMap = Map.of(
                "sup", payload.supplierCode(),
                "sid", payload.searchId(),
                "rid", payload.resultId(),
                "ten", TenantContext.getTenantIdOrDefault("default"),
                "exp", payload.expiresAt().getEpochSecond(),
                "iat", payload.issuedAt().getEpochSecond()
            );

            String payloadJson = objectMapper.writeValueAsString(payloadMap);
            String payloadBase64 = hmacSigner.encode(payloadJson);
            String keyId = hmacSigner.activeKeyId();
            String signedData = keyId + "." + payloadBase64;
            String signature = hmacSigner.sign(signedData);

            return signedData + "." + signature;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to encode offer payload", e);
        }
    }

    public OfferPayload decode(String token) {
        try {
            if (token == null || token.length() > 4096) throw new InvalidTokenException("Invalid token size");
            String[] parts = token.split("\\.", -1);
            boolean legacy = parts.length == 2;
            if (parts.length != 3 && !legacy) throw new InvalidTokenException("Invalid token format");
            if (legacy && TenantContext.getTenantId() != null) throw new InvalidTokenException("Legacy token is not tenant-bound");

            String keyId = legacy ? hmacSigner.activeKeyId() : parts[0];
            String payloadBase64 = legacy ? parts[0] : parts[1];
            String signature = legacy ? parts[1] : parts[2];
            String signedData = legacy ? payloadBase64 : keyId + "." + payloadBase64;
            if (!hmacSigner.verify(signedData, signature, keyId)) {
                throw new InvalidTokenException("Token signature verification failed");
            }

            String payloadJson = hmacSigner.decode(payloadBase64);
            @SuppressWarnings("unchecked")
            Map<String, Object> payloadMap = objectMapper.readValue(payloadJson, Map.class);
            String currentTenant = TenantContext.getTenantId();
            if (currentTenant != null && !currentTenant.equals(payloadMap.get("ten"))) {
                throw new InvalidTokenException("Offer tenant mismatch");
            }

            Instant expiresAt = Instant.ofEpochSecond(((Number) payloadMap.get("exp")).longValue());
            Instant issuedAt = Instant.ofEpochSecond(((Number) payloadMap.get("iat")).longValue());

            var payload = new OfferPayload(
                (String) payloadMap.get("sup"),
                (String) payloadMap.get("sid"),
                (String) payloadMap.get("rid"),
                expiresAt, issuedAt
            );

            if (payload.isExpired()) {
                throw new OfferExpiredException("Offer expired at " + expiresAt);
            }

            return payload;
        } catch (InvalidTokenException | OfferExpiredException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidTokenException("Failed to decode offer token", e);
        }
    }
}
