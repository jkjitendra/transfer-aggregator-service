package com.arcube.transferaggregator.exception;

import com.arcube.transferaggregator.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/** Global exception handler for REST API */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            errors.put(fieldName, error.getDefaultMessage());
        });
        log.warn("Validation error: {}", errors);
        return ResponseEntity.badRequest().body(ErrorResponse.builder()
            .code("VALIDATION_ERROR")
            .message("Request validation failed")
            .details(errors)
            .requestId(MDC.get("requestId"))
            .build());
    }
    
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleJsonParseError(HttpMessageNotReadableException ex) {
        log.warn("JSON parse error: {}", ex.getMessage());
        String message = "Invalid JSON format";
        if (ex.getMessage() != null && ex.getMessage().contains("JSON parse error")) {
            message = "Malformed JSON in request body";
        }
        return ResponseEntity.badRequest().body(ErrorResponse.of(
            "INVALID_JSON", message, null, MDC.get("requestId")));
    }
    
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        log.warn("Unsupported media type: {}", ex.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(ErrorResponse.of(
            "UNSUPPORTED_MEDIA_TYPE", 
            "Content-Type must be application/json. Received: " + ex.getContentType(),
            null, MDC.get("requestId")));
    }
    
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not allowed: {} - Supported: {}", ex.getMethod(), ex.getSupportedHttpMethods());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ErrorResponse.of(
            "METHOD_NOT_ALLOWED", 
            "HTTP method " + ex.getMethod() + " is not supported for this endpoint",
            null, MDC.get("requestId")));
    }
    
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch: {} - expected {}", ex.getName(), ex.getRequiredType());
        return ResponseEntity.badRequest().body(ErrorResponse.of(
            "INVALID_PARAMETER", 
            "Invalid value for parameter '" + ex.getName() + "'",
            null, MDC.get("requestId")));
    }
    
    @ExceptionHandler(OfferExpiredException.class)
    public ResponseEntity<ErrorResponse> handleOfferExpired(OfferExpiredException ex) {
        log.warn("Offer expired: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GONE).body(ErrorResponse.of(
            "OFFER_EXPIRED", ex.getMessage(), "RE_SEARCH", MDC.get("requestId")));
    }
    
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException ex) {
        log.warn("Invalid token: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.of(
            "INVALID_TOKEN", ex.getMessage(), null, MDC.get("requestId")));
    }
    
    @ExceptionHandler(SupplierNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSupplierNotFound(SupplierNotFoundException ex) {
        log.error("Supplier not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
            "SUPPLIER_NOT_FOUND", ex.getMessage(), null, MDC.get("requestId")));
    }
    
    @ExceptionHandler(CancellationNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleCancellationNotAllowed(CancellationNotAllowedException ex) {
        log.warn("Cancellation not allowed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
            "CANCELLATION_NOT_ALLOWED", ex.getMessage(), "CONTACT_SUPPORT", MDC.get("requestId")));
    }
    
    @ExceptionHandler(ServiceBusyException.class)
    public ResponseEntity<ErrorResponse> handleServiceBusy(ServiceBusyException ex) {
        log.warn("Service busy: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header("Retry-After", "5")
            .body(ErrorResponse.of("SERVICE_BUSY", ex.getMessage(), "RETRY", MDC.get("requestId")));
    }
    
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", "60")
            .body(ErrorResponse.of("RATE_LIMIT_EXCEEDED", ex.getMessage(), "WAIT", MDC.get("requestId")));
    }
    
    @ExceptionHandler(SupplierTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleSupplierTimeout(SupplierTimeoutException ex) {
        log.warn("Supplier timeout: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(ErrorResponse.of(
            "SUPPLIER_TIMEOUT", ex.getMessage(), "RETRY", MDC.get("requestId")));
    }
    
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex) {
        log.debug("Resource not found: {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
            "NOT_FOUND", "Resource not found: " + ex.getResourcePath(), null, MDC.get("requestId")));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        // Log full exception for debugging, but don't expose details to client
        log.error("Unexpected error [requestId={}]: {}", MDC.get("requestId"), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.of(
            "INTERNAL_ERROR", "An internal error occurred. Please try again later.", "RETRY", MDC.get("requestId")));
    }
}

