package com.arcube.transferaggregator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.Map;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private String code;
    private String message;
    private String suggestedAction;
    private Map<String, Object> details;
    private Instant timestamp;
    private String requestId;
    
    public static ErrorResponse of(String code, String message, String suggestedAction, String requestId) {
        return ErrorResponse.builder()
            .code(code)
            .message(message)
            .suggestedAction(suggestedAction)
            .timestamp(Instant.now())
            .requestId(requestId)
            .build();
    }
}
