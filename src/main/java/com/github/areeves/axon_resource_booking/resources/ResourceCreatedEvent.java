package com.github.areeves.axon_resource_booking.resources;

import java.time.Instant;
import java.util.UUID;

public record ResourceCreatedEvent(UUID resourceId, String name, String description, int capacity, String location,
		Instant createdAt) {
}