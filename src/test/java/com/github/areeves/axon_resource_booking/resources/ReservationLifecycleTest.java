package com.github.areeves.axon_resource_booking.resources;

import java.time.Instant;
import java.util.UUID;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReservationLifecycleTest {

	private AggregateTestFixture<ResourceAggregate> fixture;
	private UUID resourceId;
	private UUID firstReservationId;
	private UUID firstUserId;
	private Instant start;
	private Instant end;

	@BeforeEach
	void setUp() {
		fixture = new AggregateTestFixture<>(ResourceAggregate.class);
		resourceId = UUID.randomUUID();
		firstReservationId = UUID.randomUUID();
		firstUserId = UUID.randomUUID();
		start = Instant.now().plusSeconds(3600);
		end = start.plusSeconds(3600);
	}

	@Test
	void createsAndConfirmsReservation() {
		fixture.given(new ResourceCreatedEvent(resourceId, "Room A", null, 1, "Floor 1", Instant.now()))
				.when(new ReserveResourceCommand(resourceId, firstReservationId, firstUserId, start, end))
				.expectSuccessfulHandlerExecution();

		fixture.given(new ResourceCreatedEvent(resourceId, "Room A", null, 1, "Floor 1", Instant.now()),
				new ReservationCreatedEvent(resourceId, firstReservationId, firstUserId, start, end,
						ReservationStatus.PENDING, Instant.now()))
				.when(new ConfirmReservationCommand(resourceId, firstReservationId))
				.expectSuccessfulHandlerExecution();
	}

	@Test
	void rejectsOverlappingReservationWhenCapacityIsFull() {
		ResourceCreatedEvent resource = new ResourceCreatedEvent(resourceId, "Room A", null, 1, "Floor 1", Instant.now());
		ReservationCreatedEvent firstReservation = new ReservationCreatedEvent(resourceId, firstReservationId, firstUserId,
				start, end, ReservationStatus.PENDING, Instant.now());

		fixture.given(resource, firstReservation)
				.when(new ReserveResourceCommand(resourceId, UUID.randomUUID(), UUID.randomUUID(), start.plusSeconds(60), end))
				.expectException(IllegalStateException.class);
	}

	@Test
	void cancelledReservationFreesCapacityAndWrongUserCannotCancel() {
		ResourceCreatedEvent resource = new ResourceCreatedEvent(resourceId, "Room A", null, 1, "Floor 1", Instant.now());
		ReservationCreatedEvent firstReservation = new ReservationCreatedEvent(resourceId, firstReservationId, firstUserId,
				start, end, ReservationStatus.PENDING, Instant.now());

		fixture.given(resource, firstReservation)
				.when(new CancelReservationCommand(resourceId, firstReservationId, UUID.randomUUID(), false))
				.expectException(IllegalStateException.class);

		fixture.given(resource, firstReservation, new ReservationCancelledEvent(resourceId, firstReservationId, Instant.now()))
				.when(new ReserveResourceCommand(resourceId, UUID.randomUUID(), UUID.randomUUID(), start, end))
				.expectSuccessfulHandlerExecution();
	}

	@Test
	void rejectsInvalidTimeRangeAndInactiveResource() {
		fixture.given(new ResourceCreatedEvent(resourceId, "Room A", null, 1,
				"Floor 1", Instant.now())).when(new ReserveResourceCommand(resourceId, firstReservationId, firstUserId, end, start))
				.expectException(IllegalArgumentException.class);

		fixture.given(new ResourceCreatedEvent(resourceId, "Room A", null, 1,
				"Floor 1", Instant.now()), new ResourceDeactivatedEvent(resourceId, Instant.now()))
				.when(new ReserveResourceCommand(resourceId, firstReservationId, firstUserId, start, end))
				.expectException(IllegalStateException.class);
	}
}