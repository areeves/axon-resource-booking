package com.github.areeves.axon_resource_booking.resources;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/resources/{resourceId}/availability")
public class ResourceAvailabilityQueryController {
	private static final long MAX_RANGE_DAYS = 366;
	private static final int MAX_NEXT_SLOT_COUNT = 100;
	private static final int NEXT_SLOT_HORIZON_DAYS = 90;

	private final ResourceRepository resourceRepository;
	private final ReservationRepository reservationRepository;

	public ResourceAvailabilityQueryController(ResourceRepository resourceRepository,
			ReservationRepository reservationRepository) {
		this.resourceRepository = resourceRepository;
		this.reservationRepository = reservationRepository;
	}

	@GetMapping("/calendar")
	public List<AvailabilityCalendarDay> getCalendar(@PathVariable UUID resourceId,
			@RequestParam LocalDate from, @RequestParam LocalDate to,
			@RequestParam(defaultValue = "60") int slotMinutes) {
		validateDateRange(from, to);
		if (slotMinutes < 1 || slotMinutes > 1440) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slotMinutes must be between 1 and 1440");
		}
		ResourceEntity resource = getResource(resourceId);
		ResourceAvailabilityRules rules = resource.getAvailabilityRules();
		ZoneId zone = rules.timezone() == null ? ZoneId.of("UTC") : ZoneId.of(rules.timezone());
		List<ResourceAvailability.Interval> reservations = activeReservations(resourceId);
		Duration slotDuration = Duration.ofMinutes(slotMinutes);

		return LongStream.rangeClosed(from.toEpochDay(), to.toEpochDay()).mapToObj(LocalDate::ofEpochDay).map(date -> {
			Instant dayStart = date.atStartOfDay(zone).toInstant();
			Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
			List<AvailabilitySlot> slots = new java.util.ArrayList<>();
			for (Instant slotStart = dayStart; slotStart.isBefore(dayEnd);) {
				Instant slotEnd = slotStart.plus(slotDuration);
				if (slotEnd.isAfter(dayEnd)) {
					slotEnd = dayEnd;
				}
				boolean bookable = resource.getStatus() == ResourceStatus.ACTIVE
						&& ResourceAvailability.isFullyAvailable(rules, slotStart, slotEnd);
				int remainingCapacity = bookable
						? Math.max(0, resource.getCapacity() - (int) ResourceAvailability.maximumConcurrentReservations(
								reservations, slotStart, slotEnd))
						: 0;
				slots.add(new AvailabilitySlot(slotStart, slotEnd, bookable, remainingCapacity));
				slotStart = slotEnd;
			}
			return new AvailabilityCalendarDay(date, slots);
		}).toList();
	}

	@GetMapping("/next-slots")
	public List<AvailabilitySlot> getNextSlots(@PathVariable UUID resourceId, @RequestParam Instant after,
			@RequestParam int durationMinutes, @RequestParam(defaultValue = "5") int count) {
		if (durationMinutes < 1 || durationMinutes > 1440) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "durationMinutes must be between 1 and 1440");
		}
		if (count < 1 || count > MAX_NEXT_SLOT_COUNT) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "count must be between 1 and 100");
		}
		ResourceEntity resource = getResource(resourceId);
		Instant horizonEnd = after.plus(Duration.ofDays(NEXT_SLOT_HORIZON_DAYS));
		if (resource.getStatus() != ResourceStatus.ACTIVE) {
			return List.of();
		}
		Duration duration = Duration.ofMinutes(durationMinutes);
		List<ResourceAvailability.Interval> reservations = activeReservations(resourceId);
		List<ResourceAvailability.Interval> openWindows = ResourceAvailability.openWindows(
				resource.getAvailabilityRules(), after, horizonEnd);
		List<AvailabilitySlot> result = new java.util.ArrayList<>();

		for (var window : openWindows) {
			Instant candidate = window.start().isAfter(after) ? window.start() : after;
			while (result.size() < count && !candidate.plus(duration).isAfter(window.end())) {
				Instant candidateEnd = candidate.plus(duration);
				long occupancy = ResourceAvailability.maximumConcurrentReservations(reservations, candidate, candidateEnd);
				if (occupancy < resource.getCapacity()) {
					result.add(new AvailabilitySlot(candidate, candidateEnd, true,
							resource.getCapacity() - (int) occupancy));
					candidate = candidateEnd;
					continue;
				}
				Instant currentCandidate = candidate;
				Instant nextCandidate = reservations.stream()
						.filter(reservation -> reservation.start().isBefore(candidateEnd)
								&& reservation.end().isAfter(currentCandidate))
						.map(ResourceAvailability.Interval::end)
						.filter(end -> end.isAfter(currentCandidate))
						.min(Instant::compareTo)
						.orElse(null);
				if (nextCandidate == null) {
					break;
				}
				candidate = nextCandidate;
			}
			if (result.size() >= count) {
				break;
			}
		}
		return result;
	}

	private ResourceEntity getResource(UUID resourceId) {
		return resourceRepository.findById(resourceId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private List<ResourceAvailability.Interval> activeReservations(UUID resourceId) {
		return reservationRepository.findByResourceIdOrderByStartAsc(resourceId).stream()
				.filter(reservation -> reservation.getStatus() != ReservationStatus.CANCELLED)
				.map(reservation -> new ResourceAvailability.Interval(reservation.getStart(), reservation.getEnd()))
				.toList();
	}

	private void validateDateRange(LocalDate from, LocalDate to) {
		if (to.isBefore(from)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to must be on or after from");
		}
		if (to.toEpochDay() - from.toEpochDay() >= MAX_RANGE_DAYS) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date range cannot exceed 366 days");
		}
	}

	public record AvailabilitySlot(Instant start, Instant end, boolean bookable, int remainingCapacity) {
	}

	public record AvailabilityCalendarDay(LocalDate date, List<AvailabilitySlot> slots) {
	}
}