package com.github.areeves.axon_resource_booking.resources;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

record AvailabilityTimeRange(LocalTime start, LocalTime end) {
	public AvailabilityTimeRange {
		Objects.requireNonNull(start, "start is required");
		Objects.requireNonNull(end, "end is required");
	}
}

enum AvailabilityWindowType {
	BLACKOUT,
	EXTRA
}

record AvailabilityWindow(UUID windowId, Instant start, Instant end, AvailabilityWindowType type,
		String reason, UUID createdBy, Instant createdAt) {
	public AvailabilityWindow {
		Objects.requireNonNull(windowId, "windowId is required");
		Objects.requireNonNull(start, "start is required");
		Objects.requireNonNull(end, "end is required");
		Objects.requireNonNull(type, "type is required");
		Objects.requireNonNull(createdAt, "createdAt is required");
	}
}

record ResourceAvailabilityRules(String timezone,
		Map<String, List<AvailabilityTimeRange>> weeklyPattern,
		List<AvailabilityWindow> blackouts,
		List<AvailabilityWindow> extras) {
	public ResourceAvailabilityRules {
		if (timezone != null && timezone.isBlank()) {
			timezone = null;
		}
		weeklyPattern = weeklyPattern == null ? Map.of() : weeklyPattern;
		blackouts = blackouts == null ? List.of() : blackouts;
		extras = extras == null ? List.of() : extras;
	}

	public static ResourceAvailabilityRules empty() {
		return new ResourceAvailabilityRules(null, Map.of(), List.of(), List.of());
	}

	public boolean isAlwaysAvailable() {
		return (timezone == null || timezone.isBlank())
				&& weeklyPattern.isEmpty()
				&& blackouts.isEmpty()
				&& extras.isEmpty();
	}

	public boolean hasRules() {
		return !isAlwaysAvailable();
	}

	public ResourceAvailabilityRules withTimezone(String timezone) {
		return new ResourceAvailabilityRules(timezone, weeklyPattern, blackouts, extras);
	}
}

record SetResourceAvailabilityCommand(@org.axonframework.modelling.command.TargetAggregateIdentifier UUID resourceId,
		UUID userId, String timezone, Map<String, List<AvailabilityTimeRange>> weeklyPattern) {
}

record UpdateWeeklyAvailabilityPatternCommand(@org.axonframework.modelling.command.TargetAggregateIdentifier UUID resourceId,
		UUID userId, String timezone, Map<String, List<AvailabilityTimeRange>> weeklyPattern) {
}

record SetAvailabilityRulesCommand(@org.axonframework.modelling.command.TargetAggregateIdentifier UUID resourceId,
		UUID userId, String timezone, Map<String, List<AvailabilityTimeRange>> weeklyPattern) {
}

record ClearResourceAvailabilityCommand(@org.axonframework.modelling.command.TargetAggregateIdentifier UUID resourceId,
		UUID userId) {
}

record AddResourceBlackoutCommand(@org.axonframework.modelling.command.TargetAggregateIdentifier UUID resourceId,
		UUID userId, UUID windowId, Instant start, Instant end, String reason) {
}

record AddBlackoutWindowCommand(@org.axonframework.modelling.command.TargetAggregateIdentifier UUID resourceId,
		UUID userId, UUID windowId, Instant start, Instant end, String reason) {
}

record AddResourceExtraAvailabilityCommand(@org.axonframework.modelling.command.TargetAggregateIdentifier UUID resourceId,
		UUID userId, UUID windowId, Instant start, Instant end, String reason) {
}

record AddExtraAvailabilityWindowCommand(@org.axonframework.modelling.command.TargetAggregateIdentifier UUID resourceId,
		UUID userId, UUID windowId, Instant start, Instant end, String reason) {
}

record RemoveResourceBlackoutCommand(@org.axonframework.modelling.command.TargetAggregateIdentifier UUID resourceId,
		UUID userId, UUID windowId) {
}

record RemoveBlackoutWindowCommand(@org.axonframework.modelling.command.TargetAggregateIdentifier UUID resourceId,
		UUID userId, UUID windowId) {
}

record RemoveResourceExtraAvailabilityCommand(@org.axonframework.modelling.command.TargetAggregateIdentifier UUID resourceId,
		UUID userId, UUID windowId) {
}

record RemoveExtraAvailabilityWindowCommand(@org.axonframework.modelling.command.TargetAggregateIdentifier UUID resourceId,
		UUID userId, UUID windowId) {
}

record ResourceAvailabilityRulesUpdatedEvent(UUID resourceId, String timezone,
		Map<String, List<AvailabilityTimeRange>> weeklyPattern,
		List<AvailabilityWindow> blackouts,
		List<AvailabilityWindow> extras,
		Instant updatedAt) {
}

record ResourceAvailabilityClearedEvent(UUID resourceId, Instant updatedAt) {
}

record ResourceBlackoutAddedEvent(UUID resourceId, UUID windowId, Instant start, Instant end,
		String reason, UUID createdBy, Instant createdAt) {
}

record ResourceExtraAvailabilityAddedEvent(UUID resourceId, UUID windowId, Instant start, Instant end,
		String reason, UUID createdBy, Instant createdAt) {
}

record ResourceBlackoutRemovedEvent(UUID resourceId, UUID windowId, Instant updatedAt) {
}

record ResourceExtraAvailabilityRemovedEvent(UUID resourceId, UUID windowId, Instant updatedAt) {
}

