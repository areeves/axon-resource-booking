package com.github.areeves.axon_resource_booking.resources;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aggregate
public class ResourceAggregate {
	private static final Logger log = LoggerFactory.getLogger(ResourceAggregate.class);

	@AggregateIdentifier
	private UUID resourceId;
	private String name;
	private String description;
	private int capacity;
	private String location;
	private ResourceStatus status;
	private ResourceAvailabilityRules availabilityRules = ResourceAvailabilityRules.empty();
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
	public void handle(UpdateWeeklyAvailabilityPatternCommand command) {
		if (command.userId() == null) {
			throw new IllegalArgumentException("User identity is required");
		}
		var rules = normalizeRules(command.timezone(), command.weeklyPattern());
		if (!rules.isAlwaysAvailable() && rules.timezone() == null) {
			throw new IllegalArgumentException("A timezone is required when availability rules are defined");
		}
		if (rules.timezone() != null) {
			ZoneId.of(rules.timezone());
		}
		ensureNoAvailabilityConflicts(rules, command.rejectConflictingReservations());
		AggregateLifecycle.apply(new ResourceAvailabilityRulesUpdatedEvent(resourceId, rules.timezone(), rules.weeklyPattern(),
				availabilityRules.blackouts(), availabilityRules.extras(), Instant.now()));
	}

	@CommandHandler
	public void handle(ClearResourceAvailabilityCommand command) {
		if (command.userId() == null) {
			throw new IllegalArgumentException("User identity is required");
		}
		AggregateLifecycle.apply(new ResourceAvailabilityClearedEvent(resourceId, Instant.now()));
	}

	@CommandHandler
	public void handle(AddResourceBlackoutCommand command) {
		if (command.userId() == null) {
			throw new IllegalArgumentException("User identity is required");
		}
		if (command.start() == null || command.end() == null) {
			throw new IllegalArgumentException("Blackout start and end are required");
		}
		if (!command.end().isAfter(command.start())) {
			throw new IllegalArgumentException("Blackout end must be after start");
		}
		requireAvailabilityTimezone();
		requireUnusedWindowId(command.windowId());
		var addedWindow = new AvailabilityWindow(command.windowId(), command.start(), command.end(), AvailabilityWindowType.BLACKOUT,
				command.reason(), command.userId(), Instant.now());
		var proposedBlackouts = new ArrayList<>(availabilityRules.blackouts());
		proposedBlackouts.add(addedWindow);
		ensureNoAvailabilityConflicts(new ResourceAvailabilityRules(availabilityRules.timezone(), availabilityRules.weeklyPattern(),
				proposedBlackouts, availabilityRules.extras()), command.rejectConflictingReservations());
		AggregateLifecycle.apply(new ResourceBlackoutAddedEvent(resourceId, command.windowId(), command.start(), command.end(),
				command.reason(), command.userId(), Instant.now()));
	}

	@CommandHandler
	public void handle(AddResourceExtraAvailabilityCommand command) {
		if (command.userId() == null) {
			throw new IllegalArgumentException("User identity is required");
		}
		if (command.start() == null || command.end() == null) {
			throw new IllegalArgumentException("Extra availability start and end are required");
		}
		if (!command.end().isAfter(command.start())) {
			throw new IllegalArgumentException("Extra availability end must be after start");
		}
		requireAvailabilityTimezone();
		requireUnusedWindowId(command.windowId());
		AggregateLifecycle.apply(new ResourceExtraAvailabilityAddedEvent(resourceId, command.windowId(), command.start(), command.end(),
				command.reason(), command.userId(), Instant.now()));
	}

	@CommandHandler
	public void handle(UpdateResourceBlackoutCommand command) {
		requireAvailabilityWindowUpdate(command.userId(), command.start(), command.end());
		AvailabilityWindow current = availabilityWindow(availabilityRules.blackouts(), command.windowId(), "Blackout");
		var proposedBlackouts = availabilityRules.blackouts().stream().map(window -> window.windowId().equals(command.windowId())
				? new AvailabilityWindow(window.windowId(), command.start(), command.end(), window.type(), command.reason(),
						window.createdBy(), window.createdAt())
				: window).toList();
		ensureNoAvailabilityConflicts(new ResourceAvailabilityRules(availabilityRules.timezone(), availabilityRules.weeklyPattern(),
				proposedBlackouts, availabilityRules.extras()), command.rejectConflictingReservations());
		AggregateLifecycle.apply(new ResourceBlackoutUpdatedEvent(resourceId, command.windowId(), command.start(), command.end(),
				command.reason(), command.userId(), Instant.now()));
	}

