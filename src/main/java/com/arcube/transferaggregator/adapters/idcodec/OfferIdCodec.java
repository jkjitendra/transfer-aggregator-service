package com.arcube.transferaggregator.adapters.idcodec;

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

/** Codec for encoding and decoding offerIds.
* Format: base64url(payload).base64url(signature) */
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
                "exp", payload.expiresAt().getEpochSecond(),
                "iat", payload.issuedAt().getEpochSecond()
            );

            String payloadJson = objectMapper.writeValueAsString(payloadMap);
            String payloadBase64 = hmacSigner.encode(payloadJson);
            String signature = hmacSigner.sign(payloadBase64);

            return payloadBase64 + "." + signature;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to encode offer payload", e);
        }
    }

    public OfferPayload decode(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) throw new InvalidTokenException("Invalid token format");

            String payloadBase64 = parts[0];
            String signature = parts[1];

            if (!hmacSigner.sign(payloadBase64).equals(signature)) {
                throw new InvalidTokenException("Token signature verification failed");
            }

            String payloadJson = hmacSigner.decode(payloadBase64);
            @SuppressWarnings("unchecked")
            Map<String, Object> payloadMap = objectMapper.readValue(payloadJson, Map.class);

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
