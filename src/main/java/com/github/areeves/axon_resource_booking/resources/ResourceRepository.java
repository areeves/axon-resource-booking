package com.github.areeves.axon_resource_booking.resources;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceRepository extends JpaRepository<ResourceEntity, UUID> {
}