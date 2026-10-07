package com.github.areeves.axon_resource_booking.resources;

import java.net.URI;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

	private ResourceAvailabilityRules parseRules(Map<String, Object> payload) {
		if (payload == null || payload.isEmpty()) {
			return ResourceAvailabilityRules.empty();
		}
		String timezone = null;
		Object timezoneValue = payload.getOrDefault("timezone", payload.get("timeZone"));
		if (timezoneValue != null) {
			timezone = timezoneValue.toString();
		}
		Map<String, Object> rawPattern = null;
		for (String key : List.of("weeklyPattern", "pattern", "availabilityPattern")) {
			if (payload.containsKey(key) && payload.get(key) instanceof Map<?, ?> value) {
				rawPattern = (Map<String, Object>) value;
				break;
			}
		}
		Map<String, List<AvailabilityTimeRange>> weekly = new HashMap<>();
		if (rawPattern != null) {
			for (var entry : rawPattern.entrySet()) {
				Object value = entry.getValue();
				if (!(value instanceof List<?> list)) {
					continue;
				}
				List<AvailabilityTimeRange> ranges = new ArrayList<>();
				for (Object candidate : list) {
					if (candidate instanceof Map<?, ?> map) {
						Object startValue = map.get("start");
						Object endValue = map.get("end");
						if (startValue != null && endValue != null) {
							ranges.add(new AvailabilityTimeRange(LocalTime.parse(startValue.toString()), LocalTime.parse(endValue.toString())));
						}
					}
				}
				if (!ranges.isEmpty()) {
					weekly.put(entry.getKey().toString(), ranges);
				}
			}
		}
		return new ResourceAvailabilityRules(timezone, weekly, List.of(), List.of());
	}

	private AvailabilityWindow toWindow(Map<String, Object> payload, UUID resourceId, UUID userId, AvailabilityWindowType type) {
		Object startValue = payload.get("start");
		Object endValue = payload.get("end");
		if (startValue == null || endValue == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Window start and end are required");
		}
		UUID windowId = UUID.fromString(payload.getOrDefault("windowId", UUID.randomUUID()).toString());
		return new AvailabilityWindow(windowId, Instant.parse(startValue.toString()), Instant.parse(endValue.toString()), type,
				payload.get("reason") == null ? null : payload.get("reason").toString(),
				userId, Instant.now());
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

	@GetMapping("/{resourceId}/availability")
	public ResponseEntity<ResourceAvailabilityRules> getAvailability(@PathVariable UUID resourceId) {
		return resourceRepository.findById(resourceId)
				.map(resource -> ResponseEntity.ok(resource.getAvailabilityRules()))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/{resourceId}/availability/calendar")
	public ResponseEntity<Map<String, Object>> getAvailabilityCalendar(@PathVariable UUID resourceId,
			@RequestParam Instant start, @RequestParam Instant end) {
		validateTimeWindow(start, end);
		return resourceRepository.findById(resourceId)
				.map(resource -> {
					Map<String, Object> payload = new HashMap<>();
					payload.put("resourceId", resourceId.toString());
					payload.put("start", start.toString());
					payload.put("end", end.toString());
					payload.put("rules", resource.getAvailabilityRules());
					payload.put("fullyAvailable", ResourceAvailability.isFullyAvailable(resource.getAvailabilityRules(), start, end));
					return ResponseEntity.ok(payload);
				})
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/{resourceId}/availability")
	public CompletableFuture<ResponseEntity<Void>> setAvailability(@PathVariable UUID resourceId,
			@RequestHeader("X-User-Id") UUID userId, @RequestBody Map<String, Object> payload) {
		var rules = parseRules(payload);
		return commandGateway.send(new SetResourceAvailabilityCommand(resourceId, userId, rules.timezone(), rules.weeklyPattern()))
				.thenApply(ignored -> ResponseEntity.noContent().build());
	}

	@PutMapping("/{resourceId}/availability")
	public CompletableFuture<ResponseEntity<Void>> updateAvailability(@PathVariable UUID resourceId,
			@RequestHeader("X-User-Id") UUID userId, @RequestBody Map<String, Object> payload) {
		return setAvailability(resourceId, userId, payload);
	}

	@PostMapping("/{resourceId}/availability/weekly")
	public CompletableFuture<ResponseEntity<Void>> setWeeklyAvailability(@PathVariable UUID resourceId,
			@RequestHeader("X-User-Id") UUID userId, @RequestBody Map<String, Object> payload) {
		var rules = parseRules(payload);
		return commandGateway.send(new UpdateWeeklyAvailabilityPatternCommand(resourceId, userId, rules.timezone(), rules.weeklyPattern()))
				.thenApply(ignored -> ResponseEntity.noContent().build());
	}

	@DeleteMapping("/{resourceId}/availability")
	public CompletableFuture<ResponseEntity<Void>> clearAvailability(@PathVariable UUID resourceId,
			@RequestHeader("X-User-Id") UUID userId) {
		return commandGateway.send(new ClearResourceAvailabilityCommand(resourceId, userId))
				.thenApply(ignored -> ResponseEntity.noContent().build());
	}

	@PostMapping("/{resourceId}/availability/blackouts")
	public CompletableFuture<ResponseEntity<Void>> addBlackout(@PathVariable UUID resourceId,
			@RequestHeader("X-User-Id") UUID userId, @RequestBody Map<String, Object> payload) {
		var window = toWindow(payload, resourceId, userId, AvailabilityWindowType.BLACKOUT);
		return commandGateway.send(new AddResourceBlackoutCommand(resourceId, userId, window.windowId(), window.start(),
				window.end(), window.reason())).thenApply(ignored -> ResponseEntity.noContent().build());
	}

	@PostMapping("/{resourceId}/availability/blackout")
	public CompletableFuture<ResponseEntity<Void>> addBlackoutAlias(@PathVariable UUID resourceId,
			@RequestHeader("X-User-Id") UUID userId, @RequestBody Map<String, Object> payload) {
		return addBlackout(resourceId, userId, payload);
	}

	@PostMapping("/{resourceId}/availability/extra")
	public CompletableFuture<ResponseEntity<Void>> addExtraWindow(@PathVariable UUID resourceId,
			@RequestHeader("X-User-Id") UUID userId, @RequestBody Map<String, Object> payload) {
		var window = toWindow(payload, resourceId, userId, AvailabilityWindowType.EXTRA);
		return commandGateway.send(new AddResourceExtraAvailabilityCommand(resourceId, userId, window.windowId(), window.start(),
				window.end(), window.reason())).thenApply(ignored -> ResponseEntity.noContent().build());
	}

	@PostMapping("/{resourceId}/availability/extra-window")
	public CompletableFuture<ResponseEntity<Void>> addExtraWindowAlias(@PathVariable UUID resourceId,
			@RequestHeader("X-User-Id") UUID userId, @RequestBody Map<String, Object> payload) {
		return addExtraWindow(resourceId, userId, payload);
	}

	@DeleteMapping("/{resourceId}/availability/blackouts/{windowId}")
	public CompletableFuture<ResponseEntity<Void>> removeBlackout(@PathVariable UUID resourceId,
			@PathVariable UUID windowId, @RequestHeader("X-User-Id") UUID userId) {
		return commandGateway.send(new RemoveResourceBlackoutCommand(resourceId, userId, windowId))
				.thenApply(ignored -> ResponseEntity.noContent().build());
	}

	@DeleteMapping("/{resourceId}/availability/blackout/{windowId}")
	public CompletableFuture<ResponseEntity<Void>> removeBlackoutAlias(@PathVariable UUID resourceId,
			@PathVariable UUID windowId, @RequestHeader("X-User-Id") UUID userId) {
		return removeBlackout(resourceId, windowId, userId);
	}

	@DeleteMapping("/{resourceId}/availability/extra/{windowId}")
	public CompletableFuture<ResponseEntity<Void>> removeExtra(@PathVariable UUID resourceId,
			@PathVariable UUID windowId, @RequestHeader("X-User-Id") UUID userId) {
		return commandGateway.send(new RemoveResourceExtraAvailabilityCommand(resourceId, userId, windowId))
				.thenApply(ignored -> ResponseEntity.noContent().build());
	}

	@PostMapping("/{resourceId}/availability/clear")
	public CompletableFuture<ResponseEntity<Void>> clearAvailabilityAlias(@PathVariable UUID resourceId,
			@RequestHeader("X-User-Id") UUID userId) {
		return clearAvailability(resourceId, userId);
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