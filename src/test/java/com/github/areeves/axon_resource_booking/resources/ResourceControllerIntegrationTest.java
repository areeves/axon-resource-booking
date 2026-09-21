package com.github.areeves.axon_resource_booking.resources;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@AutoConfigureMockMvc
class ResourceControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private ReservationRepository reservationRepository;

	@BeforeEach
	void clearReadModels() {
		reservationRepository.deleteAll();
		resourceRepository.deleteAll();
	}

	@Test
	void actuatorHealthEndpointIsAvailable() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void createsResourceAndProjectsItToDatabase() throws Exception {
		var result = mockMvc.perform(post("/resources")
				.with(httpBasic("admin", "admin"))
				.header("X-User-Id", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Room A","description":"Training room","capacity":4,"location":"Floor 1"}
						"""))
				.andExpect(request().asyncStarted())
				.andReturn();

		mockMvc.perform(asyncDispatch(result))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/resources/.+")));

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(resourceRepository.count()).isEqualTo(1));
	}

	@Test
	void rejectsDuplicateResourceName() throws Exception {
		resourceRepository.save(ResourceEntity.from(new ResourceCreatedEvent(UUID.randomUUID(), "Room A", null, 4,
				"Floor 1", Instant.now())));

		mockMvc.perform(post("/resources")
				.with(httpBasic("admin", "admin"))
				.header("X-User-Id", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Room A","description":"Another room","capacity":2,"location":"Floor 2"}
						"""))
				.andExpect(status().isConflict());
	}

	@Test
	void rebuildsProjectionsThroughAdminEndpoint() throws Exception {
		mockMvc.perform(post("/admin/projections/rebuild").with(httpBasic("admin", "admin")))
				.andExpect(status().isAccepted());
	}

	@Test
	void requiresUserIdentityHeaderForCreateResource() throws Exception {
		mockMvc.perform(post("/resources")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Room A","description":"Training room","capacity":4,"location":"Floor 1"}
						"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void requiresUserIdentityHeaderForReserveResource() throws Exception {
		UUID resourceId = UUID.randomUUID();
		resourceRepository.save(ResourceEntity.from(new ResourceCreatedEvent(resourceId, "Room A", null, 4,
				"Floor 1", Instant.now())));

		mockMvc.perform(post("/resources/{resourceId}/reservations", resourceId)
				.with(httpBasic("admin", "admin"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"userId":"%s","start":"%s","end":"%s"}
						""".formatted(UUID.randomUUID(), Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void retrievesOnlyActiveResources() throws Exception {
		UUID activeResourceId = UUID.randomUUID();
		UUID inactiveResourceId = UUID.randomUUID();
		Instant createdAt = Instant.now();
		resourceRepository.save(ResourceEntity.from(new ResourceCreatedEvent(activeResourceId, "Active Room", null, 4,
				"Floor 1", createdAt)));
		ResourceEntity inactiveResource = ResourceEntity.from(new ResourceCreatedEvent(inactiveResourceId, "Inactive Room", null, 2,
				"Floor 2", createdAt));
		inactiveResource.apply(new ResourceDeactivatedEvent(inactiveResourceId, createdAt));
		resourceRepository.save(inactiveResource);

		mockMvc.perform(get("/resources").with(httpBasic("admin", "admin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].resourceId", org.hamcrest.Matchers.hasItem(activeResourceId.toString())))
				.andExpect(jsonPath("$[*].resourceId", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(inactiveResourceId.toString()))));
	}

	@Test
	void findsOnlyResourcesWithRemainingCapacity() throws Exception {
		UUID fullResourceId = UUID.randomUUID();
		UUID availableResourceId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		Instant start = Instant.now().plusSeconds(3600);
		Instant end = start.plusSeconds(3600);
		resourceRepository.save(ResourceEntity.from(new ResourceCreatedEvent(fullResourceId, "Full Room", null, 1,
				"Floor 1", Instant.now())));
		resourceRepository.save(ResourceEntity.from(new ResourceCreatedEvent(availableResourceId, "Available Room", null, 2,
				"Floor 1", Instant.now())));
		reservationRepository.save(ReservationEntity.from(new ReservationCreatedEvent(fullResourceId, UUID.randomUUID(), userId,
				start, end, ReservationStatus.CONFIRMED, Instant.now())));

		mockMvc.perform(get("/resources/available")
				.with(httpBasic("admin", "admin"))
				.param("start", start.toString()).param("end", end.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].resourceId", org.hamcrest.Matchers.hasItem(availableResourceId.toString())))
				.andExpect(jsonPath("$[*].resourceId", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(fullResourceId.toString()))));
	}

	@Test
	void queriesReservationByUserResourceAndId() throws Exception {
		UUID reservationId = UUID.randomUUID();
		UUID resourceId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		Instant start = Instant.now().plusSeconds(3600);
		ReservationEntity reservation = ReservationEntity.from(new ReservationCreatedEvent(resourceId, reservationId, userId,
				start, start.plusSeconds(3600), ReservationStatus.PENDING, Instant.now()));
		reservationRepository.save(reservation);

		mockMvc.perform(get("/reservations/users/{userId}", userId).with(httpBasic("admin", "admin"))).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].reservationId").value(reservationId.toString()));
		mockMvc.perform(get("/reservations/resources/{resourceId}", resourceId).with(httpBasic("admin", "admin"))).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].reservationId").value(reservationId.toString()));
		mockMvc.perform(get("/reservations/{reservationId}", reservationId).with(httpBasic("admin", "admin"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(userId.toString()));
	}
}