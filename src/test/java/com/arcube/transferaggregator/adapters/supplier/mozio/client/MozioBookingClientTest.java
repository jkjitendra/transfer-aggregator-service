package com.arcube.transferaggregator.adapters.supplier.mozio.client;

import com.arcube.transferaggregator.adapters.supplier.mozio.MozioConfig;
import com.arcube.transferaggregator.adapters.supplier.mozio.dto.MozioBookingRequest;
import com.arcube.transferaggregator.adapters.supplier.mozio.dto.MozioBookingResponse;
import com.arcube.transferaggregator.resilience.RetryHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersUriSpec;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MozioBookingClientTest {

    @Test
    void bookUsesRetryHandlerAndWebClient() {
        WebClient.Builder builder = mock(WebClient.Builder.class);
        WebClient webClient = mock(WebClient.class);
        RetryHandler retryHandler = mock(RetryHandler.class);

        MozioConfig config = new MozioConfig();
        config.setBaseUrl("https://example.test");
        config.setApiKey("key");

        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.defaultHeader(eq("API-KEY"), anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(webClient);

        RequestBodyUriSpec postSpec = mock(RequestBodyUriSpec.class);
        @SuppressWarnings("rawtypes")
        RequestHeadersSpec headersSpec = mock(RequestHeadersSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        MozioBookingRequest request = MozioBookingRequest.builder()
            .searchId("s1")
            .resultId("r1")
            .email("a@b.com")
            .build();

        MozioBookingResponse response = new MozioBookingResponse();

        when(webClient.post()).thenReturn(postSpec);
        when(postSpec.uri("/v2/reservations/")).thenReturn(postSpec);
        when(postSpec.bodyValue(request)).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(MozioBookingResponse.class)).thenReturn(Mono.just(response));

        when(retryHandler.executeWithRetry(any()))
            .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());

        MozioBookingClient client = new MozioBookingClient(builder, config, retryHandler);

        MozioBookingResponse result = client.book(request);

        assertThat(result).isSameAs(response);
    }

    @Test
    void cancelUsesRetryHandlerAndWebClient() {
        WebClient.Builder builder = mock(WebClient.Builder.class);
        WebClient webClient = mock(WebClient.class);
        RetryHandler retryHandler = mock(RetryHandler.class);

        MozioConfig config = new MozioConfig();
        config.setBaseUrl("https://example.test");
        config.setApiKey("key");

        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.defaultHeader(eq("API-KEY"), anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(webClient);

        @SuppressWarnings("rawtypes")
        RequestHeadersUriSpec deleteSpec = mock(RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RequestHeadersSpec headersSpec = mock(RequestHeadersSpec.class);
        ResponseSpec responseSpec = mock(ResponseSpec.class);

        when(webClient.delete()).thenReturn(deleteSpec);
        when(deleteSpec.uri("/v2/reservations/{id}/", "res-1")).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()));

        when(retryHandler.executeWithRetry(any()))
            .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());

        MozioBookingClient client = new MozioBookingClient(builder, config, retryHandler);

        client.cancel("res-1");
    }
}
