package com.github.areeves.axon_resource_booking.resources;

import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

@Component
public class ResourceProjection {

	private final ResourceRepository resourceRepository;

	public ResourceProjection(ResourceRepository resourceRepository) {
		this.resourceRepository = resourceRepository;
	}

	@EventHandler
	public void on(ResourceCreatedEvent event) {
		resourceRepository.save(ResourceEntity.from(event));
	}
}