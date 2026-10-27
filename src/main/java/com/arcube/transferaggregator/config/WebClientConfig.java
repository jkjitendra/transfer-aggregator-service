package com.arcube.transferaggregator.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.slf4j.MDC;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/** WebClient configuration for making HTTP calls to suppliers */
@Configuration
public class WebClientConfig {
    
    @Bean
    public WebClientCustomizer webClientCustomizer() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .responseTimeout(Duration.ofSeconds(10));
        
        return builder -> builder.clientConnector(new ReactorClientHttpConnector(httpClient))
            .filter((request, next) -> {
                String requestId = MDC.get("requestId");
                if (requestId == null) return next.exchange(request);
                return next.exchange(ClientRequest.from(request)
                    .header("X-Request-Id", requestId).build());
            });
    }
}
