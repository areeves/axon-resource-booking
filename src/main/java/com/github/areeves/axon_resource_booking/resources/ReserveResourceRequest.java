package com.github.areeves.axon_resource_booking.resources;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ReserveResourceRequest(@NotNull UUID userId, @NotNull Instant start, @NotNull Instant end) {
}