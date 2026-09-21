package com.github.areeves.axon_resource_booking.resources;

import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.axonframework.config.ProcessingGroup;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ProcessingGroup("resource-projections")
public class ResourceProjection {
	private static final Logger log = LoggerFactory.getLogger(ResourceProjection.class);

	private final ResourceRepository resourceRepository;
	private final MeterRegistry meterRegistry;

	public ResourceProjection(ResourceRepository resourceRepository, MeterRegistry meterRegistry) {
		this.resourceRepository = resourceRepository;
		this.meterRegistry = meterRegistry;
	}

	@EventHandler
	public void on(ResourceCreatedEvent event) {
		recordEvent("resource_created", event.resourceId());
		resourceRepository.save(ResourceEntity.from(event));
	}

	@EventHandler
	public void on(ResourceUpdatedEvent event) {
		recordEvent("resource_updated", event.resourceId());
		resourceRepository.findById(event.resourceId()).ifPresent(resource -> {
			resource.apply(event);
			resourceRepository.save(resource);
		});
	}

	@EventHandler
	public void on(ResourceDeactivatedEvent event) {
		recordEvent("resource_deactivated", event.resourceId());
		resourceRepository.findById(event.resourceId()).ifPresent(resource -> {
			resource.apply(event);
			resourceRepository.save(resource);
		});
	}

	@EventHandler
	public void on(ResourceReactivatedEvent event) {
		recordEvent("resource_reactivated", event.resourceId());
		resourceRepository.findById(event.resourceId()).ifPresent(resource -> {
			resource.apply(event);
			resourceRepository.save(resource);
		});
	}

	private void recordEvent(String eventType, java.util.UUID resourceId) {
		Counter.builder("axon.events.total").tag("event", eventType).register(meterRegistry).increment();
		log.info("domain_event event_type={} resource_id={}", eventType, resourceId);
	}
}