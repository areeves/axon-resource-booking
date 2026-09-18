package com.github.areeves.axon_resource_booking.resources;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/resources")
@Validated
public class ResourceController {

	private final CommandGateway commandGateway;
	private final ResourceRepository resourceRepository;

	public ResourceController(CommandGateway commandGateway, ResourceRepository resourceRepository) {
		this.commandGateway = commandGateway;
		this.resourceRepository = resourceRepository;
	}

	@GetMapping
	public List<ResourceEntity> getActiveResources() {
		return resourceRepository.findByStatus(ResourceStatus.ACTIVE);
	}

	@GetMapping("/{resourceId}")
	public ResponseEntity<ResourceEntity> getResource(@PathVariable UUID resourceId) {
		return resourceRepository.findById(resourceId).map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping
	public CompletableFuture<ResponseEntity<Void>> create(@Valid @RequestBody CreateResourceRequest request) {
		UUID resourceId = UUID.randomUUID();
		CreateResourceCommand command = new CreateResourceCommand(resourceId, request.name(), request.description(),
				request.capacity(), request.location());

		return commandGateway.send(command).thenApply(ignored -> ResponseEntity.created(URI.create("/resources/" + resourceId))
				.build());
	}

	@PutMapping("/{resourceId}")
	public CompletableFuture<ResponseEntity<Void>> update(@PathVariable UUID resourceId,
			@Valid @RequestBody UpdateResourceRequest request) {
		UpdateResourceCommand command = new UpdateResourceCommand(resourceId, request.name(), request.description(),
				request.location());

		return commandGateway.send(command).thenApply(ignored -> ResponseEntity.noContent().build());
	}

	@PostMapping("/{resourceId}/deactivate")
	public CompletableFuture<ResponseEntity<Void>> deactivate(@PathVariable UUID resourceId) {
		return commandGateway.send(new DeactivateResourceCommand(resourceId))
				.thenApply(ignored -> ResponseEntity.noContent().build());
	}

	@PostMapping("/{resourceId}/reactivate")
	public CompletableFuture<ResponseEntity<Void>> reactivate(@PathVariable UUID resourceId) {
		return commandGateway.send(new ReactivateResourceCommand(resourceId))
				.thenApply(ignored -> ResponseEntity.noContent().build());
	}
}