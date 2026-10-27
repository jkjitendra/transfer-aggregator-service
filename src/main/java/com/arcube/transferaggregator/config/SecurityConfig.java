package com.arcube.transferaggregator.config;

import com.arcube.transferaggregator.observability.TenantFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

@Configuration
public class SecurityConfig {
    private static final Set<String> ALLOWED_ROLES = Set.of("CUSTOMER", "SUPPORT", "OPERATOR");

    @Bean
    @ConditionalOnProperty(name = "security.api.enabled", havingValue = "true", matchIfMissing = true)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ApiSecurityProperties properties,
                                                       TenantConfig tenantConfig) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/admin/**").hasAnyRole("SUPPORT", "OPERATOR")
                .requestMatchers("/api/v1/admin/**").hasRole("OPERATOR")
                .requestMatchers("/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**").hasRole("OPERATOR")
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/api-docs", "/api-docs/**", "/openapi.yaml").hasAnyRole("SUPPORT", "OPERATOR")
                .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyRole("CUSTOMER", "SUPPORT", "OPERATOR")
                .requestMatchers("/api/v1/**").hasAnyRole("CUSTOMER", "OPERATOR")
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(tokenConverter(properties))))
            .addFilterAfter(new TenantFilter(tenantConfig, properties), BearerTokenAuthenticationFilter.class)
            .build();
    }

    @Bean
    @ConditionalOnProperty(name = "security.api.enabled", havingValue = "false")
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http, ApiSecurityProperties properties) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .build();
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    @ConditionalOnProperty(name = "security.api.enabled", havingValue = "true", matchIfMissing = true)
    public JwtDecoder jwtDecoder(ApiSecurityProperties properties) {
        NimbusJwtDecoder decoder = properties.getJwkSetUri() == null || properties.getJwkSetUri().isBlank()
            ? NimbusJwtDecoder.withIssuerLocation(properties.getIssuerUri()).build()
            : NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains(properties.getAudience())
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        OAuth2TokenValidator<Jwt> issuerAndTime = JwtValidators.createDefaultWithIssuer(properties.getIssuerUri());
        decoder.setJwtValidator(jwt -> {
            OAuth2TokenValidatorResult standard = issuerAndTime.validate(jwt);
            return standard.hasErrors() ? standard : audience.validate(jwt);
        });
        return decoder;
    }

    private Converter<Jwt, AbstractAuthenticationToken> tokenConverter(ApiSecurityProperties properties) {
        return jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            Object claim = jwt.getClaims().get(properties.getRolesClaim());
            Stream<String> roles;
            if (claim instanceof List<?> list) {
                roles = list.stream().filter(String.class::isInstance).map(String.class::cast);
            } else if (claim instanceof String text) {
                roles = Arrays.stream(text.split("[,\\s]+"));
            } else {
                roles = Stream.empty();
            }
            roles.map(role -> role.toUpperCase(Locale.ROOT))
                .filter(ALLOWED_ROLES::contains)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .forEach(authorities::add);
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }

    private CorsConfigurationSource corsConfigurationSource(ApiSecurityProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Tenant-Id", "X-Request-Id", "Idempotency-Key"));
        config.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", config);
        return source;
    }
}
