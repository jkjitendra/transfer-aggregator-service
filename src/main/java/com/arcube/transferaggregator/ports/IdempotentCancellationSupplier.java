package com.arcube.transferaggregator.ports;

/** Implement only after verifying repeated cancellation is safe with the supplier. */
public interface IdempotentCancellationSupplier extends TransferSupplier {}
