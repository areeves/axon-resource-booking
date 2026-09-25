package com.github.areeves.axon_resource_booking.resources;

import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.axonframework.config.ProcessingGroup;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ProcessingGroup("resource-projections")
public class ReservationProjection {
	private static final Logger log = LoggerFactory.getLogger(ReservationProjection.class);

	private final ReservationRepository reservationRepository;
	private final ResourceUtilizationRepository utilizationRepository;
	private final MeterRegistry meterRegistry;

	public ReservationProjection(ReservationRepository reservationRepository,
			ResourceUtilizationRepository utilizationRepository, MeterRegistry meterRegistry) {
		this.reservationRepository = reservationRepository;
		this.utilizationRepository = utilizationRepository;
		this.meterRegistry = meterRegistry;
	}

	@EventHandler
	public void on(ReservationCreatedEvent event) {
		recordEvent("reservation_created", event.resourceId());
		reservationRepository.save(ReservationEntity.from(event));
		if (event.status() != ReservationStatus.CANCELLED) {
			adjustUtilization(event.resourceId(), event.start(), event.end(), 1);
		}
	}

	@EventHandler
	public void on(ReservationCancelledEvent event) {
		recordEvent("reservation_cancelled", event.resourceId());
		reservationRepository.findById(event.reservationId()).ifPresent(reservation -> {
			if (reservation.getStatus() != ReservationStatus.CANCELLED) {
				adjustUtilization(reservation.getResourceId(), reservation.getStart(), reservation.getEnd(), -1);
			}
			reservation.apply(event);
			reservationRepository.save(reservation);
		});
	}

	@EventHandler
	public void on(ReservationConfirmedEvent event) {
		recordEvent("reservation_confirmed", event.resourceId());
		reservationRepository.findById(event.reservationId()).ifPresent(reservation -> {
			reservation.apply(event);
			reservationRepository.save(reservation);
		});
	}

	private void adjustUtilization(java.util.UUID resourceId, java.time.Instant start, java.time.Instant end,
			long reservationCountDelta) {
		java.time.LocalDate date = start.atZone(java.time.ZoneOffset.UTC).toLocalDate();
		while (date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().isBefore(end)) {
			java.time.LocalDate utilizationDate = date;
			java.time.Instant dayStart = utilizationDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
			java.time.Instant dayEnd = utilizationDate.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
			java.time.Instant segmentStart = start.isAfter(dayStart) ? start : dayStart;
			java.time.Instant segmentEnd = end.isBefore(dayEnd) ? end : dayEnd;
			java.time.Duration duration = java.time.Duration.between(segmentStart, segmentEnd);
			java.math.BigDecimal seconds = java.math.BigDecimal.valueOf(duration.getSeconds())
					.add(java.math.BigDecimal.valueOf(duration.getNano(), 9))
					.multiply(java.math.BigDecimal.valueOf(reservationCountDelta));
			ResourceUtilizationEntity utilization = utilizationRepository
					.findByResourceIdAndUtilizationDate(resourceId, utilizationDate)
					.orElseGet(() -> new ResourceUtilizationEntity(resourceId, utilizationDate));
			utilization.adjust(seconds, reservationCountDelta);
			utilizationRepository.save(utilization);
			date = date.plusDays(1);
		}
	}

	private void recordEvent(String eventType, java.util.UUID resourceId) {
		Counter.builder("axon.events.total").tag("event", eventType).register(meterRegistry).increment();
		log.info("domain_event event_type={} resource_id={}", eventType, resourceId);
	}
}