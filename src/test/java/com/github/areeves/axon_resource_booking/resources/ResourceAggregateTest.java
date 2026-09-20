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
		UUID userId = UUID.randomUUID();

		fixture.givenNoPriorActivity()
				.when(new CreateResourceCommand(resourceId, userId, "Room A", "Training room", 4, "Floor 1"))
				.expectSuccessfulHandlerExecution();
	}

	@Test
	void rejectsNonPositiveCapacity() {
		UUID userId = UUID.randomUUID();
		fixture.givenNoPriorActivity()
				.when(new CreateResourceCommand(UUID.randomUUID(), userId, "Room A", null, 0, "Floor 1"))
				.expectException(IllegalArgumentException.class);
	}

	@Test
	void deactivatesAndReactivatesResource() {
		UUID resourceId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		ResourceCreatedEvent created = new ResourceCreatedEvent(resourceId, "Room A", null, 1, "Floor 1", null);

		fixture.given(created)
				.when(new DeactivateResourceCommand(resourceId, userId))
				.expectSuccessfulHandlerExecution();

		fixture.given(created, new ResourceDeactivatedEvent(resourceId, null))
				.when(new ReactivateResourceCommand(resourceId, userId))
				.expectSuccessfulHandlerExecution();
	}
}