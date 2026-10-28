package com.arcube.transferaggregator.controller;

import com.arcube.transferaggregator.config.TenantContext;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Describes the authenticated tenant and allowed UI capabilities without returning claims or tokens. */
@RestController
public class VisualSessionController {
    public record Session(String tenantId, List<String> roles) {}

    @GetMapping("/api/v1/visual/session")
    public ResponseEntity<Session> session(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore()).build();
        }
        List<String> roles = List.of("CUSTOMER", "SUPPORT", "OPERATOR").stream()
            .filter(role -> authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role)))
            .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new Session(TenantContext.getTenantId(), roles));
    }
}
