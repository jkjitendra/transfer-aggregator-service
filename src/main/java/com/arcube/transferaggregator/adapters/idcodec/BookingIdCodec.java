package com.arcube.transferaggregator.adapters.idcodec;

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
                "cnf", payload.confirmationNumber() != null ? payload.confirmationNumber() : ""
            );

            String payloadJson = objectMapper.writeValueAsString(payloadMap);
            String payloadBase64 = hmacSigner.encode(payloadJson);
            String signature = hmacSigner.sign(payloadBase64);

            return payloadBase64 + "." + signature;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to encode booking payload", e);
        }
    }

    public BookingPayload decode(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) throw new InvalidTokenException("Invalid booking token format");

            String payloadBase64 = parts[0];
            String signature = parts[1];

            if (!hmacSigner.sign(payloadBase64).equals(signature)) {
                throw new InvalidTokenException("Booking token signature verification failed");
            }

            String payloadJson = hmacSigner.decode(payloadBase64);
            @SuppressWarnings("unchecked")
            Map<String, Object> payloadMap = objectMapper.readValue(payloadJson, Map.class);

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
