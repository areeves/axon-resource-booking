package com.github.areeves.axon_resource_booking.resources;

import java.util.UUID;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record CancelReservationCommand(@TargetAggregateIdentifier UUID resourceId, UUID reservationId, UUID userId,
		boolean admin) {
}