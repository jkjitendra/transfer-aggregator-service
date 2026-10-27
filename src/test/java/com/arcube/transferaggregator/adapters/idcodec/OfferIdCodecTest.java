package com.arcube.transferaggregator.adapters.idcodec;

import com.arcube.transferaggregator.config.TokenProperties;
import com.arcube.transferaggregator.config.TenantContext;
import com.arcube.transferaggregator.exception.InvalidTokenException;
import com.arcube.transferaggregator.exception.OfferExpiredException;
import com.arcube.transferaggregator.utils.HmacSigner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfferIdCodecTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static OfferIdCodec codec() {
        TokenProperties props = new TokenProperties();
        props.setSecret("01234567890123456789012345678901");
        props.validate();
        return new OfferIdCodec(new HmacSigner(props), new ObjectMapper());
    }

    @Test
    void encodeDecodeRoundTrip() {
        OfferPayload payload = OfferPayload.of("STUB", "search-1", "result-2",
            Instant.now().plus(10, ChronoUnit.MINUTES));

        OfferIdCodec codec = codec();
        String token = codec.encode(payload);
        OfferPayload decoded = codec.decode(token);

        assertThat(decoded.supplierCode()).isEqualTo(payload.supplierCode());
        assertThat(decoded.searchId()).isEqualTo(payload.searchId());
        assertThat(decoded.resultId()).isEqualTo(payload.resultId());
    }

    @Test
    void decodeRejectsInvalidFormat() {
        OfferIdCodec codec = codec();
        assertThatThrownBy(() -> codec.decode("not-a-token"))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void decodeRejectsInvalidSignature() {
        OfferIdCodec codec = codec();
        OfferPayload payload = OfferPayload.of("STUB", "search-1", "result-2",
            Instant.now().plus(10, ChronoUnit.MINUTES));

        String token = codec.encode(payload);
        String tampered = token + "x";

        assertThatThrownBy(() -> codec.decode(tampered))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsOfferFromAnotherTenant() {
        OfferIdCodec codec = codec();
        TenantContext.setTenantId("tenant-a");
        String token = codec.encode(OfferPayload.of("STUB", "s1", "r1", Instant.now().plusSeconds(600)));
        TenantContext.setTenantId("tenant-b");
        assertThatThrownBy(() -> codec.decode(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void previousKeyCanVerifyTokenAfterRotation() {
        TokenProperties old = new TokenProperties();
        old.setSecret("01234567890123456789012345678901");
        old.setActiveKeyId("old");
        old.validate();
        TenantContext.setTenantId("tenant-a");
        String token = new OfferIdCodec(new HmacSigner(old), new ObjectMapper())
            .encode(OfferPayload.of("STUB", "s1", "r1", Instant.now().plusSeconds(600)));

        TokenProperties rotated = new TokenProperties();
        rotated.setSecret("abcdefghijklmnopqrstuvwxyzABCDEF0123456789");
        rotated.setActiveKeyId("new");
        rotated.setPreviousKeys(Map.of("old", old.getSecret()));
        rotated.validate();
        OfferIdCodec codec = new OfferIdCodec(new HmacSigner(rotated), new ObjectMapper());
        assertThat(codec.decode(token).searchId()).isEqualTo("s1");
    }

    @Test
    void decodeRejectsExpiredOffer() {
        OfferIdCodec codec = codec();
        OfferPayload payload = OfferPayload.of("STUB", "search-old", "result-old",
            Instant.now().minus(1, ChronoUnit.HOURS));

        String token = codec.encode(payload);

        assertThatThrownBy(() -> codec.decode(token))
            .isInstanceOf(OfferExpiredException.class);
    }

    @Test
    void offerPayloadNotExpiredWhenNoExpiry() {
        OfferPayload payload = new OfferPayload("S", "sid", "rid", null, Instant.now());
        assertThat(payload.isExpired()).isFalse();
    }

    @Test
    void encodeThrowsWhenJsonFails() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});

        TokenProperties props = new TokenProperties();
        props.setSecret("01234567890123456789012345678901");
        props.validate();
        OfferIdCodec codec = new OfferIdCodec(new HmacSigner(props), mapper);

        OfferPayload payload = OfferPayload.of("S", "sid", "rid",
            Instant.now().plus(1, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> codec.encode(payload))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to encode offer payload");
    }

    @Test
    void decodeWrapsUnexpectedErrors() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.readValue(anyString(), any(Class.class)))
            .thenThrow(new RuntimeException("boom"));

        TokenProperties props = new TokenProperties();
        props.setSecret("01234567890123456789012345678901");
        props.validate();
        HmacSigner signer = new HmacSigner(props);
        OfferIdCodec codec = new OfferIdCodec(signer, mapper);

        long exp = Instant.now().plus(1, ChronoUnit.MINUTES).getEpochSecond();
        long iat = Instant.now().getEpochSecond();
        String payloadJson = "{\"sup\":\"S\",\"sid\":\"sid\",\"rid\":\"rid\",\"exp\":" + exp + ",\"iat\":" + iat + "}";
        String payloadBase64 = signer.encode(payloadJson);
        String token = payloadBase64 + "." + signer.sign(payloadBase64);

        assertThatThrownBy(() -> codec.decode(token))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("Failed to decode offer token");
    }
}
