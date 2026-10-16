package com.github.areeves.axon_resource_booking.resources;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.axonframework.eventsourcing.eventstore.EventStore;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/events")
public class AggregateEventQueryController {
	private final EventStore eventStore;

	public AggregateEventQueryController(EventStore eventStore) {
		this.eventStore = eventStore;
	}

	@GetMapping("/{aggregateId}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<List<AggregateEventView>> getAggregateEvents(@PathVariable UUID aggregateId) {
		List<AggregateEventView> events = eventStore.readEvents(aggregateId.toString()).asStream()
				.map(message -> new AggregateEventView(message.getSequenceNumber(), message.getTimestamp(),
						message.getPayloadType().getSimpleName(), message.getPayload()))
				.toList();
		return events.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(events);
	}

	public record AggregateEventView(long sequenceNumber, Instant timestamp, String eventType, Object payload) {
	}
}