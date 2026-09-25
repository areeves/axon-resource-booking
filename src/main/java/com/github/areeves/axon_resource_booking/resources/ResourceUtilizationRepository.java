package com.github.areeves.axon_resource_booking.resources;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceUtilizationRepository extends JpaRepository<ResourceUtilizationEntity, Long> {

	Optional<ResourceUtilizationEntity> findByResourceIdAndUtilizationDate(UUID resourceId, LocalDate utilizationDate);

	List<ResourceUtilizationEntity> findByResourceIdAndUtilizationDateBetweenOrderByUtilizationDateAsc(UUID resourceId,
			LocalDate from, LocalDate to);
}