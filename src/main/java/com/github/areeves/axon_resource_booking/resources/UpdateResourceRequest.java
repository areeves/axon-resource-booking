package com.github.areeves.axon_resource_booking.resources;

import jakarta.validation.constraints.NotBlank;

public record UpdateResourceRequest(@NotBlank String name, String description, @NotBlank String location) {
}