package com.github.areeves.axon_resource_booking.resources;

import java.time.Instant;
import java.util.UUID;

public class ResourceCreatedEvent {

	private UUID resourceId;
	private String name;
	private String description;
	private int capacity;
	private String location;
	private Instant createdAt;

	protected ResourceCreatedEvent() {
	}

	public ResourceCreatedEvent(UUID resourceId, String name, String description, int capacity, String location,
			Instant createdAt) {
		this.resourceId = resourceId;
		this.name = name;
		this.description = description;
		this.capacity = capacity;
		this.location = location;
		this.createdAt = createdAt;
	}

	public UUID resourceId() {
		return resourceId;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public int capacity() {
		return capacity;
	}

	public String location() {
		return location;
	}

	public Instant createdAt() {
		return createdAt;
	}
}