package com.github.areeves.axon_resource_booking.resources;

import java.util.UUID;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record UpdateResourceCommand(@TargetAggregateIdentifier UUID resourceId, String name, String description,
		String location) {
}