	@CommandHandler
	public void handle(UpdateResourceExtraAvailabilityCommand command) {
		requireAvailabilityWindowUpdate(command.userId(), command.start(), command.end());
		availabilityWindow(availabilityRules.extras(), command.windowId(), "Extra availability");
		var proposedExtras = availabilityRules.extras().stream().map(window -> window.windowId().equals(command.windowId())
				? new AvailabilityWindow(window.windowId(), command.start(), command.end(), window.type(), command.reason(),
						window.createdBy(), window.createdAt())
				: window).toList();
		ensureNoAvailabilityConflicts(new ResourceAvailabilityRules(availabilityRules.timezone(), availabilityRules.weeklyPattern(),
				availabilityRules.blackouts(), proposedExtras), command.rejectConflictingReservations());
		AggregateLifecycle.apply(new ResourceExtraAvailabilityUpdatedEvent(resourceId, command.windowId(), command.start(), command.end(),
				command.reason(), command.userId(), Instant.now()));
	}

	@CommandHandler
	public void handle(RemoveResourceBlackoutCommand command) {
		if (command.userId() == null) {
			throw new IllegalArgumentException("User identity is required");
		}
		AggregateLifecycle.apply(new ResourceBlackoutRemovedEvent(resourceId, command.windowId(), Instant.now()));
	}

