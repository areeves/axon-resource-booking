package com.github.areeves.axon_resource_booking.resources;

import java.time.Instant;
import java.util.UUID;

public record ReservationCreatedEvent(UUID resourceId, UUID reservationId, UUID userId, Instant start, Instant end,
		ReservationStatus status, Instant createdAt) {
}