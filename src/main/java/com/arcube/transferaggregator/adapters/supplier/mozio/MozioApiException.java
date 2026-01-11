package com.arcube.transferaggregator.adapters.supplier.mozio;

/**
 * Custom exception for Mozio API errors.
 * Contains the error code from Mozio for proper handling.
 */
public class MozioApiException extends RuntimeException {
    
    private final String errorCode;
    private final int httpStatus;
    
    public MozioApiException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
    
    public String getErrorCode() { return errorCode; }
    public int getHttpStatus() { return httpStatus; }
    
    // Mozio-specific error codes
    public static final String SEARCH_EXPIRED = "search_expired";
    public static final String TRIP_PRICE_CHANGED = "trip_price_changed";
    public static final String DUPLICATE_RESERVATION = "duplicate_reservation";
    public static final String RES_ALREADY_CANCELED = "res_already_canceled";
    public static final String TOO_LATE_TO_CANCEL = "too_late_to_cancel";
    
    public boolean isSearchExpired() { return SEARCH_EXPIRED.equals(errorCode); }
    public boolean isPriceChanged() { return TRIP_PRICE_CHANGED.equals(errorCode); }
    public boolean isDuplicateReservation() { return DUPLICATE_RESERVATION.equals(errorCode); }
    public boolean isAlreadyCanceled() { return RES_ALREADY_CANCELED.equals(errorCode); }
    public boolean isTooLateToCanel() { return TOO_LATE_TO_CANCEL.equals(errorCode); }
}
