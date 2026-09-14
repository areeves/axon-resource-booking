package com.github.areeves.axon_resource_booking.resources;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateResourceRequest(@NotBlank String name, String description, @Min(1) int capacity,
		@NotBlank String location) {
}