	@CommandHandler
	public void handle(RemoveResourceExtraAvailabilityCommand command) {
		if (command.userId() == null) {
			throw new IllegalArgumentException("User identity is required");
		}
		availabilityWindow(availabilityRules.extras(), command.windowId(), "Extra availability");
		var proposedExtras = availabilityRules.extras().stream().filter(window -> !window.windowId().equals(command.windowId())).toList();
		ensureNoAvailabilityConflicts(new ResourceAvailabilityRules(availabilityRules.timezone(), availabilityRules.weeklyPattern(),
				availabilityRules.blackouts(), proposedExtras), command.rejectConflictingReservations());
		AggregateLifecycle.apply(new ResourceExtraAvailabilityRemovedEvent(resourceId, command.windowId(), Instant.now()));
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
		if (!ResourceAvailability.isFullyAvailable(availabilityRules, command.start(), command.end())) {
			throw new IllegalStateException("Reservation must fall entirely within the resource's bookable windows");
		}
		if (reservations.stream().anyMatch(reservation -> reservation.reservationId().equals(command.reservationId()))) {
			throw new IllegalStateException("Reservation already exists");
		}
		List<ResourceAvailability.Interval> overlappingReservations = reservations.stream()
				.filter(reservation -> reservation.status() != ReservationStatus.CANCELLED)
				.filter(reservation -> command.start().isBefore(reservation.end())
						&& command.end().isAfter(reservation.start()))
				.map(reservation -> new ResourceAvailability.Interval(reservation.start(), reservation.end()))
				.toList();
		if (ResourceAvailability.maximumConcurrentReservations(overlappingReservations, command.start(), command.end()) >= capacity) {
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
	public void on(ResourceAvailabilityRulesUpdatedEvent event) {
		availabilityRules = new ResourceAvailabilityRules(event.timezone(), event.weeklyPattern(), event.blackouts(), event.extras());
	}

	@EventSourcingHandler
	public void on(ResourceBlackoutUpdatedEvent event) {
		var updated = availabilityRules.blackouts().stream().map(window -> window.windowId().equals(event.windowId())
				? new AvailabilityWindow(window.windowId(), event.start(), event.end(), AvailabilityWindowType.BLACKOUT,
						event.reason(), window.createdBy(), window.createdAt())
				: window).toList();
		availabilityRules = new ResourceAvailabilityRules(availabilityRules.timezone(), availabilityRules.weeklyPattern(), updated,
				availabilityRules.extras());
	}

	@EventSourcingHandler
	public void on(ResourceExtraAvailabilityUpdatedEvent event) {
		var updated = availabilityRules.extras().stream().map(window -> window.windowId().equals(event.windowId())
				? new AvailabilityWindow(window.windowId(), event.start(), event.end(), AvailabilityWindowType.EXTRA,
						event.reason(), window.createdBy(), window.createdAt())
				: window).toList();
		availabilityRules = new ResourceAvailabilityRules(availabilityRules.timezone(), availabilityRules.weeklyPattern(),
				availabilityRules.blackouts(), updated);
	}

	@EventSourcingHandler
	public void on(ResourceAvailabilityClearedEvent event) {
		availabilityRules = ResourceAvailabilityRules.empty();
	}

	@EventSourcingHandler
	public void on(ResourceBlackoutAddedEvent event) {
		var current = new ArrayList<>(availabilityRules.blackouts());
		current.add(new AvailabilityWindow(event.windowId(), event.start(), event.end(), AvailabilityWindowType.BLACKOUT,
				event.reason(), event.createdBy(), event.createdAt()));
		availabilityRules = new ResourceAvailabilityRules(availabilityRules.timezone(), availabilityRules.weeklyPattern(), current,
				availabilityRules.extras());
	}

	@EventSourcingHandler
	public void on(ResourceExtraAvailabilityAddedEvent event) {
		var current = new ArrayList<>(availabilityRules.extras());
		current.add(new AvailabilityWindow(event.windowId(), event.start(), event.end(), AvailabilityWindowType.EXTRA,
				event.reason(), event.createdBy(), event.createdAt()));
		availabilityRules = new ResourceAvailabilityRules(availabilityRules.timezone(), availabilityRules.weeklyPattern(),
				availabilityRules.blackouts(), current);
	}

	@EventSourcingHandler
	public void on(ResourceBlackoutRemovedEvent event) {
		var filtered = availabilityRules.blackouts().stream()
				.filter(window -> !window.windowId().equals(event.windowId()))
				.toList();
		availabilityRules = new ResourceAvailabilityRules(availabilityRules.timezone(), availabilityRules.weeklyPattern(), filtered,
				availabilityRules.extras());
	}

	@EventSourcingHandler
	public void on(ResourceExtraAvailabilityRemovedEvent event) {
		var filtered = availabilityRules.extras().stream()
				.filter(window -> !window.windowId().equals(event.windowId()))
				.toList();
		availabilityRules = new ResourceAvailabilityRules(availabilityRules.timezone(), availabilityRules.weeklyPattern(),
				availabilityRules.blackouts(), filtered);
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

	private ResourceAvailabilityRules normalizeRules(String timezone, Map<String, List<AvailabilityTimeRange>> weeklyPattern) {
		var normalizedPattern = weeklyPattern == null ? Map.<String, List<AvailabilityTimeRange>>of() : weeklyPattern;
		var effectiveZone = timezone == null || timezone.isBlank() ? null : timezone;
		if (effectiveZone != null) {
			ZoneId.of(effectiveZone);
		}
		for (var entry : normalizedPattern.entrySet()) {
			java.time.DayOfWeek.valueOf(entry.getKey());
			for (var range : entry.getValue()) {
				if (range.start().equals(range.end())) {
					throw new IllegalArgumentException("Availability range start and end must differ");
				}
			}
		}
		return new ResourceAvailabilityRules(effectiveZone, normalizedPattern, availabilityRules.blackouts(), availabilityRules.extras());
	}

	private void requireAvailabilityTimezone() {
		if (availabilityRules.timezone() == null || availabilityRules.timezone().isBlank()) {
			throw new IllegalArgumentException("Set an availability timezone before adding availability windows");
		}
	}

	private void requireUnusedWindowId(UUID windowId) {
		if (windowId == null) {
			throw new IllegalArgumentException("Availability window ID is required");
		}
		if (availabilityRules.blackouts().stream().anyMatch(window -> window.windowId().equals(windowId))
				|| availabilityRules.extras().stream().anyMatch(window -> window.windowId().equals(windowId))) {
			throw new IllegalArgumentException("Availability window ID already exists");
		}
	}

	private void requireAvailabilityWindowUpdate(UUID userId, Instant start, Instant end) {
		if (userId == null) {
			throw new IllegalArgumentException("User identity is required");
		}
		requireAvailabilityTimezone();
		if (start == null || end == null || !end.isAfter(start)) {
			throw new IllegalArgumentException("Window end must be after start");
		}
	}

	private AvailabilityWindow availabilityWindow(List<AvailabilityWindow> windows, UUID windowId, String label) {
		return windows.stream().filter(window -> window.windowId().equals(windowId)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException(label + " window does not exist"));
	}

	private void ensureNoAvailabilityConflicts(ResourceAvailabilityRules proposedRules, boolean rejectConflicts) {
		List<UUID> conflictingReservations = reservations.stream()
				.filter(reservation -> reservation.status() != ReservationStatus.CANCELLED)
				.filter(reservation -> !ResourceAvailability.isFullyAvailable(proposedRules, reservation.start(), reservation.end()))
				.map(Reservation::reservationId)
				.toList();
		if (conflictingReservations.isEmpty()) {
			return;
		}
		if (rejectConflicts) {
			throw new IllegalStateException("Availability change conflicts with existing reservations: " + conflictingReservations);
		}
		log.warn("availability_rule_conflict resource_id={} reservation_ids={}", resourceId, conflictingReservations);
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