package com.github.areeves.axon_resource_booking.resources;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/resources")
public class ResourceUtilizationQueryController {
	private static final long MAX_RANGE_DAYS = 366;

	private final ResourceRepository resourceRepository;
	private final ResourceUtilizationRepository utilizationRepository;

	public ResourceUtilizationQueryController(ResourceRepository resourceRepository,
			ResourceUtilizationRepository utilizationRepository) {
		this.resourceRepository = resourceRepository;
		this.utilizationRepository = utilizationRepository;
	}

	@GetMapping("/{resourceId}/utilization")
	public List<ResourceDailyUtilization> getDailyUtilization(@PathVariable UUID resourceId,
			@RequestParam LocalDate from, @RequestParam LocalDate to) {
		if (to.isBefore(from)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to must be on or after from");
		}
		if (to.toEpochDay() - from.toEpochDay() >= MAX_RANGE_DAYS) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date range cannot exceed 366 days");
		}

		ResourceEntity resource = resourceRepository.findById(resourceId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		Map<LocalDate, ResourceUtilizationEntity> utilizationByDate = utilizationRepository
				.findByResourceIdAndUtilizationDateBetweenOrderByUtilizationDateAsc(resourceId, from, to).stream()
				.collect(Collectors.toMap(ResourceUtilizationEntity::getUtilizationDate, Function.identity()));
		ResourceAvailabilityRules rules = resource.getAvailabilityRules();

		return LongStream.rangeClosed(from.toEpochDay(), to.toEpochDay()).mapToObj(LocalDate::ofEpochDay).map(date -> {
			ResourceUtilizationEntity utilization = utilizationByDate.get(date);
			BigDecimal occupiedSeconds = utilization == null ? BigDecimal.ZERO : utilization.getOccupiedSeconds();
			long reservationCount = utilization == null ? 0 : utilization.getReservationCount();
			var dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
			var dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
			BigDecimal bookableSeconds = ResourceAvailability.openWindows(rules, dayStart, dayEnd).stream()
					.map(interval -> BigDecimal.valueOf(java.time.Duration.between(interval.start(), interval.end()).getSeconds())
							.add(BigDecimal.valueOf(java.time.Duration.between(interval.start(), interval.end()).getNano(), 9)))
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			BigDecimal capacitySeconds = bookableSeconds.multiply(BigDecimal.valueOf(resource.getCapacity()));
			BigDecimal bookableHours = bookableSeconds.divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
			BigDecimal occupiedHours = occupiedSeconds.divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
			BigDecimal utilizationPercent = capacitySeconds.signum() == 0 ? BigDecimal.ZERO
					: occupiedSeconds.multiply(BigDecimal.valueOf(100)).divide(capacitySeconds, 2, RoundingMode.HALF_UP);
			return new ResourceDailyUtilization(resourceId, date, reservationCount, bookableHours, occupiedHours,
					utilizationPercent);
		}).toList();
	}
}