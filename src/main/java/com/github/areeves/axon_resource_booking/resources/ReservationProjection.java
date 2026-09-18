package com.github.areeves.axon_resource_booking.resources;

import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

@Component
public class ReservationProjection {

	private final ReservationRepository reservationRepository;

	public ReservationProjection(ReservationRepository reservationRepository) {
		this.reservationRepository = reservationRepository;
	}

	@EventHandler
	public void on(ReservationCreatedEvent event) {
		reservationRepository.save(ReservationEntity.from(event));
	}

	@EventHandler
	public void on(ReservationCancelledEvent event) {
		reservationRepository.findById(event.reservationId()).ifPresent(reservation -> {
			reservation.apply(event);
			reservationRepository.save(reservation);
		});
	}

	@EventHandler
	public void on(ReservationConfirmedEvent event) {
		reservationRepository.findById(event.reservationId()).ifPresent(reservation -> {
			reservation.apply(event);
			reservationRepository.save(reservation);
		});
	}
}