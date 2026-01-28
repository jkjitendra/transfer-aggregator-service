package com.arcube.transferaggregator.service;

import com.arcube.transferaggregator.domain.Money;
import com.arcube.transferaggregator.domain.Provider;
import com.arcube.transferaggregator.domain.Vehicle;
import com.arcube.transferaggregator.domain.CancellationPolicy;
import com.arcube.transferaggregator.dto.PageRequest;
import com.arcube.transferaggregator.dto.SearchFilter;
import com.arcube.transferaggregator.dto.SearchResponse.OfferDto;
import com.arcube.transferaggregator.dto.SearchSort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class OfferFilterServiceTest {

    private final OfferFilterService service = new OfferFilterService();

    private static OfferDto offer(String id, String supplier, double price, String type, int passengers, int bags,
                                  double rating, String providerName, int duration, int distance, List<String> amenities) {
        return OfferDto.builder()
            .offerId(id)
            .supplierCode(supplier)
            .totalPrice(Money.of(price, "USD"))
            .vehicle(Vehicle.builder()
                .type(type)
                .vehicleClass(type.contains("Exec") ? "Business" : "Standard")
                .category(type.contains("SUV") ? "Private" : "Premium")
                .maxPassengers(passengers)
                .maxBags(bags)
                .build())
            .provider(Provider.builder()
                .name(providerName)
                .displayName(providerName)
                .rating(BigDecimal.valueOf(rating))
                .build())
            .estimatedDurationMinutes(duration)
            .distanceMeters(distance)
            .includedAmenities(amenities)
            .build();
    }

    private static OfferDto offerDetailed(String id, String supplier, Money price, Vehicle vehicle,
                                          Provider provider, CancellationPolicy cancellation, int duration,
                                          Integer distance, List<String> amenities) {
        return OfferDto.builder()
            .offerId(id)
            .supplierCode(supplier)
            .totalPrice(price)
            .vehicle(vehicle)
            .provider(provider)
            .cancellation(cancellation)
            .estimatedDurationMinutes(duration)
            .distanceMeters(distance)
            .includedAmenities(amenities)
            .build();
    }

    @Test
    void filtersAndSortsAndPaginates() {
        List<OfferDto> offers = List.of(
            offer("1", "STUB", 50.00, "Sedan", 3, 3, 4.5, "Alpha", 40, 15000, List.of("wifi")),
            offer("2", "SKYRIDE", 120.00, "SUV", 6, 6, 4.9, "SkyRide", 30, 18000, List.of("wifi", "meet_greet")),
            offer("3", "MOZIO", 80.00, "Executive Sedan", 3, 2, 4.7, "Carzen", 35, 14000, List.of("ride_tracking"))
        );

        SearchFilter filter = SearchFilter.builder()
            .minPrice(BigDecimal.valueOf(60))
            .vehicleTypes(List.of("suv", "executive sedan"))
            .minPassengers(3)
            .requiredAmenities(List.of("wifi"))
            .minProviderRating(BigDecimal.valueOf(4.7))
            .providerNames(List.of("skyride"))
            .build();

        SearchSort sort = SearchSort.builder()
            .field(SearchSort.SortField.PRICE)
            .direction(SearchSort.SortDirection.DESC)
            .build();

        OfferFilterService.FilterResult result = service.filterAndSort(
            offers, filter, sort, PageRequest.of(0, 10));

        assertThat(result.getOffers()).hasSize(1);
        assertThat(result.getOffers().get(0).getSupplierCode()).isEqualTo("SKYRIDE");
        assertThat(result.getTotalCount()).isEqualTo(1);
    }

    @Test
    void sortsByDurationAndPaginates() {
        List<OfferDto> offers = List.of(
            offer("1", "STUB", 50.00, "Sedan", 3, 3, 4.5, "Alpha", 40, 15000, List.of()),
            offer("2", "SKYRIDE", 120.00, "SUV", 6, 6, 4.9, "SkyRide", 30, 18000, List.of()),
            offer("3", "MOZIO", 80.00, "Executive Sedan", 3, 2, 4.7, "Carzen", 35, 14000, List.of())
        );

        OfferFilterService.FilterResult result = service.filterAndSort(
            offers,
            SearchFilter.builder().build(),
            SearchSort.builder().field(SearchSort.SortField.DURATION)
                .direction(SearchSort.SortDirection.ASC).build(),
            PageRequest.of(1, 1));

        assertThat(result.getOffers()).hasSize(1);
        assertThat(result.getOffers().get(0).getOfferId()).isEqualTo("3");
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.isHasPrevious()).isTrue();
    }

    @Test
    void returnsEmptyForOutOfRangePage() {
        List<OfferDto> offers = List.of(
            offer("1", "STUB", 50.00, "Sedan", 3, 3, 4.5, "Alpha", 40, 15000, List.of())
        );

        OfferFilterService.FilterResult result = service.filterAndSort(
            offers,
            null,
            null,
            PageRequest.of(5, 2));

        assertThat(result.getOffers()).isEmpty();
        assertThat(result.getTotalCount()).isEqualTo(1);
    }

    @Test
    void returnsEmptyWhenOffersNull() {
        OfferFilterService.FilterResult result = service.filterAndSort(null, null, null, PageRequest.first());
        assertThat(result.getOffers()).isEmpty();
        assertThat(result.getTotalCount()).isZero();
    }

    @Test
    void appliesAllFiltersIncludingFreeCancellationAndSupplier() {
        CancellationPolicy refundable = CancellationPolicy.builder()
            .tiers(List.of(new CancellationPolicy.CancellationTier(24, 100)))
            .build();

        OfferDto matching = offerDetailed(
            "match",
            "S1",
            Money.of(100, "USD"),
            Vehicle.builder().type("SUV").vehicleClass("Business").category("Private").maxPassengers(4).maxBags(3).build(),
            Provider.builder().name("BestRide").rating(BigDecimal.valueOf(4.8)).build(),
            refundable,
            30,
            12000,
            List.of("wifi", "baby_seats")
        );

        OfferDto wrongProviderNameNull = offerDetailed(
            "other",
            "S2",
            Money.of(100, "USD"),
            Vehicle.builder().type(null).vehicleClass("Business").category("Private").maxPassengers(4).maxBags(3).build(),
            Provider.builder().name(null).rating(BigDecimal.valueOf(4.8)).build(),
            refundable,
            30,
            12000,
            List.of("wifi")
        );

        SearchFilter filter = SearchFilter.builder()
            .minPrice(BigDecimal.valueOf(90))
            .maxPrice(BigDecimal.valueOf(110))
            .vehicleTypes(List.of("suv"))
            .vehicleClasses(List.of("business"))
            .vehicleCategories(List.of("private"))
            .minPassengers(3)
            .minBags(2)
            .requiredAmenities(List.of("wifi"))
            .freeCancellationOnly(true)
            .minProviderRating(BigDecimal.valueOf(4.5))
            .providerNames(List.of("bestride"))
            .maxDurationMinutes(45)
            .supplierCodes(List.of("s1"))
            .build();

        OfferFilterService.FilterResult result = service.filterAndSort(
            List.of(matching, wrongProviderNameNull),
            filter,
            SearchSort.byPrice(),
            PageRequest.of(0, 10));

        assertThat(result.getOffers()).hasSize(1);
        assertThat(result.getOffers().get(0).getOfferId()).isEqualTo("match");
    }

    @Test
    void sortsByAllSupportedFields() {
        OfferDto withNulls = offerDetailed(
            "a",
            "S1",
            null,
            null,
            null,
            null,
            60,
            null,
            List.of()
        );
        OfferDto withValues = offerDetailed(
            "b",
            "S2",
            Money.of(50, "USD"),
            Vehicle.builder().type("Sedan").maxPassengers(3).build(),
            Provider.builder().name("Zeta").rating(BigDecimal.valueOf(4.9)).build(),
            null,
            30,
            1000,
            List.of()
        );

        List<OfferDto> offers = List.of(withNulls, withValues);

        assertThat(service.filterAndSort(offers, null,
            SearchSort.builder().field(SearchSort.SortField.PRICE).direction(SearchSort.SortDirection.ASC).build(),
            PageRequest.of(0, 10)).getOffers().get(0).getOfferId()).isEqualTo("a");

        assertThat(service.filterAndSort(offers, null,
            SearchSort.builder().field(SearchSort.SortField.RATING).direction(SearchSort.SortDirection.ASC).build(),
            PageRequest.of(0, 10)).getOffers().get(0).getOfferId()).isEqualTo("a");

        assertThat(service.filterAndSort(offers, null,
            SearchSort.builder().field(SearchSort.SortField.DISTANCE).direction(SearchSort.SortDirection.ASC).build(),
            PageRequest.of(0, 10)).getOffers().get(1).getOfferId()).isEqualTo("a");

        assertThat(service.filterAndSort(offers, null,
            SearchSort.builder().field(SearchSort.SortField.PASSENGERS).direction(SearchSort.SortDirection.ASC).build(),
            PageRequest.of(0, 10)).getOffers().get(0).getOfferId()).isEqualTo("a");

        assertThat(service.filterAndSort(offers, null,
            SearchSort.builder().field(SearchSort.SortField.PROVIDER_NAME).direction(SearchSort.SortDirection.ASC).build(),
            PageRequest.of(0, 10)).getOffers().get(0).getOfferId()).isEqualTo("a");
    }

    @Test
    void applyPaginationDefaultsToFirstPageWhenNull() throws Exception {
        Method method = OfferFilterService.class.getDeclaredMethod("applyPagination", List.class, PageRequest.class);
        method.setAccessible(true);

        List<OfferDto> offers = List.of(
            offer("1", "STUB", 50.00, "Sedan", 3, 3, 4.5, "Alpha", 40, 15000, List.of())
        );

        @SuppressWarnings("unchecked")
        List<OfferDto> paged = (List<OfferDto>) method.invoke(service, offers, null);
        assertThat(paged).hasSize(1);
    }

    @Test
    void containsIgnoreCaseHandlesNullListAndNullValue() throws Exception {
        Method method = OfferFilterService.class.getDeclaredMethod("containsIgnoreCase", List.class, String.class);
        method.setAccessible(true);

        boolean listNull = (boolean) method.invoke(service, null, "x");
        assertThat(listNull).isFalse();

        boolean valueNull = (boolean) method.invoke(service, List.of("a"), null);
        assertThat(valueNull).isFalse();
    }

    @Test
    void filtersHandleNullsAndEmptyListsAndBranchOutcomes() {
        OfferDto priceNull = OfferDto.builder()
            .offerId("p0")
            .totalPrice(null)
            .vehicle(null)
            .provider(null)
            .includedAmenities(null)
            .cancellation(null)
            .estimatedDurationMinutes(50)
            .supplierCode("S1")
            .build();

        OfferDto low = offerDetailed(
            "low",
            "S1",
            Money.of(10, "USD"),
            Vehicle.builder().maxPassengers(2).maxBags(1).build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.0)).build(),
            CancellationPolicy.builder().tiers(List.of()).build(),
            30,
            1000,
            List.of("wifi")
        );

        OfferDto high = offerDetailed(
            "high",
            "S2",
            Money.of(200, "USD"),
            Vehicle.builder().maxPassengers(5).maxBags(4).build(),
            Provider.builder().name("Beta").rating(BigDecimal.valueOf(4.8)).build(),
            CancellationPolicy.builder().tiers(List.of(new CancellationPolicy.CancellationTier(24, 100))).build(),
            90,
            2000,
            List.of("wifi", "baby_seats")
        );

        SearchFilter filter = SearchFilter.builder()
            .minPrice(BigDecimal.valueOf(50))     // minPrice branch true, excludes low/null
            .maxPrice(BigDecimal.valueOf(150))    // maxPrice branch true, excludes high
            .vehicleTypes(null)                   // list null branch false
            .vehicleClasses(List.of())            // empty list branch false
            .vehicleCategories(List.of())         // empty list branch false
            .minPassengers(4)                     // compare false for low
            .minBags(2)                           // compare false for low
            .requiredAmenities(List.of("wifi"))   // includedAmenities null excludes priceNull
            .freeCancellationOnly(true)           // cancellation null excludes priceNull
            .minProviderRating(BigDecimal.valueOf(4.5)) // provider null/rating null false
            .providerNames(List.of())             // empty list branch false
            .maxDurationMinutes(40)               // duration compare false for high
            .supplierCodes(List.of())             // empty list branch false
            .build();

        OfferFilterService.FilterResult result = service.filterAndSort(
            List.of(priceNull, low, high),
            filter,
            SearchSort.byPrice(),
            PageRequest.of(0, 10));

        assertThat(result.getOffers()).isEmpty();
    }

    @Test
    void filterUsesValueNullInContainsIgnoreCase() {
        OfferDto withVehicleTypeNull = OfferDto.builder()
            .offerId("v0")
            .vehicle(Vehicle.builder().type(null).build())
            .supplierCode("S1")
            .totalPrice(Money.of(10, "USD"))
            .estimatedDurationMinutes(10)
            .build();

        SearchFilter filter = SearchFilter.builder()
            .vehicleTypes(List.of("sedan"))
            .build();

        OfferFilterService.FilterResult result = service.filterAndSort(
            List.of(withVehicleTypeNull),
            filter,
            SearchSort.byPrice(),
            PageRequest.of(0, 10));

        assertThat(result.getOffers()).isEmpty();
    }

    @Test
    void filtersByMaxPriceHandlesNullAndOverLimit() {
        OfferDto nullPrice = offerDetailed(
            "nullPrice",
            "S1",
            null,
            Vehicle.builder().type("SUV").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        OfferDto ok = offerDetailed(
            "ok",
            "S1",
            Money.of(80, "USD"),
            Vehicle.builder().type("SUV").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        OfferDto over = offerDetailed(
            "over",
            "S1",
            Money.of(120, "USD"),
            Vehicle.builder().type("SUV").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        SearchFilter filter = SearchFilter.builder()
            .maxPrice(BigDecimal.valueOf(100))
            .build();

        OfferFilterService.FilterResult result = service.filterAndSort(
            List.of(nullPrice, ok, over),
            filter,
            SearchSort.byPrice(),
            PageRequest.of(0, 10));

        assertThat(result.getOffers()).extracting(OfferDto::getOfferId)
            .containsExactly("ok");
    }

    @Test
    void filtersByVehicleTypesHandlesNullVehicleAndMismatch() {
        OfferDto nullVehicle = offerDetailed(
            "nullVehicle",
            "S1",
            Money.of(30, "USD"),
            null,
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        OfferDto mismatch = offerDetailed(
            "mismatch",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("Sedan").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        OfferDto match = offerDetailed(
            "match",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("SUV").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        SearchFilter filter = SearchFilter.builder()
            .vehicleTypes(List.of("suv"))
            .build();

        OfferFilterService.FilterResult result = service.filterAndSort(
            List.of(nullVehicle, mismatch, match),
            filter,
            SearchSort.byPrice(),
            PageRequest.of(0, 10));

        assertThat(result.getOffers()).extracting(OfferDto::getOfferId)
            .containsExactly("match");
    }

    @Test
    void filtersSkipEmptyVehicleTypesAndAmenitiesLists() {
        OfferDto low = offerDetailed(
            "low",
            "S1",
            Money.of(10, "USD"),
            Vehicle.builder().type("SUV").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of("wifi")
        );

        OfferDto high = offerDetailed(
            "high",
            "S1",
            Money.of(60, "USD"),
            Vehicle.builder().type("SUV").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of("wifi")
        );

        SearchFilter filter = SearchFilter.builder()
            .minPrice(BigDecimal.valueOf(50))
            .vehicleTypes(List.of())
            .requiredAmenities(List.of())
            .build();

        OfferFilterService.FilterResult result = service.filterAndSort(
            List.of(low, high),
            filter,
            SearchSort.byPrice(),
            PageRequest.of(0, 10));

        assertThat(result.getOffers()).extracting(OfferDto::getOfferId)
            .containsExactly("high");
    }

    @Test
    void filtersByVehicleClassesHandlesNullVehicleAndMismatch() {
        OfferDto nullVehicle = offerDetailed(
            "nullVehicle",
            "S1",
            Money.of(30, "USD"),
            null,
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        OfferDto mismatch = offerDetailed(
            "mismatch",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("SUV").vehicleClass("Standard").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        OfferDto match = offerDetailed(
            "match",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("SUV").vehicleClass("Business").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        SearchFilter filter = SearchFilter.builder()
            .vehicleClasses(List.of("business"))
            .build();

        OfferFilterService.FilterResult result = service.filterAndSort(
            List.of(nullVehicle, mismatch, match),
            filter,
            SearchSort.byPrice(),
            PageRequest.of(0, 10));

        assertThat(result.getOffers()).extracting(OfferDto::getOfferId)
            .containsExactly("match");
    }

    @Test
    void filtersByVehicleCategoriesHandlesNullVehicleAndMismatch() {
        OfferDto nullVehicle = offerDetailed(
            "nullVehicle",
            "S1",
            Money.of(30, "USD"),
            null,
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        OfferDto mismatch = offerDetailed(
            "mismatch",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("SUV").category("Shared").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        OfferDto match = offerDetailed(
            "match",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("SUV").category("Private").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        SearchFilter filter = SearchFilter.builder()
            .vehicleCategories(List.of("private"))
            .build();

        OfferFilterService.FilterResult result = service.filterAndSort(
            List.of(nullVehicle, mismatch, match),
            filter,
            SearchSort.byPrice(),
            PageRequest.of(0, 10));

        assertThat(result.getOffers()).extracting(OfferDto::getOfferId)
            .containsExactly("match");
    }

    @Test
    void filtersByMinPassengersHandlesNullVehicleAndBelow() {
        OfferDto nullVehicle = offerDetailed(
            "nullVehicle",
            "S1",
            Money.of(30, "USD"),
            null,
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        OfferDto below = offerDetailed(
            "below",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().maxPassengers(3).build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        OfferDto match = offerDetailed(
            "match",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().maxPassengers(5).build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        SearchFilter filter = SearchFilter.builder()
            .minPassengers(4)
            .build();

        OfferFilterService.FilterResult result = service.filterAndSort(
            List.of(nullVehicle, below, match),
            filter,
            SearchSort.byPrice(),
            PageRequest.of(0, 10));

        assertThat(result.getOffers()).extracting(OfferDto::getOfferId)
            .containsExactly("match");
    }

    @Test
    void filtersByMinBagsHandlesNullVehicleAndBelow() {
        OfferDto nullVehicle = offerDetailed(
            "nullVehicle",
            "S1",
            Money.of(30, "USD"),
            null,
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        OfferDto below = offerDetailed(
            "below",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().maxBags(1).build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        OfferDto match = offerDetailed(
            "match",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().maxBags(3).build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        SearchFilter filter = SearchFilter.builder()
            .minBags(2)
            .build();

        OfferFilterService.FilterResult result = service.filterAndSort(
            List.of(nullVehicle, below, match),
            filter,
            SearchSort.byPrice(),
            PageRequest.of(0, 10));

        assertThat(result.getOffers()).extracting(OfferDto::getOfferId)
            .containsExactly("match");
    }

    @Test
    void filtersByRequiredAmenitiesHandlesNullAndMissing() {
        OfferDto nullAmenities = offerDetailed(
            "nullAmenities",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("SUV").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            null
        );

        OfferDto missing = offerDetailed(
            "missing",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("SUV").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of("wifi")
        );

        OfferDto match = offerDetailed(
            "match",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("SUV").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of("wifi", "meet_greet")
        );

        SearchFilter filter = SearchFilter.builder()
            .requiredAmenities(List.of("wifi", "meet_greet"))
            .build();

        OfferFilterService.FilterResult result = service.filterAndSort(
            List.of(nullAmenities, missing, match),
            filter,
            SearchSort.byPrice(),
            PageRequest.of(0, 10));

        assertThat(result.getOffers()).extracting(OfferDto::getOfferId)
            .containsExactly("match");
    }

    @Test
    void filtersByFreeCancellationOnlyHandlesNullAndNonRefundable() {
        OfferDto nullCancellation = offerDetailed(
            "nullCancellation",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("SUV").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        OfferDto notRefundable = offerDetailed(
            "notRefundable",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("SUV").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            CancellationPolicy.builder()
                .tiers(List.of(new CancellationPolicy.CancellationTier(24, 50)))
                .build(),
            30,
            1000,
            List.of()
        );

        OfferDto refundable = offerDetailed(
            "refundable",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("SUV").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            CancellationPolicy.builder()
                .tiers(List.of(new CancellationPolicy.CancellationTier(24, 100)))
                .build(),
            30,
            1000,
            List.of()
        );

        SearchFilter filter = SearchFilter.builder()
            .freeCancellationOnly(true)
            .build();

        OfferFilterService.FilterResult result = service.filterAndSort(
            List.of(nullCancellation, notRefundable, refundable),
            filter,
            SearchSort.byPrice(),
            PageRequest.of(0, 10));

        assertThat(result.getOffers()).extracting(OfferDto::getOfferId)
            .containsExactly("refundable");
    }

    @Test
    void filtersByMinProviderRatingHandlesNullsAndBelow() {
        OfferDto nullProvider = offerDetailed(
            "nullProvider",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("SUV").build(),
            null,
            null,
            30,
            1000,
            List.of()
        );

        OfferDto nullRating = offerDetailed(
            "nullRating",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("SUV").build(),
            Provider.builder().name("Alpha").rating(null).build(),
            null,
            30,
            1000,
            List.of()
        );

        OfferDto below = offerDetailed(
            "below",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("SUV").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.0)).build(),
            null,
            30,
            1000,
            List.of()
        );

        OfferDto match = offerDetailed(
            "match",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("SUV").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.6)).build(),
            null,
            30,
            1000,
            List.of()
        );

        SearchFilter filter = SearchFilter.builder()
            .minProviderRating(BigDecimal.valueOf(4.5))
            .build();

        OfferFilterService.FilterResult result = service.filterAndSort(
            List.of(nullProvider, nullRating, below, match),
            filter,
            SearchSort.byPrice(),
            PageRequest.of(0, 10));

        assertThat(result.getOffers()).extracting(OfferDto::getOfferId)
            .containsExactly("match");
    }

    @Test
    void filtersByProviderNamesHandlesNullProviderAndMismatch() {
        OfferDto nullProvider = offerDetailed(
            "nullProvider",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("SUV").build(),
            null,
            null,
            30,
            1000,
            List.of()
        );

        OfferDto mismatch = offerDetailed(
            "mismatch",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("SUV").build(),
            Provider.builder().name("Beta").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        OfferDto match = offerDetailed(
            "match",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("SUV").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        SearchFilter filter = SearchFilter.builder()
            .providerNames(List.of("alpha"))
            .build();

        OfferFilterService.FilterResult result = service.filterAndSort(
            List.of(nullProvider, mismatch, match),
            filter,
            SearchSort.byPrice(),
            PageRequest.of(0, 10));

        assertThat(result.getOffers()).extracting(OfferDto::getOfferId)
            .containsExactly("match");
    }

    @Test
    void filtersByMaxDurationHandlesAboveLimit() {
        OfferDto ok = offerDetailed(
            "ok",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("SUV").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            30,
            1000,
            List.of()
        );

        OfferDto over = offerDetailed(
            "over",
            "S1",
            Money.of(30, "USD"),
            Vehicle.builder().type("SUV").build(),
            Provider.builder().name("Alpha").rating(BigDecimal.valueOf(4.5)).build(),
            null,
            60,
            1000,
            List.of()
        );

        SearchFilter filter = SearchFilter.builder()
            .maxDurationMinutes(40)
            .build();

        OfferFilterService.FilterResult result = service.filterAndSort(
            List.of(ok, over),
            filter,
            SearchSort.byPrice(),
            PageRequest.of(0, 10));

        assertThat(result.getOffers()).extracting(OfferDto::getOfferId)
            .containsExactly("ok");
    }

    @Test
    void sortByRatingHandlesNullRating() {
        OfferDto withNullRating = OfferDto.builder()
            .offerId("r0")
            .provider(Provider.builder().name("X").rating(null).build())
            .totalPrice(Money.of(10, "USD"))
            .estimatedDurationMinutes(10)
            .build();

        OfferDto withRating = OfferDto.builder()
            .offerId("r1")
            .provider(Provider.builder().name("Y").rating(BigDecimal.valueOf(4.5)).build())
            .totalPrice(Money.of(20, "USD"))
            .estimatedDurationMinutes(10)
            .build();

        OfferFilterService.FilterResult result = service.filterAndSort(
            List.of(withRating, withNullRating),
            null,
            SearchSort.builder().field(SearchSort.SortField.RATING).direction(SearchSort.SortDirection.ASC).build(),
            PageRequest.of(0, 10));

        assertThat(result.getOffers().get(0).getOfferId()).isEqualTo("r0");
    }

    @Test
    void filterResultEmptyHasZeroes() {
        OfferFilterService.FilterResult result = OfferFilterService.FilterResult.empty();
        assertThat(result.getOffers()).isEmpty();
        assertThat(result.getTotalCount()).isZero();
        assertThat(result.getTotalPages()).isZero();
        assertThat(result.isHasNext()).isFalse();
        assertThat(result.isHasPrevious()).isFalse();
    }
}
