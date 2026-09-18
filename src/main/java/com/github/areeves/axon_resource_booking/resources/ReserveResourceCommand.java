package com.github.areeves.axon_resource_booking.resources;

import java.time.Instant;
import java.util.UUID;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record ReserveResourceCommand(@TargetAggregateIdentifier UUID resourceId, UUID reservationId, UUID userId,
		Instant start, Instant end) {
}