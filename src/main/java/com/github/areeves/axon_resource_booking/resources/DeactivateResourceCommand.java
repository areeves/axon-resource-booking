package com.github.areeves.axon_resource_booking.resources;

import java.util.UUID;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record DeactivateResourceCommand(@TargetAggregateIdentifier UUID resourceId, UUID userId) {
}