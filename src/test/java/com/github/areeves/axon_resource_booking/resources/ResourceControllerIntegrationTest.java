package com.github.areeves.axon_resource_booking.resources;

import java.math.BigDecimal;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.Duration;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "app.sample-data.enabled=false")
@AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ResourceControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CommandGateway commandGateway;

	@Autowired
	private MeterRegistry meterRegistry;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private ReservationRepository reservationRepository;

	@Autowired
	private ResourceUtilizationRepository utilizationRepository;

	@BeforeEach
	void clearReadModels() {
		utilizationRepository.deleteAll();
		reservationRepository.deleteAll();
		resourceRepository.deleteAll();
	}

	@Test
	void actuatorHealthAndReadinessEndpointsAreAvailable() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
	}

	@Test
	void tracksCommandsSentOutsideRestControllers() {
		commandGateway.sendAndWait(new CreateResourceCommand(UUID.randomUUID(), UUID.randomUUID(), "Direct command",
				null, 1, "Floor 1"));

		assertThat(meterRegistry.get("axon.commands.total")
				.tag("command", "create_resource")
				.counter()
				.count()).isEqualTo(1.0);
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
	void savesWeeklyAvailabilityAndProjectsItForRetrieval() throws Exception {
		UUID userId = UUID.randomUUID();
		var createResult = mockMvc.perform(post("/resources")
				.with(httpBasic("admin", "admin"))
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Availability Room","description":null,"capacity":1,"location":"Floor 1"}
						"""))
				.andExpect(request().asyncStarted())
				.andReturn();
		String location = mockMvc.perform(asyncDispatch(createResult))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getHeader("Location");
		UUID resourceId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(resourceRepository.findById(resourceId)).isPresent());

		var availabilityResult = mockMvc.perform(post("/resources/{resourceId}/availability/weekly", resourceId)
				.with(httpBasic("admin", "admin"))
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"timezone":"America/New_York","weeklyPattern":{"MONDAY":[{"start":"09:00","end":"17:00"}]}}
						"""))
				.andExpect(request().asyncStarted())
				.andReturn();
		mockMvc.perform(asyncDispatch(availabilityResult)).andExpect(status().isNoContent());

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
				assertThat(resourceRepository.findById(resourceId).orElseThrow().getAvailabilityRules().weeklyPattern())
						.containsKey("MONDAY"));

		var updateResult = mockMvc.perform(post("/resources/{resourceId}/availability/weekly", resourceId)
				.with(httpBasic("admin", "admin"))
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"timezone":"America/New_York","weeklyPattern":{"TUESDAY":[{"start":"10:00","end":"16:00"}]}}
						"""))
				.andExpect(request().asyncStarted())
				.andReturn();
		mockMvc.perform(asyncDispatch(updateResult)).andExpect(status().isNoContent());

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
				assertThat(resourceRepository.findById(resourceId).orElseThrow().getAvailabilityRules().weeklyPattern())
						.containsKey("TUESDAY")
						.doesNotContainKey("MONDAY"));
		mockMvc.perform(get("/resources/{resourceId}/availability", resourceId)
				.with(httpBasic("admin", "admin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.timezone").value("America/New_York"))
				.andExpect(jsonPath("$.weeklyPattern.TUESDAY[0].start").value("10:00:00"))
				.andExpect(jsonPath("$.weeklyPattern.TUESDAY[0].end").value("16:00:00"))
				.andExpect(jsonPath("$.weeklyPattern.MONDAY").doesNotExist());
	}

	@Test
	void updatesAndProjectsAnExistingBlackoutWindow() throws Exception {
		UUID resourceId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID windowId = UUID.randomUUID();
		Instant originalStart = Instant.now().plusSeconds(86_400);
		Instant originalEnd = originalStart.plusSeconds(3600);
		Instant updatedStart = originalStart.plusSeconds(7200);
		Instant updatedEnd = updatedStart.plusSeconds(3600);
		commandGateway.sendAndWait(new CreateResourceCommand(resourceId, userId, "Window Room", null, 1, "Floor 1"));
		commandGateway.sendAndWait(new UpdateWeeklyAvailabilityPatternCommand(resourceId, userId, "UTC", Map.of(), true));
		commandGateway.sendAndWait(new AddResourceBlackoutCommand(resourceId, userId, windowId, originalStart, originalEnd,
				"Maintenance", true));

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
				assertThat(resourceRepository.findById(resourceId).orElseThrow().getAvailabilityRules().blackouts()).hasSize(1));
		var updateResult = mockMvc.perform(put("/resources/{resourceId}/availability/blackouts/{windowId}", resourceId, windowId)
				.with(httpBasic("admin", "admin"))
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"start":"%s","end":"%s","reason":"Updated maintenance"}
						""".formatted(updatedStart, updatedEnd)))
				.andExpect(request().asyncStarted())
				.andReturn();
		mockMvc.perform(asyncDispatch(updateResult)).andExpect(status().isNoContent());
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			var blackout = resourceRepository.findById(resourceId).orElseThrow().getAvailabilityRules().blackouts().getFirst();
			assertThat(blackout.start()).isEqualTo(updatedStart);
			assertThat(blackout.reason()).isEqualTo("Updated maintenance");
		});
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
	void nonAdminMayOnlyActAsTheUuidBoundToItsBasicUser() throws Exception {
		UUID resourceId = UUID.randomUUID();
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
		UUID reservationId = UUID.randomUUID();
		Instant start = Instant.now().plusSeconds(3600);
		commandGateway.sendAndWait(new CreateResourceCommand(resourceId, userId, "User-owned Room", null, 1, "Floor 1"));
		commandGateway.sendAndWait(new ReserveResourceCommand(resourceId, reservationId, userId, start, start.plusSeconds(3600)));

		mockMvc.perform(post("/resources/{resourceId}/reservations/{reservationId}/cancel", resourceId, reservationId)
				.with(httpBasic(userId.toString(), "user"))
				.header("X-User-Id", UUID.randomUUID()))
				.andExpect(status().isForbidden());

		var cancelResult = mockMvc.perform(post("/resources/{resourceId}/reservations/{reservationId}/cancel", resourceId, reservationId)
				.with(httpBasic(userId.toString(), "user"))
				.header("X-User-Id", userId))
				.andExpect(request().asyncStarted())
				.andReturn();
		mockMvc.perform(asyncDispatch(cancelResult)).andExpect(status().isNoContent());
	}

	@Test
	void allowsReservationUserIdentityFromHeaderWithoutPayloadUserId() throws Exception {
		UUID userId = UUID.randomUUID();
		Instant start = Instant.now().plusSeconds(3600);
		Instant end = start.plusSeconds(3600);

		var createResult = mockMvc.perform(post("/resources")
				.with(httpBasic("admin", "admin"))
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Header User Room","description":"Training room","capacity":4,"location":"Floor 1"}
						"""))
				.andExpect(request().asyncStarted())
				.andReturn();
		String location = mockMvc.perform(asyncDispatch(createResult)).andReturn().getResponse().getHeader("Location");
		UUID resourceId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

		var reserveResult = mockMvc.perform(post("/resources/{resourceId}/reservations", resourceId)
				.with(httpBasic("admin", "admin"))
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"start":"%s","end":"%s"}
						""".formatted(start, end)))
				.andExpect(request().asyncStarted())
				.andReturn();

		mockMvc.perform(asyncDispatch(reserveResult)).andExpect(status().isCreated());
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
	void availabilitySearchRespectsRulesAndPeakConcurrentCapacity() throws Exception {
		UUID resourceWithCapacityId = UUID.randomUUID();
		UUID resourceOutsideHoursId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LocalDate monday = LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
		Instant start = monday.atTime(10, 0).toInstant(ZoneOffset.UTC);
		Instant midpoint = monday.atTime(11, 0).toInstant(ZoneOffset.UTC);
		Instant end = monday.atTime(12, 0).toInstant(ZoneOffset.UTC);
		var capacityResource = ResourceEntity.from(new ResourceCreatedEvent(resourceWithCapacityId, "Capacity Room", null, 2,
				"Floor 1", Instant.now()));
		capacityResource.setAvailabilityRules(new ResourceAvailabilityRules("UTC",
				Map.of("MONDAY", List.of(new AvailabilityTimeRange(LocalTime.of(9, 0), LocalTime.of(12, 0)))),
				List.of(), List.of()));
		resourceRepository.save(capacityResource);
		var closedResource = ResourceEntity.from(new ResourceCreatedEvent(resourceOutsideHoursId, "Closed Room", null, 2,
				"Floor 2", Instant.now()));
		closedResource.setAvailabilityRules(new ResourceAvailabilityRules("UTC",
				Map.of("MONDAY", List.of(new AvailabilityTimeRange(LocalTime.of(12, 0), LocalTime.of(17, 0)))),
				List.of(), List.of()));
		resourceRepository.save(closedResource);
		reservationRepository.save(ReservationEntity.from(new ReservationCreatedEvent(resourceWithCapacityId, UUID.randomUUID(), userId,
				start, midpoint, ReservationStatus.CONFIRMED, Instant.now())));
		reservationRepository.save(ReservationEntity.from(new ReservationCreatedEvent(resourceWithCapacityId, UUID.randomUUID(), userId,
				midpoint, end, ReservationStatus.CONFIRMED, Instant.now())));

		mockMvc.perform(get("/resources/available")
				.with(httpBasic("admin", "admin"))
				.param("start", start.toString()).param("end", end.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].resourceId", org.hamcrest.Matchers.hasItem(resourceWithCapacityId.toString())))
				.andExpect(jsonPath("$[*].resourceId", org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.hasItem(resourceOutsideHoursId.toString()))));
	}

	@Test
	void returnsAvailabilityCalendarAndCapacityAwareNextSlots() throws Exception {
		UUID resourceId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		LocalDate monday = LocalDate.of(2026, 6, 1);
		Instant start = monday.atTime(9, 0).toInstant(ZoneOffset.UTC);
		Instant occupiedStart = monday.atTime(10, 0).toInstant(ZoneOffset.UTC);
		Instant occupiedEnd = monday.atTime(11, 0).toInstant(ZoneOffset.UTC);
		var resource = ResourceEntity.from(new ResourceCreatedEvent(resourceId, "Calendar Room", null, 1,
				"Floor 1", Instant.now()));
		resource.setAvailabilityRules(new ResourceAvailabilityRules("UTC",
				Map.of("MONDAY", List.of(new AvailabilityTimeRange(LocalTime.of(9, 0), LocalTime.of(12, 0)))),
				List.of(), List.of()));
		resourceRepository.save(resource);
		reservationRepository.save(ReservationEntity.from(new ReservationCreatedEvent(resourceId, UUID.randomUUID(), userId,
				occupiedStart, occupiedEnd, ReservationStatus.CONFIRMED, Instant.now())));

		mockMvc.perform(get("/resources/{resourceId}/availability/calendar", resourceId)
				.with(httpBasic("admin", "admin"))
				.param("from", monday.toString()).param("to", monday.toString()).param("slotMinutes", "60"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].slots[8].bookable").value(false))
				.andExpect(jsonPath("$[0].slots[9].bookable").value(true))
				.andExpect(jsonPath("$[0].slots[9].remainingCapacity").value(1))
				.andExpect(jsonPath("$[0].slots[10].remainingCapacity").value(0));

		mockMvc.perform(get("/resources/{resourceId}/availability/next-slots", resourceId)
				.with(httpBasic("admin", "admin"))
				.param("after", start.toString()).param("durationMinutes", "60").param("count", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].start").value(start.toString()))
				.andExpect(jsonPath("$[1].start").value(monday.atTime(11, 0).toInstant(ZoneOffset.UTC).toString()));
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

	@Test
	void inspectsTheFullEventStreamForAnAggregate() throws Exception {
		UUID resourceId = UUID.randomUUID();
		commandGateway.sendAndWait(new CreateResourceCommand(resourceId, UUID.randomUUID(), "Event Room", null, 1, "Floor 1"));

		mockMvc.perform(get("/admin/events/{aggregateId}", resourceId).with(httpBasic("admin", "admin")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].sequenceNumber").value(0))
				.andExpect(jsonPath("$[0].eventType").value("ResourceCreatedEvent"))
				.andExpect(jsonPath("$[0].payload.name").value("Event Room"));
	}

	@Test
	void reportsDailyUtilizationAcrossUtcMidnightAndRemovesCancelledReservations() throws Exception {
		UUID userId = UUID.randomUUID();
		var createResult = mockMvc.perform(post("/resources")
				.with(httpBasic("admin", "admin"))
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Utilization Room","description":null,"capacity":2,"location":"Floor 1"}
						"""))
				.andExpect(request().asyncStarted())
				.andReturn();
		String location = mockMvc.perform(asyncDispatch(createResult))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getHeader("Location");
		UUID resourceId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(resourceRepository.findById(resourceId)).isPresent());

		LocalDate firstDay = LocalDate.now(ZoneOffset.UTC).plusDays(2);
		Instant start = firstDay.atTime(23, 0).toInstant(ZoneOffset.UTC);
		Instant end = firstDay.plusDays(1).atTime(1, 0).toInstant(ZoneOffset.UTC);
		var reserveResult = mockMvc.perform(post("/resources/{resourceId}/reservations", resourceId)
				.with(httpBasic("admin", "admin"))
				.header("X-User-Id", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"userId":"%s","start":"%s","end":"%s"}
						""".formatted(userId, start, end)))
				.andExpect(request().asyncStarted())
				.andReturn();
		String reservationLocation = mockMvc.perform(asyncDispatch(reserveResult))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getHeader("Location");
		String[] reservationPath = reservationLocation.split("/");
		UUID reservationId = UUID.fromString(reservationPath[reservationPath.length - 1]);

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(utilizationRepository.count()).isEqualTo(2));
		mockMvc.perform(get("/resources/{resourceId}/utilization", resourceId)
				.with(httpBasic("admin", "admin"))
				.param("from", firstDay.toString())
				.param("to", firstDay.plusDays(1).toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].reservationCount").value(1))
				.andExpect(jsonPath("$[0].bookableHours").value(24.0))
				.andExpect(jsonPath("$[0].occupiedHours").value(1.0))
				.andExpect(jsonPath("$[0].utilizationPercent").value(2.08))
				.andExpect(jsonPath("$[1].reservationCount").value(1))
				.andExpect(jsonPath("$[1].bookableHours").value(24.0))
				.andExpect(jsonPath("$[1].occupiedHours").value(1.0));

		var cancelResult = mockMvc.perform(post("/resources/{resourceId}/reservations/{reservationId}/cancel", resourceId, reservationId)
				.with(httpBasic("admin", "admin"))
				.header("X-User-Id", userId))
				.andExpect(request().asyncStarted())
				.andReturn();
		mockMvc.perform(asyncDispatch(cancelResult)).andExpect(status().isNoContent());
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(utilizationRepository.findAll())
				.allSatisfy(utilization -> assertThat(utilization.getReservationCount()).isZero()));
		mockMvc.perform(get("/resources/{resourceId}/utilization", resourceId)
				.with(httpBasic("admin", "admin"))
				.param("from", firstDay.toString())
				.param("to", firstDay.plusDays(1).toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].occupiedHours").value(0.0))
				.andExpect(jsonPath("$[1].occupiedHours").value(0.0));
	}

	@Test
	void utilizationUsesScheduledBookableHoursAsItsCapacityDenominator() throws Exception {
		UUID resourceId = UUID.randomUUID();
		LocalDate monday = LocalDate.of(2026, 6, 1);
		var resource = ResourceEntity.from(new ResourceCreatedEvent(resourceId, "Scheduled Room", null, 1,
				"Floor 1", Instant.now()));
		resource.setAvailabilityRules(new ResourceAvailabilityRules("UTC",
				Map.of("MONDAY", List.of(new AvailabilityTimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0)))),
				List.of(), List.of()));
		resourceRepository.save(resource);
		var utilization = new ResourceUtilizationEntity(resourceId, monday);
		utilization.adjust(BigDecimal.valueOf(7200), 1);
		utilizationRepository.save(utilization);

		mockMvc.perform(get("/resources/{resourceId}/utilization", resourceId)
				.with(httpBasic("admin", "admin"))
				.param("from", monday.toString()).param("to", monday.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].bookableHours").value(8.0))
				.andExpect(jsonPath("$[0].occupiedHours").value(2.0))
				.andExpect(jsonPath("$[0].utilizationPercent").value(25.0));
	}
}