package com.arcube.transferaggregator.adapters.idcodec;

import com.arcube.transferaggregator.config.TenantContext;
import com.arcube.transferaggregator.exception.InvalidTokenException;
import com.arcube.transferaggregator.utils.HmacSigner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Codec for encoding and decoding booking IDs */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingIdCodec {

    private final HmacSigner hmacSigner;
    private final ObjectMapper objectMapper;

    public String encode(BookingPayload payload) {
        try {
            var payloadMap = Map.of(
                "sup", payload.supplierCode(),
                "rid", payload.reservationId(),
                "ten", TenantContext.getTenantIdOrDefault("default"),
                "cnf", payload.confirmationNumber() != null ? payload.confirmationNumber() : ""
            );

            String payloadJson = objectMapper.writeValueAsString(payloadMap);
            String payloadBase64 = hmacSigner.encode(payloadJson);
            String keyId = hmacSigner.activeKeyId();
            String signedData = keyId + "." + payloadBase64;
            String signature = hmacSigner.sign(signedData);

            return signedData + "." + signature;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to encode booking payload", e);
        }
    }

    public BookingPayload decode(String token) {
        try {
            if (token == null || token.length() > 4096) throw new InvalidTokenException("Invalid booking token size");
            String[] parts = token.split("\\.", -1);
            boolean legacy = parts.length == 2;
            if (parts.length != 3 && !legacy) throw new InvalidTokenException("Invalid booking token format");
            if (legacy && TenantContext.getTenantId() != null) throw new InvalidTokenException("Legacy booking token is not tenant-bound");

            String keyId = legacy ? hmacSigner.activeKeyId() : parts[0];
            String payloadBase64 = legacy ? parts[0] : parts[1];
            String signature = legacy ? parts[1] : parts[2];
            String signedData = legacy ? payloadBase64 : keyId + "." + payloadBase64;
            if (!hmacSigner.verify(signedData, signature, keyId)) {
                throw new InvalidTokenException("Booking token signature verification failed");
            }

            String payloadJson = hmacSigner.decode(payloadBase64);
            @SuppressWarnings("unchecked")
            Map<String, Object> payloadMap = objectMapper.readValue(payloadJson, Map.class);
            String currentTenant = TenantContext.getTenantId();
            if (currentTenant != null && !currentTenant.equals(payloadMap.get("ten"))) {
                throw new InvalidTokenException("Booking tenant mismatch");
            }

            String cnf = (String) payloadMap.get("cnf");
            if (cnf != null && cnf.isEmpty()) cnf = null;

            return new BookingPayload(
                (String) payloadMap.get("sup"),
                (String) payloadMap.get("rid"),
                cnf
            );
        } catch (InvalidTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidTokenException("Failed to decode booking token", e);
        }
    }
}
