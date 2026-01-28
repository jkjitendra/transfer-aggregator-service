package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.adapters.idcodec.OfferIdCodec;
import com.arcube.transferaggregator.adapters.idcodec.OfferPayload;
import com.arcube.transferaggregator.config.TokenProperties;
import com.arcube.transferaggregator.domain.Money;
import com.arcube.transferaggregator.dto.PricingRequest;
import com.arcube.transferaggregator.dto.PricingResponse;
import com.arcube.transferaggregator.dto.SearchResponse.OfferDto;
import com.arcube.transferaggregator.dto.SearchStateDto;
import com.arcube.transferaggregator.utils.HmacSigner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PricingServiceTest {

    @Test
    void calculatesAmenityPricingIncludingIncludedAmenity() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        OfferIdCodec codec = offerIdCodec();

        String searchId = "search-1";
        String offerId = codec.encode(OfferPayload.of("STUB", searchId, "r1", Instant.now().plusSeconds(600)));

        OfferDto offer = OfferDto.builder()
            .offerId(offerId)
            .supplierCode("STUB")
            .totalPrice(Money.of(100.00, "USD"))
            .includedAmenities(List.of("wifi"))
            .build();

        SearchStateDto state = SearchStateDto.builder()
            .searchId(searchId)
            .offers(List.of(offer))
            .statuses(java.util.Map.of())
            .supplierSearchIds(java.util.Map.of())
            .incomplete(false)
            .build();

        String json = objectMapper.writeValueAsString(state);
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("search:" + searchId)).thenReturn(json);

        PricingService service = new PricingService(codec, redis, objectMapper);

        PricingRequest request = PricingRequest.builder()
            .searchId(searchId)
            .offerId(offerId)
            .amenities(List.of("wifi", "extra_waiting_time_sixty_min"))
            .build();

        PricingResponse response = service.calculatePrice(request);

        assertThat(response.getBasePrice().value()).isEqualByComparingTo("100.00");
        assertThat(response.getAmenitiesTotal().value()).isEqualByComparingTo("35.00");
        assertThat(response.getFinalPrice().value()).isEqualByComparingTo("135.00");
        assertThat(response.getSelectedAmenities()).hasSize(2);
    }

    @Test
    void calculatesAllAmenitySwitchCases() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        OfferIdCodec codec = offerIdCodec();

        String searchId = "search-amenities";
        String offerId = codec.encode(OfferPayload.of("STUB", searchId, "r1", Instant.now().plusSeconds(600)));

        OfferDto offer = OfferDto.builder()
            .offerId(offerId)
            .supplierCode("STUB")
            .totalPrice(Money.of(10.00, "USD"))
            .includedAmenities(List.of("ride_tracking"))
            .build();

        SearchStateDto state = SearchStateDto.builder()
            .searchId(searchId)
            .offers(List.of(offer))
            .statuses(java.util.Map.of())
            .supplierSearchIds(java.util.Map.of())
            .incomplete(false)
            .build();

        String json = objectMapper.writeValueAsString(state);
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("search:" + searchId)).thenReturn(json);

        PricingService service = new PricingService(codec, redis, objectMapper);

        PricingRequest request = PricingRequest.builder()
            .searchId(searchId)
            .offerId(offerId)
            .amenities(List.of(
                "meet_and_greet",
                "baby_seats",
                "child_booster",
                "wifi",
                "extra_waiting_time_thirty_min",
                "extra_waiting_time_sixty_min",
                "sms_notifications",
                "ride_tracking"
            ))
            .build();

        PricingResponse response = service.calculatePrice(request);

        assertThat(response.getSelectedAmenities()).hasSize(8);
        assertThat(response.getFinalPrice().value()).isEqualByComparingTo("131.99");
    }

    @Test
    void getAvailableAmenitiesReturnsStaticList() {
        PricingService service = new PricingService(offerIdCodec(),
            Mockito.mock(StringRedisTemplate.class), new ObjectMapper());

        assertThat(service.getAvailableAmenities("o1")).isNotEmpty();
    }

    @Test
    void calculatesWithUnknownAmenityDefaults() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        OfferIdCodec codec = offerIdCodec();

        String searchId = "search-2";
        String offerId = codec.encode(OfferPayload.of("STUB", searchId, "r1", Instant.now().plusSeconds(600)));

        OfferDto offer = OfferDto.builder()
            .offerId(offerId)
            .supplierCode("STUB")
            .totalPrice(Money.of(10.00, "USD"))
            .build();

        SearchStateDto state = SearchStateDto.builder()
            .searchId(searchId)
            .offers(List.of(offer))
            .statuses(java.util.Map.of())
            .supplierSearchIds(java.util.Map.of())
            .incomplete(false)
            .build();

        String json = objectMapper.writeValueAsString(state);
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("search:" + searchId)).thenReturn(json);

        PricingService service = new PricingService(codec, redis, objectMapper);

        PricingResponse response = service.calculatePrice(PricingRequest.builder()
            .searchId(searchId)
            .offerId(offerId)
            .amenities(List.of("unknown_amenity"))
            .build());

        assertThat(response.getFinalPrice().value()).isEqualByComparingTo("20.00");
    }

    @Test
    void calculatesWithNullBasePriceAndNoAmenities() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        OfferIdCodec codec = offerIdCodec();

        String searchId = "search-3";
        String offerId = codec.encode(OfferPayload.of("STUB", searchId, "r1", Instant.now().plusSeconds(600)));

        OfferDto offer = OfferDto.builder()
            .offerId(offerId)
            .supplierCode("STUB")
            .totalPrice(null)
            .build();

        SearchStateDto state = SearchStateDto.builder()
            .searchId(searchId)
            .offers(List.of(offer))
            .statuses(java.util.Map.of())
            .supplierSearchIds(java.util.Map.of())
            .incomplete(false)
            .build();

        String json = objectMapper.writeValueAsString(state);
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("search:" + searchId)).thenReturn(json);

        PricingService service = new PricingService(codec, redis, objectMapper);

        PricingResponse response = service.calculatePrice(PricingRequest.builder()
            .searchId(searchId)
            .offerId(offerId)
            .amenities(null)
            .build());

        assertThat(response.getBasePrice()).isNull();
        assertThat(response.getFinalPrice().value()).isEqualByComparingTo("0.00");
        assertThat(response.getCurrency()).isEqualTo("USD");
    }

    @Test
    void calculatesWithEmptyAmenitiesList() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        OfferIdCodec codec = offerIdCodec();

        String searchId = "search-empty-amenities";
        String offerId = codec.encode(OfferPayload.of("STUB", searchId, "r1", Instant.now().plusSeconds(600)));

        OfferDto offer = OfferDto.builder()
            .offerId(offerId)
            .supplierCode("STUB")
            .totalPrice(Money.of(25.00, "USD"))
            .build();

        SearchStateDto state = SearchStateDto.builder()
            .searchId(searchId)
            .offers(List.of(offer))
            .statuses(java.util.Map.of())
            .supplierSearchIds(java.util.Map.of())
            .incomplete(false)
            .build();

        String json = objectMapper.writeValueAsString(state);
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("search:" + searchId)).thenReturn(json);

        PricingService service = new PricingService(codec, redis, objectMapper);

        PricingResponse response = service.calculatePrice(PricingRequest.builder()
            .searchId(searchId)
            .offerId(offerId)
            .amenities(List.of())
            .build());

        assertThat(response.getFinalPrice().value()).isEqualByComparingTo("25.00");
        assertThat(response.getSelectedAmenities()).isEmpty();
    }

    @Test
    void returnsMinimalResponseWhenOfferMissing() {
        ObjectMapper objectMapper = new ObjectMapper();
        OfferIdCodec codec = offerIdCodec();

        String offerId = codec.encode(OfferPayload.of("STUB", "missing", "r1", Instant.now().plusSeconds(600)));
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("search:missing")).thenReturn(null);

        PricingService service = new PricingService(codec, redis, objectMapper);

        PricingResponse response = service.calculatePrice(PricingRequest.builder()
            .searchId("missing")
            .offerId(offerId)
            .build());

        assertThat(response.getOfferId()).isEqualTo(offerId);
        assertThat(response.getBasePrice()).isNull();
        assertThat(response.getFinalPrice()).isNull();
    }

    @Test
    void returnsMinimalResponseWhenCacheJsonInvalid() throws Exception {
        OfferIdCodec codec = offerIdCodec();
        ObjectMapper mapper = Mockito.mock(ObjectMapper.class);

        String offerId = codec.encode(OfferPayload.of("STUB", "bad", "r1", Instant.now().plusSeconds(600)));
        StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("search:bad")).thenReturn("{not-json}");
        when(mapper.readValue(any(String.class), eq(SearchStateDto.class)))
            .thenThrow(new JsonProcessingException("bad") {});

        PricingService service = new PricingService(codec, redis, mapper);

        PricingResponse response = service.calculatePrice(PricingRequest.builder()
            .searchId("bad")
            .offerId(offerId)
            .build());

        assertThat(response.getOfferId()).isEqualTo(offerId);
        assertThat(response.getBasePrice()).isNull();
        assertThat(response.getFinalPrice()).isNull();
    }

    private OfferIdCodec offerIdCodec() {
        TokenProperties props = new TokenProperties();
        props.setSecret("01234567890123456789012345678901");
        props.validate();
        return new OfferIdCodec(new HmacSigner(props), new ObjectMapper());
    }
}
