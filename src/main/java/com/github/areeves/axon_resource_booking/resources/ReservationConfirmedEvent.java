package com.github.areeves.axon_resource_booking.resources;

import java.time.Instant;
import java.util.UUID;

public record ReservationConfirmedEvent(UUID resourceId, UUID reservationId, Instant lastModifiedAt) {
}