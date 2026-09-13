package com.github.areeves.axon_resource_booking.resources;

import java.time.Instant;
import java.util.UUID;

public record ResourceUpdatedEvent(UUID resourceId, String name, String description, String location,
		Instant lastModifiedAt) {
}