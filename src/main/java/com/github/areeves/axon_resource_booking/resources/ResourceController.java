package com.github.areeves.axon_resource_booking.resources;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/resources")
@Validated
public class ResourceController {

	private final CommandGateway commandGateway;

	public ResourceController(CommandGateway commandGateway) {
		this.commandGateway = commandGateway;
	}

	@PostMapping
	public CompletableFuture<ResponseEntity<Void>> create(@Valid @RequestBody CreateResourceRequest request) {
		UUID resourceId = UUID.randomUUID();
		CreateResourceCommand command = new CreateResourceCommand(resourceId, request.name(), request.description(),
				request.capacity(), request.location());

		return commandGateway.send(command).thenApply(ignored -> ResponseEntity.created(URI.create("/resources/" + resourceId))
				.build());
	}
}