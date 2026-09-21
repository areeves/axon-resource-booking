package com.github.areeves.axon_resource_booking.resources;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceRepository extends JpaRepository<ResourceEntity, UUID> {

	List<ResourceEntity> findByStatus(ResourceStatus status);

	boolean existsByName(String name);
}