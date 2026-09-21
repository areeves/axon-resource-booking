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
	private final MeterRegistry meterRegistry;

	public ReservationProjection(ReservationRepository reservationRepository, MeterRegistry meterRegistry) {
		this.reservationRepository = reservationRepository;
		this.meterRegistry = meterRegistry;
	}

	@EventHandler
	public void on(ReservationCreatedEvent event) {
		recordEvent("reservation_created", event.resourceId());
		reservationRepository.save(ReservationEntity.from(event));
	}

	@EventHandler
	public void on(ReservationCancelledEvent event) {
		recordEvent("reservation_cancelled", event.resourceId());
		reservationRepository.findById(event.reservationId()).ifPresent(reservation -> {
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

	private void recordEvent(String eventType, java.util.UUID resourceId) {
		Counter.builder("axon.events.total").tag("event", eventType).register(meterRegistry).increment();
		log.info("domain_event event_type={} resource_id={}", eventType, resourceId);
	}
}