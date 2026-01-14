package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.dto.PageRequest;
import com.arcube.transferaggregator.dto.SearchFilter;
import com.arcube.transferaggregator.dto.SearchResponse.OfferDto;
import com.arcube.transferaggregator.dto.SearchSort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
public class OfferFilterService {

    public FilterResult filterAndSort(List<OfferDto> offers, SearchFilter filter, 
                                       SearchSort sort, PageRequest page) {
        if (offers == null || offers.isEmpty()) {
            return FilterResult.empty();
        }

        Stream<OfferDto> stream = offers.stream();
        
        if (filter != null && filter.hasFilters()) {
            stream = applyFilters(stream, filter);
        }

        List<OfferDto> filtered = stream.toList();
        int totalCount = filtered.size();

        List<OfferDto> sorted = applySorting(filtered, sort);
        List<OfferDto> paged = applyPagination(sorted, page);

        int totalPages = (int) Math.ceil((double) totalCount / page.getValidatedSize());

        log.debug("Filtered {} -> {} offers, page {}/{}", 
            offers.size(), paged.size(), page.getPage() + 1, totalPages);

        return FilterResult.builder()
            .offers(paged)
            .totalCount(totalCount)
            .page(page.getPage())
            .size(paged.size())
            .totalPages(totalPages)
            .hasNext(page.getPage() < totalPages - 1)
            .hasPrevious(page.getPage() > 0)
            .build();
    }

    private Stream<OfferDto> applyFilters(Stream<OfferDto> stream, SearchFilter filter) {
        // Price filters (record accessor: value())
        if (filter.getMinPrice() != null) {
            stream = stream.filter(o -> o.getTotalPrice() != null && 
                o.getTotalPrice().value().compareTo(filter.getMinPrice()) >= 0);
        }
        if (filter.getMaxPrice() != null) {
            stream = stream.filter(o -> o.getTotalPrice() != null && 
                o.getTotalPrice().value().compareTo(filter.getMaxPrice()) <= 0);
        }

        // Vehicle type filter (record accessor: type())
        if (filter.getVehicleTypes() != null && !filter.getVehicleTypes().isEmpty()) {
            stream = stream.filter(o -> o.getVehicle() != null && 
                containsIgnoreCase(filter.getVehicleTypes(), o.getVehicle().type()));
        }

        // Vehicle class filter (record accessor: vehicleClass())
        if (filter.getVehicleClasses() != null && !filter.getVehicleClasses().isEmpty()) {
            stream = stream.filter(o -> o.getVehicle() != null && 
                containsIgnoreCase(filter.getVehicleClasses(), o.getVehicle().vehicleClass()));
        }

        // Vehicle category filter (record accessor: category())
        if (filter.getVehicleCategories() != null && !filter.getVehicleCategories().isEmpty()) {
            stream = stream.filter(o -> o.getVehicle() != null && 
                containsIgnoreCase(filter.getVehicleCategories(), o.getVehicle().category()));
        }

        // Capacity filters (record accessor: maxPassengers(), maxBags())
        if (filter.getMinPassengers() != null) {
            stream = stream.filter(o -> o.getVehicle() != null && 
                o.getVehicle().maxPassengers() >= filter.getMinPassengers());
        }
        if (filter.getMinBags() != null) {
            stream = stream.filter(o -> o.getVehicle() != null && 
                o.getVehicle().maxBags() >= filter.getMinBags());
        }

        // Amenity filters
        if (filter.getRequiredAmenities() != null && !filter.getRequiredAmenities().isEmpty()) {
            stream = stream.filter(o -> o.getIncludedAmenities() != null && 
                o.getIncludedAmenities().containsAll(filter.getRequiredAmenities()));
        }

        // Free cancellation filter (record accessor: isFullyRefundable())
        if (Boolean.TRUE.equals(filter.getFreeCancellationOnly())) {
            stream = stream.filter(o -> o.getCancellation() != null && 
                o.getCancellation().isFullyRefundable());
        }

        // Provider rating filter (record accessor: rating())
        if (filter.getMinProviderRating() != null) {
            stream = stream.filter(o -> o.getProvider() != null && 
                o.getProvider().rating() != null &&
                o.getProvider().rating().compareTo(filter.getMinProviderRating()) >= 0);
        }

        // Provider name filter (record accessor: name())
        if (filter.getProviderNames() != null && !filter.getProviderNames().isEmpty()) {
            stream = stream.filter(o -> o.getProvider() != null && 
                containsIgnoreCase(filter.getProviderNames(), o.getProvider().name()));
        }

        // Duration filter
        if (filter.getMaxDurationMinutes() != null) {
            stream = stream.filter(o -> 
                o.getEstimatedDurationMinutes() <= filter.getMaxDurationMinutes());
        }

        // Supplier filter
        if (filter.getSupplierCodes() != null && !filter.getSupplierCodes().isEmpty()) {
            stream = stream.filter(o -> 
                containsIgnoreCase(filter.getSupplierCodes(), o.getSupplierCode()));
        }

        return stream;
    }

    private List<OfferDto> applySorting(List<OfferDto> offers, SearchSort sort) {
        if (sort == null) {
            sort = SearchSort.byPrice();
        }

        Comparator<OfferDto> comparator = getComparator(sort);
        return offers.stream().sorted(comparator).toList();
    }

    private Comparator<OfferDto> getComparator(SearchSort sort) {
        Comparator<OfferDto> comparator = switch (sort.getField()) {
            case PRICE -> Comparator.comparing(
                o -> o.getTotalPrice() != null ? o.getTotalPrice().value() : BigDecimal.ZERO);
            case RATING -> Comparator.comparing(
                o -> o.getProvider() != null && o.getProvider().rating() != null 
                    ? o.getProvider().rating() : BigDecimal.ZERO);
            case DURATION -> Comparator.comparingInt(OfferDto::getEstimatedDurationMinutes);
            case DISTANCE -> Comparator.comparingInt(
                o -> o.getDistanceMeters() != null ? o.getDistanceMeters() : Integer.MAX_VALUE);
            case PASSENGERS -> Comparator.comparingInt(
                o -> o.getVehicle() != null ? o.getVehicle().maxPassengers() : 0);
            case PROVIDER_NAME -> Comparator.comparing(
                o -> o.getProvider() != null ? o.getProvider().name() : "", 
                String.CASE_INSENSITIVE_ORDER);
        };

        if (sort.getDirection() == SearchSort.SortDirection.DESC) {
            comparator = comparator.reversed();
        }

        return comparator;
    }

    private List<OfferDto> applyPagination(List<OfferDto> offers, PageRequest page) {
        if (page == null) {
            page = PageRequest.first();
        }

        int offset = page.getOffset();
        int size = page.getValidatedSize();

        if (offset >= offers.size()) {
            return List.of();
        }

        return offers.subList(offset, Math.min(offset + size, offers.size()));
    }

    private boolean containsIgnoreCase(List<String> list, String value) {
        if (list == null || value == null) return false;
        return list.stream().anyMatch(s -> s.equalsIgnoreCase(value));
    }

    @lombok.Builder
    @lombok.Data
    public static class FilterResult {
        private List<OfferDto> offers;
        private int totalCount;
        private int page;
        private int size;
        private int totalPages;
        private boolean hasNext;
        private boolean hasPrevious;

        public static FilterResult empty() {
            return FilterResult.builder()
                .offers(List.of())
                .totalCount(0)
                .page(0)
                .size(0)
                .totalPages(0)
                .build();
        }
    }
}
