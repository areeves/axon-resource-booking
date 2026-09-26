package com.github.areeves.axon_resource_booking.resources;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.sample-data", name = "enabled", havingValue = "true")
public class SampleDataInitializer implements ApplicationRunner {
	private static final UUID SEED_USER_ID = UUID.fromString("d7fc0682-163a-4f88-83e9-fb937ba4d001");
	private static final List<SampleResource> RESOURCES = List.of(
			new SampleResource(UUID.fromString("a8bd0a34-27bd-4d52-9ac1-43c8f0a10001"), "Atlas Conference Room",
					"Large meeting room with video conferencing", 8, "North Wing", List.of(
							new SampleReservation(UUID.fromString("b7bd0a34-27bd-4d52-9ac1-43c8f0a10001"), 1),
							new SampleReservation(UUID.fromString("b7bd0a34-27bd-4d52-9ac1-43c8f0a10002"), 2))),
			new SampleResource(UUID.fromString("a8bd0a34-27bd-4d52-9ac1-43c8f0a10002"), "Orion Project Room",
					"Project room with whiteboards", 4, "South Wing", List.of(
							new SampleReservation(UUID.fromString("b7bd0a34-27bd-4d52-9ac1-43c8f0a10003"), 1))));

	private final CommandGateway commandGateway;
	private final EventStore eventStore;

	public SampleDataInitializer(CommandGateway commandGateway, EventStore eventStore) {
		this.commandGateway = commandGateway;
		this.eventStore = eventStore;
	}

	@Override
	public void run(ApplicationArguments args) {
		for (SampleResource resource : RESOURCES) {
			seed(resource);
		}
	}

	private void seed(SampleResource resource) {
		if (!hasEvent(resource.resourceId(), payload -> payload instanceof ResourceCreatedEvent event
				&& event.resourceId().equals(resource.resourceId()))) {
			commandGateway.sendAndWait(new CreateResourceCommand(resource.resourceId(), SEED_USER_ID, resource.name(),
					resource.description(), resource.capacity(), resource.location()));
		}

		for (SampleReservation reservation : resource.reservations()) {
			if (hasEvent(resource.resourceId(), payload -> payload instanceof ReservationCreatedEvent event
					&& event.reservationId().equals(reservation.reservationId()))) {
				continue;
			}

			Instant start = Instant.now().plus(Duration.ofDays(reservation.daysFromNow())).truncatedTo(java.time.temporal.ChronoUnit.HOURS);
			commandGateway.sendAndWait(new ReserveResourceCommand(resource.resourceId(), reservation.reservationId(),
					SEED_USER_ID, start, start.plus(Duration.ofHours(1))));
		}
	}

	private boolean hasEvent(UUID aggregateId, Predicate<Object> predicate) {
		return eventStore.readEvents(aggregateId.toString()).asStream()
				.map(message -> message.getPayload())
				.anyMatch(predicate);
	}

	private record SampleResource(UUID resourceId, String name, String description, int capacity, String location,
			List<SampleReservation> reservations) {
	}

	private record SampleReservation(UUID reservationId, int daysFromNow) {
	}
}