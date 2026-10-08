package com.github.areeves.axon_resource_booking.resources;

import java.util.ArrayList;
import java.util.UUID;
import java.util.function.UnaryOperator;

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
		if (resourceRepository.existsById(event.resourceId())) {
			return;
		}
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

	@EventHandler
	public void on(ResourceAvailabilityRulesUpdatedEvent event) {
		updateAvailability(event.resourceId(), ignored -> new ResourceAvailabilityRules(
				event.timezone(), event.weeklyPattern(), event.blackouts(), event.extras()));
	}

	@EventHandler
	public void on(ResourceAvailabilityClearedEvent event) {
		updateAvailability(event.resourceId(), ignored -> ResourceAvailabilityRules.empty());
	}

	@EventHandler
	public void on(ResourceBlackoutAddedEvent event) {
		updateAvailability(event.resourceId(), rules -> {
			var blackouts = new ArrayList<>(rules.blackouts());
			blackouts.add(new AvailabilityWindow(event.windowId(), event.start(), event.end(), AvailabilityWindowType.BLACKOUT,
					event.reason(), event.createdBy(), event.createdAt()));
			return new ResourceAvailabilityRules(rules.timezone(), rules.weeklyPattern(), blackouts, rules.extras());
		});
	}

	@EventHandler
	public void on(ResourceExtraAvailabilityAddedEvent event) {
		updateAvailability(event.resourceId(), rules -> {
			var extras = new ArrayList<>(rules.extras());
			extras.add(new AvailabilityWindow(event.windowId(), event.start(), event.end(), AvailabilityWindowType.EXTRA,
					event.reason(), event.createdBy(), event.createdAt()));
			return new ResourceAvailabilityRules(rules.timezone(), rules.weeklyPattern(), rules.blackouts(), extras);
		});
	}

	@EventHandler
	public void on(ResourceBlackoutRemovedEvent event) {
		updateAvailability(event.resourceId(), rules -> new ResourceAvailabilityRules(rules.timezone(), rules.weeklyPattern(),
				rules.blackouts().stream().filter(window -> !window.windowId().equals(event.windowId())).toList(),
				rules.extras()));
	}

	@EventHandler
	public void on(ResourceExtraAvailabilityRemovedEvent event) {
		updateAvailability(event.resourceId(), rules -> new ResourceAvailabilityRules(rules.timezone(), rules.weeklyPattern(),
				rules.blackouts(),
				rules.extras().stream().filter(window -> !window.windowId().equals(event.windowId())).toList()));
	}

	private void updateAvailability(UUID resourceId, UnaryOperator<ResourceAvailabilityRules> update) {
		resourceRepository.findById(resourceId).ifPresent(resource -> {
			resource.setAvailabilityRules(update.apply(resource.getAvailabilityRules()));
			resourceRepository.save(resource);
		});
	}

	private void recordEvent(String eventType, java.util.UUID resourceId) {
		Counter.builder("axon.events.total").tag("event", eventType).register(meterRegistry).increment();
		log.info("domain_event event_type={} resource_id={}", eventType, resourceId);
	}
}