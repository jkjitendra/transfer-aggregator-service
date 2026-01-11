package com.arcube.transferaggregator.dto;

import com.arcube.transferaggregator.domain.BookingStatus;
import com.arcube.transferaggregator.domain.Money;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookResponse {
    private String bookingId;
    private BookingStatus status;
    private String confirmationNumber;
    private Money totalPrice;
    private String pickupInstructions;
    private ProviderContactDto provider;
    private String errorCode;
    private String errorMessage;
    private String suggestedAction;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderContactDto {
        private String name;
        private String contactPhone;
    }
    
    public static BookResponse confirmed(String bookingId, String confirmationNumber, 
            Money totalPrice, String pickupInstructions, ProviderContactDto provider) {
        return BookResponse.builder()
            .bookingId(bookingId)
            .status(BookingStatus.CONFIRMED)
            .confirmationNumber(confirmationNumber)
            .totalPrice(totalPrice)
            .pickupInstructions(pickupInstructions)
            .provider(provider)
            .build();
    }
    
    public static BookResponse pending(String bookingId) {
        return BookResponse.builder()
            .bookingId(bookingId)
            .status(BookingStatus.PENDING)
            .build();
    }
    
    public static BookResponse failed(String errorCode, String errorMessage, String suggestedAction) {
        return BookResponse.builder()
            .status(BookingStatus.FAILED)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .suggestedAction(suggestedAction)
            .build();
    }
    
    public static BookResponse priceChanged() {
        return BookResponse.builder()
            .status(BookingStatus.PRICE_CHANGED)
            .errorCode("PRICE_CHANGED")
            .errorMessage("Price changed; please re-search")
            .suggestedAction("RE_SEARCH")
            .build();
    }
}
