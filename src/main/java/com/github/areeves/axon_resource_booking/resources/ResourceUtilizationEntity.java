package com.github.areeves.axon_resource_booking.resources;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "resource_daily_utilization", uniqueConstraints = @UniqueConstraint(columnNames = { "resource_id",
		"utilization_date" }))
public class ResourceUtilizationEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private UUID resourceId;

	@Column(name = "utilization_date", nullable = false)
	private LocalDate utilizationDate;

	@Column(nullable = false, precision = 20, scale = 9)
	private BigDecimal occupiedSeconds = BigDecimal.ZERO;

	@Column(nullable = false)
	private long reservationCount;

	protected ResourceUtilizationEntity() {
	}

	public ResourceUtilizationEntity(UUID resourceId, LocalDate utilizationDate) {
		this.resourceId = resourceId;
		this.utilizationDate = utilizationDate;
	}

	public UUID getResourceId() {
		return resourceId;
	}

	public LocalDate getUtilizationDate() {
		return utilizationDate;
	}

	public BigDecimal getOccupiedSeconds() {
		return occupiedSeconds;
	}

	public long getReservationCount() {
		return reservationCount;
	}

	public void adjust(BigDecimal secondsDelta, long reservationCountDelta) {
		BigDecimal updatedOccupiedSeconds = occupiedSeconds.add(secondsDelta);
		long updatedReservationCount = reservationCount + reservationCountDelta;
		if (updatedOccupiedSeconds.signum() < 0 || updatedReservationCount < 0) {
			throw new IllegalStateException("Utilization projection cannot contain negative totals");
		}
		occupiedSeconds = updatedOccupiedSeconds;
		reservationCount = updatedReservationCount;
	}
}