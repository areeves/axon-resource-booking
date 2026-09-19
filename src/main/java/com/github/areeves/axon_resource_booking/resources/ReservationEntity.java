package com.github.areeves.axon_resource_booking.resources;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservations")
public class ReservationEntity {

	@Id
	private UUID reservationId;

	@Column(nullable = false)
	private UUID resourceId;

	@Column(nullable = false)
	private UUID userId;

	@Column(name = "start_time", nullable = false)
	private Instant start;

	@Column(name = "end_time", nullable = false)
	private Instant end;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReservationStatus status;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant lastModifiedAt;

	protected ReservationEntity() {
	}

	private ReservationEntity(ReservationCreatedEvent event) {
		reservationId = event.reservationId();
		resourceId = event.resourceId();
		userId = event.userId();
		start = event.start();
		end = event.end();
		status = event.status();
		createdAt = event.createdAt();
		lastModifiedAt = event.createdAt();
	}

	public static ReservationEntity from(ReservationCreatedEvent event) {
		return new ReservationEntity(event);
	}

	public UUID getReservationId() { return reservationId; }
	public UUID getResourceId() { return resourceId; }
	public UUID getUserId() { return userId; }
	public Instant getStart() { return start; }
	public Instant getEnd() { return end; }
	public ReservationStatus getStatus() { return status; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getLastModifiedAt() { return lastModifiedAt; }

	public void apply(ReservationCancelledEvent event) {
		status = ReservationStatus.CANCELLED;
		lastModifiedAt = event.lastModifiedAt();
	}

	public void apply(ReservationConfirmedEvent event) {
		status = ReservationStatus.CONFIRMED;
		lastModifiedAt = event.lastModifiedAt();
	}
}