package com.github.areeves.axon_resource_booking.resources;

import java.time.Instant;
import java.util.UUID;

public record ReservationCancelledEvent(UUID resourceId, UUID reservationId, Instant lastModifiedAt) {
}