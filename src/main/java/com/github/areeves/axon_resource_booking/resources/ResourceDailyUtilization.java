package com.github.areeves.axon_resource_booking.resources;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ResourceDailyUtilization(UUID resourceId, LocalDate date, long reservationCount, BigDecimal occupiedHours,
		BigDecimal utilizationPercent) {
}