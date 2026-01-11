package com.arcube.transferaggregator.adapters.supplier.mozio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MozioCancelRequest {
    @JsonProperty("reservation_id")
    private String reservationId;
}
