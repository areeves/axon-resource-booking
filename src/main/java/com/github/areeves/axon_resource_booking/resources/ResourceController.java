package com.github.areeves.axon_resource_booking.resources;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.time.Instant;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/resources")
@Validated
public class ResourceController {

	private final CommandGateway commandGateway;
	private final ResourceRepository resourceRepository;
	private final ReservationRepository reservationRepository;
	private final CommandMetrics commandMetrics;

	public ResourceController(CommandGateway commandGateway, ResourceRepository resourceRepository,
			ReservationRepository reservationRepository, CommandMetrics commandMetrics) {
		this.commandGateway = commandGateway;
		this.resourceRepository = resourceRepository;
		this.reservationRepository = reservationRepository;
		this.commandMetrics = commandMetrics;
	}

	@GetMapping
	public List<ResourceEntity> getActiveResources() {
		return resourceRepository.findByStatus(ResourceStatus.ACTIVE);
	}

	@GetMapping("/available")
	public List<ResourceEntity> getAvailableResources(@RequestParam Instant start, @RequestParam Instant end) {
		validateTimeWindow(start, end);
		List<ReservationStatus> activeStatuses = List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
		return resourceRepository.findByStatus(ResourceStatus.ACTIVE).stream()
				.filter(resource -> reservationRepository
						.findByResourceIdAndStatusInAndStartLessThanAndEndGreaterThan(resource.getResourceId(), activeStatuses,
								end, start).size() < resource.getCapacity())
				.toList();
	}

	private void validateTimeWindow(Instant start, Instant end) {
		if (!end.isAfter(start)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "end must be after start");
		}
	}

	@GetMapping("/{resourceId}")
	public ResponseEntity<ResourceEntity> getResource(@PathVariable UUID resourceId) {
		return resourceRepository.findById(resourceId).map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping
	public CompletableFuture<ResponseEntity<Void>> create(@RequestHeader("X-User-Id") UUID userId,
			@Valid @RequestBody CreateResourceRequest request) {
		if (resourceRepository.existsByName(request.name())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Resource name already exists");
		}
		UUID resourceId = UUID.randomUUID();
		commandMetrics.increment("create_resource");
		CreateResourceCommand command = new CreateResourceCommand(resourceId, userId, request.name(),
				request.description(), request.capacity(), request.location());

		return commandGateway.send(command).thenApply(ignored -> ResponseEntity.created(URI.create("/resources/" + resourceId))
				.build());
	}

	@PutMapping("/{resourceId}")
	public CompletableFuture<ResponseEntity<Void>> update(@PathVariable UUID resourceId,
			@RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody UpdateResourceRequest request) {
		UpdateResourceCommand command = new UpdateResourceCommand(resourceId, userId, request.name(),
				request.description(), request.location());
		commandMetrics.increment("update_resource");

		return commandGateway.send(command).thenApply(ignored -> ResponseEntity.noContent().build());
	}

	@PostMapping("/{resourceId}/deactivate")
	public CompletableFuture<ResponseEntity<Void>> deactivate(@PathVariable UUID resourceId,
			@RequestHeader("X-User-Id") UUID userId) {
		commandMetrics.increment("deactivate_resource");
		return commandGateway.send(new DeactivateResourceCommand(resourceId, userId))
				.thenApply(ignored -> ResponseEntity.noContent().build());
	}

	@PostMapping("/{resourceId}/reactivate")
	public CompletableFuture<ResponseEntity<Void>> reactivate(@PathVariable UUID resourceId,
			@RequestHeader("X-User-Id") UUID userId) {
		commandMetrics.increment("reactivate_resource");
		return commandGateway.send(new ReactivateResourceCommand(resourceId, userId))
				.thenApply(ignored -> ResponseEntity.noContent().build());
	}

	@PostMapping("/{resourceId}/reservations")
	public CompletableFuture<ResponseEntity<Void>> reserve(@PathVariable UUID resourceId,
			@RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody ReserveResourceRequest request) {
		if (request.userId() != null && !request.userId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User identity in header and payload must match");
		}
		UUID reservationId = UUID.randomUUID();
		commandMetrics.increment("reserve_resource");
		ReserveResourceCommand command = new ReserveResourceCommand(resourceId, reservationId, userId,
				request.start(), request.end());
		return commandGateway.send(command).thenApply(ignored -> ResponseEntity
				.created(URI.create("/resources/" + resourceId + "/reservations/" + reservationId)).build());
	}

	@PostMapping("/{resourceId}/reservations/{reservationId}/cancel")
	public CompletableFuture<ResponseEntity<Void>> cancel(@PathVariable UUID resourceId,
			@PathVariable UUID reservationId, @RequestHeader("X-User-Id") UUID userId,
			Authentication authentication) {
		boolean admin = authentication.getAuthorities().stream()
				.anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
		commandMetrics.increment("cancel_reservation");
		return commandGateway.send(new CancelReservationCommand(resourceId, reservationId, userId, admin))
				.thenApply(ignored -> ResponseEntity.noContent().build());
	}

	@PostMapping("/{resourceId}/reservations/{reservationId}/confirm")
	public CompletableFuture<ResponseEntity<Void>> confirm(@PathVariable UUID resourceId,
			@PathVariable UUID reservationId, @RequestHeader("X-User-Id") UUID userId) {
		commandMetrics.increment("confirm_reservation");
		return commandGateway.send(new ConfirmReservationCommand(resourceId, reservationId, userId))
				.thenApply(ignored -> ResponseEntity.noContent().build());
	}
}