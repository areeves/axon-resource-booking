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

	@EventHandler
	public void on(ResourceUpdatedEvent event) {
		resourceRepository.findById(event.resourceId()).ifPresent(resource -> {
			resource.apply(event);
			resourceRepository.save(resource);
		});
	}

	@EventHandler
	public void on(ResourceDeactivatedEvent event) {
		resourceRepository.findById(event.resourceId()).ifPresent(resource -> {
			resource.apply(event);
			resourceRepository.save(resource);
		});
	}

	@EventHandler
	public void on(ResourceReactivatedEvent event) {
		resourceRepository.findById(event.resourceId()).ifPresent(resource -> {
			resource.apply(event);
			resourceRepository.save(resource);
		});
	}
}