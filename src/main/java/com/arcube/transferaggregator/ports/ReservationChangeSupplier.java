package com.arcube.transferaggregator.ports;

import com.arcube.transferaggregator.domain.ReservationChangeCommitCommand;
import com.arcube.transferaggregator.domain.ReservationChangeSearchCommand;

import java.time.Duration;

/**
 * Extended interface for suppliers that support reservation changes.
 * Not all suppliers support this - check with supportsReservationChanges().
 */
public interface ReservationChangeSupplier {

    /**
     * Whether this supplier supports in-place reservation changes.
     * If false, changes must be done via cancel + re-book.
     */
    boolean supportsReservationChanges();

    /**
     * Step 1: Search for change options.
     * Returns offers from the same provider as the original reservation.
     * Mozio: POST /v2/search/reservation_changes/
     */
    SupplierReservationChangeSearchResult searchForChange(
        ReservationChangeSearchCommand command, Duration timeout);

    /**
     * Step 2: Commit the reservation change.
     * Cancels old reservation and creates new one atomically.
     * Mozio: POST /v2/reservations/changes/
     */
    SupplierReservationChangeResult changeReservation(
        ReservationChangeCommitCommand command, Duration timeout);
}
