package com.github.areeves.axon_resource_booking.resources;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "resources")
public class ResourceEntity {

	@Id
	private UUID resourceId;

	@Column(nullable = false, unique = true)
	private String name;

	private String description;

	@Column(nullable = false)
	private int capacity;

	@Column(nullable = false)
	private String location;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ResourceStatus status;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant lastModifiedAt;

	protected ResourceEntity() {
	}

	private ResourceEntity(UUID resourceId, String name, String description, int capacity, String location,
			ResourceStatus status, Instant createdAt) {
		this.resourceId = resourceId;
		this.name = name;
		this.description = description;
		this.capacity = capacity;
		this.location = location;
		this.status = status;
		this.createdAt = createdAt;
		this.lastModifiedAt = createdAt;
	}

	public static ResourceEntity from(ResourceCreatedEvent event) {
		return new ResourceEntity(event.resourceId(), event.name(), event.description(), event.capacity(), event.location(),
				ResourceStatus.ACTIVE, event.createdAt());
	}

	public UUID getResourceId() {
		return resourceId;
	}
}