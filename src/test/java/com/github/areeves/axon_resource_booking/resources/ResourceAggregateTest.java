package com.github.areeves.axon_resource_booking.resources;

import java.util.UUID;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResourceAggregateTest {

	private AggregateTestFixture<ResourceAggregate> fixture;

	@BeforeEach
	void setUp() {
		fixture = new AggregateTestFixture<>(ResourceAggregate.class);
	}

	@Test
	void createsActiveResource() {
		UUID resourceId = UUID.randomUUID();

		fixture.givenNoPriorActivity()
				.when(new CreateResourceCommand(resourceId, "Room A", "Training room", 4, "Floor 1"))
				.expectSuccessfulHandlerExecution();
	}

	@Test
	void rejectsNonPositiveCapacity() {
		fixture.givenNoPriorActivity()
				.when(new CreateResourceCommand(UUID.randomUUID(), "Room A", null, 0, "Floor 1"))
				.expectException(IllegalArgumentException.class);
	}

	@Test
	void deactivatesAndReactivatesResource() {
		UUID resourceId = UUID.randomUUID();
		ResourceCreatedEvent created = new ResourceCreatedEvent(resourceId, "Room A", null, 1, "Floor 1", null);

		fixture.given(created)
				.when(new DeactivateResourceCommand(resourceId))
				.expectSuccessfulHandlerExecution();

		fixture.given(created, new ResourceDeactivatedEvent(resourceId, null))
				.when(new ReactivateResourceCommand(resourceId))
				.expectSuccessfulHandlerExecution();
	}
}