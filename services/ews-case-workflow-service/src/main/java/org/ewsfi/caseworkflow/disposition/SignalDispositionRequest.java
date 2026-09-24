package org.ewsfi.caseworkflow.disposition;

/**
 * Request body for {@link SignalDispositionController#recordDisposition}. {@code disposition} must
 * be {@code ACCEPTED} or {@code REJECTED} -- the two terminal human decisions this slice supports,
 * per the signal lifecycle in docs/architecture/01-architecture-blueprint.md Section 15.
 */
public record SignalDispositionRequest(String disposition, String reason) {
}
