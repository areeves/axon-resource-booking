package com.github.areeves.axon_resource_booking.resources;

import java.util.List;
import java.util.UUID;

import org.axonframework.eventsourcing.eventstore.EventStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reservations")
public class ReservationQueryController {

	private final ReservationRepository reservationRepository;
	private final EventStore eventStore;

	public ReservationQueryController(ReservationRepository reservationRepository, EventStore eventStore) {
		this.reservationRepository = reservationRepository;
		this.eventStore = eventStore;
	}

	@GetMapping("/users/{userId}")
	public List<ReservationEntity> getUserReservations(@PathVariable UUID userId) {
		return reservationRepository.findByUserIdOrderByStartAsc(userId);
	}

	@GetMapping("/resources/{resourceId}")
	public List<ReservationEntity> getResourceReservations(@PathVariable UUID resourceId) {
		return reservationRepository.findByResourceIdOrderByStartAsc(resourceId);
	}

	@GetMapping("/{reservationId}")
	public ResponseEntity<ReservationEntity> getReservation(@PathVariable UUID reservationId) {
		return reservationRepository.findById(reservationId).map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/{reservationId}/events")
	public ResponseEntity<List<Object>> getReservationEvents(@PathVariable UUID reservationId) {
		return reservationRepository.findById(reservationId)
				.<ResponseEntity<List<Object>>>map(reservation -> ResponseEntity.ok(eventStore.readEvents(reservation.getResourceId().toString())
						.asStream()
						.map(message -> (Object) message.getPayload())
						.filter(payload -> belongsToReservation(payload, reservationId))
						.toList()))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	private boolean belongsToReservation(Object payload, UUID reservationId) {
		return switch (payload) {
		case ReservationCreatedEvent event -> reservationId.equals(event.reservationId());
		case ReservationCancelledEvent event -> reservationId.equals(event.reservationId());
		case ReservationConfirmedEvent event -> reservationId.equals(event.reservationId());
		default -> false;
		};
	}
}