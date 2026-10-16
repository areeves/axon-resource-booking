package com.github.areeves.axon_resource_booking.resources;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ResourceAvailabilityTest {
	@Test
	void supportsOvernightWeeklyWindows() {
		var rules = new ResourceAvailabilityRules("UTC",
				Map.of("MONDAY", List.of(new AvailabilityTimeRange(LocalTime.of(22, 0), LocalTime.of(2, 0)))),
				List.of(), List.of());

		assertThat(ResourceAvailability.isFullyAvailable(rules, Instant.parse("2026-06-02T01:00:00Z"),
				Instant.parse("2026-06-02T02:00:00Z"))).isTrue();
		assertThat(ResourceAvailability.isFullyAvailable(rules, Instant.parse("2026-06-02T02:00:00Z"),
				Instant.parse("2026-06-02T03:00:00Z"))).isFalse();
	}

	@Test
	void handlesWeeklyWindowsAcrossDaylightSavingTransitions() {
		var rules = new ResourceAvailabilityRules("America/New_York",
				Map.of("SUNDAY", List.of(new AvailabilityTimeRange(LocalTime.of(1, 0), LocalTime.of(4, 0)))),
				List.of(), List.of());

		assertThat(ResourceAvailability.isFullyAvailable(rules, Instant.parse("2026-03-08T06:00:00Z"),
				Instant.parse("2026-03-08T08:00:00Z"))).isTrue();
	}

	@Test
	void appliesEffectiveDatesToSeasonalWeeklyRanges() {
		var rules = new ResourceAvailabilityRules("UTC",
				Map.of("MONDAY", List.of(new AvailabilityTimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0),
						LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 30)))),
				List.of(), List.of());

		assertThat(ResourceAvailability.isFullyAvailable(rules, Instant.parse("2026-06-01T10:00:00Z"),
				Instant.parse("2026-06-01T11:00:00Z"))).isFalse();
		assertThat(ResourceAvailability.isFullyAvailable(rules, Instant.parse("2026-06-08T10:00:00Z"),
				Instant.parse("2026-06-08T11:00:00Z"))).isTrue();
	}

	@Test
	void blackoutOnlyRulesKeepTheResourceAvailableOutsideBlackoutWindows() {
		Instant blackoutStart = Instant.parse("2026-06-01T12:00:00Z");
		var rules = new ResourceAvailabilityRules("UTC", Map.of(), List.of(
				new AvailabilityWindow(UUID.randomUUID(), blackoutStart, Instant.parse("2026-06-01T13:00:00Z"),
						AvailabilityWindowType.BLACKOUT, "Maintenance", UUID.randomUUID(), Instant.now())), List.of());

		assertThat(ResourceAvailability.isFullyAvailable(rules, Instant.parse("2026-06-01T11:00:00Z"), blackoutStart)).isTrue();
		assertThat(ResourceAvailability.isFullyAvailable(rules, blackoutStart, Instant.parse("2026-06-01T13:00:00Z"))).isFalse();
		assertThat(ResourceAvailability.isFullyAvailable(rules, Instant.parse("2026-06-01T13:00:00Z"),
				Instant.parse("2026-06-01T14:00:00Z"))).isTrue();
	}

	@Test
	void subtractsOverlappingBlackoutsWithoutRemovingAdjacentOpenWindows() {
		LocalDate monday = LocalDate.of(2026, 6, 1);
		var firstBlackoutStart = monday.atTime(12, 0).atZone(ZoneId.of("UTC")).toInstant();
		var rules = new ResourceAvailabilityRules("UTC",
				Map.of("MONDAY", List.of(
						new AvailabilityTimeRange(LocalTime.of(9, 0), LocalTime.of(12, 0)),
						new AvailabilityTimeRange(LocalTime.of(13, 0), LocalTime.of(17, 0)))),
				List.of(
						new AvailabilityWindow(UUID.randomUUID(), firstBlackoutStart,
								monday.atTime(14, 0).atZone(ZoneId.of("UTC")).toInstant(), AvailabilityWindowType.BLACKOUT,
								null, UUID.randomUUID(), Instant.now()),
						new AvailabilityWindow(UUID.randomUUID(), monday.atTime(13, 0).atZone(ZoneId.of("UTC")).toInstant(),
								monday.atTime(15, 0).atZone(ZoneId.of("UTC")).toInstant(), AvailabilityWindowType.BLACKOUT,
								null, UUID.randomUUID(), Instant.now())),
				List.of());

		var openWindows = ResourceAvailability.openWindows(rules,
				monday.atTime(9, 0).atZone(ZoneId.of("UTC")).toInstant(),
				monday.atTime(17, 0).atZone(ZoneId.of("UTC")).toInstant());

		assertThat(openWindows).containsExactly(
				new ResourceAvailability.Interval(monday.atTime(9, 0).atZone(ZoneId.of("UTC")).toInstant(), firstBlackoutStart),
				new ResourceAvailability.Interval(monday.atTime(15, 0).atZone(ZoneId.of("UTC")).toInstant(),
						monday.atTime(17, 0).atZone(ZoneId.of("UTC")).toInstant()));
	}

	@Test
	void calculatesPeakOccupancyAcrossHalfOpenIntervals() {
		Instant start = Instant.parse("2026-06-01T10:00:00Z");
		Instant midpoint = Instant.parse("2026-06-01T11:00:00Z");
		Instant end = Instant.parse("2026-06-01T12:00:00Z");

		assertThat(ResourceAvailability.maximumConcurrentReservations(List.of(
				new ResourceAvailability.Interval(start, midpoint),
				new ResourceAvailability.Interval(midpoint, end)), start, end)).isEqualTo(1);
	}
}