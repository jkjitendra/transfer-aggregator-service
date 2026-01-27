package com.arcube.transferaggregator.adapters.idcodec;

import com.arcube.transferaggregator.config.TokenProperties;
import com.arcube.transferaggregator.exception.InvalidTokenException;
import com.arcube.transferaggregator.utils.HmacSigner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookingIdCodecTest {

    private static BookingIdCodec codec() {
        TokenProperties props = new TokenProperties();
        props.setSecret("01234567890123456789012345678901");
        props.validate();
        return new BookingIdCodec(new HmacSigner(props), new ObjectMapper());
    }

    @Test
    void encodeDecodeRoundTrip() {
        BookingPayload payload = BookingPayload.of("STUB", "res-123", "CONF-1");
        BookingIdCodec codec = codec();

        String token = codec.encode(payload);
        BookingPayload decoded = codec.decode(token);

        assertThat(decoded.supplierCode()).isEqualTo(payload.supplierCode());
        assertThat(decoded.reservationId()).isEqualTo(payload.reservationId());
        assertThat(decoded.confirmationNumber()).isEqualTo(payload.confirmationNumber());
    }

    @Test
    void decodeRejectsInvalidSignature() {
        BookingPayload payload = BookingPayload.of("STUB", "res-123", "CONF-1");
        BookingIdCodec codec = codec();

        String token = codec.encode(payload);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> codec.decode(tampered))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void decodeTreatsBlankConfirmationAsNull() {
        BookingPayload payload = BookingPayload.of("STUB", "res-123", "");
        BookingIdCodec codec = codec();

        String token = codec.encode(payload);
        BookingPayload decoded = codec.decode(token);

        assertThat(decoded.confirmationNumber()).isNull();
    }

    @Test
    void encodeHandlesNullConfirmationNumber() {
        BookingPayload payload = new BookingPayload("STUB", "res-123", null);
        BookingIdCodec codec = codec();

        String token = codec.encode(payload);
        BookingPayload decoded = codec.decode(token);

        assertThat(decoded.confirmationNumber()).isNull();
    }

    @Test
    void encodeThrowsWhenJsonFails() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});

        TokenProperties props = new TokenProperties();
        props.setSecret("01234567890123456789012345678901");
        props.validate();
        BookingIdCodec codec = new BookingIdCodec(new HmacSigner(props), mapper);

        assertThatThrownBy(() -> codec.encode(BookingPayload.of("S", "R", "C")))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to encode booking payload");
    }

    @Test
    void decodeRejectsInvalidFormat() {
        BookingIdCodec codec = codec();

        assertThatThrownBy(() -> codec.decode("invalid"))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void decodeTreatsMissingConfirmationAsNull() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.readValue(anyString(), any(Class.class)))
            .thenReturn(Map.of("sup", "S1", "rid", "R1"));

        TokenProperties props = new TokenProperties();
        props.setSecret("01234567890123456789012345678901");
        props.validate();
        HmacSigner signer = new HmacSigner(props);
        BookingIdCodec codec = new BookingIdCodec(signer, mapper);

        String payloadBase64 = signer.encode("{\"sup\":\"S1\",\"rid\":\"R1\"}");
        String token = payloadBase64 + "." + signer.sign(payloadBase64);

        BookingPayload decoded = codec.decode(token);
        assertThat(decoded.confirmationNumber()).isNull();
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
        BookingIdCodec codec = new BookingIdCodec(signer, mapper);

        String payloadBase64 = signer.encode("{\"sup\":\"S1\",\"rid\":\"R1\"}");
        String token = payloadBase64 + "." + signer.sign(payloadBase64);

        assertThatThrownBy(() -> codec.decode(token))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("Failed to decode booking token");
    }
}
