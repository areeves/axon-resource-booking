package com.github.areeves.axon_resource_booking.resources;

import java.util.UUID;
import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<ReservationEntity, UUID> {

	List<ReservationEntity> findByUserIdOrderByStartAsc(UUID userId);

	List<ReservationEntity> findByResourceIdOrderByStartAsc(UUID resourceId);

	List<ReservationEntity> findByResourceIdAndStatusInAndStartLessThanAndEndGreaterThan(UUID resourceId,
			List<ReservationStatus> statuses, Instant end, Instant start);
}