package com.github.areeves.axon_resource_booking.resources;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Objects;

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
	private final List<Reservation> reservations = new ArrayList<>();

	protected ResourceAggregate() {
	}

	@CommandHandler
	public ResourceAggregate(CreateResourceCommand command) {
		if (command.userId() == null) {
			throw new IllegalArgumentException("User identity is required");
		}
		if (command.name() == null || command.name().isBlank()) {
			throw new IllegalArgumentException("Resource name is required");
		}
		if (command.location() == null || command.location().isBlank()) {
			throw new IllegalArgumentException("Resource location is required");
		}
		if (command.capacity() < 1) {
			throw new IllegalArgumentException("Resource capacity must be at least 1");
		}
		AggregateLifecycle.apply(new ResourceCreatedEvent(command.resourceId(), command.name(), command.description(),
				command.capacity(), command.location(), Instant.now()));
	}

	@CommandHandler
	public void handle(UpdateResourceCommand command) {
		if (command.userId() == null) {
			throw new IllegalArgumentException("User identity is required");
		}
		if (command.name() == null || command.name().isBlank()) {
			throw new IllegalArgumentException("Resource name is required");
		}
		if (command.location() == null || command.location().isBlank()) {
			throw new IllegalArgumentException("Resource location is required");
		}
		AggregateLifecycle.apply(new ResourceUpdatedEvent(resourceId, command.name(), command.description(),
				command.location(), Instant.now()));
	}

	@CommandHandler
	public void handle(DeactivateResourceCommand command) {
		if (command.userId() == null) {
			throw new IllegalArgumentException("User identity is required");
		}
		if (status == ResourceStatus.INACTIVE) {
			throw new IllegalStateException("Resource is already inactive");
		}
		AggregateLifecycle.apply(new ResourceDeactivatedEvent(resourceId, Instant.now()));
	}

	@CommandHandler
	public void handle(ReactivateResourceCommand command) {
		if (command.userId() == null) {
			throw new IllegalArgumentException("User identity is required");
		}
		if (status == ResourceStatus.ACTIVE) {
			throw new IllegalStateException("Resource is already active");
		}
		AggregateLifecycle.apply(new ResourceReactivatedEvent(resourceId, Instant.now()));
	}

	@CommandHandler
	public void handle(ReserveResourceCommand command) {
		if (command.userId() == null) {
			throw new IllegalArgumentException("User identity is required");
		}
		if (command.start() == null || command.end() == null) {
			throw new IllegalArgumentException("Reservation start and end are required");
		}
		if (status == ResourceStatus.INACTIVE) {
			throw new IllegalStateException("Reservations are not allowed on an inactive resource");
		}
		if (!command.end().isAfter(command.start())) {
			throw new IllegalArgumentException("Reservation end must be after start");
		}
		if (command.start().isBefore(Instant.now())) {
			throw new IllegalArgumentException("Reservation cannot start in the past");
		}
		if (reservations.stream().anyMatch(reservation -> reservation.reservationId().equals(command.reservationId()))) {
			throw new IllegalStateException("Reservation already exists");
		}
		long overlappingReservations = reservations.stream()
				.filter(reservation -> reservation.status() != ReservationStatus.CANCELLED)
				.filter(reservation -> command.start().isBefore(reservation.end())
						&& command.end().isAfter(reservation.start()))
				.count();
		if (overlappingReservations >= capacity) {
			throw new IllegalStateException("Resource capacity is exceeded for the requested time range");
		}
		AggregateLifecycle.apply(new ReservationCreatedEvent(resourceId, command.reservationId(), command.userId(),
				command.start(), command.end(), ReservationStatus.PENDING, Instant.now()));
	}

	@CommandHandler
	public void handle(CancelReservationCommand command) {
		if (command.userId() == null) {
			throw new IllegalArgumentException("User identity is required");
		}
		Reservation reservation = reservation(command.reservationId());
		if (!command.admin() && !reservation.userId().equals(command.userId())) {
			throw new IllegalStateException("Only the reservation owner or an admin may cancel it");
		}
		if (reservation.status() == ReservationStatus.CANCELLED) {
			throw new IllegalStateException("Reservation is already cancelled");
		}
		AggregateLifecycle.apply(new ReservationCancelledEvent(resourceId, command.reservationId(), Instant.now()));
	}

	@CommandHandler
	public void handle(ConfirmReservationCommand command) {
		if (command.userId() == null) {
			throw new IllegalArgumentException("User identity is required");
		}
		Reservation reservation = reservation(command.reservationId());
		if (reservation.status() != ReservationStatus.PENDING) {
			throw new IllegalStateException("Only pending reservations may be confirmed");
		}
		AggregateLifecycle.apply(new ReservationConfirmedEvent(resourceId, command.reservationId(), Instant.now()));
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

	@EventSourcingHandler
	public void on(ReservationCreatedEvent event) {
		reservations.add(new Reservation(event.reservationId(), event.userId(), event.start(), event.end(), event.status()));
	}

	@EventSourcingHandler
	public void on(ReservationCancelledEvent event) {
		reservation(event.reservationId()).status = ReservationStatus.CANCELLED;
	}

	@EventSourcingHandler
	public void on(ReservationConfirmedEvent event) {
		reservation(event.reservationId()).status = ReservationStatus.CONFIRMED;
	}

	private Reservation reservation(UUID reservationId) {
		return reservations.stream().filter(candidate -> candidate.reservationId().equals(reservationId)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Reservation does not exist"));
	}

	private static final class Reservation {
		private final UUID reservationId;
		private final UUID userId;
		private final Instant start;
		private final Instant end;
		private ReservationStatus status;

		private Reservation(UUID reservationId, UUID userId, Instant start, Instant end, ReservationStatus status) {
			this.reservationId = reservationId;
			this.userId = userId;
			this.start = start;
			this.end = end;
			this.status = status;
		}

		private UUID reservationId() { return reservationId; }
		private UUID userId() { return userId; }
		private Instant start() { return start; }
		private Instant end() { return end; }
		private ReservationStatus status() { return status; }

		@Override
		public boolean equals(Object other) {
			if (this == other) return true;
			if (!(other instanceof Reservation reservation)) return false;
			return Objects.equals(reservationId, reservation.reservationId)
					&& Objects.equals(userId, reservation.userId)
					&& Objects.equals(start, reservation.start)
					&& Objects.equals(end, reservation.end)
					&& status == reservation.status;
		}

		@Override
		public int hashCode() {
			return Objects.hash(reservationId, userId, start, end, status);
		}
	}
}