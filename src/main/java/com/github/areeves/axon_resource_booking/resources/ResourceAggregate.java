package com.github.areeves.axon_resource_booking.resources;

import java.time.Instant;
import java.util.UUID;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

@Aggregate
public class ResourceAggregate {

	@AggregateIdentifier
	private UUID resourceId;
	private String name;
	private String description;
	private int capacity;
	private String location;
	private ResourceStatus status;

	protected ResourceAggregate() {
	}

	@CommandHandler
	public ResourceAggregate(CreateResourceCommand command) {
		if (command.capacity() < 1) {
			throw new IllegalArgumentException("Resource capacity must be at least 1");
		}
		AggregateLifecycle.apply(new ResourceCreatedEvent(command.resourceId(), command.name(), command.description(),
				command.capacity(), command.location(), Instant.now()));
	}

	@CommandHandler
	public void handle(UpdateResourceCommand command) {
		AggregateLifecycle.apply(new ResourceUpdatedEvent(resourceId, command.name(), command.description(),
				command.location(), Instant.now()));
	}

	@CommandHandler
	public void handle(DeactivateResourceCommand command) {
		if (status == ResourceStatus.INACTIVE) {
			throw new IllegalStateException("Resource is already inactive");
		}
		AggregateLifecycle.apply(new ResourceDeactivatedEvent(resourceId, Instant.now()));
	}

	@CommandHandler
	public void handle(ReactivateResourceCommand command) {
		if (status == ResourceStatus.ACTIVE) {
			throw new IllegalStateException("Resource is already active");
		}
		AggregateLifecycle.apply(new ResourceReactivatedEvent(resourceId, Instant.now()));
	}

	@EventSourcingHandler
	public void on(ResourceCreatedEvent event) {
		resourceId = event.resourceId();
		name = event.name();
		description = event.description();
		capacity = event.capacity();
		location = event.location();
		status = ResourceStatus.ACTIVE;
	}

	@EventSourcingHandler
	public void on(ResourceUpdatedEvent event) {
		name = event.name();
		description = event.description();
		location = event.location();
	}

	@EventSourcingHandler
	public void on(ResourceDeactivatedEvent event) {
		status = ResourceStatus.INACTIVE;
	}

	@EventSourcingHandler
	public void on(ResourceReactivatedEvent event) {
		status = ResourceStatus.ACTIVE;
	}
}