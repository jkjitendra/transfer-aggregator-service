package com.arcube.transferaggregator.dto;

import com.arcube.transferaggregator.dto.SearchResponse.OfferDto;
import com.arcube.transferaggregator.dto.SearchResponse.SupplierStatusDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchStateDto implements Serializable {
    
    private String searchId;
    private List<OfferDto> offers;
    private Map<String, SupplierStatusDto> statuses;
    private Map<String, String> supplierSearchIds;
    private boolean incomplete;
}
