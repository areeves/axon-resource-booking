package com.github.areeves.axon_resource_booking.resources;

import java.util.UUID;

public record CreateResourceCommand(UUID resourceId, String name, String description, int capacity, String location) {
}