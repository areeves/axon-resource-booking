package com.github.areeves.axon_resource_booking.resources;

import java.time.Instant;
import java.util.UUID;

public record ResourceReactivatedEvent(UUID resourceId, Instant lastModifiedAt) {
}