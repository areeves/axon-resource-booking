package com.github.areeves.axon_resource_booking.resources;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ResourceAvailability {
	private ResourceAvailability() {
	}

	public static boolean isFullyAvailable(ResourceAvailabilityRules rules, Instant start, Instant end) {
		if (start == null || end == null || !end.isAfter(start)) {
			return false;
		}
		if (rules == null || rules.isAlwaysAvailable()) {
			return true;
		}

		List<AvailabilityWindow> blackoutWindows = rules.blackouts() == null ? List.of() : rules.blackouts();
		List<AvailabilityWindow> extraWindows = rules.extras() == null ? List.of() : rules.extras();
		List<Interval> open = new ArrayList<>();
		String timezone = rules.timezone();
		var zone = timezone == null || timezone.isBlank() ? java.time.ZoneOffset.UTC : java.time.ZoneId.of(timezone);
		var fromDate = start.atZone(zone).toLocalDate();
		var toDate = end.atZone(zone).toLocalDate();
		for (var date = fromDate.minusDays(1); !date.isAfter(toDate.plusDays(1)); date = date.plusDays(1)) {
			var dayName = date.getDayOfWeek().name();
			for (var range : rules.weeklyPattern().getOrDefault(dayName, List.of())) {
				var from = date.atTime(range.start()).atZone(zone).toInstant();
				var to = date.atTime(range.end()).atZone(zone).toInstant();
				if (!to.isAfter(from)) {
					to = date.plusDays(1).atTime(range.end()).atZone(zone).toInstant();
				}
				if (to.isBefore(start) || from.isAfter(end)) {
					continue;
				}
				open.add(new Interval(max(from, start), min(to, end)));
			}
		}
		for (var window : extraWindows) {
			var candidate = new Interval(max(window.start(), start), min(window.end(), end));
			if (candidate.start().isBefore(candidate.end())) {
				open.add(candidate);
			}
		}
		for (var blackout : blackoutWindows) {
			var candidate = new Interval(max(blackout.start(), start), min(blackout.end(), end));
			if (candidate.start().isBefore(candidate.end())) {
				open = subtractIntervals(open, List.of(candidate));
			}
		}
		return coversInterval(open, start, end);
	}

	public static List<Interval> openWindows(ResourceAvailabilityRules rules, Instant start, Instant end) {
		if (rules == null || rules.isAlwaysAvailable()) {
			return List.of(new Interval(start, end));
		}
		List<Interval> intervals = new ArrayList<>();
		var timezone = rules.timezone() == null || rules.timezone().isBlank() ? java.time.ZoneOffset.UTC : java.time.ZoneId.of(rules.timezone());
		for (var date = start.atZone(timezone).toLocalDate().minusDays(1); !date.isAfter(end.atZone(timezone).toLocalDate().plusDays(1)); date = date.plusDays(1)) {
			for (var range : rules.weeklyPattern().getOrDefault(date.getDayOfWeek().name(), List.of())) {
				var candidateStart = date.atTime(range.start()).atZone(timezone).toInstant();
				var candidateEnd = date.atTime(range.end()).atZone(timezone).toInstant();
				if (!candidateEnd.isAfter(candidateStart)) {
					candidateEnd = date.plusDays(1).atTime(range.end()).atZone(timezone).toInstant();
				}
				if (candidateEnd.isAfter(start) && candidateStart.isBefore(end)) {
					intervals.add(new Interval(max(candidateStart, start), min(candidateEnd, end)));
				}
			}
		}
		for (var window : rules.extras()) {
			var candidate = new Interval(max(window.start(), start), min(window.end(), end));
			if (candidate.start().isBefore(candidate.end())) {
				intervals.add(candidate);
			}
		}
		for (var blackout : rules.blackouts()) {
			var candidate = new Interval(max(blackout.start(), start), min(blackout.end(), end));
			if (candidate.start().isBefore(candidate.end())) {
				intervals = subtractIntervals(intervals, List.of(candidate));
			}
		}
		return mergeIntervals(intervals);
	}

	private static boolean coversInterval(List<Interval> intervals, Instant start, Instant end) {
		var merged = mergeIntervals(intervals);
		Instant cursor = start;
		for (var interval : merged) {
			if (interval.end().isBefore(start) || interval.start().isAfter(end)) {
				continue;
			}
			var actualStart = interval.start().isBefore(start) ? start : interval.start();
			var actualEnd = interval.end().isAfter(end) ? end : interval.end();
			if (actualStart.isAfter(cursor)) {
				return false;
			}
			cursor = actualEnd.isAfter(cursor) ? actualEnd : cursor;
		}
		return cursor.equals(end);
	}

	private static List<Interval> subtractIntervals(List<Interval> intervals, List<Interval> exclusions) {
		List<Interval> result = new ArrayList<>();
		for (var interval : intervals) {
			List<Interval> remaining = new ArrayList<>();
			remaining.add(interval);
			for (var exclusion : exclusions) {
				List<Interval> next = new ArrayList<>();
				for (var part : remaining) {
					if (part.end().isBefore(exclusion.start()) || part.start().isAfter(exclusion.end())) {
						next.add(part);
						continue;
					}
					var leftStart = part.start();
					var leftEnd = exclusion.start().isBefore(part.start()) ? part.start() : exclusion.start();
					if (leftStart.isBefore(leftEnd)) {
						next.add(new Interval(leftStart, leftEnd));
					}
					var rightStart = exclusion.end().isAfter(part.end()) ? part.end() : exclusion.end();
					var rightEnd = part.end();
					if (rightStart.isBefore(rightEnd)) {
						next.add(new Interval(rightStart, rightEnd));
					}
				}
				remaining = next;
			}
			result.addAll(remaining);
		}
		return mergeIntervals(result);
	}

	private static List<Interval> mergeIntervals(List<Interval> intervals) {
		if (intervals.isEmpty()) {
			return List.of();
		}
		List<Interval> sorted = new ArrayList<>(intervals);
		sorted.sort(Comparator.comparing(Interval::start));
		List<Interval> merged = new ArrayList<>();
		var current = sorted.getFirst();
		for (int i = 1; i < sorted.size(); i++) {
			var next = sorted.get(i);
			if (!next.start().isAfter(current.end())) {
				current = new Interval(current.start(), max(current.end(), next.end()));
			} else {
				merged.add(current);
				current = next;
			}
		}
		merged.add(current);
		return merged;
	}

	public record Interval(Instant start, Instant end) {
		public Interval {
			Objects.requireNonNull(start, "start is required");
			Objects.requireNonNull(end, "end is required");
		}
	}

	private static Instant max(Instant left, Instant right) {
		return left.isAfter(right) ? left : right;
	}

	private static Instant min(Instant left, Instant right) {
		return left.isBefore(right) ? left : right;
	}
}
