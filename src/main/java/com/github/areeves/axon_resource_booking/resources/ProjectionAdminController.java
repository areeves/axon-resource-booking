package com.github.areeves.axon_resource_booking.resources;

import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/projections")
public class ProjectionAdminController {

	private final ResourceRepository resourceRepository;
	private final ReservationRepository reservationRepository;
	private final EventProcessingConfiguration eventProcessingConfiguration;

	public ProjectionAdminController(ResourceRepository resourceRepository, ReservationRepository reservationRepository,
			EventProcessingConfiguration eventProcessingConfiguration) {
		this.resourceRepository = resourceRepository;
		this.reservationRepository = reservationRepository;
		this.eventProcessingConfiguration = eventProcessingConfiguration;
	}

	@PostMapping("/rebuild")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Void> rebuild() {
		resourceRepository.deleteAllInBatch();
		reservationRepository.deleteAllInBatch();
		TrackingEventProcessor processor = eventProcessingConfiguration
				.eventProcessor("resource-projections", TrackingEventProcessor.class)
				.orElseThrow(() -> new IllegalStateException("Projection processor is not tracking-enabled"));
		processor.shutDown();
		processor.resetTokens();
		processor.start();
		return ResponseEntity.accepted().build();
	}
}