package com.arcube.transferaggregator.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Serves the OpenAPI specification from openapi.yaml */
@RestController
public class OpenApiController {
    
    @GetMapping(value = "/openapi.yaml", produces = "application/x-yaml")
    public ResponseEntity<String> getOpenApiSpec() throws IOException {
        Resource resource = new ClassPathResource("openapi.yaml");
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/x-yaml"))
            .body(content);
    }
}
